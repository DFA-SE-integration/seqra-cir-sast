SHELL := /bin/bash
.ONESHELL:
.SHELLFLAGS := -euo pipefail -c

ROOT := $(abspath .)
MOUNT_ROOT = /workspace

SEQRA_CMN_BLD_DIR		:= $(ROOT)/seqra-common-build
SEQRA_IR_DIR 			:= $(ROOT)/seqra-ir
SEQRA_PROJ_MODEL_DIR 	:= $(ROOT)/seqra-project-model
SEQRA_UTILS_DIR			:= $(ROOT)/seqra-utils
SEQRA_CONFIG_RULES_DIR 	:= $(ROOT)/seqra-configuration-rules
SEQRA_CONFIG_DIR 		:= $(ROOT)/seqra-config
SEQRA_DF_CORE_DIR 		:= $(ROOT)/seqra-dataflow-core
SEQRA_SAST_TUTIL		:= $(ROOT)/seqra-sast-test-util

DOCKER_IMAGE 			:= seqra-ubuntu-jdk21
DOCKER_BOOTSTRAP    	:= scripts/00_bootstrap_ubuntu24.sh

# Assert in docker-shell
INSIDE_DOCKER 			:= $(shell echo $$INSIDE_DOCKER)

HOST_ARCH 				:= $(shell uname -m)

CIRTAC_DIR 				:= $(MOUNT_ROOT)/cir-tac-linux-$(HOST_ARCH)
CIRTAC_KLEE		 		:= $(CIRTAC_DIR)/cir-klee/cir-klee
CIRTAC_COMPILER 		:= $(CIRTAC_DIR)/cir-ser-proto/cir-ser-proto

BUILD_TESTSUITE 		:= scripts/02_build_testsuite.sh

KLEE_SOURCE				:= $(ROOT)/klee
KLEE_DIR 				:= $(MOUNT_ROOT)/klee-linux-$(HOST_ARCH)
KLEE_BIN 				:= $(KLEE_DIR)/klee

# Common
.PHONY: help docker_check

help:
	@echo "Targets:"
	@echo "Docker (Ubuntu + JDK21):"
	@echo "  make docker-image		- build image $(DOCKER_IMAGE) (linux/amd64)"
	@echo "  make docker-shell		- run interactive shell in container + mount ~/.ssh, ~/.gradle (repo mounted)"
	@echo ""
	@echo "cir-tac:"
	@echo "  make clangir-link-build	- link pre-builded clangir to /tmp/llvm-build"
	@echo "  make clangir				- build clangir submodule"
	@echo "  make protobuf				- build protobuf"
	@echo "  make cir-tac				- build cir-tac"
	@echo ""
	@echo "Seqra:"
	@echo "  make clean		- clean seqra modules + .m2 repo packages (Gradle)"
	@echo "  make build		- build seqra modules (Gradle)"
	@echo "  make test		- CWE416 Use-After-Free bad/good entrypoint tests (Gradle)"
	@echo "  make test-se	- IFDS + LLVM lowering + CirSastSeAnalyzer E2E (Gradle)"

docker_check:
ifneq ($(INSIDE_DOCKER),1)
	$(error "Error: Need to enter shell first! Run 'make docker-shell'")
endif


# Docker
.PHONY: docker-image docker-shell

docker-image:
	docker build -t "$(DOCKER_IMAGE)" -f "$(ROOT)/Dockerfile" "$(ROOT)"

# Need to work with cir-tac-bin-x86
# see INSIDE_DOCKER
docker-shell:
	docker run --rm -it \
		-v "$(ROOT):$(MOUNT_ROOT)" \
		-v "$(HOME)/.ssh:/root/.ssh:ro" \
		-v "$(HOME)/.m2:/root/.m2" \
		-v "$(HOME)/.gradle:/root/.gradle" \
		--tmpfs /tmp:exec \
		-e INSIDE_DOCKER=1 \
		-e CIRTAC_COMPILER=$(CIRTAC_COMPILER) \
		-e CIRTAC_KLEE=$(CIRTAC_KLEE) \
		-e KLEE_BIN=$(KLEE_BIN) \
		-e JULIET_ROOT=$(MOUNT_ROOT)/seqra-ir/seqra-ir-core-cir/src/test/resources/juliet \
		-w $(MOUNT_ROOT) "$(DOCKER_IMAGE)"

# cir-tac
.PHONY: clangir-link-build clangir protobuf cir-tac testsuite

testsuite: docker_check clangir-link-build
	bash "$(BUILD_TESTSUITE)"

# Link done before $(ROOT)/clangir/llvm/build build with symlink
clangir-link-build:
	ln -snf $(ROOT)/clangir/llvm/build /tmp/llvm-build

