#include <clang/CIR/Dialect/IR/CIRDialect.h>
#include <mlir/Parser/Parser.h>

#include <filesystem>

int main(int argc, char *argv[]) {
  mlir::MLIRContext context;
  mlir::DialectRegistry registry;
  registry.insert<cir::CIRDialect>();

  context.appendDialectRegistry(registry);
  context.allowUnregisteredDialects();

  if (argc < 2) {
    return -1;
  }

  std::filesystem::path relPath = argv[1];

  auto absPath = std::filesystem::absolute(relPath);
  if (!std::filesystem::exists(absPath)) {
    return -2;
  }

  mlir::ParserConfig parseConfig(&context);
  auto module =
      mlir::parseSourceFile<mlir::ModuleOp>(relPath.c_str(), parseConfig);
  if (module.get() == nullptr) {
    return -3;
  }
  return 0;
}
