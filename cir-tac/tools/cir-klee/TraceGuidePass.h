#pragma once

#include "proto/trace.pb.h"

#include "llvm/ADT/StringRef.h"
#include "llvm/Support/raw_ostream.h"

#include <optional>

namespace llvm {
class Module;
}

bool runTraceGuidePass(llvm::Module &M, /*Trace pb */);
