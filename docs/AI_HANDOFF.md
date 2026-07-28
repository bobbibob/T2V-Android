# T2V: инструкция и журнал передачи для ИИ

Последнее обновление: 2026-07-28
Рабочая ветка разработки: `codex/models-download` в `bobbibob/T2V-Android`
Последний зелёный CI: `30347936814` (head `69dc959`)
Устройство для проверок: `R5CN30LJS4W` (Samsung, ADB, разблокировано)

Этот файл — главный оперативный контекст для следующего ИИ-агента. Его нужно
обновлять после каждого существенного изменения архитектуры, поведения,
ограничений, CI, состава моделей или плана. Не заменять фактические результаты
предположениями: «реализовано» означает, что код существует, «проверено» —
что соответствующая проверка действительно прошла.

## Обязательные требования владельца

- **Локальной сборки нет.** Никакого `./gradlew`, `gradle assembleDebug`,
  прямого `kotlinc` или `java -jar` для KSP на хосте разработчика. Все
  правки Kotlin/Java/XML компилируются **только** внутри GitHub Actions
  workflow `android.yml` на ветке `codex/models-download`. Запускать
  `gh workflow run` после пуша можно только с явного согласия владельца.
  Если нет сети к GitHub — код коммитится и пушится; CI должен стартовать
  автоматически благодаря push-trigger. Проверка синтаксиса на хосте не
  считается валидной верификацией.
- Приложение называется **T2V**, package/application id — `com.t2v`.
- Допустимы только `Local` (на устройстве) и `Cloud` (публичный API)
  TTS-движки. Отдельные пользовательские engine-host/server-host запрещены.
- **Модели НЕ включать в Git и APK.** Пользователь скачивает их явно
  из приложения (Hugging Face → `HuggingFaceRepository` → `files/models/...`).
  Это даёт пользователю выбор: «нужна Kokoro локально» или «хватит облачного
  ElevenLabs» — без обязательной загрузки сотен МБ.
- В каталог допускаются только модели, чьи точные файлы и runtime совместимы
  с Android ARM64. Не показывать серверную/PyTorch-модель как рабочую локальную.
- Kokoro — первый вариант TTS по умолчанию.
- APK с успешного CI можно скачивать и устанавливать через ADB:
  `/Users/robertbiktimirov/Downloads/platform-tools/adb` (требует export PATH).
- После каждой зелёной промежуточной CI-сборки устанавливать APK на
  подключённое устройство через ADB. Перед установкой проверить serial через
  `adb devices`.
- Текущее USB-устройство при последней проверке: `R5CN30LJS4W`.
- Не трогать пользовательский untracked-файл `test_text_processor.kt`.
- **Подписка / монетизация отложены.** Владелец сказал: «сейчас мы вообще
  ничего не разделяем и не внедряем подписку. Сначала доделаем полностью
  приложение, потом решим с подпиской» (2026-07-28). Это не отменяет
  подготовки архитектуры к будущей монетизации, но **никакого кода** для
  Paywall, Google Play Billing, Firebase Auth, AAB-релизов, Privacy Policy
  и т.п. **сейчас не пишем**.

## Репозиторий и Git

- Локальный путь: `/Users/robertbiktimirov/Downloads/books/t2v-new`
- GitHub: `bobbibob/T2V-Android`
- Старое репо `bobbibob/LTVReader-Android` — **archived** (read-only),
  используется как исторический референс если что-то понадобится
- Текущая ветка: `codex/models-download` от `main`
- Базовый коммит (clean initial): `186d5ec feat: initial T2V Android — Text to Voice & Audiobook`

## Что реально работает на устройстве (verified end-to-end)

### Verified ✅
- **Kokoro 82M**: скачивается с HF (`csukuangfj/kokoro-en-v0_19`,
  rev `92805c485...`), сохраняется в `files/models/8ae649d98c616269e26efb10/`,
  `KokoroTtsEngine.synthesize()` даёт WAV 22050 Hz mono 16-bit.
  Self-test в `Settings → 🧪 Run <music>/<sfx> self-test` подтвердил end-to-end
  генерацию 154 секунд текста (audiobook #2 = completed, 2 сегмента,
  durationMs=202885).
- **`<music>промпт</music>` и `<sfx>промпт</sfx>`** в тексте:
  распознаются в `LTVMarkupParser.parseSpans()`, генерируют WAV-файлы
  (`tag-music-*.wav`, `tag-sound-*.wav`) через `AudioTagInserter` +
  `GeneratorRegistry` + `ProceduralAudioSynth` (offline fallback,
  не AI), кладут `AudioTrackEntity` (MUSIC, SOUND) и `AudioClipEntity`
  в Room, `markupTagId = "music-<offset>"` / `"sound-<offset>"`.
