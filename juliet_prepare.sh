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
  echo "==> $f (+io.c) -> $out"
  # Amalgamate the testcase with io.c into a single translation unit so the
  # support functions (printLine/printWLine/printIntLine, globalReturns*) end
  # up with bodies in the CIR instead of bare `cir.func private` declarations.
  # KLEE needs those bodies to step into the sink and observe the deref of the
  # freed pointer; the seqra sink rule still matches the call by name. #include
  # (rather than cat) keeps per-file loc() info accurate in the emitted CIR.
  wdir=$(mktemp -d)
  if [ "$CXX" -eq 1 ]; then
    wrap="$wdir/unit.cpp"
    printf '#include "%s"\n#include "%s/io.c"\n' "$f" "$INC" > "$wrap"
    "$CLANGXX" -std=c++17 -x c++ -I"$INC" -Wno-everything -S -Xclang -emit-cir-flat -o "$out" "$wrap"
  else
    wrap="$wdir/unit.c"
    printf '#include "%s"\n#include "%s/io.c"\n' "$f" "$INC" > "$wrap"
    "$CLANG" -x c -I"$INC" -Wno-everything -S -Xclang -emit-cir-flat -o "$out" "$wrap"
  fi
  rm -rf "$wdir"
done