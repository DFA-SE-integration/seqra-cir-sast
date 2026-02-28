package org.seqra.common

import org.gradle.api.Project
import org.gradle.internal.extensions.core.extra

interface SeqraDependency {
    val seqraRepository: String
    val versionProperty: String

    private fun repoInitializedProperty(project: Project): String =
        "seqra-initialized-${project.path}-$seqraRepository"

    private fun Project.ensureRepository() {
        val initProperty = repoInitializedProperty(this)
        if (extra.has(initProperty)) return
        extra.set(initProperty, this)

        includeSeqraRepository(seqraRepository)
    }

    fun Project.propertyDep(group: String, name: String): String {
        ensureRepository()

        val version = project.findProperty(versionProperty) as? String
            ?: error("Property $versionProperty required for $group:$name")
        return dep(group, name, version)
    }
}
