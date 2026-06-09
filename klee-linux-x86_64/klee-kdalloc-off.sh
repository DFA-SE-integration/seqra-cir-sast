#!/bin/sh
# qemu-user cannot back KLEE's ~1.1 TiB deterministic-allocator reservation
# (heap=1024 GiB) -> the process is Killed and steps 0 instructions.
exec "/workspace/klee-linux-x86_64/klee" --kdalloc=false "$@"
