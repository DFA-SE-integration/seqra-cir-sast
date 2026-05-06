#include "cir-tac/AliasSerializer.h"
#include "cir-tac/AttrSerializer.h"
#include "cir-tac/CirToLlvmIr.h"
#include "cir-tac/OpSerializer.h"
#include "cir-tac/TypeSerializer.h"
#include "cir-tac/Util.h"
#include "proto/alias.pb.h"
#include "proto/model.pb.h"

#include <clang/CIR/Dialect/IR/CIRDialect.h>
#include <clang/CIR/Passes.h>
#include <llvm/ADT/DenseMap.h>
#include <llvm/ADT/StringMap.h>
#include <llvm/ADT/TypeSwitch.h>
#include <llvm/Support/Casting.h>
#include <llvm/Support/ErrorHandling.h>
#include <llvm/Support/raw_ostream.h>
#include <mlir/Dialect/DLTI/DLTI.h>
#include <mlir/Dialect/Func/IR/FuncOps.h>
#include <mlir/Dialect/LLVMIR/LLVMDialect.h>
#include <mlir/IR/BuiltinOps.h>
#include <mlir/IR/Dialect.h>
#include <mlir/IR/MLIRContext.h>
#include <mlir/IR/OpImplementation.h>
#include <mlir/IR/Operation.h>
#include <mlir/IR/Types.h>
#include <mlir/IR/Visitors.h>
#include <mlir/Parser/Parser.h>

#include "llvm/IR/LLVMContext.h"

#include <fcntl.h>
#include <unistd.h>

#include <stdexcept>
#include <string>

using namespace protocir;

