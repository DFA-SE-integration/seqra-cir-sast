import SeqraIrDependency.seqra_ir_api_jvm
import SeqraIrDependency.seqra_ir_core
import org.seqra.common.KotlinDependency

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
}

dependencies {
    api(project(":configuration-rules-common"))

    implementation(seqra_ir_api_jvm)
    implementation(seqra_ir_core)

    implementation(KotlinDependency.Libs.kotlinx_serialization_core)
    implementation(KotlinDependency.Libs.kaml)
}
