#include "cir-tac/CirToLlvmIr.h"

#include "clang/CIR/LowerToLLVM.h"

#include "mlir/IR/BuiltinOps.h"

#include "llvm/IR/LLVMContext.h"
#include "llvm/IR/Module.h"

std::unique_ptr<llvm::Module>
lowerCirToLlvmIr(mlir::ModuleOp module, llvm::LLVMContext &llvmCtx) {
  return cir::direct::tryLowerDirectlyFromCIRToLLVMIR(module, llvmCtx);
}
