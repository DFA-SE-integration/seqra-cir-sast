#pragma once

#include <memory>

namespace llvm {
class LLVMContext;
class Module;
} // namespace llvm

namespace mlir {
class ModuleOp;
} // namespace mlir

/// Lowers \p module to LLVM IR in place. \p module is destructively
/// transformed: on success, returns the produced LLVM module; on failure,
/// returns nullptr. The CIR module must not be used after this call.
std::unique_ptr<llvm::Module> lowerCirToLlvmIr(mlir::ModuleOp module,
                                               llvm::LLVMContext &llvmCtx);
