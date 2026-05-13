#ifndef CIR_TAC_LLVM16_COMPAT_H_
#define CIR_TAC_LLVM16_COMPAT_H_

#include "llvm/ADT/StringRef.h"

#include <string>

namespace llvm {
class Module;
}

/// Transform \p M so its printed LLVM IR is acceptable to LLVM 16's assembler / bitcode reader
/// (e.g. `llvm-as-16`). Strips attributes introduced after LLVM 16 where they appear on functions,
/// parameters, return values, and call sites: \c nofpclass, \c initializes, \c dead_on_unwind.
///
/// When adding support for more LLVM 17–20-only keywords, run the corpus diagnostic first
/// (\c scripts/diag_llvm16_keywords.sh + \c cir-llvm16-dump); extend stripping only for tokens
/// that appear in lowered IR. See \c cir-tac/README-llvm16.md.
///
/// If \p M has no target triple, sets <tt>x86_64-unknown-linux-gnu</tt>.
bool prepareLlvmModuleForLlvm16(llvm::Module &M);

/// Run external \p llvm-as (e.g. llvm-as-16) on \p llPath → \p bcOutPath.
bool runLlvmAs(llvm::StringRef llvmAsPath, llvm::StringRef llPath,
               llvm::StringRef bcOutPath, std::string &errorMessage);

#endif // CIR_TAC_LLVM16_COMPAT_H_
