#pragma once

#include <memory>

namespace llvm {
class LLVMContext;
class Module;
} // namespace llvm

namespace mlir {
class ModuleOp;
} // namespace mlir

/// Clones \p module, lowers the clone to LLVM IR, and returns the LLVM module.
/// The input \p module is not modified. On failure, returns nullptr.
std::unique_ptr<llvm::Module> lowerCirToLlvmIr(mlir::ModuleOp module,
                                               llvm::LLVMContext &llvmCtx);
