import org.seqra.common.configureDefault
import org.seqra.common.configureDefaultJvm
import org.seqra.common.configureDefaultPublishing

plugins {
    `java-library`
    `maven-publish`
}

group = "org.seqra.sast-test-util"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
}

configureDefaultJvm()
configureDefaultPublishing("seqra-sast-test-util")
