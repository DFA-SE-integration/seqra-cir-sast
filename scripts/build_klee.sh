#!/usr/bin/env bash

#Install prerequisites
apt-get update && apt install -y \
  lsb-release \
  gnupg \
  software-properties-common \
  libgoogle-perftools-dev \
  libsqlite3-dev \
  libgmp-dev \
  pkg-config \
  bison flex \
  minisat vim

#LLVM16
#https://klee-se.org/build/build-from-source/
#wget -O - https://apt.llvm.org/llvm-snapshot.gpg.key | apt-key add -
#apt-get install -y clang-16 llvm-16 llvm-16-dev llvm-16-tools

#Constraint solver
#https://klee-se.org/build/build-stp/
STP_TAG=tags/2.3.3
STP_SOURCE=/tmp/stp_source
STP_BUILD=/tmp/stp_source/build
STP_INSTALL_PREFIX=/usr/local
STP_DIR="${STP_INSTALL_PREFIX}/lib/cmake/STP"

git clone https://github.com/stp/stp.git "$STP_SOURCE"
cd "$STP_SOURCE"
git -C "$STP_SOURCE" fetch --tags
git -C "$STP_SOURCE" checkout "$STP_TAG"
mkdir build
cd build
cmake -DCMAKE_CXX_FLAGS='-include stdint.h' \
      ..
cmake --build .
cmake --install .

if [[ -f "${STP_DIR}/STPTargets.cmake" ]] && grep -q 'libabc-pic' "${STP_DIR}/STPTargets.cmake"; then
  echo "ERROR: Installed STP still references libabc-pic in STPTargets.cmake (wrong STP revision?)." >&2
  echo "Expected a clean install of STP ${STP_TAG} without that export." >&2
  exit 1
fi

#TODO add klee posix runtime
#TODO Build libc++: To be able to run C++ code, you also need to enable support for the C++ standard library.

#Configure KLEE
# $1: path to KLEE source tree (Makefile passes $(KLEE_SOURCE)).
# $2: optional path to copy klee binary (Makefile passes $(KLEE_DIR)).
KLEE_SOURCE="${1:-/workspace/klee}"
KLEE_BUILD=/tmp/klee_build
KLEE_BIN_DEST="${2:-}"
mkdir -p "$KLEE_BUILD"
cmake -DENABLE_TCMALLOC=ON \
      -DCMAKE_INCLUDE_PATH=/usr/include \
      -DENABLE_UNIT_TESTS=OFF \
      -DENABLE_SYSTEM_TESTS=OFF \
      -DENABLE_SOLVER_STP=ON \
      -DSTP_DIR="$STP_DIR" \
      -B "$KLEE_BUILD" -S "$KLEE_SOURCE"
cmake --build "$KLEE_BUILD"
if [[ -n "$KLEE_BIN_DEST" ]]; then
  mkdir -p "$KLEE_BIN_DEST"
  cp "${KLEE_BUILD}/bin/klee" "$KLEE_BIN_DEST"
  cp "${STP_INSTALL_PREFIX}"/lib/libstp.so* "$KLEE_BIN_DEST"/
  mkdir -p "${KLEE_BIN_DEST}/runtime"
  cp -r "${KLEE_BUILD}/runtime/lib" "${KLEE_BIN_DEST}/runtime/"
fi
