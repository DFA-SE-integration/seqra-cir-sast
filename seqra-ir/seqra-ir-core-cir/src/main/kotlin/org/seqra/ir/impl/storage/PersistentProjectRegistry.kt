package org.seqra.ir.impl.storage

import org.seqra.ir.api.cir.CIRDatabase
import org.seqra.ir.api.cir.CIRProject
import org.seqra.ir.api.cir.CirName
import org.seqra.ir.api.cir.ProjectCompilationTarget
import org.seqra.ir.api.cir.TargetID
import org.seqra.ir.api.cir.TargetName
import org.seqra.ir.api.storage.ers.Transaction
import org.seqra.ir.api.storage.ers.links

class RegisteredCompilationTarget(
    private val targetId: TargetID, private val registry: PersistentProjectRegistry
) {
    private val resolution
        get() = registry.resolve(targetId)

    val knownDependencies: List<CirName>
        get() = resolution.internalDependencies

    val foreignDependencies: List<TargetName>
        get() = resolution.externalDependencies
}

class PersistentProjectRegistry(db: CIRDatabase) {
    private val persistence = db.persistence

    private fun registerCompilationTarget(
        txn: Transaction, target: ProjectCompilationTarget
    ): RegisteredCompilationTarget {
        txn.find(PersistenceProjectEntity.TARGET_ENTITY, PersistenceProjectEntity.Target.ID, target.targetId.name)
            .firstOrNull()?.let {
                return RegisteredCompilationTarget(target.targetId, this)
            }

        val targetEntity = txn.newEntity(PersistenceProjectEntity.TARGET_ENTITY)
        targetEntity[PersistenceProjectEntity.Target.ID] = target.targetId.name

        val directDependencies = links(targetEntity, PersistenceProjectEntity.Target.INTERN_DEPENDENCIES)
        for (dependencyCirFileName in target.internalDependencies) {
            val dependencyEntity = txn.newEntity(PersistenceProjectEntity.DEPENDENCY_ENTITY).also {
                it[PersistenceProjectEntity.Dependency.ID] = dependencyCirFileName
            }
            directDependencies += dependencyEntity
        }

        val foreignDependencies = links(targetEntity, PersistenceProjectEntity.Target.EXTERN_DEPENDENCIES)
        for (externalDependencyName in target.externalDependencies) {
            val dependencyEntity = txn.newEntity(PersistenceProjectEntity.DEPENDENCY_ENTITY).also {
                it[PersistenceProjectEntity.Dependency.ID] = externalDependencyName
            }
            foreignDependencies += dependencyEntity
        }
        return RegisteredCompilationTarget(target.targetId, this)
    }

    fun registerIfNeeded(project: CIRProject): List<RegisteredCompilationTarget> {
        val result = mutableListOf<RegisteredCompilationTarget>()
        persistence.write { txn ->
            for (target in project.targets) {
                val compilationTarget = project.findTarget(target)
                result.add(registerCompilationTarget(txn, compilationTarget))
            }
        }
        return result
    }

    private fun innerResolve(
        txn: Transaction, targetName: TargetName, resolved: MutableSet<CirName>, unknown: MutableSet<TargetName>
    ) {
        val targetEntities =
            txn.find(PersistenceProjectEntity.TARGET_ENTITY, PersistenceProjectEntity.Target.ID, targetName)
        if (targetEntities.isEmpty) {
            unknown.add(targetName)
            return
        }

        val targetEntity = targetEntities.single()
        for (directDependencyEntity in targetEntity.getLinks(PersistenceProjectEntity.Target.INTERN_DEPENDENCIES)) {
            resolved.add(directDependencyEntity.get<String>(PersistenceProjectEntity.Dependency.ID)!!)
        }

        for (foreignDependencyEntity in targetEntity.getLinks(PersistenceProjectEntity.Target.EXTERN_DEPENDENCIES)) {
            innerResolve(
                txn, foreignDependencyEntity.get<String>(PersistenceProjectEntity.Dependency.ID)!!, resolved, unknown
            )
        }
    }

    fun resolve(target: TargetID): ProjectCompilationTarget {
        val resolved = hashSetOf<CirName>()
        val unknown = hashSetOf<TargetName>()
        persistence.read { txn ->
            innerResolve(txn, target.name, resolved, unknown)
        }
        return ProjectCompilationTarget(target, resolved.toList(), unknown.toList())
    }
}

object PersistenceProjectEntity {
    const val PROJECT_ENTITY = "Project"
    const val TARGET_ENTITY = "CompilationTarget"

    object Target {
        const val ID = "nameID"
        const val INTERN_DEPENDENCIES = "DirectDependencies"
        const val EXTERN_DEPENDENCIES = "ExternDependencies"
    }

    const val DEPENDENCY_ENTITY = "Dependency"

    object Dependency {
        const val ID = "nameID"
    }
}