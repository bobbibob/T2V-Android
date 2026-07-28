# T2V — Roadmap

Версии и статус разработки. Текущая итерация — **0.2.0** (alpha).

## ✅ Сделано

### v0.1.0 (2026-07-23)
- [x] Структура Gradle-проекта (Kotlin DSL, AGP 8.5, Kotlin 1.9.24).
- [x] Манифест с разрешениями для TTS, файлов, ffmpeg.
- [x] `core/text/TextProcessor` — порт 1:1 с Python-версии (regex, чанки, главы).
- [x] `core/markup/LTVMarkupParser` — все команды (`{{voice}}`, `{{pause}}`, `{{speed}}`, ...).
- [x] `core/markup/MarkupHighlighter` — подсветка в Compose.
- [x] `core/audio/AudioEncoder` — WAV 16-bit PCM I/O.
- [x] `core/audio/AudioMixer` — voice + music + ducking + fade + normalize.
- [x] `core/audio/FFmpegBridge` — обёртка над ffmpeg-бинарником из assets.
- [x] `core/audio/WaveformExtractor` — min/max envelope.
- [x] `core/subtitle/SubtitleWriter` — SRT + karaoke-ASS.
- [x] `core/normalization/TextNormalizer` — числа, валюты, даты, проценты, римские.
- [x] `core/project/ProjectManager` — импорт TXT/MD/DOCX.
- [x] `tts/engines/KokoroTtsEngine` — локально через onnxruntime-android (NNAPI).
- [x] `tts/engines/OpenAiTtsEngine`, `ElevenLabsTtsEngine`, `GeminiTtsEngine`,
       `AzureTtsEngine`, `CustomHttpTtsEngine` — через OkHttp + kotlinx-serialization.
- [x] `tts/registry/EngineRegistry` — ленивая фабрика движков.
- [x] `data/AppDatabase` + 6 DAO + 6 entities (Room).
- [x] `data/SettingsRepository` — DataStore Preferences.
- [x] `worker/GenerationPipeline` — полный пайплайн с retry/cancel/progress.
- [x] `worker/GenerationService` — Foreground-сервис.
- [x] `ui/MainActivity` + `LTVTheme` (Material 3).
- [x] `ui/navigation/LTVNavHost` — 7 экранов.
- [x] `ui/components/LTVScaffold` + `LTVTopBar` + custom bottom nav.
- [x] `ui/waveform/WaveformCanvas` — Compose-канвас.
- [x] `ui/screens/editor` — текстовый редактор + LTV-подсветка.
- [x] `ui/screens/generation` — выбор движка, скорость, прогресс, кнопки.
- [x] `ui/screens/voices` — список голосов по движкам.
- [x] `ui/screens/projects` — список проектов.
- [x] `ui/screens/review` — список сегментов аудиокниги.
- [x] `ui/screens/music` — упрощённый микшер (voice + music + ducking).
- [x] `ui/screens/settings` — общие настройки + API-ключи.
- [x] 11 локализаций strings.xml (en/ru/es/fr/de/it/pt/zh/ja/hi/ar).
- [x] Манифест с `networkSecurityConfig` (cleartext к LAN).
- [x] `docs/PORTING.md`, `docs/ROADMAP.md`, `docs/LTV_MARKUP.md`.
- [x] 8 unit-тестов (JVM) + 1 android e2e (ART).
- [x] Tools: `inspect_layout.sh`, `check_completeness.sh`, `install.sh`.
- [x] GitHub Actions: `./gradlew :app:assembleDebug` собирает APK ~40 МБ.
- [x] `AGENTS.md` обновлён.

### v0.2.0 (2026-07-26 .. 2026-07-28)
- [x] Упавшие тесты починены (writeSilence, pause ms/s, clean, currencies, Num2Words).
- [x] **3-track editor**: VOICE / MUSIC / SOUND дорожки, Room `AudioTrackEntity` /
      `AudioClipEntity` / `ChapterExportEntity` + миграция `1 -> 2`.
- [x] **FFmpeg + LAME 3.100** для настоящего MP3 (не AAC).
- [x] **`{{music}}` и `{{sfx}}` теги** (двойные фигурные скобки) → генерация клипа
      на правильной тайм-позиции.
- [x] **`<music>промпт</music>` и `<sfx>промпт</sfx>`** (XML-стиль) →
      `LTVMarkupParser.parseSpans()` рвёт voice-чанк в каждом теге,
      `AudioTagInserter` создаёт клип на нужной дорожке.
- [x] **GenerationModelCatalog** — единый типизированный источник для
      Voice/Music/Sound моделей с `Support.Verified` /
      `RuntimeInDevelopment` / `Experimental` статусами.
