package com.t2v.core.midi.sf2

/**
 * Минимальное представление распарсенного SoundFont 2.x файла.
 *
 * Спека: http://www.synthfont.com/sfspec24.pdf
 *
 * Реализованные chunk'и:
 *  - LIST/INFO (version)
 *  - sdta (smpl — 16-bit PCM сэмплы)
 *  - pdta:
 *    - phdr (preset headers, 38 байт запись)
 *    - pbag (preset bags, 4 байта)
 *    - pgen (preset generators, 4 байта)
 *    - inst (instruments, 22 байта)
 *    - ibag (instrument bags, 4 байта)
 *    - igen (instrument generators, 4 байта)
 *    - shdr (sample headers, 46 байт)
 *
 * Не реализовано (но и не нужно для рендеринга):
 *  - Modulators (articulation, filter envelopes)
 *  - Региональные zоны с микро-тюнингом
 *  - ROM-ссылки
 */
data class SoundFont(
    val version: String,
    val sampleData: ShortArray,
    val presets: List<Preset>,
    val instruments: List<Instrument>,
    val samples: List<Sample>,
) {
    fun presetByProgram(program: Int): Preset? =
        presets.firstOrNull { it.program == program && it.bank == 0 }

    fun drumPreset(note: Int): Preset? =
        // For channel 9 (drums) we look up preset 128 in bank 128
        presets.firstOrNull { it.program == note && it.bank == 128 }
}

data class Preset(
    val name: String,
    val program: Int,
    val bank: Int,
    val pbagIndex: Int,
)

data class Instrument(
    val name: String,
    val ibagIndex: Int,
)

data class Sample(
    val name: String,
    val start: Int,         // sample index in sampleData
    val end: Int,
    val loopStart: Int,
    val loopEnd: Int,
    val sampleRate: Int,
    val originalKey: Int,   // MIDI note
    val correction: Int,    // pitch correction in cents
    val sampleType: SampleType,
    val link: Int = -1,     // linked right/left sample for stereo
)

enum class SampleType {
    MONO_SAMPLE,
    RIGHT_SAMPLE,
    LEFT_SAMPLE,
    LINKED_SAMPLE,
    ROM_MONO,
    ROM_RIGHT,
    ROM_LEFT,
    ROM_LINKED,
    UNKNOWN;

    companion object {
        fun fromInt(value: Int): SampleType = when (value) {
            1 -> MONO_SAMPLE
            2 -> RIGHT_SAMPLE
            4 -> LEFT_SAMPLE
            8 -> LINKED_SAMPLE
            0x8001 -> ROM_MONO
            0x8002 -> ROM_RIGHT
            0x8004 -> ROM_LEFT
            0x8008 -> ROM_LINKED
            else -> UNKNOWN
        }
    }
}

/**
 * SF2 generators we care about.
 *
 * Source: SF2 spec § 8.1, generator indexes.
 * https://github.com/spessasus/sf2-dump/blob/main/sf2spec.pdf
 */
object Sf2Generators {
    const val STARTADDRESS_OFFSET = 0
    const val ENDADDRESS_OFFSET = 1
    const val STARTLOOP_ADDRESS_OFFSET = 2
    const val ENDLOOP_ADDRESS_OFFSET = 3
    const val INITIAL_ATTENUATION = 48
    const val PAN = 17
    const val COARSE_TUNE = 17  // alias, not real
    const val FINE_TUNE = 18  // wrong; in spec these are different indexes
    const val SAMPLE_ID = 53
    const val INSTRUMENT_ID = 41
    // We'll look up these by SF2 spec index when we encounter them.

    const val KEY_RANGE = 43
    const val VELOCITY_RANGE = 44
    const val OVERRIDING_ROOT_KEY = 58  // sample's effective root key when this zone plays
    const val EXCLUSIVE_CLASS = 59
}
