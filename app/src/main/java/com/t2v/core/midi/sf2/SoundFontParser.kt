package com.t2v.core.midi.sf2

import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Парсер SoundFont 2.x файлов.
 *
 * Не пытаемся быть полностью spec-compliant — мы реализуем только то, что
 * нужно для рендеринга GeneralUser GS (или аналогичного GM SoundFont):
 *  - sampleData (16-bit PCM)
 *  - presets (phdr → pbag → pgen)
 *  - instruments (inst → ibag → igen)
 *  - samples (shdr)
 *
 **  Generators, которые мы НЕ поддерживаем (но не критичны):
 *  - Initial filter cutoff / resonance (мы их игнорируем)
 *  - Modulators (LFO, envelope-to-pitch) — игнорируются
 *  - Custom balance / pan — берём только default
 *
 * Спека: http://www.synthfont.com/sfspec24.pdf
 */
object SoundFontParser {

    fun parse(input: InputStream): SoundFont {
        val data = input.readBytes()
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        var version = "2.1"
        var sampleData = ShortArray(0)
        val presets = mutableListOf<Preset>()
        val instruments = mutableListOf<Instrument>()
        val samples = mutableListOf<Sample>()

        // Verify RIFF header
        val riff = readAscii(bb, 4)
        require(riff == "RIFF") { "Not a RIFF file: '$riff'" }
        bb.position(bb.position() + 4)  // size
        val sfbk = readAscii(bb, 4)
        require(sfbk == "sfbk") { "Not an SF2 file: '$sfbk'" }

        // Iterate top-level LIST chunks
        while (bb.remaining() >= 8) {
            val chunkId = readAscii(bb, 4)
            val chunkSize = bb.int.toLong() and 0xFFFFFFFFL
            if (chunkId == "LIST") {
                val listType = readAscii(bb, 4)
                val listEnd = (bb.position() + (chunkSize - 4)).toInt()
                when (listType) {
                    "INFO" -> {
                        // read version (ifil chunk)
                        while (bb.position() < listEnd) {
                            val subId = readAscii(bb, 4)
                            val subSize = bb.int.toLong() and 0xFFFFFFFFL
                            if (subId == "ifil") {
                                version = "${bb.short}.${bb.short}"
                            } else {
                                bb.position(bb.position() + subSize.toInt())
                            }
                            if (subSize % 2 == 1L) bb.position(bb.position() + 1)
                        }
                    }
                    "sdta" -> {
                        while (bb.position() < listEnd) {
                            val subId = readAscii(bb, 4)
                            val subSize = bb.int.toLong() and 0xFFFFFFFFL
                            if (subId == "smpl") {
                                sampleData = ShortArray(subSize.toInt() / 2)
                                for (i in sampleData.indices) {
                                    sampleData[i] = bb.short
                                }
                            } else {
                                bb.position(bb.position() + subSize.toInt())
                            }
                            if (subSize % 2 == 1L) bb.position(bb.position() + 1)
                        }
                    }
                    "pdta" -> {
                        var phdrRaw: List<ByteArray> = emptyList()
                        var pbagRaw: List<ByteArray> = emptyList()
                        var pgenRaw: List<ByteArray> = emptyList()
                        var instRaw: List<ByteArray> = emptyList()
                        var ibagRaw: List<ByteArray> = emptyList()
                        var igenRaw: List<ByteArray> = emptyList()
                        var shdrRaw: List<ByteArray> = emptyList()
                        while (bb.position() < listEnd) {
                            val subId = readAscii(bb, 4)
                            val subSize = bb.int.toLong() and 0xFFFFFFFFL
                            val bytes = ByteArray(subSize.toInt())
                            bb.get(bytes)
                            when (subId) {
                                "phdr" -> phdrRaw = splitRecords(bytes, 38)
                                "pbag" -> pbagRaw = splitRecords(bytes, 4)
                                "pgen" -> pgenRaw = splitRecords(bytes, 4)
                                "inst" -> instRaw = splitRecords(bytes, 22)
                                "ibag" -> ibagRaw = splitRecords(bytes, 4)
                                "igen" -> igenRaw = splitRecords(bytes, 4)
                                "shdr" -> shdrRaw = splitRecords(bytes, 46)
                            }
                            if (subSize % 2 == 1L) bb.position(bb.position() + 1)
                        }
                        samples.addAll(shdrRaw.map { parseSample(it) })
                        instruments.addAll(instRaw.dropLast(1).map { parseInstrument(it) })  // last is terminal
                        presets.addAll(phdrRaw.dropLast(1).map { parsePreset(it) })  // last is terminal
                        // We don't fully resolve zone structure here — for our simplified
                        // renderer we just need preset.program -> first sample mapping.
                    }
                    else -> {
                        // Skip unknown LIST
                        bb.position(listEnd)
                    }
                }
            } else {
                // Skip unknown top-level chunk
                bb.position(bb.position() + chunkSize.toInt())
                if (chunkSize % 2 == 1L) bb.position(bb.position() + 1)
            }
        }

        return SoundFont(
            version = version,
            sampleData = sampleData,
            presets = presets,
            instruments = instruments,
            samples = samples,
        )
    }

    private fun splitRecords(bytes: ByteArray, recordSize: Int): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        var i = 0
        while (i + recordSize <= bytes.size) {
            out.add(bytes.copyOfRange(i, i + recordSize))
            i += recordSize
        }
        return out
    }

    private fun parsePreset(bytes: ByteArray): Preset {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val name = readAscii(bb, 20)
        val program = bb.short.toInt() and 0xFF
        val bank = bb.short.toInt() and 0xFF
        val pbagIndex = bb.short.toInt() and 0xFFFF
        // 12 bytes of pbag wBank and dwMod reserved
        return Preset(name = name.trimEnd('\u0000'), program = program, bank = bank, pbagIndex = pbagIndex)
    }

    private fun parseInstrument(bytes: ByteArray): Instrument {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val name = readAscii(bb, 20)
        val ibagIndex = bb.short.toInt() and 0xFFFF
        return Instrument(name = name.trimEnd('\u0000'), ibagIndex = ibagIndex)
    }

    private fun parseSample(bytes: ByteArray): Sample {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val name = readAscii(bb, 20)
        val start = bb.int
        val end = bb.int
        val loopStart = bb.int
        val loopEnd = bb.int
        val sampleRate = bb.int
        val originalKey = bb.short.toInt() and 0xFF
        val correction = bb.short.toInt()
        val sampleLink = bb.short.toInt() and 0xFFFF
        val sampleType = SampleType.fromInt(bb.short.toInt() and 0xFFFF)
        return Sample(
            name = name.trimEnd('\u0000'),
            start = start,
            end = end,
            loopStart = loopStart,
            loopEnd = loopEnd,
            sampleRate = sampleRate,
            originalKey = originalKey,
            correction = correction,
            sampleType = sampleType,
            link = sampleLink,
        )
    }

    private fun readAscii(bb: ByteBuffer, n: Int): String {
        val arr = ByteArray(n)
        bb.get(arr)
        return String(arr, Charsets.US_ASCII)
    }
}
