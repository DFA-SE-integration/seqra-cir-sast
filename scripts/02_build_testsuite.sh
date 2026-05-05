#!/usr/bin/env bash
set -euo pipefail

source "scripts/globals.sh"

LLVM_VER="20"
export LLVM_BIN="$CLANGIR_BIN"
export INC_DIR="$TSUITE_ROOT"
export SRC_DIR="$TSUITE_SRC"
export DEST_DIR="$TSUIT_BC_20"
source "$ROOT/scripts/emit_cir.sh"