- [x] **Локальный Kokoro через sherpa-onnx Android runtime**, verified end-to-end
      на устройстве R5CN30LJS4W (audiobook #2 = completed, 154 сек).
- [x] **Локальные Piper/VITS** на 15 языках (Русский, English, German,
      French, Spanish, Italian, Chinese, Japanese, Hindi, Bengali, Arabic,
      Korean).
- [x] **NSynth** (Magenta) — registered, refuses to run до ARM64 smoke-test.
- [x] **Self-test кнопка** в Settings для отладки `<music>/<sfx>` pipeline.
- [x] **DownloadableModelCard** в ModelsScreen — единая UI-карточка
      для скачивания любой модели из каталога.
- [x] **MusicGen Small scaffold** — `MusicGenMusicGenerator` зарегистрирован в
      `GeneratorRegistry`. UI card в Music tab. Сейчас fallback на
      `ProceduralAudioSynth` (нет рабочего ARM64 ONNX-экспорта). Когда
      сообщество выпустит совместимый экспорт — переключаем.
- [x] **ZipVoice Distill TTS engine** — `ZipVoiceTtsEngine` зарегистрирован в
      `EngineRegistry` как `Local` с `supportsCloning=true`. Валидирует
      `referenceAudioPath`. Бросает `NotInstalled` пока Android
      sherpa-onnx runtime не обновлён для ZipVoice.
- [x] **Kokoro inference timeout** — `withTimeoutOrNull(5 мин)` на каждый
      segment. Если Kokoro зависает на длинном тексте, audiobook больше
      не stuck в `running` — выдаёт clear error.
- [x] **`AudioTagInserter` перенесён ПОСЛЕ voice-сегментов** в
      `GenerationPipeline.generate()`. Раньше inserter видел
      `durationMs=0` для всех segments, теперь — реальные значения.
      `timelineStartMs` больше не 0.
- [x] TagDocs Info dialog на каждой model/generator/engine карточке.
- [x] 11 локалей strings.xml покрытие всех новых строк.

**CI run 30347936814 зелёный** (test + build, APK 43 МБ).

## 🚧 В работе (v0.2.0 → v0.3.0)

### Сейчас

- [ ] **Реальный MusicGen inference** — нужен ARM64 ONNX-экспорт от
      сообщества. Кандидаты: `wide-video/musicgen-small-v1.0.0` (int8 ~422 МБ
      decoder + 110 МБ text encoder + 60 МБ encodec_decode). MusicGen —
      autoregressive encoder-decoder, не подходит для текущего
      sherpa-onnx Android runtime. Нужен LiteRT/TFLite-экспорт от Meta
      или сообщества.
- [ ] **Реальный ZipVoice inference** — нужен апдейт Android sherpa-onnx
      runtime, который бы включал `OfflineTts` с ZipVoice-конфигом
      (`promptText` + `promptSamples`). Следить за релизами
      [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx/releases).
- [ ] **ElevenLabs Clone UI** — `VoicesScreen.kt` revert'нут в `8fc42a0`
      после серии поломок. Нужно заново: локальный Piper picker dialog
      ИЛИ починить ElevenLabs clone flow с API-ключом.
- [ ] **Реальный G2P для Kokoro** — сейчас ASCII-fallback, что даёт
      плохое произношение. eSpeak-ng уже скачивается как часть Kokoro
      bundle (см. `files/models/8ae649d98c616269e26efb10/espeak-ng-data/`)
      — нужно включить его в `KokoroTtsEngine` вместо ASCII-fallback.
- [ ] **Визуальный waveform-timeline** в AudioEditorScreen — пока только
      числовые поля. Нужен drag/trim/split + визуальный playhead.

### Дальше (v0.3.0+)

- [ ] Voice gallery sync (GitHub каталог).
- [ ] Background downloads через WorkManager.
- [ ] SRT/ASS-экспорт через UI.
- [ ] Tablet-адаптация (adaptive layout, two-pane).
- [ ] Auto-TTS (распознавание языка текста).
- [ ] Material You dynamic colors.
- [ ] UI-тесты (Compose UI Test).
- [ ] Расширенные правила нормализации.
- [ ] Шаринг аудиокниги (Android Share Intent).

## 🛣 Дальше (v0.4.0+ — после доделки приложения)

- [ ] **Подписка / монетизация** (Play Billing Library v7+,
      server-side verification, paywall screen, premium-функции).
      Подписка отложена до завершения функциональной части
      (решение владельца от 2026-07-28).
- [ ] Публикация в Google Play / F-Droid. Требует keystore, AAB build,
      Privacy Policy URL, Data Safety form, $25 developer account.
- [ ] **Backend** (server-side verification подписки, прокси к
      ElevenLabs / MusicGen для скрытия ключей, хранение клонов
      голосов).

## ❌ Не будет

- ❌ Серверные TTS-движки и отдельный engine-host.
- ❌ Непроверенные локальные Chatterbox / Qwen3 / OmniVoice.
- ❌ Faster Whisper — нет билдов ctranslate2 под Android.
- ❌ Встроенный Python / MCP / HTTP-сервер.
- ❌ Windows-only фичи (CreateDesktopShortcut, UAC, signed installer).
- ❌ Модели в APK / Asset Packs (пользователь скачивает сам).

## Целевые метрики

| Метрика | Цель |
|---|---|
| Минимальный APK (без моделей) | < 50 МБ |
| APK с Kokoro (скачивается отдельно) | < 50 МБ |
| Генерация 1 страницы текста на mid-range устройстве (Kokoro) | < 30 с |
| Время холодного старта | < 1.5 с |
| Покрытие тестами (core) | > 60 % |

## Публикация (отложено)

Публикация в Google Play / F-Droid **не входит в ближайшие планы**.
Сначала нужно доделать приложение (v0.3.0). Затем — подписка и
публикация. Распространение сейчас — через
[GitHub Actions artifacts](../../actions) и прямые ссылки на .apk.
Когда/если решим публиковать — см. `docs/DEPLOYMENT.md`.
