#pragma once

#include "proto/trace.pb.h"

namespace llvm {
class Module;
}

bool runTraceAssertPass(llvm::Module &M, /*Trace pb */);
