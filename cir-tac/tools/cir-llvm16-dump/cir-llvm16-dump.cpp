/// Diagnostic: parse .cir, lower to LLVM IR, optionally strip for llvm-as-16, print .ll.
#include "cir-tac/CirToLlvmIr.h"
#include "cir-tac/Llvm16Compat.h"

#include <clang/CIR/Dialect/IR/CIRDialect.h>
#include <llvm/ADT/StringRef.h>
#include <llvm/IR/LLVMContext.h>
#include <llvm/Support/raw_ostream.h>
#include <mlir/Dialect/DLTI/DLTI.h>
#include <mlir/Dialect/Func/IR/FuncOps.h>
#include <mlir/Dialect/LLVMIR/LLVMDialect.h>
#include <mlir/IR/BuiltinOps.h>
#include <mlir/Parser/Parser.h>

using namespace mlir;

int main(int argc, char *argv[]) {
  bool strip = false;
  int argStart = 1;
  while (argStart < argc && llvm::StringRef(argv[argStart]).starts_with("--")) {
    if (llvm::StringRef(argv[argStart]) == "--strip") {
      strip = true;
    } else {
      llvm::errs() << "unknown flag: " << argv[argStart] << "\n";
      return 1;
    }
    ++argStart;
  }
  if (argStart >= argc) {
    llvm::errs() << "usage: cir-llvm16-dump [--strip] <file.cir>\n";
    return 1;
  }

  MLIRContext context;
  mlir::DialectRegistry registry;
  registry.insert<cir::CIRDialect, mlir::DLTIDialect, mlir::LLVM::LLVMDialect,
                  mlir::func::FuncDialect>();
  context.appendDialectRegistry(registry);
  context.allowUnregisteredDialects();

  mlir::ParserConfig parseConfig(&context);
  auto module = mlir::parseSourceFile<mlir::ModuleOp>(argv[argStart], parseConfig);
  if (!module) {
    llvm::errs() << "failed to parse " << argv[argStart] << "\n";
    return 1;
  }

  llvm::LLVMContext llvmCtx;
  std::unique_ptr<llvm::Module> llvmMod = lowerCirToLlvmIr(*module, llvmCtx);
  if (!llvmMod) {
    llvm::errs() << "failed to lower CIR to LLVM IR\n";
    return 1;
  }
  if (strip)
    prepareLlvmModuleForLlvm16(*llvmMod);
  llvmMod->print(llvm::outs(), nullptr);
  return 0;
}
