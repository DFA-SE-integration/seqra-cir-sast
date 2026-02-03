# Ubuntu 24.04 x86_64 для кроссплатформенной сборки (например с ARM Mac).
FROM --platform=linux/amd64 ubuntu:24.04

COPY scripts/00_bootstrap_ubuntu24.sh /tmp/bootstrap.sh
RUN bash /tmp/bootstrap.sh && rm -rf /var/lib/apt/lists/* && rm /tmp/bootstrap.sh

WORKDIR /workspace

CMD ["/bin/bash"]
