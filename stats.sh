#!/bin/bash

# Build seqra modules before!
mkdir -p /workspace/reports /workspace/runlogs

run_cfg () {           # $1=имя конфигурации  $2=bad|good
    local cfg=$1 kind=$2
    local cls="CWE416UseAfterFree$( [ "$kind" = bad ] && echo Bad || echo Good )EntrypointsTest"

    # свежий TSV для пары (конфиг,kind), чтобы append не накапливал старое
    export CIR_KLEE_RESULTS_TSV="/workspace/reports/${cfg}-${kind}.tsv"
    rm -f "$CIR_KLEE_RESULTS_TSV"

    ( cd /workspace/seqra-cir-sast && ./gradlew :test --rerun-tasks --tests "org.seqra.cir.sast.${cls}" ) \
        2>&1 | tee "/workspace/runlogs/${cfg}-${kind}.log"

    # снимок JUnit-отчёта именно этого прогона
    mkdir -p "/workspace/reports/${cfg}-${kind}-junit"
    cp seqra-cir-sast/build/test-results/test/*.xml "/workspace/reports/${cfg}-${kind}-junit/"
}

cd /workspace

# ── 1. IFDS (без SE) ───────────────────────────────
export SEQRA_SE_MODE=none
unset CIRTAC_KLEE_NO_TRACE_GUIDE CIRTAC_KLEE_NO_TRACE_ASSERT
run_cfg ifds bad
run_cfg ifds good

# ── 2. IFDS + SE, без проходов ─────────────────────
export SEQRA_SE_MODE=klee
export CIRTAC_KLEE_NO_TRACE_GUIDE=1
export CIRTAC_KLEE_NO_TRACE_ASSERT=1
run_cfg nopass bad
run_cfg nopass good

# ── 3. IFDS + SE, только TraceGuidePass ────────────
export SEQRA_SE_MODE=klee
unset  CIRTAC_KLEE_NO_TRACE_GUIDE          # guide ВКЛ
export CIRTAC_KLEE_NO_TRACE_ASSERT=1       # assert ВЫКЛ
run_cfg guide bad
run_cfg guide good

# ── 4. IFDS + SE, только TraceAssertPass ───────────
export SEQRA_SE_MODE=klee
export CIRTAC_KLEE_NO_TRACE_GUIDE=1        # guide ВЫКЛ
unset  CIRTAC_KLEE_NO_TRACE_ASSERT         # assert ВКЛ
run_cfg assert bad
run_cfg assert good

# ── 4. IFDS + SE, both ───────────
export SEQRA_SE_MODE=klee
unset  CIRTAC_KLEE_NO_TRACE_GUIDE          # guide ВКЛ
unset  CIRTAC_KLEE_NO_TRACE_ASSERT         # assert ВКЛ
run_cfg both bad
run_cfg both good

# сводка счётчиков по всем JUnit-отчётам (tests / failures / skipped / time):
cd /workspace && grep -h "<testsuite " reports/*-junit/*.xml