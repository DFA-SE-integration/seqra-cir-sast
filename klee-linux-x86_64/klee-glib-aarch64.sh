#!/bin/sh
exec /workspace/klee-linux-x86_64/klee --kdalloc=false --link-llvm-lib=/workspace/scripts/glib_models_aarch64.bc "$@"
