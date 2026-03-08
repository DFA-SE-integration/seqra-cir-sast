import SeqraConfigurationDependency.seqraRulesJvm

plugins {
    `kotlin-conventions`
}

dependencies {
    implementation(seqraRulesJvm)
}

tasks.withType<ProcessResources> {
    val configDir = layout.projectDirectory.dir("config")

    from(configDir)
}
