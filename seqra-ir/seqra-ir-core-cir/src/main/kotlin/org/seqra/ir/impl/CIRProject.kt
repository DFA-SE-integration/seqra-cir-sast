package org.seqra.ir.impl

import org.seqra.ir.api.cir.CIRProject
import org.seqra.ir.api.cir.ProjectCompilationTarget
import org.seqra.ir.api.cir.CompileCommands
import org.seqra.ir.api.cir.LinkCommands
import org.seqra.ir.api.cir.TargetID
import org.seqra.ir.impl.sources.findCompileCommands
import org.seqra.ir.impl.sources.findLinkCommands
import java.io.File

data class CIRProjectImpl(
    private val cirLinkCommands: File?, private val cirCompileCommands: File?
) : CIRProject {
    constructor(projectDir: File) : this(
        projectDir.findLinkCommands().singleOrNull(), projectDir.findCompileCommands().singleOrNull()
    ) {
        if (cirLinkCommands == null) {
            logger.warn { "Did not find link commands for project ${projectDir.path}" }
        }
        if (cirCompileCommands == null) {
            logger.warn { "Did not find compile commands for project ${projectDir.path}" }
        }
    }

    override fun refreshed() = CIRProjectImpl(cirLinkCommands, cirCompileCommands)

    private val linkCommands = cirLinkCommands?.let { LinkCommands.fromFile(it) }
    private val compileCommands = cirCompileCommands?.let { CompileCommands.fromFile(it) }

    override val targets: Set<TargetID>
        get() = (linkCommands?.targets.orEmpty() + compileCommands?.targets.orEmpty()).map { TargetID(it, this) }
            .also { if (it.isEmpty()) throw IllegalArgumentException("Attempted to create an empty project: no targets have been found.") }
            .toSet()

    override fun findTarget(targetID: TargetID): ProjectCompilationTarget {
        // Search for target in the compile commands
        compileCommands?.mapObjectToCir(targetID.name)?.let {
            return ProjectCompilationTarget(
                targetId = targetID, internalDependencies = listOf(it), externalDependencies = emptyList()
            )
        }

        // If did not succeed, trey to search in link commands
        val dependencies = linkCommands?.dependenciesOfTarget(targetID.name).orEmpty()

        // If even link commands does contain any information
        // about given target, then it is unknown
        if (dependencies.isEmpty()) {
            return ProjectCompilationTarget(targetID, emptyList(), listOf(targetID.name))
        }

        // This is cirs
        val internalDependencies = dependencies.mapNotNull { compileCommands?.mapObjectToCir(it) }
        val externalDependencies = dependencies.filter {
            compileCommands?.mapObjectToCir(it) == null && linkCommands?.targets?.let { targets ->
                !targets.contains(
                    it
                )
            } ?: true
        }

        return ProjectCompilationTarget(targetID, internalDependencies, externalDependencies)
    }
}