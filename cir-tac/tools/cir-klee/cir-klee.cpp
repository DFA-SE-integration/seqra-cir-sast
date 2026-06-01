#include "cir-tac/CirToLlvmIr.h"
#include "cir-tac/Llvm16Compat.h"
#include "cir-tac/StampSeqraOpIdsForTraceGuide.h"
#include "TraceAssertPass.h"
#include "TraceGuidePass.h"
#include "proto/result.pb.h"
#include "proto/trace.pb.h"

#include <clang/CIR/Dialect/IR/CIRDialect.h>

#include "llvm/ADT/SmallString.h"
#include "llvm/ADT/StringRef.h"
#include "llvm/IR/IRBuilder.h"
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
#include <mlir/IR/SymbolTable.h>
#include <mlir/Parser/Parser.h>

#include <chrono>
#include <cstdlib>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <regex>
#include <string>
#include <vector>

namespace {

using Clock = std::chrono::steady_clock;
template <typename T0, typename T1> double ms(T0 a, T1 b) {
  return std::chrono::duration<double, std::milli>(b - a).count();
}

static std::string resolveLlvmAs() {
  if (const char *E = std::getenv("LLVM_AS_16_BIN"); E && *E)
    return std::string(E);
  return "/usr/bin/llvm-as-16";
}

struct TempPath {
  llvm::SmallString<256> Path;
  bool Valid = false;

  ~TempPath() {
    if (Valid)
      llvm::sys::fs::remove(Path);
  }
};

/// KLEE `--output-dir` under a uniquely named subdirectory; removed on exit unless
/// `Preserve` is set (typically from `CIR_KLEE_KEEP_OUTPUT`).
struct TempDir {
  llvm::SmallString<256> Path;
  bool Valid = false;
  bool Preserve = false;

