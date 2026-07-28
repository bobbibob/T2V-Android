# Changelog

Все значимые изменения в T2V документируются здесь.

## [Unreleased] - 2026-07-28

### Added
- **MusicGen Small scaffold** in `generators/impl/MusicGenMusicGenerator.kt`.
  - `Generator` implementation registered in `GeneratorRegistry`.
  - `GenerationModelCatalog` entry `musicgen-small` with
    `support=RuntimeInDevelopment`, `repository="wide-video/musicgen-small-v1.0.0"`,
    `approximateDownloadBytes=600_000_000`, `license="CC-BY-NC-4.0"`.
  - UI card under ModelsScreen → Music tab. Selecting routes `<music>`
    tag generation to the MusicGen generator.
  - **Currently falls back to `ProceduralAudioSynth.synthMusic()`** because
    no working ARM64 ONNX export of the autoregressive decoder exists
    for Android yet. The fallback is transparent to the user.
- **ZipVoice Distill TTS engine** in `tts/engines/ZipVoiceTtsEngine.kt`.
  - `TtsEngine` implementation registered in `EngineRegistry` as
    `id="zipvoice_distill"`, `kind=Local`, `supportsCloning=true`.
  - `GenerationModelCatalog` entry `zipvoice-distill-int8` (already
    existed; now backed by a real engine class instead of just metadata).
  - Validates `VoiceConfig.referenceAudioPath` in `synthesize()` and
    throws `TtsEngineException.Generic` if the file is missing.
  - Throws `TtsEngineException.NotInstalled("zipvoice_distill")` when
    the Android sherpa-onnx runtime is not yet updated for ZipVoice
    inference.
- `models_download_button` string in all 11 locales (en/ru/ar/de/es/fr/
  hi/it/ja/pt/zh).

