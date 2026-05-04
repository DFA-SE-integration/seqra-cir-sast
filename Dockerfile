FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl git python3 rsync \
    build-essential cmake clang lld make ninja-build \
    openjdk-21-jdk \
    libedit-dev zlib1g-dev \
    libboost-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

CMD ["/bin/bash", "-l"]
