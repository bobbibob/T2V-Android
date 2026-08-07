# Текущий статус

**Дата обновления:** 2026-07-28
**Ветка:** `codex/models-download` (последний зелёный CI 30338815715, head `984e208a`)
**Стабильная база:** `codex/audio-production` (последний зелёный CI 30328757706, head `8fc42a0`)
**APK:** ~44 МБ, скачивается через `gh run download` + curl workaround
**Тесты:** зелёные (`30338815715` — `test` + `build` оба success)
**Устройство для проверки:** `R5CN30LJS4W` (Samsung, ADB, разблокировано)

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

✅ Сделанные фиксы (последняя сессия, 2026-08-07):
- Фикс Kokoro зависания (>3к символов) — `splitLongText()` режет ≤1200 символов
- Фикс AudioTagInserter.positionFor (timelineStartMs=0) — insert после цикла
- Авто-открытие AudioEditor при наличии тегов (уже было в GenerationScreen)
- MusicGenOnnxGenerator зарегистрирован (каркас: манифест, каталог, registry)
- Скачивание моделей из каталога стало универсальным: `downloadModelFromCatalog()`
  качает любую запись с реальным HF-репозиторием; Kokoro остался особым случаем.
  Добавлена поддержка `.tflite`-файлов.
- UI-карточка `musicgen-small` (DownloadableModelCard) в Models → Music.
- **Кнопка «Скачать» у `musicgen-small` скрыта** (`Support.RuntimeInDevelopment`):
  публичного tflite-экспорта нет, репозиторий `wide-video/...` только ONNX —
  `HuggingFaceRepository.install` качает только `.tflite` файлы и отклоняет репо
  без них (вместо загрузки всего репозитория на ~15 ГБ).
- Динамические локальные TTS: `SherpaOnnxLocalEngine` + каталожное обнаружение
  установленных sherpa-onnx моделей в `EngineRegistry` — новая модель = запись в
  каталоге + репозиторий, без нового кода-адаптера.

❌ Не готово / отложено:
- Реальная AI-генерация музыки (MusicGen): каркас + скачивание есть, нужен ARM64 smoke-test + реальный инференс
- sherpa-onnx TTS с клонированием голоса
- Починка ElevenLabs clone UI
- Визуальный waveform-timeline (drag/trim/split)
- Voice gallery sync через GitHub

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

**Текущая сессия (2026-07-28):**

| # | Задача | Статус | Комментарий |
|---|---|---|---|
| 3 | Вернуть скачивание моделей | ✅ Сделано | `DownloadableModelCard` в `codex/models-download` |
| — | Фикс Kokoro зависания | ✅ Сделано | `splitLongText()` режет текст на предложения ≤1200 символов |
| — | Фикс `AudioTagInserter.positionFitFor` | ✅ Сделано | `insert()` после цикла voice-сегментов |
| — | Авто-открытие AudioEditor | ✅ Сделано | UI в `GenerationScreen` |
| 1 | MusicGen через ONNX | 🚧 Каркас (без скачивания) | Каталог, манифест, UI-карточка; кнопка скрыта до реального tflite-экспорта. Остался tflite-экспорт + ARM64 smoke-test |
| — | Динамические локальные TTS-модели | ✅ Сделано | `SherpaOnnxLocalEngine` + каталожное обнаружение в `EngineRegistry` |
| 2 | sherpa-onnx клонирование | 📋 Очередь | sherpa-onnx runtime подключён, нужна модель + UI |

## Следующие шаги (после MusicGen)

### Фаза — доделка приложения
- Visual waveform timeline
- ElevenLabs clone fix
- Voice gallery sync
- Tablet-адаптация
- Material You dynamic colors

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

# Скачать APK последнего зелёного CI (workaround для прерванного download)
gh run list --workflow android.yml --branch codex/models-download --limit 1 \
  --json databaseId,conclusion | jq -r '.[] | select(.conclusion=="success") | .databaseId'
# (затем: gh api ... artifacts, curl -L -C - -o ...)

# Проверить Kokoro файлы
adb -s R5CN30LJS4W shell 'run-as com.t2v.debug ls files/models/'

# Установить APK
adb -s R5CN30LJS4W install -r /path/to/app-debug.apk
```

## Чего точно не будет (на ближайшее время)

- ❌ Подписка / монетизация
- ❌ Локальные Chatterbox / Qwen3 / OmniVoice (только через remote host)
- ❌ Встроенный Python / MCP-сервер
- ❌ Faster Whisper на устройстве
- ❌ Публикация в Google Play (сейчас — APK через GitHub)
- ❌ Модели в APK (пользователь скачивает сам, на выбор)
