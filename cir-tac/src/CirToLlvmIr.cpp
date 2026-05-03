#include "cir-tac/CirToLlvmIr.h"

#include "clang/CIR/LowerToLLVM.h"

#include "mlir/IR/BuiltinOps.h"

#include "llvm/IR/LLVMContext.h"
#include "llvm/IR/Module.h"

std::unique_ptr<llvm::Module>
lowerCirToLlvmIr(mlir::ModuleOp module, llvm::LLVMContext &llvmCtx) {
  mlir::ModuleOp cloned = mlir::cast<mlir::ModuleOp>(module->clone());
  auto llvmMod = cir::direct::tryLowerDirectlyFromCIRToLLVMIR(cloned, llvmCtx);
  cloned->erase();
  return llvmMod;
}
