#include "cir-tac/Deserializer.h"
#include "cir-tac/Util.h"
#include "mlir/IR/Value.h"
#include "mlir/IR/Verifier.h"
#include "proto/model.pb.h"

#include <fstream>

#include <clang/CIR/Dialect/IR/CIRDialect.h>
#include <clang/CIR/Passes.h>
#include <llvm/ADT/DenseMap.h>
#include <llvm/ADT/TypeSwitch.h>
#include <llvm/Support/Casting.h>
#include <llvm/Support/ErrorHandling.h>
#include <mlir/Dialect/LLVMIR/LLVMDialect.h>
#include <mlir/IR/BuiltinOps.h>
#include <mlir/IR/Dialect.h>
#include <mlir/IR/MLIRContext.h>
#include <mlir/IR/OpImplementation.h>
#include <mlir/IR/Operation.h>
#include <mlir/IR/Types.h>
#include <mlir/IR/Visitors.h>
#include <mlir/Parser/Parser.h>

using namespace protocir;

int main(int argc, char *argv[]) {
  GOOGLE_PROTOBUF_VERIFY_VERSION;

  MLIRModule pModule;

  {
    std::fstream input(argv[1], std::ios::in | std::ios::binary);
    if (!pModule.ParseFromIstream(&input)) {
      llvm::errs() << "Failed to parse [" << argv[1] << "] proto file\n\n";
      return -1;
    }
  }

  mlir::MLIRContext ctx;
  mlir::DialectRegistry registry;
  registry.insert<cir::CIRDialect>();
  ctx.appendDialectRegistry(registry);
  ctx.allowUnregisteredDialects();
  ctx.loadDialect<cir::CIRDialect>();
  auto module = Deserializer::deserializeModule(ctx, pModule);
  auto flags = mlir::OpPrintingFlags();
  flags.enableDebugInfo(true);
  module.print(llvm::outs(), flags);

  google::protobuf::ShutdownProtobufLibrary();
}
