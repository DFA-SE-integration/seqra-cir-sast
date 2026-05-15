#include "cir-tac/CirToLlvmIr.h"
#include "cir-tac/Llvm16Compat.h"
#include "cir-tac/StampSeqraOpIdsForTraceGuide.h"
#include "TraceGuidePass.h"
#include "proto/trace.pb.h"

#include <clang/CIR/Dialect/IR/CIRDialect.h>

#include "llvm/ADT/StringRef.h"
#include "llvm/IR/LLVMContext.h"
#include "llvm/IR/Module.h"
#include "llvm/Support/FileSystem.h"
#include "llvm/Support/Path.h"
#include "llvm/Support/Program.h"
#include "llvm/Support/raw_ostream.h"

#include <mlir/Dialect/DLTI/DLTI.h>
#include <mlir/Dialect/Func/IR/FuncOps.h>
#include <mlir/Dialect/LLVMIR/LLVMDialect.h>
#include <mlir/IR/BuiltinOps.h>
#include <mlir/IR/Dialect.h>
#include <mlir/IR/MLIRContext.h>
#include <mlir/Parser/Parser.h>

#include <chrono>
#include <cstdlib>
#include <fstream>
#include <string>
#include <vector>

namespace {

static constexpr llvm::StringLiteral kLlvmAs = "/usr/bin/llvm-as-16";

struct TempPath {
  llvm::SmallString<256> Path;
  bool Valid = false;

  ~TempPath() {
    if (Valid)
      llvm::sys::fs::remove(Path);
  }
};

static bool cirFuncHasBody(cir::FuncOp f) { return !f.getFunctionBody().empty(); }

static cir::FuncOp findFuncBySym(mlir::ModuleOp m, llvm::StringRef sym) {
  for (mlir::Operation &op : *m.getBody()) {
    if (auto fn = mlir::dyn_cast<cir::FuncOp>(&op))
      if (fn.getSymName() == sym)
        return fn;
  }
  return cir::FuncOp();
}

/// Move top-level ops from \p extra into \p primary. When both modules define
/// the same `cir.func` symbol, keep a single definition (Juliet `_a` decl +
/// `_b` body).
static bool mergeExtraModuleIntoPrimary(mlir::ModuleOp primary,
                                        mlir::ModuleOp extra) {
  mlir::Block &pBlock = *primary.getBody();
  llvm::SmallVector<mlir::Operation *, 32> extraOps;
  for (mlir::Operation &op : extra.getBody()->without_terminator())
    extraOps.push_back(&op);

  for (mlir::Operation *op : extraOps) {
    auto fn = mlir::dyn_cast<cir::FuncOp>(op);
    if (!fn) {
      op->moveBefore(&pBlock, pBlock.end());
      continue;
    }
    cir::FuncOp conflict = findFuncBySym(primary, fn.getSymName());
    if (conflict) {
      if (!cirFuncHasBody(conflict) && cirFuncHasBody(fn)) {
        conflict.erase();
      } else if (cirFuncHasBody(conflict) && !cirFuncHasBody(fn)) {
        fn.erase();
        continue;
      } else if (!cirFuncHasBody(conflict) && !cirFuncHasBody(fn)) {
        fn.erase();
        continue;
      } else {
        fn.erase();
        continue;
      }
    }
    op->moveBefore(&pBlock, pBlock.end());
  }
  return true;
}

} // namespace

