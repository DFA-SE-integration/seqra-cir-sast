#!/usr/bin/env bash
#
# big_projects.sh — run the seqra CIR use-after-free analyzer over the large,
# real-world SARD/Wireshark test cases in big-projects/ and measure the SE-mode
# matrix exactly like stats.sh does for the Juliet fixtures.
#
# RUN THIS INSIDE THE SEQRA CONTAINER ONLY:  `make docker-shell` then
# `bash big_projects.sh`. It needs the clangir toolchain (/tmp/llvm-build),
# CIRTAC_COMPILER / CIRTAC_KLEE / KLEE_BIN (exported by docker-shell) and a
# freshly built seqra (`make build`).
#
# Pipeline per project (big-projects/<id>/):
#   1. read manifest.sarif   -> variant (buggy|fixed), artifact .c files, ground
#                               truth state (bad|good), codeFlow source lines
#   2. faithful .cir build   -> download wireshark-sate6-<variant>-v1.2.zip once,
#                               ./configure + `bear -- make` to capture real
#                               compile_commands.json, then re-emit each artifact
#                               .c with clangir `-Xclang -emit-cir-flat`, reusing
#                               the captured -I/-D/-std flags.
#   3. entrypoints (SARIF)   -> map each codeFlow line to its enclosing function
#                               (ctags); that unmangled C symbol is the IFDS entry.
#   4. plan                  -> append rows to big-projects/cir/entrypoints.tsv
#                               (label, expected, entry, all-project-.cir).
#   5. analysis (= stats.sh) -> run BigProjectsEntrypointsTest under the SE-mode
#                               matrix; snapshot TSV + JUnit into reports/big-projects.
#
# Optional args: project directory names to restrict the run (default: all).
# Useful env: BIG_WORK (build scratch dir), CLANG, BIG_SKIP_PREP=1, BIG_SKIP_RUN=1,
#             BIG_JOBS (make parallelism), BIG_MAKE_TARGETS, SEQRA_TEST_XMX.

set -uo pipefail   # deliberately NOT -e: one project/file failure must not abort the batch

# ── locations ──────────────────────────────────────────────────────────────
ROOT="${WORKSPACE_ROOT:-/workspace}"
BIG="$ROOT/big-projects"
CLANG="${CLANG:-/tmp/llvm-build/bin/clang}"
WORK="${BIG_WORK:-/tmp/big-projects-work}"
CACHE="$WORK/cache"
CIR_ROOT="$BIG/cir"
PLAN="$CIR_ROOT/entrypoints.tsv"
REPORTS="$ROOT/reports/big-projects"
RUNLOGS="$ROOT/runlogs/big-projects"
TOOLS="$WORK/tools"

DEP_BASE_URL="https://samate.nist.gov/SARD/downloads/dependencies"
JOBS="${BIG_JOBS:-$(nproc 2>/dev/null || echo 4)}"
# Build only the libraries needed for compile_commands.json. A full top-level
# `make` enters doc generation and can hang in old Wireshark's `tshark -G fields`.
MAKE_TARGETS="${BIG_MAKE_TARGETS:-epan/libwireshark.la wiretap/libwiretap.la wsutil/libwsutil.la}"
# Match the SARD Dockerfile (keep debug info / unoptimized so traces stay readable).
CONFIGURE_FLAGS=(--disable-wireshark --disable-glibtest --disable-dftest CC=gcc CFLAGS="-Og -g")

# All diagnostics go to stderr so command substitution of value-returning
# functions (ensure_variant_tree) captures only the value.
log()  { printf '\033[1;34m[big]\033[0m %s\n' "$*" >&2; }
warn() { printf '\033[1;33m[big][warn]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[big][fatal]\033[0m %s\n' "$*" >&2; exit 1; }

# ── 0. preflight ─────────────────────────────────────────────────────────────
preflight() {
    [ "${INSIDE_DOCKER:-}" = "1" ] || die "run inside the seqra container (make docker-shell)"
    [ -d "$BIG" ] || die "missing $BIG"
    [ -x "$CLANG" ] || die "clangir clang not found at $CLANG (run 'make clangir' / clangir-link-build)"
    [ -n "${CIRTAC_COMPILER:-}" ] || die "CIRTAC_COMPILER is not set (exported by make docker-shell)"

    # Wireshark-1.2 build deps (xenial-era) are not in the ubuntu24 base image; install best-effort.
    local need=(bear ctags python3 gcc flex autoconf automake libtool pkg-config wget unzip)
    local missing=()
    for t in "${need[@]}"; do command -v "$t" >/dev/null 2>&1 || missing+=("$t"); done
    command -v yacc >/dev/null 2>&1 || command -v bison >/dev/null 2>&1 || missing+=("bison")
    if [ "${#missing[@]}" -gt 0 ]; then
        warn "missing tools: ${missing[*]} — attempting apt-get install"
        apt-get update -yq && apt-get install -yq \
            bear universal-ctags python3 build-essential flex bison byacc \
            autoconf automake libtool pkg-config wget unzip \
            zlib1g-dev libpcap-dev libglib2.0-dev libgtk2.0-dev gettext \
            || warn "apt-get failed; install the build deps manually and re-run with BIG_SKIP_PREP unset"
    fi
    mkdir -p "$WORK" "$CACHE" "$CIR_ROOT" "$REPORTS" "$RUNLOGS" "$TOOLS"
    write_helpers
}

