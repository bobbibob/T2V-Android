package com.t2v.core.audio

import android.content.Context
import com.t2v.core.markup.AudioTag
import com.t2v.data.AudioClipEntity
import com.t2v.data.AudioTrackEntity
import com.t2v.generators.Generator
import com.t2v.generators.GeneratorCategory
import com.t2v.generators.GeneratorRequest
import com.t2v.generators.GeneratorRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Generates audio clips from `<music>` / `<sfx>` markup tags and saves them to
 * Room so they show up in the AudioEditor timeline.
 *
 * Position on the timeline is computed from the cumulative duration of the
 * voice segments already in Room: the inserter reads each preceding segment's
 * `durationMs` and `pauseBeforeMs`, sums them, and uses that as the new clip's
 * `timelineStartMs`. This keeps the clip glued to the point where the tag
 * appeared in the source text.
 *
 * Default `gainDb` comes from the project's settings (selected generator's
 * volume), so the editor's volume slider reflects the user's choice without
 * baking it into the markup.
 */
class AudioTagInserter(
    private val appContext: Context,
    private val generatorRegistry: () -> GeneratorRegistry,
    private val trackVolumeDb: () -> Map<AudioTrackKind, Double>,
    /** Returns id selected by the user (without `:music`/`:sound` suffix). */
    private val selectedMusicId: () -> String = { "" },
    private val selectedSoundId: () -> String = { "" },
) {
    suspend fun insert(tags: List<AudioTag>, audiobookId: Long): Int = withContext(Dispatchers.IO) {
        if (tags.isEmpty()) return@withContext 0
        val db = AppDatabaseProxy.from(appContext, audiobookId)
        var inserted = 0
        for (tag in tags) {
            val category = when (tag.category) {
                AudioTag.Category.Music -> GeneratorCategory.Music
                AudioTag.Category.Sound -> GeneratorCategory.Sound
            }
            val generator = pickGenerator(category) ?: continue
            val trackKind = when (tag.category) {
                AudioTag.Category.Music -> AudioTrackKind.Music
                AudioTag.Category.Sound -> AudioTrackKind.Sound
            }
            val trackId = "$audiobookId-${trackKind.name.lowercase()}"
            val output = File(
                appContext.filesDir,
                "audiobooks/$audiobookId/tag-${tag.category.name.lowercase()}-${UUID.randomUUID()}.wav",
            )
            output.parentFile?.mkdirs()
            val generated = runCatching {
                generator.generate(
                    GeneratorRequest(
                        prompt = tag.prompt,
                        outputFile = output,
                        category = category,
                    ),
                )
            }.getOrNull() ?: continue
            val timelineStartMs = db.positionFor(tag)
            val trackVolume = trackVolumeDb().getValue(trackKind)
            db.persist(
                AudioTrackEntity(
                    id = trackId,
                    audiobookId = audiobookId,
                    type = trackKind.name.uppercase(),
                    title = trackKind.name,
                    orderIndex = trackKind.ordinal,
                    volumeDb = trackVolume.toFloat(),
                    updatedAt = System.currentTimeMillis(),
                ),
                AudioClipEntity(
                    id = UUID.randomUUID().toString(),
                    trackId = trackId,
                    sourcePath = generated.outputFile.absolutePath,
                    timelineStartMs = timelineStartMs,
                    sourceStartMs = 0,
                    sourceEndMs = generated.durationMs.toLong().coerceAtLeast(0),
                    gainDb = 0f,
                    speed = 1f,
                    fadeInMs = 0,
                    fadeOutMs = 0,
                    loop = false,
                    locked = false,
                    markupTagId = "${tag.category.name.lowercase()}-${tag.startOffset}",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            inserted += 1
        }
        inserted
    }

    private fun pickGenerator(category: GeneratorCategory): Generator? {
        val registry = generatorRegistry()
        val preferred = when (category) {
            GeneratorCategory.Music -> selectedMusicId()
            GeneratorCategory.Sound -> selectedSoundId()
        }.takeIf { it.isNotBlank() }
        // Accept the bare id, the suffixed `id:music`/`id:sound` form, and the
        // Accept either the bare generator id or the suffixed
        // `:music`/`:sound` form that older ModelsScreen entries used.
        val candidates = registry.all()
        val match = preferred?.let { pref ->
            candidates.firstOrNull { gen ->
                gen.id == pref || gen.id == pref.removeSuffix(":music").removeSuffix(":sound")
            }
        }
        return match?.takeIf { it.isAvailable() }
            ?: candidates.firstOrNull { it.category == category && it.isAvailable() }
    }
}

/**
 * Tiny wrapper around AppDatabase so this module doesn't need to import the
 * full data layer (which would create a cycle through worker → data → core).
 * It only exposes the two operations we need: read the cumulative voice
 * position up to a tag, and write the new clip row.
 */
internal object AppDatabaseProxy {
    fun from(context: Context, audiobookId: Long): TagPersistence =
        RoomTagPersistence(context, audiobookId)
}

/**
 * Resolves the timeline position for a tag and persists the resulting clip.
 *
 * Position is computed as the sum of `pauseBeforeMs + durationMs` for all
 * segments that appear before this tag, plus any pause-before-ms from the
 * segment that contains the tag itself. When there are no segments yet the
 * tag lands at 0 ms (which is fine for fresh audiobooks).
 */
internal interface TagPersistence {
    suspend fun positionFor(tag: AudioTag): Long
    suspend fun persist(track: AudioTrackEntity, clip: AudioClipEntity)
}

internal class RoomTagPersistence(
    context: Context,
    private val audiobookId: Long,
) : TagPersistence {

    private val database = com.t2v.data.AppDatabase.get(context)

    override suspend fun positionFor(tag: AudioTag): Long {
        val segments = database.segments().listForAudiobook(audiobookId)
        return segments.sumOf { (it.pauseBeforeMs + it.durationMs).toLong() }
    }

    override suspend fun persist(track: AudioTrackEntity, clip: AudioClipEntity) {
        database.audioTimeline().upsertTracks(listOf(track))
        database.audioTimeline().upsertClips(listOf(clip))
    }
}
