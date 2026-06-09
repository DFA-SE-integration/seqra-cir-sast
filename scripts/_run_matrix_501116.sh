#!/usr/bin/env bash
# Full SE-mode matrix on 501116 with metrics like the Juliet chapter.
# SE modes run the emulated x86 cir-klee+KLEE (--kdalloc=false + glib models),
# on union-shimmed .cir. Cleans tmpfs between modes (xodus/protocir temps fill
# the 5.9G /tmp otherwise -> IOException). Per-mode TSV + JUnit snapshot.
set -uo pipefail
WS=/workspace
CD=$WS/big-projects/cir/501116-v1.0.0
ln -snf $WS/clangir/llvm/build /tmp/llvm-build

clean_tmp() {
  rm -rf /tmp/xodusKeyValueStorage* /tmp/seqra-cir-sast-dataflow-* \
         /tmp/cir-klee-* /tmp/klee-out-* /tmp/cir-klee-output-* 2>/dev/null || true
}

# union shim so cir-klee can parse (keep .orig)
cd "$CD"
for f in radius_dict.cir packet-radius.cir; do cp -f "$f" "$f.orig"; done
python3 "$WS/scripts/_union_shim.py" radius_dict.cir packet-radius.cir 2>/dev/null

export INSIDE_DOCKER=1 CIRTAC_COMPILER=$WS/cir-tac-linux-arm64/cir-ser-proto/cir-ser-proto
export SEQRA_BIGPROJ_PLAN=$WS/big-projects/cir/entrypoints.tsv
export SEQRA_BIGPROJ_DIR=$WS/big-projects/cir
# SE (emulated x86) settings — used by klee modes only
export CIRTAC_KLEE=$WS/cir-tac-linux-x86_64/cir-klee/cir-klee
export KLEE_BIN=$WS/klee-linux-x86_64/klee-glib-aarch64.sh   # --kdalloc=false + glib aarch64 models
export LD_LIBRARY_PATH=$WS/klee-linux-x86_64:/usr/lib/x86_64-linux-gnu:/usr/lib
export CIRTAC_KLEE_TIMEOUT_SEC=120

run_mode() {
  local name=$1
  clean_tmp
  echo "######## MODE=$name ($(date +%H:%M:%S)) ########"
  export CIR_KLEE_RESULTS_TSV=$WS/reports/big-projects/m_$name.tsv
  rm -f "$CIR_KLEE_RESULTS_TSV"
  ( cd $WS/seqra-cir-sast && ./gradlew :test --rerun-tasks \
      --tests "org.seqra.cir.sast.BigProjectsEntrypointsTest" ) \
      >$WS/runlogs/big-projects/m_$name.log 2>&1
  mkdir -p $WS/reports/big-projects/m_$name-junit
  cp $WS/seqra-cir-sast/build/test-results/test/*.xml $WS/reports/big-projects/m_$name-junit/ 2>/dev/null || true
  echo "$name: $(grep -aoE '501116-v1.0.0:[A-Za-z_]+ (PASSED|FAILED)' $WS/runlogs/big-projects/m_$name.log | sort -u | tr '\n' ' ')"
}

export SEQRA_SE_MODE=none
unset CIRTAC_KLEE_NO_TRACE_GUIDE CIRTAC_KLEE_NO_TRACE_ASSERT 2>/dev/null || true
run_mode ifds

export SEQRA_SE_MODE=klee CIRTAC_KLEE_NO_TRACE_GUIDE=1 CIRTAC_KLEE_NO_TRACE_ASSERT=1
run_mode nopass

export SEQRA_SE_MODE=klee; unset CIRTAC_KLEE_NO_TRACE_GUIDE; export CIRTAC_KLEE_NO_TRACE_ASSERT=1
run_mode guide

export SEQRA_SE_MODE=klee CIRTAC_KLEE_NO_TRACE_GUIDE=1; unset CIRTAC_KLEE_NO_TRACE_ASSERT
run_mode assert

export SEQRA_SE_MODE=klee; unset CIRTAC_KLEE_NO_TRACE_GUIDE CIRTAC_KLEE_NO_TRACE_ASSERT
run_mode full

cd "$CD"; for f in radius_dict.cir packet-radius.cir; do mv -f "$f.orig" "$f"; done
clean_tmp
echo "MATRIX DONE ($(date +%H:%M:%S))"
