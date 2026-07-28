package com.t2v.core.audio

/**
 * Утилиты кодирования/декодирования WAV.
 *
 * 16-битный PCM, поддержка mono/stereo. Прямой порт упрощённой логики
 * из `app/utils/ffmpeg_utils.py` — но без ffmpeg, чтобы работать на
 * Android, где ffmpeg подключается отдельно (см. ffmpeg-kit).
 */
object AudioEncoder {

    /** Write raw mono 16-bit PCM as a WAV file. */
    fun encodePcm16MonoWav(out: java.io.File, pcm: ShortArray, sampleRate: Int): Long {
        val chunk = AudioChunk(samples = pcm, sampleRate = sampleRate, channels = 1)
        return writeWav(out, chunk)
    }

    /** Записать [chunk] в WAV-файл [out]. Возвращает количество байт данных. */
    fun writeWav(out: java.io.File, chunk: AudioChunk): Long {
        val dataSize = chunk.samples.size * 2
        val byteRate = chunk.sampleRate * chunk.channels * 2
        val totalSize = 36 + dataSize

        java.io.DataOutputStream(java.io.BufferedOutputStream(out.outputStream())).use { os ->
            // RIFF header
            os.write(byteArrayOf(0x52, 0x49, 0x46, 0x46))
            os.writeIntLe(totalSize)
            os.write(byteArrayOf(0x57, 0x41, 0x56, 0x45))
            // fmt subchunk
            os.write(byteArrayOf(0x66, 0x6D, 0x74, 0x20))
            os.writeIntLe(16)              // subchunk size
            os.writeShortLe(1)              // PCM
            os.writeShortLe(chunk.channels.toShort().toInt())
            os.writeIntLe(chunk.sampleRate)
            os.writeIntLe(byteRate)
            os.writeShortLe((chunk.channels * 2).toShort().toInt()) // block align
            os.writeShortLe(16)             // bits per sample
            // data subchunk
            os.write(byteArrayOf(0x64, 0x61, 0x74, 0x61))
            os.writeIntLe(dataSize)
            for (s in chunk.samples) os.writeShortLe(s.toInt())
        }
        return dataSize.toLong()
    }

    /**
     * Прочитать PCM из WAV-файла. Поддерживает 16-битный PCM,
     * остальные форматы конвертируются через ffmpeg-kit заранее.
     */
    fun readWav(file: java.io.File): Pair<WavInfo, AudioChunk> {
        java.io.RandomAccessFile(file, "r").use { raf ->
            fun readLeShort(): Int {
                val b0 = raf.read()
                val b1 = raf.read()
                return (b1 shl 8) or b0
            }
            fun readLeInt(): Int {
                val b0 = raf.read()
                val b1 = raf.read()
                val b2 = raf.read()
                val b3 = raf.read()
                return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
            }
            val riff = ByteArray(4).also { raf.readFully(it) }
            require(riff.toString(Charsets.US_ASCII) == "RIFF") { "Not a RIFF file" }
            readLeInt() // riffSize
            val wave = ByteArray(4).also { raf.readFully(it) }
            require(wave.toString(Charsets.US_ASCII) == "WAVE") { "Not a WAVE file" }
            var sampleRate = 0
            var channels = 0
            var bitsPerSample = 0
            var dataOffset = 0
            var dataSize = 0
            while (raf.filePointer < raf.length()) {
                val idBytes = ByteArray(4)
                if (raf.read(idBytes) < 4) break
                val id = idBytes.toString(Charsets.US_ASCII)
                if (id[0].code == 0) break
                val size = readLeInt()
                when (id) {
                    "fmt " -> {
                        val audioFormat = readLeShort() and 0xFFFF
                        require(audioFormat == 1) { "Only PCM is supported (got format $audioFormat)" }
                        channels = readLeShort() and 0xFFFF
                        sampleRate = readLeInt()
                        readLeInt() // byteRate
                        readLeShort() // blockAlign
                        bitsPerSample = readLeShort() and 0xFFFF
                        require(bitsPerSample == 16) { "Only 16-bit PCM is supported" }
                        val extraFmt = size - 16
                        if (extraFmt > 0) raf.skipBytes(extraFmt)
                    }
                    "data" -> {
                        dataOffset = raf.filePointer.toInt()
                        dataSize = size
                        val samples = ShortArray(dataSize / 2)
                        for (i in samples.indices) {
                            samples[i] = readLeShort().toShort()
                        }
                        val info = WavInfo(sampleRate, channels, bitsPerSample, dataOffset, dataSize)
                        return info to AudioChunk(samples, sampleRate, channels)
                    }
                    else -> raf.skipBytes(size)
                }
            }
            error("WAV file has no 'data' subchunk")
        }
    }

    /** Сгенерировать WAV-файл с тишиной нужной длительности. */
    fun writeSilence(out: java.io.File, durationMs: Int, sampleRate: Int = 22050, channels: Int = 1): Long {
        val n = (sampleRate * channels * durationMs) / 1000
        val silence = ShortArray(n) // already zero
        return writeWav(out, AudioChunk(silence, sampleRate, channels))
    }