# ── python helpers (written to disk to keep heredocs out of the hot path) ─────
write_helpers() {
    cat > "$TOOLS/manifest.py" <<'PY'
import json, sys
m = json.load(open(sys.argv[1]))
run = m["runs"][0]
props = run.get("properties", {})
print("STATE\t%s" % props.get("state", "unknown"))
deps = props.get("dependencies") or []
print("DEP\t%s" % (deps[0] if deps else ""))
for a in run.get("artifacts", []):
    uri = a.get("location", {}).get("uri", "")
    if uri:
        print("ART\t%s" % uri)
for res in run.get("results", []):
    for cf in res.get("codeFlows", []):
        for tf in cf.get("threadFlows", []):
            for loc in tf.get("locations", []):
                pl = loc.get("location", {}).get("physicalLocation", {})
                uri = pl.get("artifactLocation", {}).get("uri", "")
                line = pl.get("region", {}).get("startLine")
                if uri and line:
                    print("LINE\t%s\t%d" % (uri, line))
PY

    # Re-emit one source file as flat CIR, reusing only the include/define/std flags
    # captured from the real gcc build (codegen flags are dropped so clangir doesn't choke).
    cat > "$TOOLS/emit_cir.py" <<'PY'
import json, os, subprocess, sys
cc_path, src_abs, out_cir, clang = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
base = os.path.basename(src_abs)
db = json.load(open(cc_path))
entry = None
for e in db:
    f = e.get("file", "")
    if os.path.basename(f) == base:
        entry = e
        break
if entry is None:
    sys.stderr.write("no compile_commands entry for %s\n" % base)
    sys.exit(2)

if "arguments" in entry:
    args = list(entry["arguments"])
else:
    import shlex
    args = shlex.split(entry["command"])

cwd = entry.get("directory", os.getcwd())
keep, i = [], 1            # skip argv[0] (the original compiler)
takes_arg = {"-I", "-isystem", "-iquote", "-include", "-D", "-U", "-idirafter"}
while i < len(args):
    a = args[i]
    if a in takes_arg and i + 1 < len(args):
        keep += [a, args[i + 1]]; i += 2; continue
    if a.startswith(("-I", "-D", "-U", "-isystem", "-iquote", "-include", "-idirafter")) or a.startswith("-std="):
        keep.append(a); i += 1; continue
    i += 1

cmd = [clang] + keep + [
    "-I", os.path.dirname(src_abs),
    "-Wno-everything", "-Qunused-arguments",
    "-S", "-Xclang", "-emit-cir-flat",
    "-o", out_cir, src_abs,
]
sys.stderr.write("[emit] (cwd=%s) %s\n" % (cwd, " ".join(cmd)))
sys.exit(subprocess.call(cmd, cwd=cwd))
PY

    # Enclosing-function symbol for a given 1-based line, via universal-ctags.
    cat > "$TOOLS/enclosing.py" <<'PY'
import subprocess, sys
src, target = sys.argv[1], int(sys.argv[2])
out = subprocess.run(["ctags", "-x", "--c-kinds=f", "--language-force=C", src],
                     capture_output=True, text=True).stdout
best_name, best_line = None, -1
for ln in out.splitlines():
    parts = ln.split(None, 4)          # name kind line file source-text
    if len(parts) < 4 or parts[1] != "function":
        continue
    try:
        start = int(parts[2])
    except ValueError:
        continue
    if start <= target and start > best_line:
        best_line, best_name = start, parts[0]
print(best_name or "")
PY
}

