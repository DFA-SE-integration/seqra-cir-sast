#!/usr/bin/env bash
set -euo pipefail

# Project
export ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# clangir / LLVM20
export CLANGIR_LLVM_BUILD_DIR="/tmp/llvm-build"
export CLANGIR_BIN="$CLANGIR_LLVM_BUILD_DIR/bin"
export CLANGIR_LLVM_CMAKE="$CLANGIR_LLVM_BUILD_DIR/lib/cmake/llvm"

# Tests
export TEST_ROOT="$ROOT/tests"

# Test-Suite
export TSUITE_ROOT="$ROOT/Test-Suite"
export TSUITE_SRC="$ROOT/Test-Suite/src"
export TSUIT_BC_20="$TSUITE_ROOT/build/bc/llvm-20"

# Results
export RESULTS_ROOT="$ROOT/results"

# Tests
export RESULTS_TSUITE="$RESULTS_ROOT/Test-Suite"