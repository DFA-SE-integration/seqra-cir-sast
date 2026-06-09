#!/usr/bin/env bash
# Temp helper: run all 4 SE modes on 501116 and snapshot per-mode TSV+JUnit.
set -uo pipefail
WS=/workspace
CD=$WS/big-projects/cir/501116-v1.0.0
ln -snf $WS/clangir/llvm/build /tmp/llvm-build

cd "$CD"
for f in radius_dict.cir packet-radius.cir; do cp -f "$f" "$f.orig"; done
python3 "$WS/scripts/_union_shim.py" radius_dict.cir packet-radius.cir

export INSIDE_DOCKER=1 CIRTAC_COMPILER=$WS/cir-tac-linux-arm64/cir-ser-proto/cir-ser-proto
export SEQRA_BIGPROJ_PLAN=$WS/big-projects/cir/entrypoints.tsv
export SEQRA_BIGPROJ_DIR=$WS/big-projects/cir
export LD_LIBRARY_PATH=$WS/klee-linux-x86_64:/usr/lib/x86_64-linux-gnu:/usr/lib
export CIRTAC_KLEE=$WS/cir-tac-linux-x86_64/cir-klee/cir-klee
export KLEE_BIN=$WS/klee-linux-x86_64/klee-kdalloc-off.sh
export CIRTAC_KLEE_TIMEOUT_SEC=120

run_mode() {
  local name=$1
  echo "######## MODE=$name ########"
  export CIR_KLEE_RESULTS_TSV=$WS/reports/big-projects/$name.tsv
  rm -f "$CIR_KLEE_RESULTS_TSV"
  ( cd $WS/seqra-cir-sast && ./gradlew :test --rerun-tasks --tests "org.seqra.cir.sast.BigProjectsEntrypointsTest" ) \
      >$WS/runlogs/big-projects/$name.log 2>&1
  mkdir -p $WS/reports/big-projects/$name-junit
  cp $WS/seqra-cir-sast/build/test-results/test/*.xml $WS/reports/big-projects/$name-junit/ 2>/dev/null || true
  echo "$name done: $(grep -aoE '501116-v1.0.0:[A-Za-z_]+ (PASSED|FAILED|SKIPPED)' $WS/runlogs/big-projects/$name.log | sort -u | tr '\n' ' ')"
}

export SEQRA_SE_MODE=none
unset CIRTAC_KLEE_NO_TRACE_GUIDE CIRTAC_KLEE_NO_TRACE_ASSERT 2>/dev/null || true
run_mode ifds

export SEQRA_SE_MODE=klee CIRTAC_KLEE_NO_TRACE_GUIDE=1 CIRTAC_KLEE_NO_TRACE_ASSERT=1
run_mode nopass

export SEQRA_SE_MODE=klee
unset CIRTAC_KLEE_NO_TRACE_GUIDE
export CIRTAC_KLEE_NO_TRACE_ASSERT=1
run_mode guide

export SEQRA_SE_MODE=klee
unset CIRTAC_KLEE_NO_TRACE_GUIDE CIRTAC_KLEE_NO_TRACE_ASSERT
run_mode full

cd "$CD"
for f in radius_dict.cir packet-radius.cir; do mv -f "$f.orig" "$f"; done
echo "ALL MODES DONE"
