SHELL := /bin/bash
.ONESHELL:
.SHELLFLAGS := -euo pipefail -c

ROOT := $(abspath .)

SEQRA_CMN_BLD_DIR	:= $(ROOT)/seqra-common-build
SEQRA_IR_DIR 		:= $(ROOT)/seqra-ir
SEQRA_PROJ_MODEL_DIR 		:= $(ROOT)/seqra-project-model
SEQRA_UTILS_DIR		:= $(ROOT)/seqra-utils
SEQRA_CONFIG_RULES_DIR := $(ROOT)/seqra-configuration-rules
SEQRA_CONFIG_DIR := $(ROOT)/seqra-config
SEQRA_DF_CORE_DIR 	:= $(ROOT)/seqra-dataflow-core
SEQRA_SAST_TUTIL	:= $(ROOT)/seqra-sast-test-util

DOCKER_IMAGE 		:= seqra-cir-ubuntu24
DOCKER_BOOTSTRAP    := scripts/00_bootstrap_ubuntu24.sh

.PHONY: help doctor report

help:
	@echo "Targets:"
	@echo "Docker (Ubuntu 24 x86_64):"
	@echo "  make docker-image		- build image $(DOCKER_IMAGE) (linux/amd64)"
	@echo "  make docker-shell		- run interactive shell in container + mount ~/.ssh, ~/.gradle (repo mounted)"
	@echo ""
	@echo "Seqra:"
	@echo "  make seqra-dfa-core		- build seqra-dataflow-core (Gradle)"
	@echo ""
	@echo "Clean:"
	@echo "  make clean-dfa-core	- remove seqra modules build artifacts"
	@echo "  make clean-maven-local	- remove all org.seqra* from ~/.m2 and reset build stamps"

# docker
.PHONY: docker-image docker-shell docker-run

docker-image:
	docker build --platform linux/amd64 -t "$(DOCKER_IMAGE)" -f "$(ROOT)/Dockerfile" "$(ROOT)"

docker-shell: docker-image
	docker run --rm -it \
		-v "$(ROOT):/workspace" \
		-v "$(HOME)/.ssh:/root/.ssh:ro" \
		-v "$(HOME)/.gradle:/root/.gradle" \
		-w /workspace "$(DOCKER_IMAGE)"

# seqra-dataflow-core build dependencies
seqra-cir-sast: .seqra-common-build
.seqra-common-build:
	cd "$(SEQRA_CMN_BLD_DIR)" && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean publishToMavenLocal && \
	cd "$(ROOT)" && touch $@

seqra-cir-sast: .seqra-ir
.seqra-ir: .seqra-common-build
	cd "$(SEQRA_IR_DIR)" && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean publishToMavenLocal && \
	cd "$(ROOT)" && touch $@

seqra-cir-sast: .seqra-project-model
.seqra-project-model:
	cd "$(SEQRA_PROJ_MODEL_DIR)" && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean publishToMavenLocal && \
	cd "$(ROOT)" && touch $@

seqra-cir-sast: .seqra-configuration-rules
.seqra-configuration-rules:
	cd "$(SEQRA_CONFIG_RULES_DIR)" && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean publishToMavenLocal && \
	cd "$(ROOT)" && touch $@

seqra-cir-sast: .seqra-config
.seqra-config:
	cd "$(SEQRA_CONFIG_DIR)" && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean publishToMavenLocal && \
	cd "$(ROOT)" && touch $@

seqra-cir-sast: .seqra-utils
.seqra-utils:
	cd "$(SEQRA_UTILS_DIR)" && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean publishToMavenLocal && \
	cd "$(ROOT)" && touch $@

seqra-cir-sast: .seqra-sast-test-util
.seqra-sast-test-util:
	cd "$(SEQRA_SAST_TUTIL)" && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean publishToMavenLocal && \
	cd "$(ROOT)" && touch $@

seqra-cir-sast: .seqra-dfa-core
.seqra-dfa-core:
	cd "$(SEQRA_DF_CORE_DIR)" && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean publishToMavenLocal && \
	cd "$(ROOT)" && touch $@

.PHONY: seqra-cir-sast
seqra-cir-sast: .seqra-cir-sast
.seqra-cir-sast:
	cd seqra-cir-sast && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process clean build && \
	cd "$(ROOT)" && touch $@

.PHONY: seqra-cir-sast-test
seqra-cir-sast-test: .seqra-cir-sast
	cd seqra-cir-sast && GRADLE_OPTS="-Xmx4g -Dkotlin.daemon.jvm.options=-Xmx3g" ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process test

# clean
.PHONY: clean-dfa-core-deps clean-dfa-core clean-all clean-maven-local
clean-maven-local:
	rm -rf "$(HOME)/.m2/repository/org/seqra"
	rm -f "$(ROOT)"/.seqra-*

clean-dfa-core-deps:
	cd "$(SEQRA_CMN_BLD_DIR)" && ./gradlew clean
	cd "$(SEQRA_IR_DIR)" && ./gradlew clean
	cd "$(SEQRA_UTILS_DIR)" && ./gradlew clean
	cd "$(SEQRA_CONFIG_RULES_DIR)" && ./gradlew clean

clean-dfa-core: clean-dfa-core-deps
	cd "$(SEQRA_DF_CORE_DIR)" && ./gradlew clean