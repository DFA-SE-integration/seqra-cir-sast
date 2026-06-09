#!/usr/bin/env bash
#
# setup_klee_x86_emulation_arm64.sh — make the x86_64-only KLEE / cir-klee
# symbolic-execution stage runnable on the ARM64 seqra container via qemu-user.
#
# WHY THIS EXISTS
#   KLEE and cir-klee are shipped x86_64-only (klee-linux-x86_64/,
#   cir-tac-linux-x86_64/cir-klee); there are NO arm64 builds. That is why
#   big_projects.sh's SE-mode matrix (nopass/guide/assert/full) is commented out
#   on the arm64 box — only IFDS (SEQRA_SE_MODE=none) runs natively. This script
#   wires up everything needed to run those x86_64 binaries under emulation so
#   the SE modes can be exercised. IFDS / clangir / gradle stay native arm64;
#   only cir-klee + klee are emulated (spawned as subprocesses).
#
# WHAT IT DOES (idempotent)
#   1. amd64 multiarch apt: pins the arm64 ports source to arm64 and adds an
#      archive.ubuntu.com amd64 source, then installs the amd64 runtime the
#      x86_64 binaries link against (incl. libtcmalloc.so.4 via
#      libgoogle-perftools4 and libminisat.so.2 via minisat — see
#      scripts/build_klee.sh; libstp is bundled in klee-linux-x86_64/).
#   2. Creates a KLEE wrapper that adds --kdalloc=false. KLEE's deterministic
#      allocator reserves ~1.1 TiB of fixed mmap (heap alone = 1024 GiB), which
#      qemu-user cannot satisfy → the klee process is "Killed" and steps 0
#      instructions. --kdalloc=false uses ordinary allocation and runs fine.
#   3. Writes an env file (scripts/klee-x86-emu.env) you `source` before running
#      the SE matrix. No separate emulator install is needed: Docker Desktop's VM
#      already registers qemu-x86_64 via binfmt_misc.
#
# USAGE (inside the seqra container, e.g. `make docker-shell`)
#   bash scripts/setup_klee_x86_emulation_arm64.sh        # one-time setup
#   source scripts/klee-x86-emu.env                       # export SE binary env
#   # then enable the SE configs in big_projects.sh run_matrix (or call run_cfg)
#   # and run, reusing already-emitted .cir:
#   BIG_SKIP_PREP=1 bash big_projects.sh <project-id>
#
# CAVEATS for big real-world projects (NOT needed for the Juliet-style cases)
#   - cir-klee's (LLVM-16-era) CIR reader rejects `cir.get_member` on C unions
#     ("member type mismatch"). Run `patch_cir_unions <file.cir>` (below) to
#     rewrite union member access to an equivalent bitcast as a shim.
#   - The .cir must be emitted for x86_64 (cir.triple), else KLEE warns
#     "Module and host target triples do not match" and won't execute the
#     aarch64 module. Cross-emit with `clang --target=x86_64-pc-linux-gnu
#     -emit-cir-flat` (needs x86_64 headers/datalayout).
#   - KLEE models libc malloc/free natively but NOT g_free/g_malloc, so glib
#     UAFs won't be confirmed by the native detector without models.

set -uo pipefail

WS="${WORKSPACE_ROOT:-/workspace}"
KLEE_X86_DIR="$WS/klee-linux-x86_64"
CIRKLEE_X86="$WS/cir-tac-linux-x86_64/cir-klee/cir-klee"
KLEE_BIN_REAL="$KLEE_X86_DIR/klee"
KLEE_WRAPPER="$KLEE_X86_DIR/klee-kdalloc-off.sh"
ENV_FILE="$WS/scripts/klee-x86-emu.env"

UBUNTU_SOURCES="/etc/apt/sources.list.d/ubuntu.sources"
AMD64_SOURCES="/etc/apt/sources.list.d/amd64.sources"

log()  { printf '\033[1;34m[klee-emu]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[klee-emu][warn]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[klee-emu][fatal]\033[0m %s\n' "$*" >&2; exit 1; }

# ── 0. sanity ────────────────────────────────────────────────────────────────
[ "$(uname -m)" = "aarch64" ] || warn "host is not aarch64 ($(uname -m)); this script targets the arm64 container"
[ -x "$KLEE_BIN_REAL" ] || die "missing x86_64 KLEE at $KLEE_BIN_REAL"
[ -x "$CIRKLEE_X86" ]   || die "missing x86_64 cir-klee at $CIRKLEE_X86"
command -v dpkg >/dev/null || die "dpkg required"

# ── 1. amd64 multiarch + runtime libs ────────────────────────────────────────
if ! dpkg --print-foreign-architectures | grep -qx amd64; then
    log "enabling amd64 multiarch"
    # pin the existing arm64 ports source to arm64 so apt does not look for
    # amd64 packages on ports.ubuntu.com (it only carries non-amd64 arches)
    if [ -f "$UBUNTU_SOURCES" ] && ! grep -q '^Architectures:' "$UBUNTU_SOURCES"; then
        sed -i '/^Types: deb$/a Architectures: arm64' "$UBUNTU_SOURCES"
    fi
    cat > "$AMD64_SOURCES" <<'EOF'
Types: deb
URIs: http://archive.ubuntu.com/ubuntu/
Suites: noble noble-updates noble-backports
Components: main universe restricted multiverse
Architectures: amd64

Types: deb
URIs: http://security.ubuntu.com/ubuntu/
Suites: noble-security
Components: main universe restricted multiverse
Architectures: amd64
EOF
    dpkg --add-architecture amd64
    apt-get update -yq || warn "apt-get update returned non-zero"
