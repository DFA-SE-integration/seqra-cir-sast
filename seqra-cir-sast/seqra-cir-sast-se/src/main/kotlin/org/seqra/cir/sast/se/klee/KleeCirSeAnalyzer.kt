package org.seqra.cir.sast.se.klee

import mu.KLogging
import org.seqra.cir.sast.se.api.CirSeAnalyzer
import org.seqra.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object KleeCirSeAnalyzer : CirSeAnalyzer {

    private val logger = object : KLogging() {}.logger

    /** Lowered bitcode for debugging (cir-klee `--keep-bc`). */
    private val keepBcPath: Path = Path.of("/workspace/kleebc/klee.bc")

    /** Same search order as cir-klee when env is unset (e.g. Gradle not forwarding env). */
    private fun resolveLlvmAs(): String? {
        System.getenv("LLVM_AS_16")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        System.getenv("LLVM_AS")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val candidates = listOf(
            "/usr/lib/llvm-16/bin/llvm-as",
            "/usr/bin/llvm-as-16",
            "/usr/bin/llvm-as-14",
            "/usr/lib/llvm-14/bin/llvm-as",
            "/usr/bin/llvm-as-13",
            "/usr/lib/llvm-13/bin/llvm-as",
        )
        for (p in candidates) {
            val f = Path.of(p)
            if (Files.isRegularFile(f) && Files.isExecutable(f)) return p
        }
        return null
    }

    private fun resolveKlee(): String? {
        System.getenv("KLEE_BIN")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val candidates = listOf(
            "/workspace/klee-linux-x86_64/klee",
            "/workspace/klee-linux-arm64/klee",
        )
        for (p in candidates) {
            val f = Path.of(p)
            if (Files.isRegularFile(f) && Files.isExecutable(f)) return p
        }
        return null
    }

    override fun verifyTrace(trace: VulnerabilityWithTrace, cirFile: Path): Boolean {
        val cirKlee = envExecutable("CIRTAC_KLEE")

        val pbFile = Files.createTempFile("cir-klee-pre-", ".pb")
        try {
            Files.write(pbFile, KleeTraceSerializer.serialize(trace))

            Files.createDirectories(keepBcPath.parent)
            val keepBc = keepBcPath.toAbsolutePath().normalize()
            val llvmAs = resolveLlvmAs()
            val klee = resolveKlee()

            val cmd = mutableListOf(
                cirKlee.absolutePath,
                cirFile.toAbsolutePath().toString(),
                pbFile.toAbsolutePath().toString(),
            )
            klee?.let { cmd.add("--klee=$it") }
            llvmAs?.let { cmd.add("--llvm-as=$it") }
            cmd.add("--keep-bc=$keepBc")
            System.getenv("CIRKLEE_TRACE_GUIDE_STATS")?.trim()?.takeIf { it == "1" || it.equals("true", ignoreCase = true) }?.let {
                cmd.add("--trace-guide-stats")
            }

            val proc = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()

            val out = proc.inputStream.bufferedReader().readText()
            val rc = proc.waitFor()
            logger.info { "cir-klee exit=$rc keep-bc=$keepBc output:\n$out" }
            return rc == 0
        } finally {
            Files.deleteIfExists(pbFile)
        }
    }

    private fun envExecutable(name: String): File {
        val raw = System.getenv(name)?.trim().orEmpty()
        check(raw.isNotEmpty()) { "Environment variable $name must be set to an executable path" }
        val f = File(raw)
        check(f.exists() && f.canExecute()) { "$name is not executable: ${f.absolutePath}" }
        return f
    }
}
