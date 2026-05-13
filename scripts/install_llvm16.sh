#!/usr/bin/env bash
set -euo pipefail

apt-get update
apt-get install -y --no-install-recommends \
  clang-16 \
  llvm-16 \
  llvm-16-dev \
  llvm-16-tools
rm -rf /var/lib/apt/lists/*
