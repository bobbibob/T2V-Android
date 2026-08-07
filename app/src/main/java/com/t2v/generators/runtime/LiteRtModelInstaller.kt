package com.t2v.generators.runtime

import com.t2v.core.model.GenerationModelCatalog
import java.io.File
import java.security.MessageDigest

/**
 * Plans and reports on the LiteRT/TFLite model install for a generator.
 *
 * The installer never downloads anything on its own. It returns a [Plan] that
 * the caller (e.g. `ModelsScreen`) can hand to the existing
 * [com.t2v.server.HuggingFaceRepository] so the actual transport reuses the
 * already verified Hugging Face client.
 *
 * Verification matches the [LiteRtModelRuntime] manifest exactly: byte size and
 * SHA-256 per file. This makes accidental truncation or corruption impossible
 * to ignore.
 */
class LiteRtModelInstaller(
    private val runtime: LiteRtModelRuntime,
) {
    /** Mirror of [LiteRtModelRuntime.rootDirectory] so tests can swap it via the runtime. */
    private val effectiveRoot: File get() = runtime.rootDirectory

    inner class Plan(
        val modelId: String,
        val catalog: GenerationModelCatalog.Entry,
        val manifest: LiteRtModelRuntime.BundleManifest,
        val destinationRoot: File,
    ) {
        val isInstalled: Boolean
            get() = this@LiteRtModelInstaller.runtime.isInstalled(manifest)

        fun verify(): Map<String, String> = this@LiteRtModelInstaller.runtime.verifyChecksums(manifest)
    }

    fun plan(
        manifest: LiteRtModelRuntime.BundleManifest,
        catalog: GenerationModelCatalog.Entry,
    ): Plan {
        val destination = File(effectiveRoot, manifest.modelId)
        return Plan(
            modelId = manifest.modelId,
            catalog = catalog,
            manifest = manifest,
            destinationRoot = destination,
        )
    }

    /**
     * Marks an entry as fully installed by writing a sidecar file with the
     * recorded byte count and SHA-256. The runtime uses those to short-circuit
     * downloads on subsequent runs.
     */
    fun markInstalled(plan: Plan, filePath: String, expectedBytes: Long, sha256: String): File {
        val target = File(plan.destinationRoot, filePath)
        target.parentFile?.mkdirs()
        if (!target.exists() || target.length() != expectedBytes) {
            // The installer is intentionally write-only here. The caller must
            // copy the actual bytes from the Hugging Face staging directory
            // before invoking this hook.
            throw IllegalStateException(
                "Cannot mark ${target.absolutePath} as installed: size mismatch (${target.length()} vs $expectedBytes)",
            )
        }
        val actual = sha256Of(target)
        if (!actual.equals(sha256, ignoreCase = true)) {
            throw IllegalStateException(
                "Cannot mark ${target.absolutePath} as installed: sha256 mismatch",
            )
        }
        File(plan.destinationRoot, "$filePath.sha256").writeText(sha256)
        return target
    }

    /**
     * Records that an actual device smoke-test for [plan] ran successfully.
     *
     * Until this flag is set, [isSmokeTested] returns false and any
     * Generator built on top of this installer will refuse to run. AGENTS.md
     * forbids shipping a TFLite model inside the APK and forbids marking a
     * model as Verified without a smoke-test, so the gate stays closed until
     * a real ARM64 run has happened.
     */
    fun markSmokeTested(plan: Plan) {
        File(plan.destinationRoot, ".smoke-tested").writeText(
            System.currentTimeMillis().toString(),
        )
    }

    /** True when [markSmokeTested] has been called for [plan]. */
    fun isSmokeTested(plan: Plan): Boolean =
        File(plan.destinationRoot, ".smoke-tested").isFile

    /** Convenience overload: smoke-tested for the NSynth bundle. */
    fun isSmokeTested(): Boolean =
        File(effectiveRoot, "${LiteRtModelRuntime.NSYNTH_WAVENET.modelId}/.smoke-tested").isFile

    /** True when the bundle for [manifest] has been smoke-tested on a device. */
    fun isSmokeTestedInstalled(manifest: LiteRtModelRuntime.BundleManifest): Boolean =
        File(effectiveRoot, "${manifest.modelId}/.smoke-tested").isFile

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
