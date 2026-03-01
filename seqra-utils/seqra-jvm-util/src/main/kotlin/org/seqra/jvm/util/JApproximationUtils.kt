package org.seqra.jvm.util

import org.seqra.ir.api.jvm.JIRClassOrInterface
import org.seqra.ir.api.jvm.JIRClassType
import org.seqra.ir.api.jvm.JIRClasspath
import org.seqra.ir.api.jvm.JIRClasspathFeature
import org.seqra.ir.api.jvm.JIRDatabase
import org.seqra.ir.approximation.Approximations
import org.seqra.ir.impl.types.JIRClassTypeImpl
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class ApproximationPaths(
    val engineApiJarPath: String? = System.getenv("seqra.jvm.api.jar.path"),
    val engineApproximationsJarPath: String? = System.getenv("seqra.jvm.approximations.jar.path")
) {
    val namedPaths = mapOf(
        "Seqra engine API" to engineApiJarPath,
        "Seqra Approximations" to engineApproximationsJarPath
    )
    val presentPaths: Set<String> = namedPaths.values.filterNotNull().toSet()
    val allPathsArePresent = namedPaths.values.all { it != null }
}

private val classpathApproximations: MutableMap<JIRClasspath, Set<String>> = ConcurrentHashMap()

// TODO: use another way to detect internal classes (e.g. special bytecode location type)
val JIRClassOrInterface.isInternalClass: Boolean
    get() = classpathApproximations[classpath]?.contains(name) ?: false

val JIRClassType.isInternalClass: Boolean
    get() = if (this is JIRClassTypeImpl) {
        classpathApproximations[classpath]?.contains(name) ?: false
    } else {
        jIRClass.isInternalClass
    }

suspend fun JIRDatabase.classpathWithApproximations(
    dirOrJars: List<File>,
    features: List<JIRClasspathFeature> = emptyList(),
    approximationPaths: ApproximationPaths = ApproximationPaths(),
): JIRClasspath? {
    if (!approximationPaths.allPathsArePresent) {
        return null
    }

    val approximationsPath = approximationPaths.presentPaths.map { File(it) }

    val cpWithApproximations = dirOrJars + approximationsPath

    val approximations = this.features.filterIsInstance<Approximations>().singleOrNull()
        ?: error("Approximations feature not found in database features")

    val featuresWithApproximations = features + listOf(approximations)

    val cp = classpath(cpWithApproximations, featuresWithApproximations.distinct())

    val approximationsLocations = cp.locations.filter { it.jarOrFolder in approximationsPath }
    val approximationsClasses = approximationsLocations.flatMapTo(hashSetOf()) { it.classNames ?: emptySet() }
    classpathApproximations[cp] = approximationsClasses

    return cp
}
