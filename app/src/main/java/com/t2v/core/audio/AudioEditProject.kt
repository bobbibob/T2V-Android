package com.t2v.core.audio

import java.util.UUID

enum class AudioTrackKind { Voice, Music, Sound }

data class AudioEditClip(
    val id: String = UUID.randomUUID().toString(),
    val sourcePath: String,
    /** Position of the clip on the chapter timeline. */
    val timelineStartMs: Long = 0,
    val startMs: Long = 0,
    /** Zero means until the end of the source. */
    val endMs: Long = 0,
    val speed: Double = 1.0,
    val gainDb: Double = 0.0,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
    val loop: Boolean = false,
    val locked: Boolean = false,
    val markupTagId: String? = null,
)

data class AudioEditProject(
    val voiceClips: List<AudioEditClip> = emptyList(),
    val musicClips: List<AudioEditClip> = emptyList(),
    val soundClips: List<AudioEditClip> = emptyList(),
    val voiceVolumeDb: Double = 0.0,
    val musicVolumeDb: Double = -24.0,
    val soundVolumeDb: Double = -8.0,
)
