# Changelog

Все значимые изменения в T2V документируются здесь.

## [Unreleased] - 2026-08-07

### Added
- **MusicGen-small через LiteRT** (`MusicGenOnnxGenerator`): зарегистрирован
  манифест трёхступенчатого пайплайна (`text_encoder.tflite` / `lm.tflite` /
  `audio_decoder.tflite`), каталог-запись `musicgen-small` (
  `RuntimeInDevelopment`, `canInstall=false`), TagDocs и регистрация в
  `GeneratorRegistry`. Генератор, как и NSynth, отдаёт `isAvailable()=false`
  до ARM64 smoke-test на устройстве — ничего фейкового не генерирует.
- `LiteRtModelInstaller.isSmokeTestedInstalled(manifest)` — обобщённый
  смоук-гейт для любого LiteRT-бандла (раньше был только для NSynth).
- **Универсальное скачивание моделей из каталога**: `ModelsViewModel
  .downloadModelFromCatalog()` теперь обрабатывает любую запись каталога с
  реальным Hugging Face-репозиторием (`author/name`), а не только Kokoro.
  LiteRT-бандлы (например `musicgen-small`) качают только свои `.tflite`
  файлы (`HuggingFaceRepository.install`); репозиторий без `.tflite`
  (ONNX-экспорт) — явная ошибка, а не загрузка всего репозитория. Кнопка
  «Скачать» в `DownloadableModelCard` показывается только для записей с
  `Support.Verified` (`canInstall`). В Models → Music добавлена
  `DownloadableModelCard` для `musicgen-small` (ранее карточка существовала,
  но не использовалась нигде).
- **Динамические локальные TTS-модели**: новый `SherpaOnnxLocalEngine` +
  каталожное обнаружение в `EngineRegistry`. Любая голосовая запись в
  `GenerationModelCatalog` с реальным HF-репозиторием, чьи файлы установлены,
  автоматически становится движком — без нового кода-адаптера.
- `GenerationModelCatalog.isHuggingFaceRepository(modelId)` /
  `localVoiceModelEntries()` — хелперы, отделяющие настоящие HF-репозитории
  от release-заглушек.
- **Фильтр по языку голоса**: в Models → Голос и на экране «Голоса» появился
  выпадающий список языка (`ExposedDropdownMenuBox`). Выбранный язык
  фильтрует Piper/VITS-голоса и Kokoro (английский) — показываются только
  модели, поддерживающие выбранный язык.

### Fixed
- **Kokoro зависание на длинных текстах** (>3 000 символов): `KokoroTtsEngine`
  теперь дробит текст на предложения по ≤1200 символов (`splitLongText`),
  синтезирует каждый кусок отдельным вызовом `OfflineTts.generate()` и
  склеивает PCM (`concat`). Каждый нативный вызов остаётся далеко ниже
  порога зависания.
- **`timelineStartMs=0` для всех `<music>/<sfx>` клипов**: вызов
  `AudioTagInserter.insert()` перенесён из начала `GenerationPipeline`
  в конец цикла voice-сегментов (до кодирования), когда `durationMs`
  сегментов уже известны. Позиция клипа считается по реальной длительности
  речи, а не по нулям.

### Known issues (отложено)
- **ElevenLabs clone UI** не реагирует на нажатие «Создать клон».

### Notes
- Подписка / монетизация **отложены** (см. `docs/AI_HANDOFF.md`).
- Сборка — только через GitHub Actions (`android.yml`), без локальных скриптов.

## [Unreleased] - 2026-07-28

### Added
- **Self-test кнопка в Settings** (`🧪 Run <music>/<sfx> self-test`).
  Дописывает `<music>ambient pad</music> <sfx>door creak</sfx>` в последний
  проект и запускает `GenerationPipeline.generate()`. По завершении
  показывает `audiobookId`, статус, `segmentsDone/Total` и количество
  music+sound клипов. Локализована в 11 локалях.
- **`DownloadableModelCard`** в `ModelsScreen` — переиспользуемая карточка
  для скачивания любой модели из `GenerationModelCatalog` через единый flow
  (Download / progress bar / cancel / Select).
- **`ModelsViewModel.downloadModelFromCatalog(catalogId)`** — единая точка
  входа для скачивания из каталога. Сейчас реально скачивает только
  `kokoro-82m`; для остальных записей пишет «Загрузка пока не подключена».
- **`ModelsState`**: новые поля `downloadingCatalogId`, `catalogDownloadProgress`,
  `catalogDownloadedBytes`, `catalogDownloadTotalBytes`, `isInstalled(catalogId, repository)`.
- **`HuggingFaceRepository.VERIFIED_ANDROID_MODELS`** теперь формируется
  динамически из `GenerationModelCatalog.entries` (все записи с `repository`
  в формате `author/name` и не начинающиеся с `http`). Kokoro гарантированно
  присутствует как fallback.
- **Строки** `settings_debug`, `settings_debug_selftest_help`,
  `settings_debug_selftest`, `models_download_button` во всех 11 локалях.

### Fixed
- `VoicesScreen.kt` revert'нут в `8fc42a0` baseline после серии поломок
  (CD65FC0, BFDF022, 4CB57E1, 3C8B8C2). Локальный Piper picker диалог
  не добавлялся до стабилизации.

### Known issues (отложено)
- **Kokoro inference зависает** на длинных текстах (>3 000 символов).
  Audiobook #3 застрял в `running` после повторного self-test.
- **`timelineStartMs=0`** для всех `<music>/<sfx>` клипов. Причина:
  `AudioTagInserter.insert()` вызывается ДО voice-сегментов в
  `GenerationPipeline.kt:109`, когда `durationMs=0` для всех segments.
  Должен вызываться после цикла.
- **ElevenLabs clone UI** не реагирует на нажатие «Создать клон».

### Notes
- Подписка / монетизация **отложены** (см. `docs/AI_HANDOFF.md`).
- Никаких изменений в `aapt2`, signing, R8, AAB. APK как и раньше
  собирается через `:app:assembleDebug`.
- Все 4 коммита текущей сессии на ветке `codex/models-download`
  (647dcf8, ccdd817e, 0f86301, 984e208) — последний CI 30338815715 зелёный.

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
