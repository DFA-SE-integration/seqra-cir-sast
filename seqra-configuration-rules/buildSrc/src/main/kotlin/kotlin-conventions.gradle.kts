import org.seqra.common.configureDefault

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

group = "org.seqra.configuration"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
}

configureDefault("seqra-configuration-rules")
