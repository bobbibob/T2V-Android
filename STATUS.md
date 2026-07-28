# Текущий статус

**Дата обновления:** 2026-07-28
**Ветка:** `codex/models-download` в `bobbibob/T2V-Android`
**Последний зелёный CI:** `30347936814` (head `69dc959` — "fix(worker): drop duplicated primary constructor")
**APK:** ~43 МБ, собран через `assembleDebug`
**Тесты:** зелёные (test + build оба success)
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
- MusicGen Music Generator (scaffold + UI; fallback на ProceduralAudioSynth)
- ZipVoice TTS Engine (scaffold для voice cloning)
- Kokoro inference timeout (5 мин) — больше не stuck в running
- AudioTagInserter перенесён после voice-сегментов (timelineStartMs теперь
  правильный, не 0)
- Тесты: writeSilence, pause ms/s, clean, currencies, Num2Words — все зелёные
- Документация: 25 файлов в docs/

❌ Не готово / отложено (без подписки):
- Реальный MusicGen inference (нужен ARM64 ONNX-экспорт от сообщества)
- Реальный ZipVoice inference (нужен апдейт sherpa-onnx Android runtime)
- ElevenLabs Clone UI (был revertнут, не трогали)
- Реальный G2P для Kokoro (ASCII-fallback)
- Визуальный waveform-timeline (drag/trim/split)
- Voice gallery sync через GitHub

🚫 Не будет (по решению владельца):
- Подписка / монетизация (отложена до полной доделки приложения)
- Google Play Billing, Firebase Auth, AAB-релиз
- Модели в APK / Asset Packs (пользователь скачивает сам, на выбор)
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
| 1 | MusicGen через ONNX | 🟡 Scaffold | Кандидаты: `wide-video/musicgen-small-v1.0.0` (int8 ~422 МБ) |
| 2 | sherpa-onnx клонирование | 🟡 Scaffold | ZipVoice engine зарегистрирован, ждём runtime |
| — | Авто-открытие AudioEditor | ✅ Сделано | `GenerationPipeline.Progress.audioTagClips` уже считается |
| — | Фикс Kokoro зависания | ✅ Сделано | `withTimeoutOrNull(5 мин)` на каждый segment |
| — | Фикс `AudioTagInserter.positionFor` | ✅ Сделано | Inserter после voice-сегментов, не до |

| # | Следующие (после документации) | Статус | Комментарий |
|---|---|---|---|
| — | Реальный MusicGen inference | 📋 Следующий | ARM64 ONNX-экспорт от сообщества |
| — | Реальный ZipVoice inference | 📋 Очередь | sherpa-onnx Android runtime update |
| — | ElevenLabs Clone UI fix | 📋 Очередь | VoicesScreen.kt revert'нут, чинить заново |
| — | G2P для Kokoro (eSpeak-ng) | 📋 Очередь | espeak-ng-data уже скачивается, нужен hook |

## Следующие шаги (после MusicGen/ZipVoice inference)

### Фаза — доделка приложения
- Visual waveform timeline
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
