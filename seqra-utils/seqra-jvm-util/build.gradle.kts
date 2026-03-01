import org.seqra.common.KotlinDependency
import SeqraIrDependency.seqra_ir_api_jvm
import SeqraIrDependency.seqra_ir_approximations
import SeqraIrDependency.seqra_ir_core

plugins {
    id("kotlin-conventions")
}

dependencies {
    api(project(":common-util"))

    implementation(seqra_ir_api_jvm)
    implementation(seqra_ir_core)
    implementation(seqra_ir_approximations)

    implementation(KotlinDependency.Libs.reflect)
}