  ~TempDir() {
    if (!Valid || Preserve)
      return;
    llvm::sys::fs::remove_directories(Path, /* IgnoreErrors=*/true);
  }
};

static bool cirKleeKeepOutputEnv() {
  const char *Raw = std::getenv("CIR_KLEE_KEEP_OUTPUT");
  if (!Raw || !Raw[0])
    return false;
  llvm::StringRef S(Raw);
  S = S.trim();
  return S.equals_insensitive("1") || S.equals_insensitive("true") ||
         S.equals_insensitive("yes") || S.equals_insensitive("on");
}

static bool cirFuncHasBody(cir::FuncOp f) { return !f.getFunctionBody().empty(); }

static cir::FuncOp findFuncBySym(mlir::ModuleOp m, llvm::StringRef sym) {
  for (mlir::Operation &op : *m.getBody()) {
    if (auto fn = mlir::dyn_cast<cir::FuncOp>(&op))
      if (fn.getSymName() == sym)
        return fn;
  }
  return cir::FuncOp();
}

static mlir::Operation *findTopLevelSymbolByName(mlir::ModuleOp m,
                                                 llvm::StringRef sym) {
  for (mlir::Operation &op : *m.getBody()) {
    auto attr = op.getAttrOfType<mlir::StringAttr>("sym_name");
    if (attr && attr.getValue() == sym)
      return &op;
  }
  return nullptr;
}

/// Move top-level ops from \p extra into \p primary. When both modules define
/// the same `cir.func` symbol, keep a single definition (Juliet `_a` decl +
/// `_b` body). For other duplicate top-level symbols, keep both copies by
/// renaming the extra symbol and its internal references (Juliet split files
/// duplicate private support globals such as `.str`, often with different
/// array types).
static bool mergeExtraModuleIntoPrimary(mlir::ModuleOp primary,
                                        mlir::ModuleOp extra) {
  mlir::Block &pBlock = *primary.getBody();
  mlir::MLIRContext *ctx = primary.getContext();
  unsigned renamedSymbols = 0;
  llvm::SmallVector<mlir::Operation *, 32> extraOps;
  for (mlir::Operation &op : extra.getBody()->without_terminator())
    extraOps.push_back(&op);

  for (mlir::Operation *op : extraOps) {
    auto fn = mlir::dyn_cast<cir::FuncOp>(op);
    if (!fn) {
      if (auto attr = op->getAttrOfType<mlir::StringAttr>("sym_name")) {
        if (findTopLevelSymbolByName(primary, attr.getValue())) {
          std::string oldName = attr.getValue().str();
          std::string newName;
          do {
            newName =
                oldName + "__seqra_merge_" + std::to_string(renamedSymbols++);
          } while (findTopLevelSymbolByName(primary, newName) ||
                   findTopLevelSymbolByName(extra, newName));

          auto newAttr = mlir::StringAttr::get(ctx, newName);
          if (mlir::failed(
                  mlir::SymbolTable::replaceAllSymbolUses(attr, newAttr, extra))) {
            llvm::errs() << "error: failed to rename duplicate symbol "
                         << oldName << " to " << newName << "\n";
            return false;
          }
          op->setAttr(mlir::SymbolTable::getSymbolAttrName(), newAttr);
        }
      }
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

// Parse the human-readable `<output-dir>/info` file KLEE writes. The format is
// "Key = Value" or "Key: Value" lines; we look for instruction / path counters
// using forgiving regexes (the field names vary slightly across KLEE versions).
static void parseKleeInfo(llvm::StringRef OutputDir, trace::KleeResult &Result) {
  llvm::SmallString<256> InfoPath(OutputDir);
  llvm::sys::path::append(InfoPath, "info");
  std::ifstream In(InfoPath.c_str());
  if (!In)
    return;

  static const std::regex InstrRe(R"(^\s*(?:KLEE:\s+done:\s+)?(?:total\s+)?[Ii]nstructions\s*[:=]\s*([0-9]+))");
  static const std::regex CompletedRe(R"(^\s*(?:KLEE:\s+done:\s+)?[Cc]ompleted\s+paths\s*[:=]\s*([0-9]+))");
  static const std::regex ExploredRe(R"(^\s*(?:KLEE:\s+done:\s+)?[Ee]xplored\s+paths\s*[:=]\s*([0-9]+))");

  std::string Line;
  while (std::getline(In, Line)) {
    std::smatch M;
    if (Result.klee_instructions() == 0 && std::regex_search(Line, M, InstrRe))
      Result.set_klee_instructions(std::stoull(M[1].str()));
    if (Result.klee_completed_paths() == 0 && std::regex_search(Line, M, CompletedRe))
      Result.set_klee_completed_paths(std::stoull(M[1].str()));
    if (Result.klee_explored_paths() == 0 && std::regex_search(Line, M, ExploredRe))
      Result.set_klee_explored_paths(std::stoull(M[1].str()));
  }
}

// Scan KLEE's output directory for the `*.err` files it drops per error. Two
// kinds drive the verdict:
//   * native memory errors (`*.ptr.err` / `*.free.err`) — a use-after-free was
//     reached along the guided path: evidence the trace is realizable;
//   * `*.abort.err` — the per-edge `klee_abort()` from TraceAssertPass fired,
//     i.e. a guided path violated an edge precondition. That means the IFDS
//     fact did not hold concretely, so the trace is a product of the analysis'
//     over-approximation and must be refuted.
// A trace is confirmed iff KLEE reached the use-after-free AND no edge
// precondition was ever violated. File-based detection is more robust than
// scraping stdout.
static void collectKleeErrFiles(llvm::StringRef OutputDir,
                                trace::KleeResult &Result) {
  std::error_code EC;
  for (llvm::sys::fs::directory_iterator It(OutputDir, EC), End;
       It != End && !EC; It.increment(EC)) {
    llvm::StringRef Name = llvm::sys::path::filename(It->path());
    if (Name.ends_with(".ptr.err") || Name.ends_with(".free.err"))
      Result.add_klee_mem_err_files(It->path());
    else if (Name.ends_with(".abort.err"))
      Result.add_klee_abort_files(It->path());
  }
  Result.set_trace_confirmed(Result.klee_mem_err_files_size() > 0 &&
                             Result.klee_abort_files_size() == 0);
}

static bool writeResult(const std::string &Path, const trace::KleeResult &R) {
  std::ofstream Out(Path, std::ios::binary | std::ios::trunc);
  if (!Out)
    return false;
  return R.SerializeToOstream(&Out);
}

struct Args {
  std::vector<std::string> Cir;
  std::string TracePb;
  std::string ResultPath;
  bool NoTraceGuide = false;
  bool NoTraceAssert = false;
  bool ParseOk = false;
};

static Args parseArgs(int argc, char **argv) {
  Args A;
  std::vector<std::string> Positional;
  for (int i = 1; i < argc; ++i) {
    llvm::StringRef Arg(argv[i]);
    if (Arg.starts_with("--result=")) {
      A.ResultPath = Arg.drop_front(strlen("--result=")).str();
      continue;
    }
    if (Arg == "--no-trace-guide") {
      A.NoTraceGuide = true;
      continue;
    }
    if (Arg == "--no-trace-assert") {
      A.NoTraceAssert = true;
      continue;
    }
    Positional.push_back(argv[i]);
  }
  if (Positional.size() < 2)
    return A;
  A.TracePb = Positional.back();
  Positional.pop_back();
  A.Cir = std::move(Positional);
  A.ParseOk = true;
  return A;
}

static void defineFunctionIfDeclaration(llvm::Module &M, llvm::StringRef Name,
                                        llvm::FunctionType *Ty,
                                        llvm::function_ref<void(
                                            llvm::Function &)> DefineBody) {
  llvm::Function *F = M.getFunction(Name);
  if (!F)
    F = llvm::Function::Create(Ty, llvm::GlobalValue::ExternalLinkage, Name, M);
  if (!F->isDeclaration())
    return;
  if (F->getFunctionType() != Ty)
    return;
  DefineBody(*F);
}

static void defineCppAllocatorShims(llvm::Module &M) {
  llvm::LLVMContext &Ctx = M.getContext();
  llvm::Type *VoidTy = llvm::Type::getVoidTy(Ctx);
  llvm::Type *PtrTy = llvm::PointerType::getUnqual(Ctx);
  llvm::Type *SizeTy = M.getDataLayout().getIntPtrType(Ctx);

  auto *MallocTy = llvm::FunctionType::get(PtrTy, {SizeTy}, false);
  llvm::FunctionCallee Malloc = M.getOrInsertFunction("malloc", MallocTy);
  auto *FreeTy = llvm::FunctionType::get(VoidTy, {PtrTy}, false);
  llvm::FunctionCallee Free = M.getOrInsertFunction("free", FreeTy);

  auto *NewTy = llvm::FunctionType::get(PtrTy, {SizeTy}, false);
  for (llvm::StringRef Name : {"_Znwm", "_Znam"}) {
    defineFunctionIfDeclaration(M, Name, NewTy, [&](llvm::Function &F) {
      llvm::BasicBlock *BB = llvm::BasicBlock::Create(Ctx, "entry", &F);
      llvm::IRBuilder<> B(BB);
      B.CreateRet(B.CreateCall(Malloc, {F.getArg(0)}));
    });
  }

  auto *DeleteTy = llvm::FunctionType::get(VoidTy, {PtrTy}, false);
  for (llvm::StringRef Name : {"_ZdlPv", "_ZdaPv"}) {
    defineFunctionIfDeclaration(M, Name, DeleteTy, [&](llvm::Function &F) {
      llvm::BasicBlock *BB = llvm::BasicBlock::Create(Ctx, "entry", &F);
      llvm::IRBuilder<> B(BB);
      B.CreateCall(Free, {F.getArg(0)});
      B.CreateRetVoid();
    });
  }

  auto *SizedDeleteTy = llvm::FunctionType::get(VoidTy, {PtrTy, SizeTy}, false);
  for (llvm::StringRef Name : {"_ZdlPvm", "_ZdaPvm"}) {
    defineFunctionIfDeclaration(M, Name, SizedDeleteTy, [&](llvm::Function &F) {
      llvm::BasicBlock *BB = llvm::BasicBlock::Create(Ctx, "entry", &F);
      llvm::IRBuilder<> B(BB);
      B.CreateCall(Free, {F.getArg(0)});
      B.CreateRetVoid();
    });
  }
}

static void defineJulietControlGlobals(llvm::Module &M) {
  llvm::LLVMContext &Ctx = M.getContext();
  llvm::Type *I32Ty = llvm::Type::getInt32Ty(Ctx);
  for (auto [Name, Value] : {
           std::pair<llvm::StringRef, int32_t>("GLOBAL_CONST_TRUE", 1),
           std::pair<llvm::StringRef, int32_t>("GLOBAL_CONST_FALSE", 0),
           std::pair<llvm::StringRef, int32_t>("GLOBAL_CONST_FIVE", 5),
           std::pair<llvm::StringRef, int32_t>("globalTrue", 1),
           std::pair<llvm::StringRef, int32_t>("globalFalse", 0),
       }) {
    llvm::GlobalVariable *G = M.getNamedGlobal(Name);
    if (!G || !G->isDeclaration() || G->getValueType() != I32Ty)
      continue;
    G->setInitializer(llvm::ConstantInt::get(I32Ty, Value));
    G->setConstant(Name.starts_with("GLOBAL_CONST_"));
    G->setLinkage(llvm::GlobalValue::ExternalLinkage);
  }
}

static void defineRandShim(llvm::Module &M) {
  llvm::LLVMContext &Ctx = M.getContext();
  llvm::Type *I32Ty = llvm::Type::getInt32Ty(Ctx);
  auto *RandTy = llvm::FunctionType::get(I32Ty, {}, false);
  llvm::Function *Rand = M.getFunction("rand");
  if (!Rand)
    Rand = llvm::Function::Create(RandTy, llvm::GlobalValue::ExternalLinkage,
                                  "rand", M);
  if (!Rand->isDeclaration() || Rand->getFunctionType() != RandTy)
    return;

  auto *KleeRangeTy =
      llvm::FunctionType::get(I32Ty, {I32Ty, I32Ty, llvm::PointerType::getUnqual(Ctx)}, false);
  llvm::FunctionCallee KleeRange = M.getOrInsertFunction("klee_range", KleeRangeTy);

  llvm::BasicBlock *BB = llvm::BasicBlock::Create(Ctx, "entry", Rand);
  llvm::IRBuilder<> B(BB);
  llvm::Value *Name = B.CreateGlobalStringPtr("seqra.rand");
  llvm::Value *Value = B.CreateCall(
      KleeRange,
      {llvm::ConstantInt::get(I32Ty, 0), llvm::ConstantInt::get(I32Ty, 2), Name});
  B.CreateRet(Value);
}

static llvm::FunctionCallee getOrCreateCStringReadShim(llvm::Module &M) {
  llvm::LLVMContext &Ctx = M.getContext();
  llvm::Type *VoidTy = llvm::Type::getVoidTy(Ctx);
  llvm::Type *CharTy = llvm::Type::getInt8Ty(Ctx);
  llvm::Type *I64Ty = llvm::Type::getInt64Ty(Ctx);
  llvm::Type *PtrTy = llvm::PointerType::getUnqual(Ctx);

  auto *Ty = llvm::FunctionType::get(VoidTy, {PtrTy}, false);
  llvm::FunctionCallee Callee =
      M.getOrInsertFunction("__seqra_mock_read_c_string", Ty);
  auto *F = llvm::dyn_cast<llvm::Function>(Callee.getCallee());
  if (!F || !F->isDeclaration())
    return Callee;

  llvm::BasicBlock *Entry = llvm::BasicBlock::Create(Ctx, "entry", F);
  llvm::BasicBlock *Loop = llvm::BasicBlock::Create(Ctx, "loop", F);
  llvm::BasicBlock *Next = llvm::BasicBlock::Create(Ctx, "next", F);
  llvm::BasicBlock *Done = llvm::BasicBlock::Create(Ctx, "done", F);

  llvm::IRBuilder<> B(Entry);
  B.CreateBr(Loop);

  B.SetInsertPoint(Loop);
  auto *P = B.CreatePHI(PtrTy, 2, "p");
  P->addIncoming(F->getArg(0), Entry);
  llvm::Value *Ch = B.CreateLoad(CharTy, P);
  llvm::Value *IsZero = B.CreateICmpEQ(Ch, llvm::ConstantInt::get(CharTy, 0));
  B.CreateCondBr(IsZero, Done, Next);

  B.SetInsertPoint(Next);
  llvm::Value *PNext =
      B.CreateGEP(CharTy, P, llvm::ConstantInt::get(I64Ty, 1), "p.next");
  B.CreateBr(Loop);
  P->addIncoming(PNext, Next);

  B.SetInsertPoint(Done);
  B.CreateRetVoid();

  return Callee;
}

static llvm::FunctionCallee getOrCreateWideCStringReadShim(llvm::Module &M) {
  llvm::LLVMContext &Ctx = M.getContext();
  llvm::Type *VoidTy = llvm::Type::getVoidTy(Ctx);
  // On the Linux target used by these fixtures, wchar_t lowers to signed i32.
  llvm::Type *CharTy = llvm::Type::getInt32Ty(Ctx);
  llvm::Type *I64Ty = llvm::Type::getInt64Ty(Ctx);
  llvm::Type *PtrTy = llvm::PointerType::getUnqual(Ctx);

  auto *Ty = llvm::FunctionType::get(VoidTy, {PtrTy}, false);
  llvm::FunctionCallee Callee =
      M.getOrInsertFunction("__seqra_mock_read_wide_c_string", Ty);
  auto *F = llvm::dyn_cast<llvm::Function>(Callee.getCallee());
  if (!F || !F->isDeclaration())
    return Callee;

  llvm::BasicBlock *Entry = llvm::BasicBlock::Create(Ctx, "entry", F);
  llvm::BasicBlock *Loop = llvm::BasicBlock::Create(Ctx, "loop", F);
  llvm::BasicBlock *Next = llvm::BasicBlock::Create(Ctx, "next", F);
  llvm::BasicBlock *Done = llvm::BasicBlock::Create(Ctx, "done", F);

  llvm::IRBuilder<> B(Entry);
  B.CreateBr(Loop);

  B.SetInsertPoint(Loop);
  auto *P = B.CreatePHI(PtrTy, 2, "p");
  P->addIncoming(F->getArg(0), Entry);
  llvm::Value *Ch = B.CreateLoad(CharTy, P);
  llvm::Value *IsZero = B.CreateICmpEQ(Ch, llvm::ConstantInt::get(CharTy, 0));
  B.CreateCondBr(IsZero, Done, Next);

  B.SetInsertPoint(Next);
  llvm::Value *PNext =
      B.CreateGEP(CharTy, P, llvm::ConstantInt::get(I64Ty, 1), "p.next");
  B.CreateBr(Loop);
  P->addIncoming(PNext, Next);

  B.SetInsertPoint(Done);
  B.CreateRetVoid();

  return Callee;
}

static void defineExternalFunctionShims(llvm::Module &M) {
  llvm::FunctionCallee ReadCString = getOrCreateCStringReadShim(M);
  llvm::FunctionCallee ReadWideCString = getOrCreateWideCStringReadShim(M);
  llvm::SmallVector<llvm::CallBase *, 16> PrintfCalls;
  llvm::SmallVector<llvm::CallBase *, 16> WprintfCalls;

  for (llvm::Function &F : M) {
    for (llvm::BasicBlock &BB : F) {
      for (llvm::Instruction &I : BB) {
        auto *Call = llvm::dyn_cast<llvm::CallBase>(&I);
        if (!Call)
          continue;
        llvm::Function *Callee = Call->getCalledFunction();
        if (!Callee || Call->arg_size() < 2)
          continue;
        if (!Call->getArgOperand(1)->getType()->isPointerTy())
          continue;
        if (Callee->getName() == "printf")
          PrintfCalls.push_back(Call);
        else if (Callee->getName() == "wprintf")
          WprintfCalls.push_back(Call);
      }
    }
  }

  for (llvm::CallBase *Call : PrintfCalls) {
    llvm::IRBuilder<> B(Call);
    B.CreateCall(ReadCString, {Call->getArgOperand(1)});
  }
  for (llvm::CallBase *Call : WprintfCalls) {
    llvm::IRBuilder<> B(Call);
    B.CreateCall(ReadWideCString, {Call->getArgOperand(1)});
  }
}

} // namespace

int main(int argc, char **argv) {
  Args A = parseArgs(argc, argv);
  if (!A.ParseOk) {
    llvm::errs() << "usage: cir-klee <input.cir> [<more.cir>...] <trace.pb>\n"
                    "  Options:\n"
                    "    --result=<path>      Write a serialized trace.KleeResult here.\n"
                    "    --no-trace-guide     Skip TraceGuidePass (klee_silent_exit branch cuts).\n"
                    "    --no-trace-assert    Skip TraceAssertPass (per-edge klee_abort precondition checks).\n"
                    "  The trace is confirmed by KLEE's own use-after-free detection at the\n"
                    "  sink (*.ptr.err / *.free.err); there is no injected sink marker.\n"
                    "  Lowers CIR to LLVM IR, assembles with llvm-as-16, runs\n"
                    "  KLEE in a temporary output directory removed after the run.\n"
                    "  Set CIR_KLEE_KEEP_OUTPUT=1 to preserve that directory for\n"
                    "  debugging. Extra .cir files are merged into the first\n"
                    "  (Juliet _a + _b split). Entry point is taken from\n"
                    "  trace.entry_point_name. Requires KLEE_BIN in the environment.\n";
    return 2;
  }

  trace::Trace Pb;
  {
    std::ifstream In(A.TracePb, std::ios::binary);
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

  trace::KleeResult Result;

  mlir::MLIRContext Context;
  mlir::DialectRegistry Registry;
  Registry.insert<cir::CIRDialect, mlir::DLTIDialect, mlir::LLVM::LLVMDialect,
                  mlir::func::FuncDialect>();
  Context.appendDialectRegistry(Registry);
  Context.allowUnregisteredDialects();

  mlir::ParserConfig ParseConfig(&Context);
  auto OwningModule =
      mlir::parseSourceFile<mlir::ModuleOp>(A.Cir.front(), ParseConfig);
  if (!OwningModule) {
    llvm::errs() << "error: failed to parse CIR module\n";
    return 1;
  }

  for (size_t i = 1; i < A.Cir.size(); ++i) {
    auto Extra = mlir::parseSourceFile<mlir::ModuleOp>(A.Cir[i], ParseConfig);
    if (!Extra) {
      llvm::errs() << "error: failed to parse CIR module: " << A.Cir[i] << "\n";
      return 1;
    }
    if (!mergeExtraModuleIntoPrimary(*OwningModule, *Extra)) {
      llvm::errs() << "error: failed to merge CIR module: " << A.Cir[i] << "\n";
      return 1;
    }
  }
  if (A.Cir.size() > 1)
    llvm::outs() << "cir-klee: merged " << (A.Cir.size() - 1)
                 << " extra CIR module(s)\n";

  stampSeqraOpIdsForTraceGuide(*OwningModule);

  llvm::LLVMContext LlvmCtx;
  auto CirToLlvmT0 = Clock::now();
  std::unique_ptr<llvm::Module> LlvmMod =
      lowerCirToLlvmIr(*OwningModule, LlvmCtx);
  if (!LlvmMod) {
    llvm::errs() << "error: CIR→LLVM lowering failed\n";
    return 1;
  }
  prepareLlvmModuleForLlvm16(*LlvmMod);
  defineCppAllocatorShims(*LlvmMod);
  defineJulietControlGlobals(*LlvmMod);
  defineRandShim(*LlvmMod);
  defineExternalFunctionShims(*LlvmMod);
  Result.set_cir_to_llvm_ms(ms(CirToLlvmT0, Clock::now()));

  if (!LlvmMod->getFunction(EntryName)) {
    llvm::errs() << "error: entry_point_name '" << EntryName
                 << "' not found in lowered LLVM module. Available named "
                    "functions (first 32):\n";
    unsigned Shown = 0;
    for (llvm::Function &Fn : *LlvmMod) {
      if (Fn.isDeclaration() || Fn.getName().empty())
        continue;
      llvm::errs() << "  " << Fn.getName() << "\n";
      if (++Shown >= 32) {
        llvm::errs() << "  ...\n";
        break;
      }
    }
    return 1;
  }

  if (!A.NoTraceGuide) {
    auto T0 = Clock::now();
    bool Ok = runTraceGuidePass(*LlvmMod, Pb);
    Result.set_trace_guide_ms(ms(T0, Clock::now()));
    llvm::outs() << "runTraceGuidePass: " << Result.trace_guide_ms() << " ms\n";
    if (!Ok) {
      llvm::errs() << "error: TraceGuidePass failed\n";
      return 1;
    }
  } else {
    llvm::outs() << "cir-klee: --no-trace-guide, skipping TraceGuidePass\n";
  }

  if (!A.NoTraceAssert) {
    auto T0 = Clock::now();
    bool Ok = runTraceAssertPass(*LlvmMod, Pb);
    Result.set_trace_assert_ms(ms(T0, Clock::now()));
    llvm::outs() << "runTraceAssertPass: " << Result.trace_assert_ms()
                 << " ms\n";
    if (!Ok) {
      llvm::errs() << "error: TraceAssertPass failed\n";
      return 1;
    }
  } else {
    llvm::outs() << "cir-klee: --no-trace-assert, skipping TraceAssertPass\n";
  }

  // No sink instrumentation: the trace is confirmed by KLEE's own use-after-free
  // detection (`*.ptr.err` / `*.free.err`) when execution reaches the sink and
  // dereferences the freed pointer along the guided path. See
  // collectKleeMemErrFiles below.

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

  {
    auto T0 = Clock::now();
    std::string LlvmAsErr;
    std::string LlvmAsBin = resolveLlvmAs();
    bool Ok = runLlvmAs(LlvmAsBin, LlTmp.Path, BcTmp.Path, LlvmAsErr);
    Result.set_llvm_as_ms(ms(T0, Clock::now()));
    if (!Ok) {
      llvm::errs() << "error: llvm-as: " << LlvmAsErr << "\n";
      return 1;
    }
  }

  TempDir KleeOutDir;
  KleeOutDir.Preserve = cirKleeKeepOutputEnv();
  if (std::error_code EC =
          llvm::sys::fs::createUniqueDirectory("cir-klee-output", KleeOutDir.Path)) {
    llvm::errs() << "error: createUniqueDirectory(KLEE output): "
                  << EC.message() << "\n";
    return 1;
  }
  if (std::error_code EC =
          llvm::sys::fs::remove_directories(KleeOutDir.Path, /*IgnoreErrors=*/false)) {
    llvm::errs() << "error: remove placeholder KLEE output dir: "
                  << EC.message() << "\n";
    return 1;
  }
  KleeOutDir.Valid = true;
  if (KleeOutDir.Preserve)
    llvm::outs() << "cir-klee: preserving KLEE output dir: " << KleeOutDir.Path
                 << "\n";

  std::string OutputDirArg =
      std::string("--output-dir=") + std::string(KleeOutDir.Path);
  std::string EntryArg = std::string("--entry-point=") + EntryName;
  std::vector<std::string> Storage;
  Storage.reserve(5);
  Storage.push_back(std::string(llvm::sys::path::filename(KleeBin)));
  Storage.push_back(std::move(OutputDirArg));
  Storage.push_back(std::move(EntryArg));
  Storage.push_back(std::string(llvm::StringRef(BcTmp.Path)));

  llvm::SmallVector<llvm::StringRef, 8> ExecArgs;
  for (auto &S : Storage)
    ExecArgs.push_back(S);

  llvm::SmallString<256> KleeLibDir(llvm::sys::path::parent_path(KleeBin));
  std::string NewLdLibraryPath = KleeLibDir.str().str() + ":/usr/local/lib";
  if (const char *ExistingLd = std::getenv("LD_LIBRARY_PATH")) {
    if (*ExistingLd) {
      NewLdLibraryPath += ":";
      NewLdLibraryPath += ExistingLd;
    }
  }
  setenv("LD_LIBRARY_PATH", NewLdLibraryPath.c_str(), 1);
  if (!std::getenv("KLEE_RUNTIME_LIBRARY_PATH")) {
    llvm::SmallString<256> KleeRuntimeLibDir(KleeLibDir);
    llvm::sys::path::append(KleeRuntimeLibDir, "runtime", "lib");
    if (llvm::sys::fs::is_directory(KleeRuntimeLibDir))
      setenv("KLEE_RUNTIME_LIBRARY_PATH",
             KleeRuntimeLibDir.str().str().c_str(), 1);
  }

  std::string ExecErr;
  auto KleeT0 = Clock::now();
  int RC = llvm::sys::ExecuteAndWait(KleeBin, ExecArgs, std::nullopt, {}, 0, 0,
                                     &ExecErr);
  Result.set_klee_ms(ms(KleeT0, Clock::now()));
  if (!ExecErr.empty())
    llvm::errs() << ExecErr << "\n";

  collectKleeErrFiles(KleeOutDir.Path, Result);
  parseKleeInfo(KleeOutDir.Path, Result);

  if (!A.ResultPath.empty()) {
    if (!writeResult(A.ResultPath, Result))
      llvm::errs() << "warning: failed to write --result=" << A.ResultPath
                    << "\n";
  }

  if (RC < 0)
    return 1;
  // Canonical signal of trace confirmation is the KleeResult file. Exit code 0
  // means KLEE ran to completion regardless of whether the trace was confirmed
  // — readers must consult --result=<path>.
  return 0;
}
