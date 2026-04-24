#pragma once

#include "Util.h"
#include "proto/alias.pb.h"
#include "proto/setup.pb.h"

#include <clang/CIR/Dialect/IR/CIRDialect.h>
#include <llvm/ADT/DenseMap.h>
#include <mlir/IR/BuiltinOps.h>
#include <mlir/IR/Value.h>

#include <vector>

using namespace protocir;

/**
 * Computes intra-procedural CIR-level alias groups for a module.
 *
 * Algorithm (Variant B — CIR-level, no LLVM IR lowering required):
 *   For each function:
 *     1. Collect all pointer-typed block arguments and op results.
 *     2. Use union-find to group values that are structural aliases:
 *        - `cir.cast` with pointer operand and pointer result: union both.
 *        - `cir.store` / `cir.load` pairs are NOT tracked here (heap aliasing).
 *     3. Export groups with >= 2 members as CIRAliasGroup proto messages.
 *
 * The OpCache and BlockCache passed here MUST be the same instances used during
 * IR serialization so that MLIROpID / MLIRBlockID values are consistent.
 */
class AliasSerializer {
public:
  AliasSerializer(MLIRModuleID moduleID) : moduleID(moduleID) {}

  /**
   * Serialize alias data for a single function.
   * @param func      The CIR function to analyze.
   * @param opCache   Op cache already populated for this function (same as main serializer).
   * @param blockCache Block cache already populated for this function.
   * @param typeCache  Type cache used to serialize MLIRValue type IDs.
   */
  CIRFunctionAliasData serializeFunction(cir::FuncOp func,
                                         const OpCache &opCache,
                                         const BlockCache &blockCache,
                                         TypeCache &typeCache);

private:
  MLIRModuleID moduleID;

  // Union-Find helpers (backed by a DenseMap; path-compressed)
  using ValueMap = llvm::DenseMap<mlir::Value, mlir::Value>;

  mlir::Value find(ValueMap &parent, mlir::Value v);
  void unite(ValueMap &parent, mlir::Value a, mlir::Value b);

  bool isPointerType(mlir::Type type);

  MLIRValue makeOpResultValue(const OpCache &opCache,
                              TypeCache &typeCache,
                              mlir::OpResult result);

  MLIRValue makeBlockArgValue(const BlockCache &blockCache,
                              TypeCache &typeCache,
                              mlir::BlockArgument arg);
};
