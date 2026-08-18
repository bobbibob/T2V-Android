# Текущий статус

**Дата обновления:** 2026-08-19
**Ветка:** `feature/offline-work` → merge в `main`
**APK:** ~44 МБ, скачивается через `gh run download`
**Тесты:** зелёные (test + build оба success)
**Устройство для проверки:** `R5CN30LJS4W` (Samsung, ADB, разблокировано)
**MusicGen smoke-test:** ✅ прошёл на устройстве (3 теста, 26 сек)

## Что лежит на полке

```
✅ Сделано (verified end-to-end на устройстве):
- Локальный Kokoro 82M через sherpa-onnx (audiobook #2 = completed, 154 сек)
- <music>/<sfx> XML-теги в тексте → AudioClipEntity в Room (audiobook #2)
- 3-track editor (VOICE / MUSIC / SOUND) с FFmpeg + LAME для MP3
- 15 языков Piper/VITS (Русский, English, German, French, Spanish,
  Italian, Chinese, Japanese, Hindi, Bengali, Arabic, Korean)
- 11 локализаций strings.xml
- Self-test кнопка в Settings (🧪 Run <music>/<sfx> self-test)
- DownloadableModelCard — единая UI-карточка скачивания
- GenerationModelCatalog — единый типизированный каталог
- TagDocs Info dialog на каждой model/generator/engine карточке
- Тесты: writeSilence, pause ms/s, clean, currencies, Num2Words — все зелёные
- Документация: 24 файла в docs/

✅ Сделано (сессия 2026-08-19):
- MusicGen ARM64 smoke-test прошёл на устройстве R5CN30LJS4W:
  text_encoder → decoder (step-0 golden tokens) → EnCodec → PCM WAV.
  3 теста, 26 секунд. markSmokeTested() записан.
- ElevenLabs clone UI: фикс JSON-парсинга voice_id и name
  (.jsonPrimitive.content вместо сломанного .toString().trim('"'))
- Background downloads через WorkManager: ModelDownloadWorker
  (CoroutineWorker) — загрузка моделей выживает уход с экрана
- Tablet-адаптация: NavigationRail для больших экранов
  (material3-window-size-class, WindowSizeClass)
- Voice gallery sync: VoiceGallerySync — каталог голосов из
  bobbibob/T2V-VoiceGallery с офлайн-кэшем
- UI-тесты: RoutesTest, TimelineViewTest (clipDurationMs)

❌ Не готово / отложено:
- sherpa-onnx TTS с клонированием голоса
- Визуальный waveform-timeline (drag/trim/split)
- Material You dynamic colors
- Compose UI Test (androidTest)

🚫 Не будет (по решению владельца):
- Подписка / монетизация (отложена до полной доделки приложения)
- Google Play Billing, Firebase Auth, AAB-релиз
- Модели в APK / Asset Packs (пользователь скачивает сам)
- Серверные TTS-движки / engine-host
- Faster Whisper на устройстве
- Встроенный Python / MCP / HTTP-сервер
- iOS-версия
```

## Что делаем прямо сейчас

**Цель:** доделать приложение до v0.3.0 (полная функциональность) **до** того,
как принимать решения по подписке/публикации.

**Сессия 2026-08-19:**

| # | Задача | Статус | Комментарий |
|---|---|---|---|
| 1 | MusicGen ARM64 smoke-test | ✅ Сделано | 3 теста прошли на R5CN30LJS4W, 26 сек |
| 2 | ElevenLabs clone UI fix | ✅ Сделано | JSON-парсинг voice_id/name исправлен |
| 3 | Background downloads (WorkManager) | ✅ Сделано | ModelDownloadWorker, прогресс через WorkInfo |
| 4 | Tablet-адаптация | ✅ Сделано | NavigationRail для Medium/Expanded |
| 5 | Voice gallery sync | ✅ Сделано | VoiceGallerySync + 8 JVM-тестов |
| 6 | UI-тесты | ✅ Сделано | RoutesTest, TimelineViewTest, ElevenLabsTtsEngineTest |

## Следующие шаги

### Фаза — доделка приложения (v0.3.0)
- Visual waveform timeline (drag/trim/split)
- sherpa-onnx клонирование голоса
- Material You dynamic colors
- Compose UI Test (androidTest)
- SRT/ASS-экспорт через UI
- Шаринг аудиокниги (Android Share Intent)

### Потом — подписка и публикация
- Google Play Billing Library v7+ + server-side verification
- Firebase Auth / Google Sign-In
- AAB build + signing + R8
- Privacy Policy URL (GitHub Pages)
- Data Safety form в Play Console
- Paywall screen

## Полезные однострочники

```bash
# Снимок БД
adb -s R5CN30LJS4W exec-out run-as com.t2v.debug sh -c \
  "cat databases/t2v.db databases/t2v.db-wal databases/t2v.db-shm" > /tmp/t2v.db
sqlite3 /tmp/t2v.db "PRAGMA wal_checkpoint(FULL);"

# Скачать APK последнего зелёного CI
gh run list --workflow android.yml --limit 1 \
  --json databaseId,conclusion | jq -r '.[] | select(.conclusion=="success") | .databaseId'
gh run download <run-id> -n app-debug

# Проверить MusicGen файлы
adb -s R5CN30LJS4W shell 'run-as com.t2v.debug ls -la files/models/litert/musicgen-small/onnx/'

# Установить APK
adb -s R5CN30LJS4W install -r /tmp/t2v-apk/app-debug.apk

# Запустить MusicGen smoke-test
adb -s R5CN30LJS4W shell am instrument -w \
  -e class com.t2v.generators.MusicGenOnnxSmokeTest \
  com.t2v.debug.test/androidx.test.runner.AndroidJUnitRunner
```

## Чего точно не будет (на ближайшее время)

- ❌ Подписка / монетизация
- ❌ Локальные Chatterbox / Qwen3 / OmniVoice (только через remote host)
- ❌ Встроенный Python / MCP-сервер
- ❌ Faster Whisper на устройстве
- ❌ Публикация в Google Play (сейчас — APK через GitHub)
- ❌ Модели в APK (пользователь скачивает сам, на выбор)