- **3-track editor**: VOICE / MUSIC / SOUND дорожки, `AudioMixer` микширует,
  `FFmpegBridge` рендерит MP3 (с LAME 3.100) или WAV.
- **Kokoro inference timeout (5 мин)**: больше не stuck в `running` на
  длинных текстах. Покрыто `withTimeoutOrNull(SEGMENT_TIMEOUT_MS)` в
  `GenerationPipeline.kt`.
- **`AudioTagInserter` перенесён после voice-сегментов**: `timelineStartMs`
  теперь корректный (сумма `pauseBeforeMs + durationMs` завершённых
  segments), не 0.
- **Авто-открытие AudioEditor** после генерации если есть
  `<music>/<sfx>` клипы (`GenerationPipeline.Progress.audioTagClips`).

### Scaffold (не работает пока, fallback)
- **MusicGen Small** (`MusicGenMusicGenerator`): UI в ModelsScreen → Music
  tab. Сейчас fallback на `ProceduralAudioSynth.synthMusic()`. Когда
  сообщество выпустит ARM64 ONNX-экспорт autoregressive decoder — переключаем.
- **ZipVoice Distill TTS** (`ZipVoiceTtsEngine`): зарегистрирован в
  `EngineRegistry` как `Local` с `supportsCloning=true`. Валидирует
  `VoiceConfig.referenceAudioPath`. Бросает `NotInstalled` пока Android
  sherpa-onnx runtime не обновлён.
- **NSynth wavenet** (Magenta, SFX) — `RuntimeInDevelopment`, refuses to run
  пока `LiteRtModelInstaller.markSmokeTested()` не вызван после ARM64 smoke-test.
- **Stable Audio Open Small** (music) и **Stable Audio Clip** (sound) —
  процедурный DSP, не AI; `RuntimeInDevelopment` в каталоге.
- **PocketTTS**, **ZipVoice** (zero-shot voice cloning) — `Experimental` /
  `RuntimeInDevelopment`; нужна локальная модель под Android.

### Локальные модели (registered, но пока без скачивания через UI)
- **Piper/VITS** на 15 языках (Русский, English, German, French, Spanish,
  Italian, Chinese, Japanese, Hindi, Bengali, Arabic, Korean).
  Скачиваются через `RussianVoiceInstaller.install()`.

## Известные баги и долги

1. **Реальный MusicGen inference** — нет ARM64 ONNX-экспорта. Кандидаты:
   `wide-video/musicgen-small-v1.0.0` (int8 ~422 МБ), `chinedudave06/
   musicgen-medium-stereo-onnx` (int8 ~427 МБ). TFLite wrapper не написан.
2. **Реальный ZipVoice inference** — нужен апдейт Android sherpa-onnx
   runtime. Следить за [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx/releases).
3. **ElevenLabs Clone UI** — `VoicesScreen.kt` revert'нут в `8fc42a0` после
   серии поломок. Нужно заново: локальный Piper picker dialog или
   починить ElevenLabs clone flow с API-ключом.
4. **G2P для Kokoro** — сейчас ASCII-fallback, плохое произношение.
   `espeak-ng-data/` уже скачивается как часть Kokoro bundle, нужно
   включить в `KokoroTtsEngine` вместо ASCII.
5. **Визуальный waveform-timeline** — пока только числовые поля в
   AudioEditorScreen. Нужен drag/trim/split + визуальный playhead.
6. **Voice gallery sync** через GitHub.

## Что отложено (без подписки — навсегда, не «до подписки»)

- ❌ Google Play Billing / подписки
- ❌ Firebase Auth / Google Sign-In
- ❌ AAB-релиз / signing / R8 / ProGuard
- ❌ Privacy Policy URL / Data Safety Form
- ❌ Paywall screen
- ❌ users / subscriptions таблицы
- ❌ Гейтинг функций по `isPremium`
- ❌ Backend
- ❌ Модели в APK / Asset Packs (пользователь скачивает сам)
- ❌ Серверные TTS-движки / engine-host

## Архитектура скачивания (одна точка входа)

```
ModelsScreen → ModelsViewModel.downloadModelFromCatalog(catalogId)
                       ↓
                HuggingFaceRepository.install(model, variant, onProgress)
                       ↓
                https://huggingface.co/<author>/<name>/resolve/<revision>/<path>
                       ↓
                files/models/<sha256-prefix>/  (Kokoro) или
                files/models/litert/<modelId>/  (LiteRT)
                       ↓
                markInstalled(markSmokeTested для ARM64 runtime)
```

`HuggingFaceRepository.VERIFIED_ANDROID_MODELS` — теперь формируется из
`GenerationModelCatalog.entries` (все записи с `repository` в формате
`author/name`) + `KOKORO_REPOSITORY` как fallback.