# ── 2a. build a wireshark base tree per variant (once) -> compile_commands.json
ensure_variant_tree() {
    local variant="$1"                # buggy | fixed
    local tree="$WORK/$variant"
    local marker="$WORK/.cc_ready_$variant"     # fixed location; holds the resolved tree path
    [ -f "$marker" ] && { cat "$marker"; return 0; }

    local zip="wireshark-sate6-${variant}-v1.2.zip"
    local zippath="$CACHE/$zip"
    if [ ! -s "$zippath" ]; then
        log "downloading $zip"
        curl --proto '=https' --tlsv1.2 -sSfL "$DEP_BASE_URL/$zip" -o "$zippath" \
            || { warn "download failed: $zip"; return 1; }
    fi

    rm -rf "$tree"; mkdir -p "$tree"
    unzip -nq "$zippath" -d "$tree" || { warn "unzip failed: $zip"; return 1; }
    # Some dependency archives wrap the source in a single top-level directory.
    if [ ! -f "$tree/configure" ]; then
        local sub; sub=$(find "$tree" -maxdepth 2 -name configure -print -quit 2>/dev/null)
        [ -n "$sub" ] && tree="$(dirname "$sub")"
    fi
    [ -f "$tree/configure" ] || { warn "no ./configure in $variant tree"; return 1; }

    log "configuring $variant wireshark tree (this is slow)"
    ( cd "$tree" && ./configure "${CONFIGURE_FLAGS[@]}" ) >"$RUNLOGS/configure-$variant.log" 2>&1 \
        || warn "configure returned non-zero ($variant); continuing (config.h may still be usable)"

    log "building $variant with bear targets: $MAKE_TARGETS"
    ( cd "$tree" && bear -- make -j"$JOBS" $MAKE_TARGETS ) >"$RUNLOGS/make-$variant.log" 2>&1 \
        || warn "make returned non-zero ($variant); continuing if compile_commands.json captured epan/*"

    [ -s "$tree/compile_commands.json" ] || { warn "no compile_commands.json for $variant"; return 1; }
    printf '%s' "$tree" > "$marker"
    echo "$tree"
}

variant_of_dep() { case "$1" in *buggy*) echo buggy;; *fixed*) echo fixed;; *) echo "";; esac; }

# ── per-project preparation: .cir + entrypoint plan rows ─────────────────────
prepare_project() {
    local pdir="$1" pid; pid="$(basename "$pdir")"
    local manifest="$pdir/manifest.sarif"
    [ -f "$manifest" ] || { warn "$pid: no manifest.sarif, skip"; return; }

    local state="" dep="" arts=() lines=()
    while IFS=$'\t' read -r tag a b; do
        case "$tag" in
            STATE) state="$a";;
            DEP)   dep="$a";;
            ART)   [ -n "$a" ] && arts+=("$a");;
            LINE)  [ -n "$a" ] && lines+=("$a"$'\t'"$b");;   # uri \t line
        esac
    done < <(python3 "$TOOLS/manifest.py" "$manifest")

    local variant; variant="$(variant_of_dep "$dep")"
    [ -n "$variant" ] || { warn "$pid: unknown variant from dep='$dep', skip"; return; }
    local expected; [ "$state" = "bad" ] && expected="bad" || expected="good"
    log "$pid: state=$state -> expected=$expected, variant=$variant, artifacts=${#arts[@]}"

    local tree; tree="$(ensure_variant_tree "$variant")" || { warn "$pid: no $variant tree, skip"; return; }

    local outdir="$CIR_ROOT/$pid"; mkdir -p "$outdir"
    local cir_list=() emitted_c=()
    for uri in "${arts[@]}"; do
        case "$uri" in *.c) ;; *) continue;; esac   # only .c gets a CIR; .l/.h are inputs to it
        local src="$pdir/$uri"
        [ -f "$src" ] || { warn "$pid: missing artifact $uri"; continue; }
        # Overlay the to-be-analyzed version onto the built tree so the CIR reflects it.
        local dst="$tree/$uri"; mkdir -p "$(dirname "$dst")"; cp -f "$src" "$dst"

        local out="$outdir/$(basename "${uri%.c}").cir"
        if python3 "$TOOLS/emit_cir.py" "$tree/compile_commands.json" "$dst" "$out" "$CLANG" \
                >>"$RUNLOGS/emit-$pid.log" 2>&1 && [ -s "$out" ]; then
            log "$pid: emitted $(basename "$out")"
            cir_list+=("$out"); emitted_c+=("$uri")
        else
            warn "$pid: CIR emit failed for $uri (see $RUNLOGS/emit-$pid.log)"
        fi
    done
    [ "${#cir_list[@]}" -gt 0 ] || { warn "$pid: no .cir produced, skip plan"; return; }

    local cirs_csv; cirs_csv="$(IFS=,; echo "${cir_list[*]}")"

    # entrypoints = enclosing function of each codeFlow line that lands in an emitted .c
    declare -A seen=()
    local added=0
    for entry in "${lines[@]}"; do
        local uri="${entry%%$'\t'*}" line="${entry##*$'\t'}"
        case "$uri" in *.c) ;; *) continue;; esac
        printf '%s\n' "${emitted_c[@]}" | grep -qx "$uri" || continue
        local sym; sym="$(python3 "$TOOLS/enclosing.py" "$tree/$uri" "$line")"
        [ -n "$sym" ] || { warn "$pid: no enclosing fn at $uri:$line"; continue; }
        [ -n "${seen[$sym]:-}" ] && continue
        # The entry must exist as a definition in the combined classpath, else IFDS throws.
        if ! grep -qhE "cir\.func\b[^@]*@${sym}\(" "${cir_list[@]}"; then
            warn "$pid: entry '$sym' ($uri:$line) not a cir.func definition in this TU set, skip"
            continue
        fi
        seen[$sym]=1
        printf '%s\t%s\t%s\t%s\n' "${pid}:${sym}" "$expected" "$sym" "$cirs_csv" >> "$PLAN"
        added=$((added+1))
    done
    log "$pid: $added entrypoint(s) added to plan"
}

