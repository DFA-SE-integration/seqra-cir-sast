#!/usr/bin/env bash
set -euo pipefail

# Must be defined:
# LLVM_BIN - toolchain binary path
if [ ! -n "${LLVM_BIN:-}" ]; then
  echo "LLVM_BIN not defined!" >&2
  exit 1
fi
# INC_DIR - test include dir
if [ ! -n "${INC_DIR:-}" ]; then
  echo "INC_DIR not defined!" >&2
  exit 1
fi
# SRC_DIR - test source dir
if [ ! -n "${SRC_DIR:-}" ]; then
  echo "SRC_DIR not defined!" >&2
  exit 1
fi
# DEST_DIR - target to place emmited bytecode
if [ ! -n "${DEST_DIR:-}" ]; then
  echo "DEST_DIR not defined!" >&2
  exit 1
fi
if [ ! -x "${LLVM_BIN}/clang" ]; then
  echo "clang not found at ${LLVM_BIN}/clang. Build/install the requested LLVM toolchain first." >&2
  exit 1
fi

mkdir -p "$DEST_DIR"
count=$(find "$DEST_DIR" -name "*.bc" 2>/dev/null | wc -l)
if [ "$count" -gt 0 ]; then
  echo "$DEST_DIR already has $count .bc file(s), skipping."
  return 0 2>/dev/null || exit 0
fi

# Find all source files recursively in SRC_DIR directory
while IFS= read -r -d '' src_f; do
  # Get relative path from SRC_DIR
  rel_path="${src_f#$SRC_DIR/}"
  
  # Construct destination path: DEST_DIR + relative_path + .bc
  bc_f="$DEST_DIR/${rel_path}.cir"
  
  # Create destination directory
  bc_dir=$(dirname "$bc_f")
  mkdir -p "$bc_dir"
  
  echo "Compiling $src_f -> $bc_f"
  
  base_flags=(-Wno-everything -fno-discard-value-names -I"$INC_DIR" -emit-cir -o "$bc_f" "$src_f")
  $LLVM_BIN/clang -g "${base_flags[@]}" || true
done < <(find "$SRC_DIR" -type f \( -name "*.c" -o -name "*.cpp" \) -print0 2>/dev/null | sort -z)

echo "OK: bitcode generated under $DEST_DIR"


