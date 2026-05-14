#include "cir-tac/CirToLlvmIr.h"
#include "cir-tac/Llvm16Compat.h"
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

} // namespace

int main(int argc, char **argv) {
  if (argc != 3) {
    llvm::errs() << "usage: cir-klee <input.cir> <trace.pb>\n"
                    "  Lowers CIR to LLVM IR, assembles with llvm-as-16, runs "
                    "KLEE (--output-dir=/dev/null).\n"
                    "  Entry point is taken from trace.entry_point_name. "
                    "Requires KLEE_BIN in the environment.\n";
    return 2;
  }

  trace::Trace Pb;
  {
    std::ifstream In(argv[2], std::ios::binary);
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

  llvm::LLVMContext LlvmCtx;
  std::unique_ptr<llvm::Module> LlvmMod =
      lowerCirToLlvmIr(*OwningModule, LlvmCtx);
  if (!LlvmMod) {
    llvm::errs() << "error: CIR→LLVM lowering failed\n";
    return 1;
  }
  prepareLlvmModuleForLlvm16(*LlvmMod);

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
