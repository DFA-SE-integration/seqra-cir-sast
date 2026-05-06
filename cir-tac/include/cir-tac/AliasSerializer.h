#pragma once

#include "Util.h"
#include "proto/alias.pb.h"
#include "proto/setup.pb.h"

#include <clang/CIR/Dialect/IR/CIRDialect.h>
#include <llvm/ADT/DenseMap.h>
#include <llvm/ADT/StringMap.h>

#include <vector>

namespace llvm {
class Module;
} // namespace llvm

using namespace protocir;

/// Per-function alias context for Sea-dsa: stable mapping from `cir.seqra.op_id`
/// / LLVM `!seqra.op` to serialized MLIR values.
///
/// Populated while the CIR module is alive (`stampAndBuildContext` shares the
/// same `OpCache` / `BlockCache` / `TypeCache` as `OpSerializer`); CIR→LLVM
/// lowering may then destroy the CIR module without invalidating these protos.
struct FunctionAliasContext {
  llvm::DenseMap<uint64_t, MLIRValue> opIdToResult0;

  std::vector<MLIRValue> entryBlockArgs;
};

class AliasSerializer {
public:
  explicit AliasSerializer(MLIRModuleID moduleID) : moduleID(moduleID) {}

  /// Stamp `cir.seqra.op_id` (= serialized `MLIROpID`) on every result-producing
  /// op in \p func and fill \p out. Must run after `OpSerializer` has walked the
  /// function so `OpCache` ids match the protobuf exactly.
  static void stampAndBuildContext(cir::FuncOp func, OpCache &opCache,
                                   BlockCache &blockCache,
                                   TypeCache &typeCache,
                                   FunctionAliasContext &out);

  /// Run Sea-dsa on \p llvmModule and emit alias groups using the
  /// precomputed contexts. Safe after destructive CIR lowering.
  CIRModuleAliasData
  serializeModule(llvm::Module &llvmModule,
                  const llvm::StringMap<FunctionAliasContext> &funcContexts);

private:
  MLIRModuleID moduleID;
};
