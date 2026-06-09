#!/bin/sh
exec /workspace/klee-linux-x86_64/klee --kdalloc=false "$@"