int main(int argc, char *argv[]) {
  mlir::MLIRContext context;
  mlir::DialectRegistry registry;
  // CIRDialect is the primary dialect of the input. The rest are necessary
  // for parsing modules that carry data-layout / lowering metadata
  // (`dlti.dl_spec`, `llvm.*` attrs) and for `tryLowerDirectlyFromCIRToLLVMIR`,
  // which the alias serializer runs to obtain the LLVM IR fed to SeaDsa.
  registry.insert<cir::CIRDialect, mlir::DLTIDialect, mlir::LLVM::LLVMDialect,
                  mlir::func::FuncDialect>();

  context.appendDialectRegistry(registry);
  context.allowUnregisteredDialects();

  if (argc < 2) {
    throw std::runtime_error("no clangir source file path was given");
  }

  std::filesystem::path relPath = argv[1];

  auto absPath = std::filesystem::absolute(relPath);
  if (!std::filesystem::exists(absPath)) {
    throw std::runtime_error("missing path given");
  }

  mlir::ParserConfig parseConfig(&context);
  auto module =
      mlir::parseSourceFile<mlir::ModuleOp>(relPath.c_str(), parseConfig);
  if (module.get() == nullptr) {
    throw std::runtime_error("Module was parsed incorrectly! Aborting...");
  }
  MLIRModule pModule;
  MLIRModuleID pModuleID;
  std::string moduleId = (*module).getName().value_or("").str();
  *pModuleID.mutable_id() = moduleId;
  *pModule.mutable_id() = pModuleID;

  TypeCache typeCache(pModuleID);
  AttributeSerializer attributeSerializer(pModuleID, typeCache);

  // Per-function pre-encoded alias contexts. Populated below from the same
  // OpCache/BlockCache/TypeCache the rest of the serializer uses, so that
  // they survive the destructive CIR→LLVM lowering performed at the end.
  llvm::StringMap<FunctionAliasContext> funcAliasCtx;

  auto &bodyRegion = (*module).getBodyRegion();

  for (auto &bodyBlock : bodyRegion) {
    for (auto &topOp : bodyBlock) {
      if (auto cirFunc = llvm::dyn_cast<cir::FuncOp>(topOp)) {
        CIRFunction *pFunction = pModule.add_functions();
        CIRFunctionID pFunctionID;
        *pFunctionID.mutable_module_id() = pModuleID;
        std::string funcId = cirFunc.getSymName().str();
        *pFunctionID.mutable_id() = funcId;
        *pFunction->mutable_id() = pFunctionID;

        BlockCache blockCache;
        OpCache opCache;
        // Populate caches
        for (auto &block : cirFunc.getFunctionBody()) {
          blockCache.getMLIRBlockID(&block);
          for (auto &inst : block) {
            opCache.getMLIROpID(&inst);
          }
        }

        TypeSerializer typeSerializer(pModuleID, typeCache);
        OpSerializer opSerializer(pModuleID, typeCache, opCache, blockCache);

        for (auto &block : cirFunc.getFunctionBody()) {
          auto pBlockID = blockCache.getMLIRBlockID(&block);
          MLIRBlock *pBlock = pFunction->mutable_blocks()->add_block();
          *pBlock->mutable_id() = pBlockID;
          for (auto argumentType : block.getArgumentTypes()) {
            auto pargumentType = typeSerializer.serializeMLIRType(argumentType);
            *pBlock->add_argument_types() = pargumentType.id();
          }
          for (auto &inst : block) {
            auto pInst = opSerializer.serializeOperation(inst);
            *pBlock->add_operations() = pInst;
          }
          MLIRArgLocList pLocList;
          for (auto &arg : block.getArguments()) {
            *pLocList.add_list() =
                attributeSerializer.serializeMLIRLocation(arg.getLoc());
          }
          *pBlock->mutable_arg_locs() = pLocList;
        }

        MLIRArgLocList pLocList;
        for (auto &arg : cirFunc.getArguments()) {
          *pLocList.add_list() =
              attributeSerializer.serializeMLIRLocation(arg.getLoc());
        }
        *pFunction->mutable_arg_locs() = pLocList;
        *pFunction->mutable_loc() =
            attributeSerializer.serializeMLIRLocation(cirFunc->getLoc());

        auto pInfo = opSerializer.serializeOperation(topOp);
        *pFunction->mutable_info() = pInfo.func_op();

        MLIRModuleOp pModuleOp;
        *pModuleOp.mutable_function() = pFunctionID;
        *pModule.add_op_order() = pModuleOp;

        // Stamp `cir.seqra.op_id` and capture MLIRValues after serialization so
        // OpCache ids match protocir exactly (Sea-dsa maps LLVM !seqra.op).
        AliasSerializer::stampAndBuildContext(cirFunc, opCache, blockCache,
                                                typeCache, funcAliasCtx[funcId]);
      } else if (auto cirGlobal = llvm::dyn_cast<cir::GlobalOp>(topOp)) {
        CIRGlobal *pGlobal = pModule.add_globals();
        CIRGlobalID pGlobalID;
        *pGlobalID.mutable_module_id() = pModuleID;
        std::string globalId = cirGlobal.getSymName().str();
        *pGlobalID.mutable_id() = globalId;
        *pGlobal->mutable_id() = pGlobalID;
        OpCache opCache;
        BlockCache blockCache;
        OpSerializer opSerializer(pModuleID, typeCache, opCache, blockCache);
        auto pInfo = opSerializer.serializeOperation(topOp);
        *pGlobal->mutable_info() = pInfo.global_op();
        *pGlobal->mutable_loc() =
            attributeSerializer.serializeMLIRLocation(cirGlobal->getLoc());

        MLIRModuleOp pModuleOp;
        *pModuleOp.mutable_global() = pGlobalID;
        *pModule.add_op_order() = pModuleOp;
      }
    }
  }

  TypeSerializer typeSerializer(pModuleID, typeCache);

  auto typeCacheSize = 0;
  do {
    typeCacheSize = typeCache.map().size();
    auto typeCacheCopy = typeCache;
    for (auto &type : typeCacheCopy.map()) {
      typeSerializer.serializeMLIRType(type.getFirst());
    }
  } while (typeCacheSize < typeCache.map().size());

  for (auto &type : typeCache.map()) {
    auto pType = typeSerializer.serializeMLIRType(type.getFirst());
    *pModule.add_types() = pType;
  }

  *pModule.mutable_loc() =
      attributeSerializer.serializeMLIRLocation(module->getLoc());

  for (auto &attr : module->getOperation()->getAttrs()) {
    if (attr.getValue().getDialect().getNamespace() == "cir") {
      pModule.mutable_attributes()->Add(
          attributeSerializer.serializeMLIRNamedAttr(attr));
    } else {
      std::string strValue;
      llvm::raw_string_ostream os(strValue);
      attr.getValue().print(os);
      MLIRRawNamedAttr pRawAttr;
      *pRawAttr.mutable_name() =
          attributeSerializer.serializeMLIRStringAttr(attr.getName());
      *pRawAttr.mutable_raw_value() = strValue;
      *pModule.add_raw_attrs() = pRawAttr;
    }
  }

  // The CIR side of the serializer is done; Sea-dsa alias groups require the
  // CIR module to be lowered to LLVM IR in place. After lowering the MLIR
  // module no longer holds CIR ops — only LLVM dialect remains.
  //
  // CIR→LLVM lowering can emit diagnostics through `llvm::outs()`; rebind fd 1
  // to /dev/null during lowering so stdout stays clean for the binary protobuf.
  llvm::outs().flush();
  int savedStdout = ::dup(STDOUT_FILENO);
  int devnull = ::open("/dev/null", O_WRONLY);
  if (savedStdout < 0 || devnull < 0) {
    llvm::errs() << "error: failed to silence stdout for CIR→LLVM lowering\n";
    if (savedStdout >= 0)
      ::close(savedStdout);
    if (devnull >= 0)
      ::close(devnull);
    return 1;
  }
  ::dup2(devnull, STDOUT_FILENO);
  ::close(devnull);

  {
    llvm::LLVMContext llvmCtx;
    std::unique_ptr<llvm::Module> llvmMod =
        lowerCirToLlvmIr(*module, llvmCtx);
    llvm::outs().flush();
    if (llvmMod) {
      AliasSerializer aliasSerializer(pModuleID);
      *pModule.mutable_alias_data() =
          aliasSerializer.serializeModule(*llvmMod, funcAliasCtx);
    } else {
      ::dup2(savedStdout, STDOUT_FILENO);
      ::close(savedStdout);
      savedStdout = -1;
      llvm::errs() << "warning: failed to lower CIR to LLVM IR; alias_data "
                      "will be empty\n";
      CIRModuleAliasData emptyAlias;
      *emptyAlias.mutable_module_id() = pModuleID;
      *pModule.mutable_alias_data() = std::move(emptyAlias);
    }
  }

  if (savedStdout >= 0) {
    ::dup2(savedStdout, STDOUT_FILENO);
    ::close(savedStdout);
  }

  // Binary protobuf to stdout after lowering so diagnostics cannot race.
  std::string binary;
  pModule.SerializeToString(&binary);
  llvm::outs() << binary;
  llvm::outs().flush();

  return 0;
}