    /**
     * Concatenate PCM WAV files without FFmpeg. Input formats must match.
     * Audio data is streamed, so long books are not loaded into memory.
     */
    fun concatWav(inputs: List<java.io.File>, out: java.io.File): Long {
        require(inputs.isNotEmpty()) { "No WAV files to concatenate" }
        val metadata = inputs.map(::readMetadata)
        val first = metadata.first()
        require(metadata.all {
            it.sampleRate == first.sampleRate &&
                it.channels == first.channels &&
                it.bitsPerSample == first.bitsPerSample
        }) { "All WAV files must have the same PCM format" }
        val totalDataSize = metadata.sumOf { it.dataSize.toLong() }
        require(totalDataSize <= Int.MAX_VALUE - 36L) { "WAV output exceeds 2 GB" }
        out.parentFile?.mkdirs()
        java.io.DataOutputStream(java.io.BufferedOutputStream(out.outputStream())).use { output ->
            output.write(byteArrayOf(0x52, 0x49, 0x46, 0x46))
            output.writeIntLe((36L + totalDataSize).toInt())
            output.write(byteArrayOf(0x57, 0x41, 0x56, 0x45))
            output.write(byteArrayOf(0x66, 0x6D, 0x74, 0x20))
            output.writeIntLe(16)
            output.writeShortLe(1)
            output.writeShortLe(first.channels)
            output.writeIntLe(first.sampleRate)
            output.writeIntLe(first.sampleRate * first.channels * 2)
            output.writeShortLe(first.channels * 2)
            output.writeShortLe(first.bitsPerSample)
            output.write(byteArrayOf(0x64, 0x61, 0x74, 0x61))
            output.writeIntLe(totalDataSize.toInt())

            val buffer = ByteArray(128 * 1024)
            for ((index, input) in inputs.withIndex()) {
                java.io.RandomAccessFile(input, "r").use { source ->
                    source.seek(metadata[index].dataOffset)
                    var remaining = metadata[index].dataSize
                    while (remaining > 0) {
                        val read = source.read(buffer, 0, minOf(buffer.size, remaining))
                        if (read < 0) error("Unexpected end of WAV: ${input.name}")
                        output.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            }
        }
        return totalDataSize
    }

    private data class WavMetadata(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val dataOffset: Long,
        val dataSize: Int,
    )

    private fun readMetadata(file: java.io.File): WavMetadata {
        java.io.RandomAccessFile(file, "r").use { raf ->
            fun readLeShort(): Int = raf.read() or (raf.read() shl 8)
            fun readLeInt(): Int =
                raf.read() or (raf.read() shl 8) or (raf.read() shl 16) or (raf.read() shl 24)

            val riff = ByteArray(4).also { raf.readFully(it) }
            require(riff.toString(Charsets.US_ASCII) == "RIFF") { "Not a RIFF file: ${file.name}" }
            readLeInt()
            val wave = ByteArray(4).also { raf.readFully(it) }
            require(wave.toString(Charsets.US_ASCII) == "WAVE") { "Not a WAVE file: ${file.name}" }
            var sampleRate = 0
            var channels = 0
            var bitsPerSample = 0
            while (raf.filePointer < raf.length()) {
                val idBytes = ByteArray(4)
                if (raf.read(idBytes) < 4) break
                val size = readLeInt()
                when (idBytes.toString(Charsets.US_ASCII)) {
                    "fmt " -> {
                        require(readLeShort() == 1) { "Only PCM WAV is supported" }
                        channels = readLeShort()
                        sampleRate = readLeInt()
                        raf.skipBytes(6)
                        bitsPerSample = readLeShort()
                        require(bitsPerSample == 16) { "Only 16-bit PCM WAV is supported" }
                        if (size > 16) raf.skipBytes(size - 16)
                    }
                    "data" -> return WavMetadata(
                        sampleRate,
                        channels,
                        bitsPerSample,
                        raf.filePointer,
                        size,
                    )
                    else -> raf.skipBytes(size + (size and 1))
                }
            }
            error("WAV file has no data chunk: ${file.name}")
        }
    }
}

// DataInput/DataOutput helpers для little-endian
private fun java.io.DataInputStream.readIntLe(): Int =
    readUnsignedShort() or (readUnsignedShort() shl 16)

private fun java.io.DataInputStream.readShortLe(): Short {
    val b0 = read()
    val b1 = read()
    return ((b1 shl 8) or b0).toShort()
}

private fun java.io.DataOutputStream.writeIntLe(v: Int) {
    write(v and 0xFF)
    write((v shr 8) and 0xFF)
    write((v shr 16) and 0xFF)
    write((v shr 24) and 0xFF)
}

private fun java.io.DataOutputStream.writeShortLe(v: Int) {
    write(v and 0xFF)
    write((v shr 8) and 0xFF)
}
