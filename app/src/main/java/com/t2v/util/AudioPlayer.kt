package com.t2v.util

import android.content.Context
import android.media.MediaPlayer
import java.io.File

/**
 * Простой обёртчик над `MediaPlayer` для превью аудио.
 *
 * Используется в UI для воспроизведения сгенерированных сегментов,
 * голосовых превью и финального микса.
 */
class AudioPlayer {

    private var player: MediaPlayer? = null

    fun play(file: File, onComplete: () -> Unit = {}) {
        stop()
        if (!file.exists()) return
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                onComplete()
                stop()
            }
            setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            prepare()
            start()
        }
    }

    fun stop() {
        try {
            player?.stop()
        } catch (_: IllegalStateException) { }
        player?.release()
        player = null
    }
}
