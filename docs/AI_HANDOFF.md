# T2V: инструкция и журнал передачи для ИИ

Последнее обновление: 2026-07-28
Рабочая ветка разработки: `codex/models-download` (последний зелёный CI 30338815715, head `984e208a`)
Стабильная база: `codex/audio-production` (последний зелёный CI 30328757706, head `8fc42a0`)
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
  workflow `android.yml`. Запускать `gh workflow run` после пуша можно
  только с явного согласия владельца. На машине разработчика нет Android
  SDK; `kotlinc` локально даёт ложноположительные результаты.
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

- Локальный путь: `/Users/robertbiktimirov/Downloads/books/t2v`
- GitHub: `bobbibob/T2V-Android`
- Текущая ветка: `codex/models-download`
- Стабильная база: `codex/audio-production`
- Базовый проверенный коммит: `e052f1c feat: add Russian voices and voice cloning`.

## Что реально работает на устройстве (по состоянию на 2026-07-28)

### Verified end-to-end (через self-test, база данных, логи):

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

### Реализовано в коде, но с известными ограничениями:

- **Kokoro inference зависает** на длинных текстах (>3 000 символов):
  **ПОЧИНЕНО** — `KokoroTtsEngine.splitLongText()` режет текст на
  предложения ≤1200 символов, каждый кусок синтезируется отдельным вызовом
  `OfflineTts.generate()`, PCM склеивается. Нативные вызовы больше не
  превышают порог.
- **`<music>/<sfx>` клипы имеют `timelineStartMs = 0`** (баг):
  **ПОЧИНЕНО** — `AudioTagInserter.insert()` перенесён в `GenerationPipeline`
  из начала в конец цикла voice-сегментов, когда `durationMs` уже известны.
  Позиция клипа теперь считается по реальной длительности речи.
- **ElevenLabs clone UI не реагирует** на нажатие «Создать клон».
  `VoicesScreen.kt` ломался 4 раза, был revert'нут в `8fc42a0`.
  **Не починено** — отложено.
- **MusicGen** — `MusicGenOnnxGenerator` зарегистрирован (манифест
  text_encoder/lm/audio_decoder, каталог `musicgen-small`, TagDocs,
  `GeneratorRegistry`), но **ARM64 smoke-test не пройден**, поэтому
  `isAvailable()=false`. Нужен реальный LiteRT-совместимый .tflite под
  Android и скачивание через `HuggingFaceRepository` + `markSmokeTested()`.

### Локальные модели (registered, но пока без скачивания через UI):

- **Piper/VITS** на 15 языках (Русский, English, German, French, Spanish,
  Italian, Chinese, Japanese, Hindi, Bengali, Arabic, Korean).
  Скачиваются через `RussianVoiceInstaller.install()`.
- **Piper/VITS с Hugging Face** (uk-UA, ca-ES, cs-CZ, da-DK, el-GR, fa-IR,
  fi-FI, hu-HU, nl-NL, pt-BR, ro-RO, tr-TR) — реальные записи каталога
  `vits-piper-*`, качаются по кнопке через `HuggingFaceRepository.install()`
  (`downloadAllFiles=true`), после установки автоматически становятся
  `SherpaOnnxLocalEngine`. Нужен **устройственный тест**: скачать
  один голос, выбрать его, сгенерировать WAV.
  → **Устройственный тест (S20, arm64)**: ✅ загрузка UK-UA (38.6 MB),
  файлы + манифест в `files/models/<sha256(repo)>`, движок распознан
  («Голоса» → `vits-piper-uk-ua` → Lada · Selected), настройки
  `tts_engine/voice_id/language` записаны. ✅ **WAV-синтез**: текст →
  GenerationScreen → `files/audiobooks/1/seg_00000.wav` + `audiobook.wav`
  (16 kHz, mono, 16-bit). Полный цикл работает.
- **NSynth wavenet** (Magenta, SFX) — `RuntimeInDevelopment`, refuses to run
  пока `LiteRtModelInstaller.markSmokeTested()` не вызван после ARM64 smoke-test.
- **Yandex Speech engine** (`YandexTtsEngine`) — новый облачный движок:
  `tts.api.cloud.yandex.net/speech/v1/tts:synthesize`, авторизация `Api-Key` +
  `x-folder-id`. Настройки в Settings (API Key / Folder ID), зарегистрирован в
  `EngineRegistry`, покрыт `RegistrySmokeTest`. **Проверить на устройстве**:
  сгенерировать WAV (формат `lpcm`, обёрнут в WAV локально).
- **Stable Audio Open Small** (music) и **Stable Audio Clip** (sound) —
  не модели, а **процедурный DSP** (`ProceduralAudioSynth`), всегда доступны
  (Verified в каталоге, скачивать нечего). SFX: door, whoosh, notif, rain,
  wind, explosion, click, footstep, heartbeat, laser, alarm, water, thunder,
  applause, whisper, glass. Music: ambient, calm, dark, uplifting, sad,
  tension, dreamy, mysterious, peaceful, suspense, romantic, energetic,
  jazzy, warm (и алиасы).
- **PocketTTS**, **ZipVoice** (zero-shot voice cloning) — `Experimental` /
  `RuntimeInDevelopment`; нужна локальная модель под Android.

### Архитектура скачивания (одна точка входа):

