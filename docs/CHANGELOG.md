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
