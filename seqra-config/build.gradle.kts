import SeqraConfigurationDependency.seqraRulesCore

plugins {
    `kotlin-conventions`
}

dependencies {
    implementation(seqraRulesCore)
}

tasks.withType<ProcessResources> {
    val configDir = layout.projectDirectory.dir("config")

    from(configDir)
}