else
    log "amd64 multiarch already enabled"
fi

# runtime needed by klee / cir-klee / cir-ser-proto (x86_64), per readelf:
#   base C/C++: libc6 libstdc++6 libgcc-s1 zlib1g
#   klee extra: libsqlite3-0 libz3-4 libllvm16 libtcmalloc.so.4 libminisat.so.2
#   (libstp.so is bundled in klee-linux-x86_64/)
log "installing amd64 runtime libraries"
apt-get install -yq \
    libc6:amd64 libstdc++6:amd64 libgcc-s1:amd64 zlib1g:amd64 \
    libsqlite3-0:amd64 libz3-4:amd64 libllvm16:amd64 \
    libgoogle-perftools4:amd64 minisat:amd64 \
    || die "failed to install amd64 runtime libs"

# ── 2. KLEE wrapper that disables the deterministic allocator ────────────────
log "writing KLEE wrapper $KLEE_WRAPPER (--kdalloc=false)"
cat > "$KLEE_WRAPPER" <<EOF
#!/bin/sh
# qemu-user cannot back KLEE's ~1.1 TiB deterministic-allocator reservation
# (heap=1024 GiB) -> the process is Killed and steps 0 instructions.
exec "$KLEE_BIN_REAL" --kdalloc=false "\$@"
EOF
chmod +x "$KLEE_WRAPPER"

# ── 3. env file ──────────────────────────────────────────────────────────────
log "writing env file $ENV_FILE"
cat > "$ENV_FILE" <<EOF
# Source this before running big_projects.sh SE modes on the arm64 container.
# Emulates the x86_64 cir-klee + KLEE via qemu-user (binfmt provided by Docker).
export SEQRA_SE_MODE=klee
export CIRTAC_KLEE="$CIRKLEE_X86"
export KLEE_BIN="$KLEE_WRAPPER"
export LD_LIBRARY_PATH="$KLEE_X86_DIR:/usr/lib/x86_64-linux-gnu:/usr/lib\${LD_LIBRARY_PATH:+:\$LD_LIBRARY_PATH}"
# Per-mode flags (set by big_projects.sh run_matrix; shown for manual runs):
#   nopass : CIRTAC_KLEE_NO_TRACE_GUIDE=1 CIRTAC_KLEE_NO_TRACE_ASSERT=1
#   guide  : CIRTAC_KLEE_NO_TRACE_ASSERT=1
#   assert : CIRTAC_KLEE_NO_TRACE_GUIDE=1
#   full   : (neither)
EOF

# ── 4. verify the emulated binaries run ──────────────────────────────────────
log "verifying emulated KLEE under qemu"
if LD_LIBRARY_PATH="$KLEE_X86_DIR:/usr/lib/x86_64-linux-gnu:/usr/lib" "$KLEE_BIN_REAL" --version >/dev/null 2>&1; then
    ver="$(LD_LIBRARY_PATH="$KLEE_X86_DIR:/usr/lib/x86_64-linux-gnu:/usr/lib" "$KLEE_BIN_REAL" --version 2>&1 | head -1)"
    log "OK: $ver"
else
    die "emulated 'klee --version' failed — check amd64 libs / qemu binfmt"
fi
if LD_LIBRARY_PATH="$KLEE_X86_DIR:/usr/lib/x86_64-linux-gnu:/usr/lib" "$CIRKLEE_X86" --help >/dev/null 2>&1; then
    log "OK: cir-klee runs under emulation"
else
    warn "emulated cir-klee --help returned non-zero (it may still work)"
fi

log "done. Next:"
log "  source $ENV_FILE"
log "  # enable SE configs in big_projects.sh run_matrix, then:"
log "  BIG_SKIP_PREP=1 bash big_projects.sh <project-id>"

# ── optional helper: union get_member -> bitcast shim ────────────────────────
# Call as:  patch_cir_unions path/to/file.cir   (edits in place, keeps .orig)
# Rewrites `cir.get_member %u[i] {name=..} : !cir.ptr<UNION> -> RES` into
# `cir.cast(bitcast, %u : !cir.ptr<UNION>), RES` (all union members are at
# offset 0, so &u.member == (T*)&u). Unblocks cir-klee's older CIR parser.
patch_cir_unions() {
    local f="$1"; [ -f "$f" ] || { echo "no such file: $f" >&2; return 1; }
    cp -f "$f" "$f.orig"
    python3 - "$f" <<'PY'
import re,sys
fn=sys.argv[1]; src=open(fn).read()
unions=set(re.findall(r"^(!ty_\S+) = !cir\.struct<union ", src, re.M))
gm=re.compile(r'^(\s*)(%\S+ = )cir\.get_member (%\S+)\[(\d+)\] \{name = "[^"]*"\} : (!cir\.ptr<(![^>]+)>) -> (.*)$')
out=[];n=0
for ln in src.split("\n"):
    m=gm.match(ln)
    if m and m.group(6) in unions:
        indent,res,op,idx,srctype,_,rest=m.groups()
        mloc=re.match(r"(.*?)( loc\(.*\))?$", rest)
        out.append(f"{indent}{res}cir.cast(bitcast, {op} : {srctype}), {mloc.group(1)}{mloc.group(2) or ''}"); n+=1
    else:
        out.append(ln)
open(fn,"w").write("\n".join(out))
sys.stderr.write("patched %d union get_member ops (%d union types) in %s\n"%(n,len(unions),fn))
PY
}
