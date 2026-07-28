# MIDI-рендерер в T2V (2026-07-28)

## Зачем

TinyMusician (asigalov61, MIT) выдаёт **MIDI-токены** — не WAV. Чтобы их
превратить в звук, нужны две вещи:

1. **Декодер токенов → MIDI-события** (NoteOn, NoteOff, Instrument, …)
2. **Синтезатор/рендерер MIDI → PCM** (sine fallback или SoundFont)

Когда появится ONNX-экспорт TinyMusician, эти две части сразу заработают.
Сейчас они уже работают с **fallback-генерацией** из mood-keywords.

## Архитектура

```
core/midi/
├── MidiEvents.kt                  # data classes: MidiSequence, MidiEvent (NoteOn/Off/...)
├── StandardMidiFileParser.kt      # читает .mid файлы (SMF format 0/1)
├── TinyMusicianMidiDecoder.kt     # декодирует токены TinyMusician
│                                 # + fallbackFromPrompt(prompt) для режима "без модели"
├── MidiRenderer.kt                # renderSine(seq) — fallback синтез (не требует SoundFont)
│   └── Synthesiser                # GM-инструменты (0..127) через синусы+ADSR
├── synth/
│   └── DrumKit.kt                 # GM-drums (канал 9): kick, snare, hihat, crash
└── sf2/
    ├── SoundFont.kt               # data classes
    ├── SoundFontParser.kt         # парсер SoundFont 2.x
    └── SoundFontRenderer.kt       # рендерит через сэмплы SoundFont (когда скачан)
```

## Использование (для разработчиков)

```kotlin
// 1. Получаем MidiSequence (из TinyMusician inference или fallback)
val sequence = TinyMusicianMidiDecoder.fallbackFromPrompt("epic cinematic music")

// 2. Рендерим в PCM
val pcm = MidiRenderer.renderSine(sequence, sampleRate = 22050)

// 3. Сохраняем как WAV
AudioEncoder.encodePcm16MonoWav(outputFile, pcm, sampleRate = 22050)
```

Или через SoundFont (когда скачан):

```kotlin
val soundFont = SoundFontParser.parse(file("GeneralUser-GS.sf2").inputStream())
val renderer = SoundFontRenderer(soundFont, sampleRate = 22050)
val pcm = renderer.render(sequence)
```

## Текущий статус

| Компонент | Статус | Что работает |
|---|---|---|
| MidiEvents | ✅ | data classes |
| StandardMidiFileParser | ✅ | парсит SMF 0/1, NoteOn/Off/Program/Tempo |
| TinyMusicianMidiDecoder | 🟡 | fallback работает; реальный inference ждёт ONNX |
| MidiRenderer (sine) | ✅ | 16-bit PCM через синусоидальный синтез + ADSR |
| DrumKit | ✅ | 14 GM drum-типов (kick, snare, hihat, crash, ...) |
| SoundFontParser | ✅ | читает PHDR/PBAG/PGEN/INST/IBAG/IGEN/SHDR |
| SoundFontRenderer | ✅ | sample playback + pitch shift + ADSR |
| **End-to-end тесты** | ✅ | 4 теста в TinyMusicianMidiDecoderTest |
| **Real TinyMusician ONNX** | 📋 | ждём экспорт от asigalov61 или community |

## Лицензия

- MidiEvents / MidiRenderer / DrumKit / парсеры / рендереры: **T2V** (наш код, MIT)
- Standard MIDI File spec: публичный стандарт
- TinyMusician: **MIT** (asigalov61) — коммерчески свободная
- GeneralUser GS SoundFont: **CC-BY-3.0** — нужна атрибуция в About

## Что НЕ реализовано

- Modulators (LFO, vibrato, pitch envelope)
- Filter envelopes (low-pass / high-pass)
- Stereo (linked samples в SoundFont)
- ROM-ссылки (не нужны для on-device)
- Micro-tuning (cents-level)
- Per-zone key/velocity range resolution (берём первую зону)
- Real-time MIDI playback (мы рендерим оффлайн)

Эти ограничения **не критичны** для цели T2V: фоновая музыка и SFX.
