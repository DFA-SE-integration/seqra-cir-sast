#!/usr/bin/env bash
set -euo pipefail

export DEBIAN_FRONTEND="${DEBIAN_FRONTEND:-noninteractive}"

apt-get update
apt-get install -y --no-install-recommends \
  build-essential \
  openjdk-17-jdk 
