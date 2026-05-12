CLANG="/tmp/llvm-build/bin/clang"
CLANGXX="/tmp/llvm-build/bin/clang++"
SRC="/workspace/juliet-c/testcases/CWE416_Use_After_Free"
DST="/workspace/juliet-c/samples/CWE416_Use_After_Free"
INC="/workspace/juliet-c/testcasesupport"

mkdir -p "$DST"

for f in "$SRC"/*.c "$SRC"/*.cpp; do
  [ -e "$f" ] || continue
  base=$(basename "$f")
  name="${base%.*}"
  out="$DST/${name}.cir"
  case "$f" in
    *.cpp) CXX=1 ;;
    *)     CXX=0 ;;
  esac
  echo "==> $f -> $out"
  if [ "$CXX" -eq 1 ]; then
    "$CLANGXX" -std=c++17 -I"$INC" -Wno-everything -S -Xclang -emit-cir-flat -o "$out" "$f"
  else
    "$CLANG" -I"$INC" -Wno-everything -S -Xclang -emit-cir-flat -o "$out" "$f"
  fi
done