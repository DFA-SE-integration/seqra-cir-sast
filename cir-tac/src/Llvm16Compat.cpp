#include "cir-tac/Llvm16Compat.h"

#include "llvm/IR/Attributes.h"
#include "llvm/IR/Function.h"
#include "llvm/IR/InstrTypes.h"
#include "llvm/IR/Instructions.h"
#include "llvm/IR/Module.h"
#include "llvm/Support/FileSystem.h"
#include "llvm/Support/Path.h"
#include "llvm/Support/Program.h"
#include "llvm/Support/raw_ostream.h"

#include "llvm/ADT/SmallVector.h"
#include "llvm/ADT/StringRef.h"

using namespace llvm;

/// Drop attributes LLVM 16's bitcode reader does not understand (added in
/// later LLVM versions).
static void stripAttrsTooNewForLlvm16(Module &M) {
  for (Function &F : M) {
    if (F.hasRetAttribute(Attribute::NoFPClass))
      F.removeRetAttr(Attribute::NoFPClass);

    for (unsigned i = 0, e = F.arg_size(); i != e; ++i) {
      if (F.hasParamAttribute(i, Attribute::Initializes))
        F.removeParamAttr(i, Attribute::Initializes);
      if (F.hasParamAttribute(i, Attribute::NoFPClass))
        F.removeParamAttr(i, Attribute::NoFPClass);
      if (F.hasParamAttribute(i, Attribute::DeadOnUnwind))
        F.removeParamAttr(i, Attribute::DeadOnUnwind);
    }

    for (BasicBlock &BB : F)
      for (Instruction &I : BB) {
        auto *CB = dyn_cast<CallBase>(&I);
        if (!CB)
          continue;

        if (!CB->getType()->isVoidTy() && CB->hasRetAttr(Attribute::NoFPClass))
          CB->removeRetAttr(Attribute::NoFPClass);

        for (unsigned j = 0, je = CB->arg_size(); j != je; ++j) {
          if (CB->paramHasAttr(j, Attribute::Initializes))
            CB->removeParamAttr(j, Attribute::Initializes);
          if (CB->paramHasAttr(j, Attribute::NoFPClass))
            CB->removeParamAttr(j, Attribute::NoFPClass);
          if (CB->paramHasAttr(j, Attribute::DeadOnUnwind))
            CB->removeParamAttr(j, Attribute::DeadOnUnwind);
        }
      }
  }
}

bool prepareLlvmModuleForLlvm16(Module &M) {
  if (M.getTargetTriple().empty())
    M.setTargetTriple("x86_64-unknown-linux-gnu");

  stripAttrsTooNewForLlvm16(M);
  return true;
}

bool runLlvmAs(StringRef LlvmAsPath, StringRef LlPath, StringRef BcOutPath,
               std::string &ErrorMessage) {
  std::string ProgramPath = LlvmAsPath.str();
  if (!sys::path::has_parent_path(LlvmAsPath)) {
    llvm::ErrorOr<std::string> Found = sys::findProgramByName(LlvmAsPath);
    if (!Found) {
      ErrorMessage =
          std::string("llvm-as not found in PATH: ") + LlvmAsPath.str();
      return false;
    }
    ProgramPath = std::move(*Found);
  }

  if (!sys::fs::exists(ProgramPath)) {
    ErrorMessage = std::string("llvm-as executable not found: ") + ProgramPath;
    return false;
  }

  std::string LlStorage = std::string(LlPath);
  std::string BcStorage = std::string(BcOutPath);
  SmallVector<StringRef, 4> Args;
  Args.push_back(sys::path::filename(ProgramPath));
  Args.push_back(LlStorage);
  Args.push_back("-o");
  Args.push_back(BcStorage);

  std::string ErrMsg;
  int RC =
      sys::ExecuteAndWait(ProgramPath, Args, std::nullopt, {}, 0, 0, &ErrMsg);
  if (RC != 0) {
    ErrorMessage =
        "llvm-as exited with code " + std::to_string(RC) + ": " + ErrMsg;
    return false;
  }
  return true;
}
