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

#include <fstream>
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

  // Optional: --emit-alias=<path>
  std::string aliasOutputPath;
  for (int i = 2; i < argc; ++i) {
    std::string arg = argv[i];
    const std::string prefix = "--emit-alias=";
    if (arg.rfind(prefix, 0) == 0) {
      aliasOutputPath = arg.substr(prefix.size());
    }
  }

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

  // Build alias data (Sea-dsa) into an in-memory blob *before* writing the
  // protocir payload to stdout. CIR→LLVM lowering can emit human-readable
  // diagnostics through `llvm::outs()` (e.g. ABI-type warnings from the
  // direct-lowering pass); if any of that ends up on stdout it would corrupt
  // the serialized .protocir on the receiver side.
  //
  // `llvm::outs()` is a process-wide singleton bound to STDOUT_FILENO and
  // calls `::write(STDOUT_FILENO, ...)` at flush time, so it follows whatever
  // fd 1 currently points to. We rebind fd 1 to /dev/null for the duration of
  // lowering, then restore it before writing the protobuf payload.
  std::string aliasBlob;
  if (!aliasOutputPath.empty()) {
    CIRModuleAliasData pAliasData;
    *pAliasData.mutable_module_id() = pModuleID;

    llvm::StringMap<FunctionAliasContext> funcCtx;
    AliasSerializer::buildFunctionContexts(*module, funcCtx);

    llvm::outs().flush();
    int savedStdout = ::dup(STDOUT_FILENO);
    int devnull = ::open("/dev/null", O_WRONLY);
    if (savedStdout < 0 || devnull < 0) {
      llvm::errs() << "error: failed to silence stdout for CIR→LLVM lowering\n";
      if (savedStdout >= 0) ::close(savedStdout);
      if (devnull >= 0) ::close(devnull);
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
        pAliasData =
            aliasSerializer.serializeModule(*llvmMod, typeCache, funcCtx);
      } else {
        // The diagnostic itself was redirected to /dev/null above; surface a
        // single line to stderr so callers know alias data is empty.
        ::dup2(savedStdout, STDOUT_FILENO);
        ::close(savedStdout);
        savedStdout = -1;
        llvm::errs() << "warning: failed to lower CIR to LLVM IR; "
                        "writing empty alias data\n";
      }
    }

    if (savedStdout >= 0) {
      ::dup2(savedStdout, STDOUT_FILENO);
      ::close(savedStdout);
    }
    pAliasData.SerializeToString(&aliasBlob);
  }

  // Write protobuf IR to stdout. Done last so that nothing produced by the
  // lowering pipeline above can race with this binary write.
  std::string binary;
  pModule.SerializeToString(&binary);
  llvm::outs() << binary;
  llvm::outs().flush();

  // Persist the alias blob next to the requested path.
  if (!aliasOutputPath.empty()) {
    std::ofstream aliasFile(aliasOutputPath, std::ios::binary | std::ios::trunc);
    if (!aliasFile) {
      llvm::errs() << "error: cannot open alias output file: " << aliasOutputPath
                   << "\n";
      return 1;
    }
    aliasFile.write(aliasBlob.data(),
                    static_cast<std::streamsize>(aliasBlob.size()));
  }

  return 0;
}