# ── 5. analysis matrix (mirrors stats.sh run_cfg) ────────────────────────────
run_cfg() {                # $1 = config label
    local cfg="$1"
    export CIR_KLEE_RESULTS_TSV="$REPORTS/${cfg}.tsv"
    rm -f "$CIR_KLEE_RESULTS_TSV"
    ( cd "$ROOT/seqra-cir-sast" && ./gradlew :test --rerun-tasks \
        --tests "org.seqra.cir.sast.BigProjectsEntrypointsTest" ) \
        2>&1 | tee "$RUNLOGS/${cfg}.log"
    mkdir -p "$REPORTS/${cfg}-junit"
    cp "$ROOT"/seqra-cir-sast/build/test-results/test/*.xml "$REPORTS/${cfg}-junit/" 2>/dev/null || true
}

run_matrix() {
    [ -s "$PLAN" ] || die "empty plan ($PLAN) — preparation produced no analyzable entrypoints"
    export SEQRA_BIGPROJ_PLAN="$PLAN"
    export SEQRA_BIGPROJ_DIR="$CIR_ROOT"
    log "plan has $(grep -cv '^#' "$PLAN") entrypoint(s); driving the SE-mode matrix"

    # ── 1. IFDS only (baseline, no symbolic execution) ──────────────
    export SEQRA_SE_MODE=none
    unset CIRTAC_KLEE_NO_TRACE_GUIDE CIRTAC_KLEE_NO_TRACE_ASSERT 2>/dev/null || true
    run_cfg ifds

    # ── 2. IFDS + SE, no passes ─────────────────────────────────────
    # export SEQRA_SE_MODE=klee
    # export CIRTAC_KLEE_NO_TRACE_GUIDE=1
    # export CIRTAC_KLEE_NO_TRACE_ASSERT=1
    # run_cfg nopass

    # ── 3. IFDS + SE, only TraceGuidePass ───────────────────────────
    # export SEQRA_SE_MODE=klee
    # unset  CIRTAC_KLEE_NO_TRACE_GUIDE
    # export CIRTAC_KLEE_NO_TRACE_ASSERT=1
    # run_cfg guide

    # ── 4. IFDS + SE, only TraceAssertPass ──────────────────────────
    # export SEQRA_SE_MODE=klee
    # export CIRTAC_KLEE_NO_TRACE_GUIDE=1
    # unset  CIRTAC_KLEE_NO_TRACE_ASSERT
    # run_cfg assert

    # ── 5. IFDS + SE, both passes (full) ────────────────────────────
    # export SEQRA_SE_MODE=klee
    # unset CIRTAC_KLEE_NO_TRACE_GUIDE CIRTAC_KLEE_NO_TRACE_ASSERT
    # run_cfg full

    log "JUnit testsuite summary (tests / failures / skipped / time):"
    grep -h "<testsuite " "$REPORTS"/*-junit/*.xml 2>/dev/null || warn "no JUnit reports found"
    log "per-config TSVs: $REPORTS/{ifds,nopass}.tsv"
}

# ── main ─────────────────────────────────────────────────────────────────────
main() {
    preflight

    local projects=()
    if [ "$#" -gt 0 ]; then
        for p in "$@"; do projects+=("$BIG/$p"); done
    else
        for d in "$BIG"/*/; do [ -f "$d/manifest.sarif" ] && projects+=("${d%/}"); done
    fi
    [ "${#projects[@]}" -gt 0 ] || die "no projects with manifest.sarif under $BIG"

    if [ "${BIG_SKIP_PREP:-0}" != "1" ]; then
        : > "$PLAN"
        printf '# label\texpected\tentry_symbol\tcir_files(csv)\n' >> "$PLAN"
        for p in "${projects[@]}"; do prepare_project "$p"; done
    else
        log "BIG_SKIP_PREP=1 — reusing existing $PLAN"
    fi

    if [ "${BIG_SKIP_RUN:-0}" != "1" ]; then
        run_matrix
    else
        log "BIG_SKIP_RUN=1 — preparation only; plan at $PLAN"
    fi
}

main "$@"