- `HuggingFaceRepository.install(model, variant, onProgress)` качает файлы
  с `https://huggingface.co/<author>/<name>/resolve/<revision>/<path>`.
- `VERIFIED_ANDROID_MODELS` теперь формируется из `GenerationModelCatalog.entries`
  (все записи с `repository` в формате `author/name`) + `KOKORO_REPOSITORY`
  как fallback.
- UI: `DownloadableModelCard` в `ModelsScreen` показывает Download / progress
  / cancel / select для **любой** каталожной записи, но реально скачивается
  только Kokoro (для остальных `downloadModelFromCatalog()` пока пишет
  «Загрузка для X пока не подключена»).

## Сводка коммитов текущей сессии (codex/models-download)

1. **`647dcf8`** — feat(models): refactor download UI; DownloadableModelCard + catalog plumbing
2. **`ccd817e`** — fix(models): make InfoTarget internal so DownloadableModelCard compiles
3. **`0f86301`** — fix(models): make InfoTarget/ModelTab public (no modifier)
4. **`984e208`** — fix(models): onInfo is () -> Unit, not (InfoTarget) -> Unit

Все четыре CI run (30337677737, 30338039614, 30338445304, 30338815715) —
последний зелёный. APK 43.5 МБ скачан через GitHub API + curl.

## План работы (что делаем сейчас)

> Приоритет: **сначала доделать приложение полностью** (включая
> локальные модели), потом решать с подпиской. Никаких Paywall,
> Google Play Billing, Firebase Auth, AAB-релизов, Privacy Policy
> сейчас не пишем.

| # | Задача | Статус | Комментарий |
|---|---|---|---|
| 3 | Вернуть скачивание моделей в UI | ✅ Сделано | `DownloadableModelCard`, `downloadModelFromCatalog()` |
| 1 | MusicGen через ONNX | 🚧 Каркас готов | Зарегистрирован + UI-карточка `musicgen-small`. **Кнопка «Скачать» скрыта** — публичного tflite-экспорта нет; репозиторий `wide-video/...` только ONNX и отклоняется `HuggingFaceRepository.install` (нет `.tflite`). Остался реальный tflite-экспорт + ARM64 smoke-test + инференс |
| — | Динамические локальные TTS-модели | ✅ Сделано | `SherpaOnnxLocalEngine` + `EngineRegistry` каталожно обнаруживает установленные sherpa-onnx модели (без нового адаптера-кода) |
| — | Реальные Piper/VITS голоса с Hugging Face | ✅ Сделано | 12 записей `vits-piper-*` (uk-UA, ca-ES, cs-CZ, da-DK, el-GR, fa-IR, fi-FI, hu-HU, nl-NL, pt-BR, ro-RO, tr-TR) в каталоге + карточки с кнопкой «Скачать» в Models → Голос; `downloadAllFiles=true` для загрузки `espeak-ng-data` без расширений; после установки — движок `SherpaOnnxLocalEngine` через `localVoiceModelEntries()` |
| — | Фикс Kokoro зависания | ✅ Сделано | `splitLongText()` режет текст ≤1200 символов на предложения; синтез кусками |
| — | Фикс `AudioTagInserter.positionFor` (timelineStartMs=0) | ✅ Сделано | `insert()` перенесён после цикла voice-сегментов |
| — | Авто-открытие AudioEditor после генерации с тегами | ✅ Сделано | UI в `GenerationScreen` (LaunchedEffect на `audioTagClips`) |
| 2 | sherpa-onnx TTS с клонированием | 📋 Очередь | sherpa-onnx Android runtime уже подключён, нужна модель (PocketTTS / ZipVoice / XTTS-v2) + UI для reference audio |

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
  --json databaseId,conclusion --jq '.[] | select(.conclusion=="success") | .databaseId'
# затем: gh run download <id> -n app-debug

# Проверить Kokoro файлы на устройстве
adb -s R5CN30LJS4W shell 'run-as com.t2v.debug ls files/models/'

# Запустить self-test из UI
adb -s R5CN30LJS4W shell am start -n com.t2v.debug/com.t2v.ui.MainActivity
# (затем вручную: Settings → 🧪 Run <music>/<sfx> self-test)
```

## Известные баги и долги (issue list)

1. **Kokoro зависает** на >3к символов (audiobook #3 не завершился)
2. **`timelineStartMs=0`** для всех `<music>/<sfx>` клипов
3. **ElevenLabs clone UI** не реагирует на «Создать клон»
4. **`VoicesScreen.kt`** нестабилен — ломался 4 раза, revert'нут
5. **APK download через `gh run download`** обрывается на 30 МБ — workaround:
   curl + `https://api.github.com/repos/.../actions/artifacts/<id>/zip` с `-C -`
6. **Sherpa-onnx cloning model** не выбрана — нужен аудит готовых моделей

## Как обновлять этот файл

После каждого этапа:

1. Обновить дату и текущий коммит.
2. Перенести пункт из «не готово» в «реализовано» только после появления кода.
3. Дописать отдельную строку о фактической проверке: CI run/device/model.
4. Зафиксировать новые ограничения пользователя дословно по смыслу.
5. Указать известный дефект и ближайшее конкретное действие.
6. Не удалять важную историю решений, пока ветка не слита и не выпущена.
