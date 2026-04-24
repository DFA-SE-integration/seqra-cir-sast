#pragma once

#include "Util.h"

#include "mlir/IR/Attributes.h"
#include "mlir/IR/Block.h"
#include "proto/model.pb.h"
#include "proto/op.pb.h"
#include "proto/setup.pb.h"
#include "proto/type.pb.h"

#include <mlir/IR/Types.h>

#include <unordered_map>
#include <vector>

namespace protocir {

class Deserializer {
private:
  static void defineType(ModuleInfo &mInfo, const MLIRType &pTy);

  static void aggregateTypes(ModuleInfo &mInfo, const MLIRModule &pModule);

  static void defineIncompleteStruct(ModuleInfo &mInfo, const MLIRType &pTy);

  static void defineCompleteStruct(ModuleInfo &mInfo, const MLIRType &pTy);

  static void deserializeFunc(ModuleInfo &mInfo, const CIRFunction &pFunc);

  static void deserializeGlobal(ModuleInfo &mInfo, const CIRGlobal &pGlobal);

  static void deserializeBlock(FunctionInfo &fInfo, const MLIRBlock &pBlock,
                               bool isEntryBlock);

  static void deserializeArgLocs(ModuleInfo &mInfo,
                                 const mlir::Block::BlockArgListType &args,
                                 const MLIRArgLocList &locList);

public:
  static mlir::NamedAttribute createNamedAttribute(ModuleInfo &mInfo,
                                                   std::string name,
                                                   mlir::Attribute attr);

  static mlir::Type getType(ModuleInfo &mInfo, const MLIRTypeID &typeId);

  static mlir::Block *getBlock(FunctionInfo &fInfo, const MLIRBlockID &pBlock);

  static mlir::Value deserializeValue(FunctionInfo &fInfo,
                                      const MLIRValue &pValue);

  static cir::StructType::RecordKind deserializeRecordKind(CIRRecordKind pKind);

  static mlir::ModuleOp deserializeModule(mlir::MLIRContext &ctx,
                                          const MLIRModule &pModule);
};
} // namespace protocir
