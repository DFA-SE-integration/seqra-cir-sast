#include "cir-tac/StampSeqraOpIdsForTraceGuide.h"

#include "cir-tac/AliasSerializer.h"
#include "cir-tac/OpSerializer.h"
#include "cir-tac/TypeSerializer.h"
#include "cir-tac/Util.h"

#include <clang/CIR/Dialect/IR/CIRDialect.h>
#include <mlir/IR/BuiltinOps.h>

#include <string>

using namespace protocir;

void stampSeqraOpIdsForTraceGuide(mlir::ModuleOp module) {
  MLIRModuleID pModuleID;
  std::string moduleId = module.getName().value_or("").str();
  *pModuleID.mutable_id() = moduleId;

  TypeCache typeCache(pModuleID);

  auto &bodyRegion = module.getBodyRegion();
  for (auto &bodyBlock : bodyRegion) {
    for (auto &topOp : bodyBlock) {
      auto cirFunc = mlir::dyn_cast<cir::FuncOp>(topOp);
      if (!cirFunc)
        continue;

      BlockCache blockCache;
      OpCache opCache;
      for (auto &block : cirFunc.getFunctionBody()) {
        blockCache.getMLIRBlockID(&block);
        for (auto &inst : block) {
          opCache.getMLIROpID(&inst);
        }
      }

      TypeSerializer typeSerializer(pModuleID, typeCache);
      OpSerializer opSerializer(pModuleID, typeCache, opCache, blockCache);

      for (auto &block : cirFunc.getFunctionBody()) {
        blockCache.getMLIRBlockID(&block);
        for (auto &inst : block) {
          (void)opSerializer.serializeOperation(inst);
        }
      }

      (void)opSerializer.serializeOperation(topOp);

      FunctionAliasContext fctx;
      AliasSerializer::stampAndBuildContext(cirFunc, opCache, blockCache,
                                            typeCache, fctx);
    }
  }
}
