#!/bin/bash

CLANG="/tmp/llvm-build/bin/clang"
CLANGXX="/tmp/llvm-build/bin/clang++"
SRC="/workspace/juliet-c/testcases/CWE416_Use_After_Free"
DST="/workspace/juliet-c/samples/CWE416_Use_After_Free"
INC="/workspace/juliet-c/testcasesupport"

mkdir -p "$DST"

should_emit_fixture() {
  local name="$1"
  if [ -n "${JULIET_FIXTURE_LIST:-}" ]; then
    local item
    for item in $JULIET_FIXTURE_LIST; do
      item="${item##*/}"
      item="${item%.cir}"
      item="${item%.*}"
      if [ "$name" = "$item" ]; then
        return 0
      fi
    done
    return 1
  fi
  if [ -n "${JULIET_FIXTURE_FILTER:-}" ] && [[ "$name" != *"$JULIET_FIXTURE_FILTER"* ]]; then
    return 1
  fi
  return 0
}

emit_support_if_used() {
  local source_file="$1"
  local support_file="$2"
  local symbol="$3"
  if grep -qE "\\b${symbol}\\b" "$source_file"; then
    cat >> "$support_file"
  else
    cat >/dev/null
  fi
}

for f in "$SRC"/*.c "$SRC"/*.cpp; do
  [ -e "$f" ] || continue
  base=$(basename "$f")
  name="${base%.*}"
  out="$DST/${name}.cir"
  should_emit_fixture "$name" || continue
  if [ -z "${JULIET_REGENERATE_ALL:-}" ] && [ -z "${JULIET_FIXTURE_LIST:-}" ] && [ -z "${JULIET_FIXTURE_FILTER:-}" ] && [ ! -e "$out" ]; then
    continue
  fi
  case "$base" in
    main.cpp|main_linux.cpp) continue ;;
    *.cpp)                   CXX=1 ;;
    *)                       CXX=0 ;;
  esac
  echo "==> $f (+minimal io support) -> $out"
  # Amalgamate the testcase with only the support functions it references.
  # Pulling all of io.c into every fixture makes SeaDsa analyze many unrelated
  # helper bodies and currently trips an internal array-size assertion. Keeping
  # the bodies local and minimal still lets KLEE step into the actual Juliet
  # sink while avoiding unused support code.
  wdir=$(mktemp -d)
  support="$wdir/io_support.c"
  cat > "$support" <<'EOF'
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <wchar.h>
#include "std_testcase.h"
EOF

  emit_support_if_used "$f" "$support" "printLine" <<'EOF'
void printLine(const char *line) { if (line != NULL) printf("%s\n", line); }
EOF
  emit_support_if_used "$f" "$support" "printWLine" <<'EOF'
void printWLine(const wchar_t *line) { if (line != NULL) wprintf(L"%ls\n", line); }
EOF
  emit_support_if_used "$f" "$support" "printIntLine" <<'EOF'
void printIntLine(int intNumber) { printf("%d\n", intNumber); }
EOF
  emit_support_if_used "$f" "$support" "printShortLine" <<'EOF'
void printShortLine(short shortNumber) { printf("%hd\n", shortNumber); }
EOF
  emit_support_if_used "$f" "$support" "printFloatLine" <<'EOF'
void printFloatLine(float floatNumber) { printf("%f\n", floatNumber); }
EOF
  emit_support_if_used "$f" "$support" "printLongLine" <<'EOF'
void printLongLine(long longNumber) { printf("%ld\n", longNumber); }
EOF
  emit_support_if_used "$f" "$support" "printLongLongLine" <<'EOF'
void printLongLongLine(int64_t longLongIntNumber) { printf("%" PRId64 "\n", longLongIntNumber); }
EOF
  emit_support_if_used "$f" "$support" "printSizeTLine" <<'EOF'
void printSizeTLine(size_t sizeTNumber) { printf("%zu\n", sizeTNumber); }
EOF
  emit_support_if_used "$f" "$support" "printHexCharLine" <<'EOF'
void printHexCharLine(char charHex) { printf("%02x\n", charHex); }
EOF
  emit_support_if_used "$f" "$support" "printWcharLine" <<'EOF'
void printWcharLine(wchar_t wideChar) { wprintf(L"%lc\n", wideChar); }
EOF
  emit_support_if_used "$f" "$support" "printUnsignedLine" <<'EOF'
void printUnsignedLine(unsigned unsignedNumber) { printf("%u\n", unsignedNumber); }
EOF
  emit_support_if_used "$f" "$support" "printHexUnsignedCharLine" <<'EOF'
void printHexUnsignedCharLine(unsigned char unsignedCharacter) { printf("%02x\n", unsignedCharacter); }
EOF
  emit_support_if_used "$f" "$support" "printDoubleLine" <<'EOF'
void printDoubleLine(double doubleNumber) { printf("%g\n", doubleNumber); }
EOF
  emit_support_if_used "$f" "$support" "printStructLine" <<'EOF'
void printStructLine(const twoIntsStruct *value) { printf("%d -- %d\n", value->intOne, value->intTwo); }
EOF
  emit_support_if_used "$f" "$support" "printBytesLine" <<'EOF'
void printBytesLine(const unsigned char *bytes, size_t numBytes) {
  for (size_t i = 0; i < numBytes; ++i) printf("%02x", bytes[i]);
  puts("");
}
EOF
  emit_support_if_used "$f" "$support" "globalReturnsTrue" <<'EOF'
int globalReturnsTrue() { return 1; }
EOF
  emit_support_if_used "$f" "$support" "globalReturnsFalse" <<'EOF'
int globalReturnsFalse() { return 0; }
EOF
  emit_support_if_used "$f" "$support" "globalReturnsTrueOrFalse" <<'EOF'
int globalReturnsTrueOrFalse() { return (rand() % 2); }
EOF
  if grep -qE '\bGLOBAL_CONST_(TRUE|FALSE|FIVE)\b' "$f"; then
    cat >> "$support" <<'EOF'
const int GLOBAL_CONST_TRUE = 1;
const int GLOBAL_CONST_FALSE = 0;
const int GLOBAL_CONST_FIVE = 5;
EOF
  fi
  if grep -qE '\bglobal(True|False|Five)\b' "$f"; then
    cat >> "$support" <<'EOF'
int globalTrue = 1;
int globalFalse = 0;
int globalFive = 5;
EOF
  fi
  if grep -qE '\bglobalArg(c|v)\b' "$f"; then
    cat >> "$support" <<'EOF'
#ifdef __cplusplus
extern "C" {
#endif
int globalArgc = 0;
char **globalArgv = NULL;
#ifdef __cplusplus
}
#endif
EOF
  fi

  if [ "$CXX" -eq 1 ]; then
    wrap="$wdir/unit.cpp"
    printf '#include "%s"\n#include "%s"\n' "$f" "$support" > "$wrap"
    "$CLANGXX" -std=c++17 -x c++ -I"$INC" -Wno-everything -S -Xclang -emit-cir-flat -o "$out" "$wrap"
  else
    wrap="$wdir/unit.c"
    printf '#include "%s"\n#include "%s"\n' "$f" "$support" > "$wrap"
    "$CLANG" -x c -I"$INC" -Wno-everything -S -Xclang -emit-cir-flat -o "$out" "$wrap"
  fi
  rm -rf "$wdir"
done