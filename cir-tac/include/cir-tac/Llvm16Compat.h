#ifndef CIR_TAC_LLVM16_COMPAT_H_
#define CIR_TAC_LLVM16_COMPAT_H_

#include "llvm/ADT/StringRef.h"

#include <string>

namespace llvm {
class Module;
}

/// Strip attributes introduced after LLVM 16 (nofpclass, initializes,
/// dead_on_unwind). Optionally set default target triple if missing.
bool prepareLlvmModuleForLlvm16(llvm::Module &M);

/// Run external \p llvm-as (e.g. llvm-as-16) on \p llPath → \p bcOutPath.
bool runLlvmAs(llvm::StringRef llvmAsPath, llvm::StringRef llPath,
               llvm::StringRef bcOutPath, std::string &errorMessage);

#endif // CIR_TAC_LLVM16_COMPAT_H_
