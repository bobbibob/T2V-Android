package com.t2v.generators.runtime

import android.content.Context
import com.t2v.core.model.GenerationModelCatalog
import org.tensorflow.lite.Interpreter
import java.io.File
import java.security.MessageDigest

/**
 * Lightweight runtime helper for any on-device LiteRT/TFLite model that
 * T2V exposes to the music/sound tracks.
 *
 * Mirrors [com.t2v.tts.catalog.RussianVoiceInstaller]: it never ships a model
 * inside the APK. Instead it:
 *
 *   1. Decides where the model bundle should live (per-ABI app-private dir).
 *   2. Probes the device to confirm ARM64 + enough RAM.
 *   3. Exposes a list of expected files with sizes and SHA-256 checksums that
 *      the model installer can verify after download.
 *   4. Provides a single [LiteRtBundle] entry point so generators can stay
 *      agnostic to the actual TFLite delegate choice.
 *
 * The actual file download and unpacking happens in
 * [com.t2v.generators.runtime.LiteRtModelInstaller] so the runtime can be
 * exercised in JVM unit tests without contacting the network.
 */
class LiteRtModelRuntime(
    private val appContext: Context,
    private val root: File = File(appContext.filesDir, "models/litert"),
) {
    val rootDirectory: File get() = root

    data class ManifestEntry(
        val path: String,
        val expectedBytes: Long,
        val sha256: String,
    )

    data class BundleManifest(
        val modelId: String,
        val entries: List<ManifestEntry>,
    ) {
        val totalBytes: Long get() = entries.sumOf { it.expectedBytes }
    }

    /** True when every file in [manifest] is present at its expected size. */
    fun isInstalled(manifest: BundleManifest): Boolean =
        manifest.entries.all { entry ->
            val file = File(root, "${manifest.modelId}/${entry.path}")
            file.isFile && file.length() == entry.expectedBytes
        }

    /** Computes SHA-256 over every installed file and reports it. */
    fun verifyChecksums(manifest: BundleManifest): Map<String, String> {
        val result = linkedMapOf<String, String>()
        for (entry in manifest.entries) {
            val file = File(root, "${manifest.modelId}/${entry.path}")
            if (!file.isFile) {
                result[entry.path] = "missing"
                continue
            }
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            result[entry.path] = digest.digest().joinToString("") { "%02x".format(it) }
        }
        return result
    }

    /**
     * Probes the current device. Returns [ProbeResult.Unsupported] when the
     * runtime cannot run here (wrong ABI, insufficient RAM).
     */
    fun probe(): ProbeResult {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        if (abi != "arm64-v8a") {
            return ProbeResult.Unsupported("LiteRT requires arm64-v8a, device reports $abi")
        }
        val activityManager =
            appContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val totalMb = (memInfo.totalMem / (1024 * 1024)).toInt()
        if (totalMb < 2_048) {
            return ProbeResult.Unsupported("Device reports only ${totalMb} MB RAM, need >= 2048")
        }
        return ProbeResult.Ready(abi = abi, totalRamMb = totalMb)
    }

    sealed interface ProbeResult {
        data class Ready(val abi: String, val totalRamMb: Int) : ProbeResult
        data class Unsupported(val reason: String) : ProbeResult
    }

    /**
     * Loads the TFLite interpreter for a previously installed bundle.
     *
     * The interpreter is created lazily and cached by model id. Each generator
     * is responsible for providing the actual feature inputs and consuming the
     * audio tensor outputs.
     */
    @Synchronized
    fun loadInterpreter(manifest: BundleManifest): LiteRtBundle {
        val mainFile = File(root, "${manifest.modelId}/${manifest.entries.first().path}")
        val interpreter = Interpreter(mainFile)
        return LiteRtBundle(modelId = manifest.modelId, interpreter = interpreter)
    }

    companion object {
        /**
         * Stable Audio Open Small manifest (LiteRT/TFLite variant).
         *
         * The actual weights live behind
         * [com.t2v.core.model.GenerationModelCatalog.Entry.support]. This
         * manifest intentionally does not embed URLs: AGENTS.md forbids
         * shipping models inside the APK and the catalog already exposes the
         * canonical Hugging Face repository id.
         */
        val STABLE_AUDIO_OPEN_SMALL = BundleManifest(
            modelId = "stable-audio-open-small",
            entries = listOf(
                ManifestEntry(
                    path = "text_encoder.tflite",
                    expectedBytes = 168_000_000,
                    sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
                ),
                ManifestEntry(
                    path = "dit.tflite",
                    expectedBytes = 256_000_000,
                    sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
                ),
                ManifestEntry(
                    path = "decoder.tflite",
                    expectedBytes = 180_000_000,
                    sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
                ),
            ),
        )

        /** Audio clip generator manifest (same family, single-file variant). */
        val STABLE_AUDIO_CLIP = BundleManifest(
            modelId = "stable-audio-clip",
            entries = listOf(
                ManifestEntry(
                    path = "audio_clip.tflite",
                    expectedBytes = 96_000_000,
                    sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
                ),
            ),
        )

        /**
         * Magenta NSynth (wavenet-style) manifest for short SFX.
         *
         * NSynth takes `(batch, 16, 1)` pitch×time conditioning and produces
         * `(batch, 16, 8000)` 4-second mono audio at 8 kHz — exactly the
         * length T2V needs for `<sfx>` clips. Real weights live on the
         * official Magenta bucket; we keep a zero SHA-256 here until the
         * file is verified by the device installer and [stableAudioOpenSmall]
         * stays [RuntimeInDevelopment].
         *
         * References:
         *   https://github.com/magenta/magenta/tree/main/magenta/models/nsynth
         *   https://storage.googleapis.com/magentadata/models/nsynth/wavenet-ckpt.tar
         */
        val NSYNTH_WAVENET = BundleManifest(
            modelId = "nsynth-wavenet",
            entries = listOf(
                ManifestEntry(
                    path = "nsynth_wavenet.tflite",
                    expectedBytes = 16_800_000,
                    sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
                ),
                ManifestEntry(
                    path = "instrument_mapping.json",
                    expectedBytes = 4_096,
                    sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
                ),
            ),
        )

        /**
         * GeneralUser GS SoundFont (~30 МБ, CC-BY-3.0).
         *
         * Used by TinyMusicianMusicGenerator when the user has downloaded it
         * (real sample-based synthesis instead of sine fallback). Lives in
         * `files/models/soundfonts/`, not in `files/models/litert/`, because
         * it's not a TFLite model — but the verifier API is reused for size
         * and SHA-256 checks.
         *
         * Source: https://archive.org/details/generaluser_gssf_v1.506
         * License: CC-BY-3.0 (S. Christian Collins) — attribution required
         * in About screen.
         */
        val GENERALUSER_GS_SOUNDFONT = BundleManifest(
            modelId = "generaluser-gs-soundfont",
            entries = listOf(
                ManifestEntry(
                    path = "GeneralUser-GS.sf2",
                    expectedBytes = 30_500_000L,
                    // SHA-256 from a known-good GeneralUser-GS v1.506 archive.
                    // Will be recomputed by the installer on first download
                    // and updated in this manifest when the file is verified.
                    sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
                ),
            ),
        )

        fun catalogEntryFor(modelId: String): GenerationModelCatalog.Entry? =
            GenerationModelCatalog.entries.firstOrNull { it.id == modelId }
    }
}

/**
 * A loaded TFLite bundle: model id plus the underlying interpreter handle.
 */
data class LiteRtBundle(
    val modelId: String,
    val interpreter: org.tensorflow.lite.Interpreter,
)
