#pragma once

#include "Util.h"
#include "proto/alias.pb.h"
#include "proto/setup.pb.h"

#include <clang/CIR/Dialect/IR/CIRDialect.h>
#include <llvm/ADT/DenseMap.h>
#include <llvm/ADT/StringMap.h>

#include <cstdint>

namespace llvm {
class Module;
}

using namespace protocir;

/// Context built from CIR before LLVM lowering; used to map Sea-dsa values back
/// to protobuf MLIRValue.
struct FunctionAliasContext {
  BlockCache blockCache;
  OpCache opCache;
  mlir::Block *entryBlock = nullptr;
  MLIRBlockID entryBlockId{};
  llvm::DenseMap<uint64_t, mlir::Operation *> localIndexToCirOp;
};

class AliasSerializer {
public:
  explicit AliasSerializer(MLIRModuleID moduleID) : moduleID(moduleID) {}

  CIRModuleAliasData
  serializeModule(llvm::Module &llvmModule, TypeCache &typeCache,
                  const llvm::StringMap<FunctionAliasContext> &funcContexts);

  static bool cirOpProducesPointerResult(mlir::Operation *op);

  static void buildFunctionContexts(mlir::ModuleOp module,
                                    llvm::StringMap<FunctionAliasContext> &out);

private:
  MLIRModuleID moduleID;

  MLIRValue makeOpResultValue(const OpCache &opCache, TypeCache &typeCache,
                              mlir::OpResult result);

  MLIRValue makeBlockArgValue(const BlockCache &blockCache, TypeCache &typeCache,
                              mlir::BlockArgument arg);
};
