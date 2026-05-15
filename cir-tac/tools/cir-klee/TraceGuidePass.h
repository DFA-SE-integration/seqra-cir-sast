#pragma once

#include "proto/trace.pb.h"

namespace llvm {
class Module;
}

bool runTraceGuidePass(llvm::Module &M, const trace::Trace &Pb);
