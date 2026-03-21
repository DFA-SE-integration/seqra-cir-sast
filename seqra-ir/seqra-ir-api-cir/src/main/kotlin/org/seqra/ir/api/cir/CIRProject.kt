package org.seqra.ir.api.cir

typealias CirName = String
typealias TargetName = String

data class TargetID(val name: TargetName, val project: CIRProject)

data class ProjectCompilationTarget(
    val targetId: TargetID,
    // List of cirs
    val internalDependencies: List<CirName>,
    // List of target names
    val externalDependencies: List<TargetName>
)

interface CIRProject {
    val targets: Set<TargetID>
    fun findTarget(targetID: TargetID): ProjectCompilationTarget

    fun refreshed(): CIRProject
}