clangir: docker_check clangir-link-build
	@mkdir -p /tmp/llvm-build
	cmake -GNinja -S $(ROOT)/clangir/llvm -B /tmp/llvm-build \
		-DLLVM_ENABLE_PROJECTS="clang;mlir" \
		-DCMAKE_BUILD_TYPE=Release \
		-DLLVM_USE_LINKER=lld \
		-DCLANG_ENABLE_CIR=ON \
		-DLLVM_TARGETS_TO_BUILD="Native" \
		-DLLVM_OPTIMIZED_TABLEGEN=ON
	ninja -C /tmp/llvm-build -j4
	mv /tmp/llvm-build $(MOUNT_ROOT)/clangir/llvm/build

# klee
.PHONY: klee

klee: docker_check
	mkdir -p $(KLEE_DIR)
	bash $(ROOT)/scripts/build_klee.sh $(KLEE_SOURCE) $(KLEE_DIR)

# Need to rebuild cir-tac
# ClangConfig.cmake in:
# CMake Error at CMakeLists.txt:30 (find_package):
# Could not find a package configuration file provided by "Protobuf" with any
protobuf: docker_check
	git clone --depth 1 https://github.com/protocolbuffers/protobuf.git /tmp/protobuf
	cmake -S /tmp/protobuf -B /tmp/protobuf-build -G Ninja -Dprotobuf_BUILD_TESTS=OFF
	cmake --build /tmp/protobuf-build
	cmake --install /tmp/protobuf-build

cir-tac: docker_check clangir-link-build
	@mkdir -p /tmp/cir-tac-build
	cmake -GNinja -S $(ROOT)/cir-tac -B /tmp/cir-tac-build \
		-DCLANGIR_BUILD_DIR=/tmp/llvm-build \
		-DSEA_DSA_DIR=$(ROOT)/sea-dsa
	ninja -C /tmp/cir-tac-build -j4
	if [[ -f /tmp/cir-tac-build/tools/cir-klee/cir-klee ]]; then \
		mkdir -p $(CIRTAC_DIR)/cir-klee && \
		cp /tmp/cir-tac-build/tools/cir-klee/cir-klee $(CIRTAC_DIR)/cir-klee/cir-klee; \
	fi
	mkdir -p $(CIRTAC_DIR)/cir-ser-proto
	cp -r /tmp/cir-tac-build/tools/cir-ser-proto/cir-ser-proto $(CIRTAC_DIR)/cir-ser-proto/cir-ser-proto

# Seqra
.PHONY: build build-dfa clean test test-alias test-se

build: .seqra-common-build
.seqra-common-build:
	cd "$(SEQRA_CMN_BLD_DIR)" && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean publishToMavenLocal && \
	cd "$(ROOT)" && touch $@

build: .seqra-ir
.seqra-ir: .seqra-common-build
	cd "$(SEQRA_IR_DIR)" && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean publishToMavenLocal && \
	cd "$(ROOT)" && touch $@

build: .seqra-project-model
.seqra-project-model:
	cd "$(SEQRA_PROJ_MODEL_DIR)" && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean publishToMavenLocal && \
	cd "$(ROOT)" && touch $@

build: .seqra-configuration-rules
.seqra-configuration-rules:
	cd "$(SEQRA_CONFIG_RULES_DIR)" && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean publishToMavenLocal && \
	cd "$(ROOT)" && touch $@

build: .seqra-config
.seqra-config:
	cd "$(SEQRA_CONFIG_DIR)" && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean publishToMavenLocal && \
	cd "$(ROOT)" && touch $@

build: .seqra-utils
.seqra-utils:
	cd "$(SEQRA_UTILS_DIR)" && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean publishToMavenLocal && \
	cd "$(ROOT)" && touch $@

build: .seqra-sast-test-util
.seqra-sast-test-util:
	cd "$(SEQRA_SAST_TUTIL)" && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean publishToMavenLocal && \
	cd "$(ROOT)" && touch $@

build: .seqra-dfa-core
build-dfa:
	cd "$(SEQRA_DF_CORE_DIR)" && ./gradlew clean publishToMavenLocal
.seqra-dfa-core:
	cd "$(SEQRA_DF_CORE_DIR)" && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean publishToMavenLocal && \
	cd "$(ROOT)" && touch $@

.seqra-jvm-sast:
	cd seqra-jvm-sast && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean build && \
	cd "$(ROOT)" && touch $@

build: .seqra-cir-sast
.seqra-cir-sast:
	cd seqra-cir-sast && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean build && \
	cd "$(ROOT)" && touch $@

test-dfa: docker_check build
	cd seqra-cir-sast && ./gradlew :seqra-cir-sast-dataflow:test

test-alias: docker_check build
	cd seqra-cir-sast && ./gradlew :seqra-cir-sast-dataflow:test \
        --tests org.seqra.cir.sast.dataflow.CIRSeaDsaAliasEvalTest

test: docker_check build
	cd seqra-cir-sast && ./gradlew :test \
		--tests org.seqra.cir.sast.CWE416UseAfterFreeBadEntrypointsTest

clean:
	rm -rf "$(HOME)/.m2/repository/org/seqra"
	rm -f "$(ROOT)"/.seqra-*
