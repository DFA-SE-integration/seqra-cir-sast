#!/usr/bin/env bash
# Run after: ninja -C /tmp/cir-tac-build (with cir-llvm16-dump built).
# Collects keywords from lowered CWE416 .cir that LLVM 16 text parser may reject.
set -euo pipefail

DUMP_BIN="${CIR_LLVM16_DUMP:-/tmp/cir-tac-build/tools/cir-llvm16-dump/cir-llvm16-dump}"
SAMPLES_DIR="${JULIET_CWE416:-$(cd "$(dirname "$0")/.." && pwd)/juliet-c/samples/CWE416_Use_After_Free}"
OUT_DIR="${DIAG_OUT_DIR:-/tmp/cwe416_ll}"
KEYWORDS_OUT="${KEYWORDS_FILE:-/tmp/cwe416_keywords.txt}"

if [[ ! -x "$DUMP_BIN" ]]; then
  echo "ERROR: $DUMP_BIN not found or not executable. Build cir-tac first:" >&2
  echo "  cmake -GNinja -S cir-tac -B /tmp/cir-tac-build -DCLANGIR_BUILD_DIR=/path/to/llvm-build && ninja -C /tmp/cir-tac-build cir-llvm16-dump" >&2
  exit 1
fi
if [[ ! -d "$SAMPLES_DIR" ]]; then
  echo "ERROR: samples dir not found: $SAMPLES_DIR" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
: >"$KEYWORDS_OUT.failures"

shopt -s nullglob
count=0
ok=0
for f in "$SAMPLES_DIR"/*.cir; do
  count=$((count + 1))
  base=$(basename "$f" .cir)
  if ! "$DUMP_BIN" "$f" >"$OUT_DIR/${base}.ll" 2>>"$KEYWORDS_OUT.failures"; then
    echo "FAIL $f" >>"$KEYWORDS_OUT.failures"
  else
    ok=$((ok + 1))
  fi
done
shopt -u nullglob

grep -hoE \
  '\b(disjoint|samesign|nneg|nusw|trunc nuw|trunc nsw|writable|nofpclass|dead_on_unwind|initializes|noext|optdebug|coro_elide_safe|sanitize_realtime|sanitize_numerical_stability|hybrid_patchable|preserve_nonecc|usub_cond|usub_sat|llvm\.scmp|llvm\.ucmp|llvm\.fake_use|llvm\.experimental\.memset\.pattern|llvm\.maximumnum|llvm\.minimumnum)\b|splat \(' \
  "$OUT_DIR"/*.ll 2>/dev/null | sort -u >"$KEYWORDS_OUT" || true

# Multi-token / paren forms
grep -hoE 'range\([^)]*\)|inrange\([^)]*\)' "$OUT_DIR"/*.ll 2>/dev/null | sed 's/ .*//' | sort -u >>"$KEYWORDS_OUT" || true
sort -u "$KEYWORDS_OUT" -o "$KEYWORDS_OUT"

echo "Processed $ok/$count .cir files into $OUT_DIR"
echo "Unique keyword hits -> $KEYWORDS_OUT"
cat "$KEYWORDS_OUT"
echo "---"
if [[ -s "$KEYWORDS_OUT.failures" ]] && grep -q . "$KEYWORDS_OUT.failures" 2>/dev/null; then
  echo "Failures (see $KEYWORDS_OUT.failures)"
  head -20 "$KEYWORDS_OUT.failures"
fi
