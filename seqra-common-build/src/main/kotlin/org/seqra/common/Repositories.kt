package org.seqra.common

import org.gradle.api.Project
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.repositories

fun Project.includeSeqraRepository(name: String) {
    val seqraOrg = properties.getOrDefault("seqraOrg", "seqra")

    repositories {
        maven("https://maven.pkg.github.com/$seqraOrg/$name") {
            val seqraUser = System.getenv("SEQRA_GITHUB_ACTOR")
            val seqraToken = System.getenv("SEQRA_GITHUB_TOKEN")

            if (seqraUser != null && seqraToken != null) {
                credentials {
                    username = seqraUser
                    password = seqraToken
                }
            }
        }
    }
}