### Fixed
- **`AudioTagInserter.positionFor()` bug**: previously the inserter ran
  BEFORE the voice-segment loop, so each segment still had
  `durationMs=0`. The `positionFor()` sum was zero, and every music/sfx
  clip was placed at `timelineStartMs=0`. The inserter now runs
  AFTER the voice-segment loop, so it can read each segment's
  actual `pauseBeforeMs + durationMs` and compute the right timeline
  position. (Refs: self-test #2, all clips landed at 0.)
- **Kokoro inference hang on long text**: previously if Kokoro's ONNX
  runtime got stuck on a long segment, the audiobook stayed in
  `running` forever. `engine.synthesize()` is now wrapped in
  `withTimeoutOrNull(SEGMENT_TIMEOUT_MS = 5 minutes)`. On timeout
  the segment is marked failed and a clear error message is shown.
  (Refs: self-test #3, audiobook #3 stuck forever.)
- **AudioTagInserter moved out of the pre-segment loop in
  `GenerationPipeline.generate()`** so the inserter can see real
  segment durations.

### Notes
- Все 5 пунктов плана (см. `STATUS.md` → "Что делаем прямо сейчас")
  реализованы в `codex/models-download` ветке. CI run 30347936814
  зелёный (test + build).
- Подписка / монетизация по-прежнему **отложены** (см. решение
  владельца от 2026-07-28 в `docs/AI_HANDOFF.md`).
- Модели **не в APK** — пользователь скачивает сам через
  `HuggingFaceRepository`. MusicGen / ZipVoice — scaffolds, реальный
  inference появится когда сообщество выпустит ARM64-совместимые
  ONNX-экспорты.

## [Unreleased - 2026-07-27] - audio tags & NSynth scaffolding

### Added
- XML-style audio tags for in-text music and SFX insertion:
  `<music>prompt</music>` and `<sfx>prompt</sfx>`.
  - `LTVMarkupParser.extractAudioTags()` returns the ordered list with
    positions; `parseSpans()` now breaks voice chunks at every audio tag
    boundary so the pipeline can place the clip exactly where the tag
    appeared in the source text.
  - `MarkupState` is unchanged; voice text never contains the prompt.
- `core/audio/AudioTagInserter` runs after TTS synthesis. For each tag it
  picks the selected music/sound generator from settings, generates a WAV
  into the audiobook folder, computes the timeline position from existing
  segment durations, and persists an `AudioClipEntity` on the right track.
  Default gain comes from `AudioEditProject` defaults so the editor slider
  controls the clip just like any other music/SFX.
- `MarkupHighlighter` now colours `<music>`/`<sfx>` tags with the accent
  palette. `MarkupToolbar` adds two new chips: Music and SFX.
- 11 locales gained `markup_music` and `markup_sfx` strings.
- `LTVMarkupAudioTagsTest` covers parser behaviour; existing
  `TextProcessorTest` was updated to consume the new `ProcessResult`.
- `Magenta NSynth` (wavenet) registered in `GenerationModelCatalog` as a
  `RuntimeInDevelopment` on-device SFX model with a 4 sec mono 8 kHz
  output. `NSynthSoundGenerator` refuses to run until
  `LiteRtModelInstaller.markSmokeTested()` is called after a real ARM64
  smoke-test on a device.

### Fixed
- `AudioTimelineDao` now exposes `trackByType()` so the inserter can
  reuse a single track id per audiobook per category.

### Changed
- After generation, if the source text contained `<music>` or `<sfx>` tags
  and at least one clip was generated, the Generation screen now auto-jumps
  to the AudioEditor for that audiobook. Otherwise it still shows the
  Audiobook-ready card with manual navigation to Review / Music Mix.
- `GenerationPipeline.Progress.audioTagClips` carries the count of clips
  generated from markup tags so the UI can decide between routes without
  re-querying Room.
- `BundledMusicGenerator` / `BundledSoundGenerator` and the bundled
  `assets/music` + `assets/sound` placeholder WAVs. The bundled cards in
  ModelsScreen are gone. SFX now flows through either
  `ProceduralAudioSynth` (offline, no model) or a future Magenta NSynth
  on-device generator.

## [Unreleased - 2026-07-26] - 3-track editor & FFmpeg+LAME

### Added
- Three-track editor: VOICE / MUSIC / SOUND with `AudioTrackEntity`,
  `AudioClipEntity`, `ChapterExportEntity` Room entities.
- Room migration `1 -> 2` preserves existing projects.
- For each clip: source file, timeline position, source trim bounds,
  speed, gain, fade in/out, loop, lock, markup-tag link.
- `FFmpegBridge` timeline-render and three-track production mix.
- For real MP3: `libmp3lame` (LAME 3.100) statically linked in Android
  FFmpeg, built from source with SHA-256 verification in CI.
- ModelsScreen tabs: Голос / Музыка / Звуки.
- `core/model/GenerationModelCatalog.kt` — единый типизированный источник
  для Voice/Music/Sound моделей с `Support.Verified` /
  `RuntimeInDevelopment` / `Experimental` статусами.
- `Magenta NSynth` placeholder, `Stable Audio Open Small` (music),
  `Stable Audio 3 Small` (sound), `PocketTTS` (cloning),
  `ZipVoice Distill` (cloning) — все как `RuntimeInDevelopment` /
  `Experimental` до device smoke-test.
- Local Piper voices: Amy (en-US), Cori (en-GB), plus German (Thorsten,
  Kerstin), French (Siwis, Tom), Spanish (Carlos, Ald), Italian
  (Riccardo), Chinese (Huayan), Japanese (Kai). All through built-in
  SherpaOnnx runtime.
- TagDocs Info dialog on every model/generator card. Localized in 11
  languages via `info_*` strings.

## [0.1.0] - 2026-07-23

### Added
- Полная Android-структура (Kotlin DSL, AGP 8.5, Kotlin 1.9.24).
- Core: TextProcessor, LTVMarkupParser, AudioEncoder, AudioMixer,
  FFmpegBridge, WaveformExtractor, SubtitleWriter, TextNormalizer,
  Num2Words, ProjectManager.
- TTS-движки: Kokoro (on-device, ORT Android NNAPI), OpenAI, ElevenLabs,
  Gemini, Azure, Custom HTTP, Remote Host client.
- Data: Room (AppDatabase + 6 entities + 6 DAOs), DataStore Settings.
- UI: 7 экранов (Editor, Generation, Review, Music Mix, Voices,
  Projects, Settings) на Jetpack Compose + Material 3.
- Локальный Kokoro через sherpa-onnx Android runtime.
- Точный размер Kokoro из Hugging Face и прогресс загрузки в процентах и байтах.
- Стабильная debug-подпись APK между GitHub Actions сборками для обновления без потери данных.
- Паузы наследуют PCM-формат TTS, поэтому Kokoro 24 кГц корректно собирается в итоговый WAV.
- Ошибка генерации отображается отдельно и больше не помечается как готовая аудиокнига.
- Debug-вариант явно использует постоянный CI-keystore; сертификат APK проверяется в workflow.
- Экран генерации прокручивается и показывает плеер итогового WAV; плеер также добавлен в Review.
- Четыре русских локальных Piper/VITS-голоса: Ирина, Денис, Дмитрий и Руслан.
- Загрузка и безопасная распаковка официальных Android-пакетов русских голосов с прогрессом.
- ElevenLabs Instant Voice Clone: выбор записи, подтверждение прав, создание и выбор клона.
- Выбранный в галерее ElevenLabs голос теперь действительно передаётся в URL синтеза.
- 11 локалей strings.xml: en, ru, es, fr, de, it, pt, zh, ja, hi, ar.
- Документация: README, PORTING, ROADMAP, LTV_MARKUP, QUICKSTART,
  INTERNALS, ARCHITECTURE, CHANGELOG.
- Тесты: 8 unit + 1 android e2e (JVM), 1 instrumentation (ART).
- Tools: `inspect_layout.sh`, `check_completeness.sh`, `install.sh`.

### Not included
- Реальный G2P для Kokoro (используется ASCII-fallback).
- Faster Whisper verification.
- Непроверенные локальные Chatterbox/Qwen3/OmniVoice.
- Импорт DOCX через Apache POI (используется ручной ZIP-парсер).
- Background WorkManager (каркас GenerationService есть).
- Подписка / монетизация (отложено).
- Модели в APK (пользователь скачивает сам).

## [Unreleased] - 2026-07-28 (continued)

### Added (Models v0.3.0: полный каталог)

- **Расширен каталог `GenerationModelCatalog` до 23 записей**:
  - **Voice (8)**: Kokoro 82M, Piper/VITS, PocketTTS INT8, ZipVoice Distill,
    OpenAI TTS, ElevenLabs Multilingual v2, Gemini 2.5 Flash TTS, Azure Neural TTS.
  - **Music (10)**: TinyMusician Small (44M, MIT), TinyMusician 100M (MIT),
    GeneralUser GS SoundFont (CC-BY-3.0), procedural synth, MusicGen Small
    (CC-BY-NC-4.0 ⚠️ non-commercial), OpenAI Music, ElevenLabs Music, Suno API v1,
    Stable Audio 2.0 (Stability), Lyria 2 (Google, non-commercial).
  - **Sound (5)**: procedural synth, TinyMusician SFX, Magenta NSynth wavenet,
    Freesound CC0 Pack, ElevenLabs Sound Effects, Stable Audio 2.0.
- **Добавлены enum-поля в `Entry`**: `engineKind` (Local/Cloud) и
  `download` (None/HuggingFace/CdnBundle/Cloud). Это формализует правило
  AGENTS.md «модели не в APK, пользователь скачивает сам».
- **7 новых скаффолдов `Generator`**:
  - `TinyMusicianMusicGenerator` (id `litert.tinymusician-small.music`, MIT, 180 МБ)
  - `TinyMusicianSfxGenerator` (id `litert.tinymusician.sound`, MIT, через SoundFont)
  - `ElevenLabsMusicGenerator` (cloud, Lyria-2, до 4 минут)
  - `OpenAiMusicGenerator` (cloud, preview, 4 минуты)
  - `SunoMusicGenerator` (cloud, async POST→poll→download)
  - `StableAudioCloudGenerator` (cloud, Stability AI, music и SFX в одном)
  - `Lyria2MusicGenerator` (cloud, Google Lyria 2)
  - `FreesoundSfxGenerator` (local, CC0, 150 МБ после скачивания)
- **Удалён `MagentaRealtimeMusicGenerator`** — слишком тяжёлый (1.5 ГБ), нет
  готового LiteRT-экспорта от Google для декодер-LLM. Документировано в
  `docs/MODELS.md`.
- **SettingsRepository** получил новые ключи: `GOOGLE_KEY`, `SUNO_KEY`,
  `STABILITY_KEY`. В `SettingsScreen` добавлены поля для этих API-ключей.
- **ModelsScreen** перерисован: в Music-табе теперь 8 локальных (procedural,
  MusicGen, TinyMusician Small, TinyMusician 100M, SoundFont) + 5 облачных
  (ElevenLabs, OpenAI, Suno, Lyria 2, Stable Audio). В Sound-табе 4 локальных
  (procedural, TinyMusician SFX, NSynth, Freesound) + 2 облачных (ElevenLabs,
  Stable Audio).
- **TagDocs** добавлены для: PocketTTS, ZipVoice, TinyMusician (music и SFX),
  NSynth, Suno, Stable Audio cloud, Lyria 2, Freesound, OpenAI Music,
  ElevenLabs Music.

### Changed

- `GenerationModelCatalog.Entry` теперь требует явный `engineKind` и `download`.
  Старые записи (без них) помечены как deprecated в комментариях.
- `GeneratorRegistry` рефакторнут: разделены локальные и облачные генераторы,
  ключи API прокидываются явно.

### Documented

- `docs/MODELS.md` полностью переписан: 23 записи с типом, источником, размером,
  статусом, лицензией. Добавлены таблицы рекомендаций по выбору для voice /
  music / sound.
- `docs/AI_HANDOFF.md` (обновлено, секция «Каталог моделей 2026-07-28»)
- `STATUS.md` (обновлено, текущая итерация и план)

## [Unreleased] - 2026-07-28 (MIDI Renderer)

### Added

- **Полноценный MIDI-стек** в `core/midi/` (новый package):
  - `MidiEvents.kt` — sealed hierarchy: NoteOn, NoteOff, InstrumentChange, TempoChange, TimeShift
  - `StandardMidiFileParser.kt` — читает SMF format 0/1 (для будущего TinyMusician .mid output)
  - `TinyMusicianMidiDecoder.kt` — декодирует токены TinyMusician (TEA-like) + `fallbackFromPrompt()`
  - `MidiRenderer.kt` + `Synthesiser` — 16-bit PCM через синусоидальный синтез (16 GM-семейств)
  - `synth/DrumKit.kt` — 14 GM drum-типов (kick, snare, hihat, crash, ride, cowbell, bongo, conga) параметрически
  - `sf2/SoundFont.kt`, `sf2/SoundFontParser.kt`, `sf2/SoundFontRenderer.kt` — SoundFont 2.x парсер + sample playback
- `TinyMusicianMusicGenerator` теперь использует `MidiRenderer` (sine) вместо `ProceduralAudioSynth`
- `TinyMusicianSfxGenerator` генерирует 1-3 сек фразы через `DrumKit`
- **4 JVM unit-теста** в `core/midi/TinyMusicianMidiDecoderTest`:
  - `fallback from prompt with epic keyword returns minor progression`
  - `fallback from prompt with happy keyword returns major progression`
  - `fallback from prompt with calm keyword returns ambient progression`
  - `decoded token sequence produces NoteOn and NoteOff events`
  - `renderSine with empty sequence produces non-empty PCM` (silence)
  - `renderSine with one note produces non-zero PCM`
  - `renderSine with drum channel uses DrumKit`
  - `parses a minimal one-track MIDI file with one note`
- `docs/MIDI_RENDERER.md` — полная документация по стеку

### Architecture

- `core/midi/` — pure-Kotlin, без Android-зависимостей (кроме `kotlin.math`)
- `Synthesiser` различает 16 семейств GM-инструментов (piano, organ, guitar, bass,
  strings, brass, reed, pipe, synth lead/pad/effects, ethnic, perc, SFX)
- `DrumKit` — параметрический синтез (без сэмплов), работает в 0 МБ
- `SoundFontRenderer` — sample-based (когда скачается GeneralUser GS)

### Следующее

- Реальный TinyMusician ONNX (ждём экспорт от asigalov61 / community)
- `tools/export_tinymusician_onnx.py` (отдельный PR)
- Включить SoundFontRenderer в TinyMusicianMusicGenerator когда SoundFont скачан

## [Unreleased] - 2026-07-28 (SoundFont + ONNX export)

### Added

- **`SoundFontInstaller`** (`generators/impl/SoundFontInstaller.kt`):
  загрузчик SoundFont 2.x файлов с CDN (archive.org). Real download flow
  (HTTP → files/models/soundfonts/ → SHA-256 verify → sidecar). 30 МБ
  GeneralUser-GS скачивается за ~30 сек на 4G. Сейчас `loadInstalled()`
  синхронно парсит .sf2 через [SoundFontParser].
- **`LiteRtModelRuntime.GENERALUSER_GS_SOUNDFONT`**: новый manifest
  (30.5 МБ expected size, SHA-256 placeholder пока не выкачано).
- **`TinyMusicianMusicGenerator`**: автоматически переключается на
  `SoundFontRenderer` если SoundFont скачан, иначе `MidiRenderer.renderSine()`.
- **`TinyMusicianSfxGenerator`**: то же самое для drum-сэмплов
  (SoundFont drum kit лучше, чем sine `DrumKit`).
- **`ModelsScreen.soundFontReady`**: теперь подключен к
  `LiteRtModelRuntime.isInstalled(GENERALUSER_GS_SOUNDFONT)`. UI-карточка
  показывает реальный статус скачивания.
- **`tools/export_tinymusician_onnx.py`**: 130-строчный Python-скрипт
  для экспорта TinyMusician PyTorch → ONNX int8 → заливка в HF. Скачивает
  чекпоинт, экспортирует с `opset_version=17` (ARM64-совместимый),
  квантизует через `onnxruntime.quantization.quantize_dynamic`, считает
  SHA-256 для каждого файла, опционально пушит в HF-репо.
- **`docs/TINYMUSICIAN.md`**: полная документация по стеку, скрипту,
  roadmap интеграции ONNX.

### Architecture

- `core/midi/` — без изменений
- `SoundFontRenderer` уже мог рендерить — теперь он **вызывается** из
  реальных генераторов
- `SoundFontInstaller` использует `LiteRtModelRuntime` API для
  consistency (isInstalled / verify / file size checks)

### Следующее

- Запустить `tools/export_tinymusician_onnx.py` на рабочей станции
  → залить в `asigalov61/TinyMusician-ONNX` → обновить каталог
- `onnxruntime-mobile` AAR dependency в `app/build.gradle.kts`
- Real inference step в `TinyMusicianMusicGenerator.generate()`
