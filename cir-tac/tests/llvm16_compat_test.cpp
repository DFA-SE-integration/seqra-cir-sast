#include "cir-tac/CirToLlvmIr.h"
#include "cir-tac/Llvm16Compat.h"
#include "llvm16_compat_test_paths.h"

#include <clang/CIR/Dialect/IR/CIRDialect.h>
#include <gtest/gtest.h>
#include <llvm/IR/LLVMContext.h>
#include <llvm/Support/FileSystem.h>
#include <llvm/Support/raw_ostream.h>
#include <mlir/Dialect/DLTI/DLTI.h>
#include <mlir/Dialect/Func/IR/FuncOps.h>
#include <mlir/Dialect/LLVMIR/LLVMDialect.h>
#include <mlir/IR/BuiltinOps.h>
#include <mlir/Parser/Parser.h>

#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <filesystem>
#include <string>
#include <vector>

using namespace mlir;

static std::string findLlvmAs16() {
  if (const char *p = std::getenv("LLVM_AS_16"))
    if (llvm::sys::fs::exists(p))
      return p;
  if (const char *p = std::getenv("LLVM_AS"))
    if (llvm::sys::fs::exists(p))
      return p;
  for (const char *p : {"/usr/bin/llvm-as-16",
                        "/usr/lib/llvm-16/bin/llvm-as",
                        "/usr/bin/llvm-as-14",
                        "/usr/lib/llvm-14/bin/llvm-as"})
    if (llvm::sys::fs::exists(p))
      return p;
  return {};
}

static std::vector<std::string> collectCirFiles() {
  namespace fs = std::filesystem;
  std::vector<std::string> out;
  const fs::path root(CWE416_SAMPLES_DIR);
  if (!fs::exists(root)) {
    return out;
  }
  for (const auto &e : fs::directory_iterator(root)) {
    if (e.is_regular_file() && e.path().extension() == ".cir")
      out.push_back(e.path().string());
  }
  std::sort(out.begin(), out.end());
  return out;
}

class CWE416LowerStrip : public ::testing::TestWithParam<std::string> {};

TEST_P(CWE416LowerStrip, LowerStripAssemble) {
  const std::string llvmAs = findLlvmAs16();
  if (llvmAs.empty())
    GTEST_SKIP() << "llvm-as-16 not found (install llvm-16-tools or set LLVM_AS_16)";

  MLIRContext context;
  mlir::DialectRegistry registry;
  registry.insert<cir::CIRDialect, mlir::DLTIDialect, mlir::LLVM::LLVMDialect,
                  mlir::func::FuncDialect>();
  context.appendDialectRegistry(registry);
  context.allowUnregisteredDialects();

  const std::string path = GetParam();
  mlir::ParserConfig parseConfig(&context);
  auto module = mlir::parseSourceFile<mlir::ModuleOp>(path, parseConfig);
  ASSERT_TRUE(module) << "parse failed: " << path;

  llvm::LLVMContext llvmCtx;
  std::unique_ptr<llvm::Module> llvmMod = lowerCirToLlvmIr(*module, llvmCtx);
  ASSERT_TRUE(llvmMod) << "lower failed: " << path;

  ASSERT_TRUE(prepareLlvmModuleForLlvm16(*llvmMod));

  llvm::SmallString<128> llPath, bcPath;
  {
    std::error_code ec = llvm::sys::fs::createTemporaryFile("cwe416", "ll", llPath);
    ASSERT_FALSE(ec) << ec.message();
    ec = llvm::sys::fs::createTemporaryFile("cwe416", "bc", bcPath);
    ASSERT_FALSE(ec) << ec.message();
  }
  {
    std::error_code ec;
    llvm::raw_fd_ostream os(llPath, ec);
    ASSERT_FALSE(ec) << ec.message();
    llvmMod->print(os, nullptr);
  }

  std::string err;
  bool ok = runLlvmAs(llvmAs, llPath, bcPath, err);
  llvm::sys::fs::remove(llPath);
  llvm::sys::fs::remove(bcPath);
  EXPECT_TRUE(ok) << path << ": " << err;
}

INSTANTIATE_TEST_SUITE_P(All, CWE416LowerStrip,
                         ::testing::ValuesIn(collectCirFiles()),
                         [](const testing::TestParamInfo<CWE416LowerStrip::ParamType> &p) {
                           std::string name = std::filesystem::path(p.param).filename().string();
                           for (char &c : name)
                             if (!std::isalnum(static_cast<unsigned char>(c)))
                               c = '_';
                           return name;
                         });

TEST(CWE416Samples, DirectoryExists) {
  ASSERT_TRUE(std::filesystem::exists(CWE416_SAMPLES_DIR))
      << "Missing juliet CWE416 samples at " << CWE416_SAMPLES_DIR;
  ASSERT_FALSE(collectCirFiles().empty())
      << "No .cir files under " << CWE416_SAMPLES_DIR;
}