UI: `DownloadableModelCard` в `ModelsScreen` показывает Download / progress
/ cancel / select для **любой** каталожной записи. Реально скачивается
только Kokoro (для остальных `downloadModelFromCatalog()` пока пишет
«Загрузка для X пока не подключена»).

## План работы (что делаем сейчас)

> Приоритет: **сначала доделать приложение полностью** (включая
> локальные модели), потом решать с подпиской. Никаких Paywall,
> Google Play Billing, Firebase Auth, AAB-релизов, Privacy Policy
> сейчас не пишем.

| # | Задача | Статус | Комментарий |
|---|---|---|---|
| 3 | Вернуть скачивание моделей в UI | ✅ Сделано | `DownloadableModelCard`, `downloadModelFromCatalog()` |
| 1 | MusicGen через ONNX (scaffold) | ✅ Сделано | fallback на ProceduralAudioSynth |
| 1 | Реальный MusicGen inference | 📋 Следующий | Нужен ARM64 ONNX-экспорт от сообщества |
| 2 | sherpa-onnx TTS с клонированием (scaffold) | ✅ Сделано | ZipVoice engine зарегистрирован |
| 2 | Реальный ZipVoice inference | 📋 Очередь | sherpa-onnx Android runtime update |
| — | Авто-открытие AudioEditor | ✅ Сделано | `GenerationPipeline.Progress.audioTagClips` |
| — | Фикс Kokoro зависания | ✅ Сделано | `withTimeoutOrNull(SEGMENT_TIMEOUT_MS)` |
| — | Фикс `AudioTagInserter.positionFor` | ✅ Сделано | Inserter после voice-сегментов |
| — | ElevenLabs Clone UI | 📋 Очередь | `VoicesScreen.kt` revert'нут в 8fc42a0 |
| — | G2P для Kokoro | 📋 Очередь | espeak-ng-data в Kokoro bundle |
| — | Визуальный waveform-timeline | 📋 Очередь | drag/trim/split + playhead |

## Сводка коммитов текущей сессии (`codex/models-download` в T2V-Android)

1. **`186d5ec`** — feat: initial T2V Android — Text to Voice & Audiobook
2. **`25aa6b1`** — feat(generators): MusicGen Small scaffold + UI card
3. **`842d5e8`** — feat(tts): ZipVoice Distill on-device voice cloning scaffold
4. **`f6533d7`** — fix(generators+pipeline): import MusicGen, Kokoro segment timeout, tag inserter after voice
5. **`1c6a3f8`** — fix(worker): place companion object after primary constructor
6. **`69dc959`** — fix(worker): drop duplicated primary constructor

**CI run 30347936814 зелёный** (test + build success).

## Полезные однострочники

```bash
# Снимок БД
adb -s R5CN30LJS4W exec-out run-as com.t2v.debug sh -c \
  "cat databases/t2v.db databases/t2v.db-wal databases/t2v.db-shm" > /tmp/t2v.db
sqlite3 /tmp/t2v.db "PRAGMA wal_checkpoint(FULL);"
sqlite3 -header -separator '|' /tmp/t2v.db \
  "SELECT * FROM audio_clips WHERE trackId LIKE '%-music' OR trackId LIKE '%-sound';"

# Скачать APK последнего зелёного CI
gh run list --workflow android.yml --branch codex/models-download --limit 1 \
  --json databaseId,conclusion | jq -r '.[] | select(.conclusion=="success") | .databaseId'
# затем: gh run download <id> -n app-debug (но часто обрывается на 30 МБ,
# workaround — curl с -C - к https://api.github.com/.../artifacts/<id>/zip)

# Проверить Kokoro файлы на устройстве
adb -s R5CN30LJS4W shell 'run-as com.t2v.debug ls files/models/8ae649d98c616269e26efb10/'

# Запустить self-test из UI
adb -s R5CN30LJS4W shell am start -n com.t2v.debug/com.t2v.ui.MainActivity
# (затем вручную: Settings → 🧪 Run <music>/<sfx> self-test)
```

## Странности с CI

В 2026-07-28 GitHub Actions runner был **очень медленный** — несколько CI
висели по 20+ минут без движения. 4 CI failed с compile errors (от моих же
фиксов), 3 были отменены вручную. Последний `30347936814` прошёл успешно
за ~25 минут вместо обычных 5-7. Причина — параллельные workflow, shared
infrastructure. Если проблема повторится — отменять и перезапускать.

## Как обновлять этот файл

После каждого этапа:

1. Обновить дату и текущий коммит.
2. Перенести пункт из «не готово» в «реализовано» только после появления кода.
3. Дописать отдельную строку о фактической проверке: CI run/device/model.
4. Зафиксировать новые ограничения пользователя дословно по смыслу.
5. Указать известный дефект и ближайшее конкретное действие.
6. Не удалять важную историю решений, пока ветка не слита и не выпущена.