int main(int argc, char **argv) {
  if (argc < 3) {
    llvm::errs() << "usage: cir-klee <input.cir> [<more.cir>...] <trace.pb>\n"
                    "  Lowers CIR to LLVM IR, assembles with llvm-as-16, runs "
                    "KLEE (--output-dir=/dev/null).\n"
                    "  Extra .cir files are merged into the first (Juliet _a + "
                    "_b split).\n"
                    "  Entry point is taken from trace.entry_point_name. "
                    "Requires KLEE_BIN in the environment.\n";
    return 2;
  }

  trace::Trace Pb;
  {
    std::ifstream In(argv[argc - 1], std::ios::binary);
    if (!In || !Pb.ParseFromIstream(&In)) {
      llvm::errs() << "error: failed to parse trace.pb\n";
      return 1;
    }
  }

  const std::string &EntryName = Pb.entry_point_name();
  if (EntryName.empty()) {
    llvm::errs() << "error: empty entry_point_name in trace.pb\n";
    return 1;
  }

  const char *KleeBin = std::getenv("KLEE_BIN");
  if (!KleeBin || !*KleeBin) {
    llvm::errs() << "error: KLEE_BIN is not set\n";
    return 1;
  }

  mlir::MLIRContext Context;
  mlir::DialectRegistry Registry;
  Registry.insert<cir::CIRDialect, mlir::DLTIDialect, mlir::LLVM::LLVMDialect,
                  mlir::func::FuncDialect>();
  Context.appendDialectRegistry(Registry);
  Context.allowUnregisteredDialects();

  mlir::ParserConfig ParseConfig(&Context);
  auto OwningModule =
      mlir::parseSourceFile<mlir::ModuleOp>(argv[1], ParseConfig);
  if (!OwningModule) {
    llvm::errs() << "error: failed to parse CIR module\n";
    return 1;
  }

  for (int i = 2; i < argc - 1; ++i) {
    auto Extra = mlir::parseSourceFile<mlir::ModuleOp>(argv[i], ParseConfig);
    if (!Extra) {
      llvm::errs() << "error: failed to parse CIR module: " << argv[i] << "\n";
      return 1;
    }
    if (!mergeExtraModuleIntoPrimary(*OwningModule, *Extra)) {
      llvm::errs() << "error: failed to merge CIR module: " << argv[i] << "\n";
      return 1;
    }
  }
  if (argc > 3)
    llvm::errs() << "cir-klee: merged " << (argc - 3) << " extra CIR module(s)\n";

  stampSeqraOpIdsForTraceGuide(*OwningModule);

  llvm::LLVMContext LlvmCtx;
  std::unique_ptr<llvm::Module> LlvmMod =
      lowerCirToLlvmIr(*OwningModule, LlvmCtx);
  if (!LlvmMod) {
    llvm::errs() << "error: CIR→LLVM lowering failed\n";
    return 1;
  }
  prepareLlvmModuleForLlvm16(*LlvmMod);

  auto TraceGuideT0 = std::chrono::steady_clock::now();
  bool TraceGuideOk = runTraceGuidePass(*LlvmMod, Pb);
  auto TraceGuideT1 = std::chrono::steady_clock::now();
  using std::chrono::duration;
  double TraceGuideMs =
      duration<double, std::milli>(TraceGuideT1 - TraceGuideT0).count();
  llvm::errs() << "runTraceGuidePass: " << TraceGuideMs << " ms\n";
  if (!TraceGuideOk) {
    llvm::errs() << "error: TraceGuidePass failed\n";
    return 1;
  }

  TempPath LlTmp;
  if (std::error_code EC =
          llvm::sys::fs::createTemporaryFile("cirklee", "ll", LlTmp.Path)) {
    llvm::errs() << "error: " << EC.message() << "\n";
    return 1;
  }
  LlTmp.Valid = true;

  {
    std::error_code EC;
    llvm::raw_fd_ostream OS(LlTmp.Path, EC, llvm::sys::fs::OF_Text);
    if (EC) {
      llvm::errs() << "error: " << EC.message() << "\n";
      return 1;
    }
    LlvmMod->print(OS, nullptr);
    OS.flush();
  }

  TempPath BcTmp;
  if (std::error_code EC =
          llvm::sys::fs::createTemporaryFile("cirklee", "bc", BcTmp.Path)) {
    llvm::errs() << "error: " << EC.message() << "\n";
    return 1;
  }
  BcTmp.Valid = true;

  std::string LlvmAsErr;
  if (!runLlvmAs(kLlvmAs, LlTmp.Path, BcTmp.Path, LlvmAsErr)) {
    llvm::errs() << "error: llvm-as: " << LlvmAsErr << "\n";
    return 1;
  }

  std::string EntryArg = std::string("--entry-point=") + EntryName;
  std::vector<std::string> Storage;
  Storage.reserve(4);
  Storage.push_back(std::string(llvm::sys::path::filename(KleeBin)));
//  Storage.push_back("--output-dir=/dev/null");
  Storage.push_back(std::move(EntryArg));
  Storage.push_back(std::string(llvm::StringRef(BcTmp.Path)));

  llvm::SmallVector<llvm::StringRef, 8> Args;
  for (auto &S : Storage)
    Args.push_back(S);

  std::string ExecErr;
  int RC = llvm::sys::ExecuteAndWait(KleeBin, Args, std::nullopt, {}, 0, 0,
                                     &ExecErr);
  if (!ExecErr.empty())
    llvm::errs() << ExecErr << "\n";

  if (RC < 0)
    return 1;
  return RC;
}
