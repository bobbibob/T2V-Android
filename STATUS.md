# Текущий статус

**Дата обновления:** 2026-07-28
**Ветка:** `codex/models-download` в `bobbibob/T2V-Android`
**APK:** ~45 МБ, собран через `assembleDebug` на GitHub Actions
**Тесты:** зелёные (CI verified, run `30347936814`)
**Устройство для проверки:** `R5CN30LJS4W` (Samsung, ADB, разблокировано)

## Что лежит на полке (2026-07-28)

```
✅ Сделано end-to-end на устройстве:
- Локальный Kokoro 82M через sherpa-onnx (audiobook #2 = completed, 154 сек)
- <music>/<sfx> XML-теги в тексте → AudioClipEntity в Room
- 3-track editor (VOICE / MUSIC / SOUND) с FFmpeg + LAME для MP3
- 15 языков Piper/VITS, 11 локализаций strings.xml
- DownloadableModelCard — единая UI-карточка скачивания
- GenerationModelCatalog — 23 записи (8 voice + 10 music + 5 sound)
- TagDocs Info dialog на каждой model/generator/engine карточке
- MusicGen, ZipVoice, Magenta RealTime, TinyMusician — все в каталоге
  (scaffold + UI; реальный inference ждёт runtime/ONNX export)

❌ Не готово / отложено (без подписки):
- Реальный TinyMusician inference (нужен ONNX int8 export от сообщества)
- Реальный ZipVoice inference (sherpa-onnx Android runtime)
- Реальный MusicGen inference (авторегрессионный, нежизнеспособен на Android)
- ElevenLabs Clone UI (был revertнут, не трогали)
- Реальный G2P для Kokoro (ASCII-fallback → eSpeak-ng)
- Визуальный waveform-timeline (drag/trim/split)
- Voice gallery sync через GitHub
- Реальные HTTP-вызовы для ElevenLabs Music, OpenAI Music, Suno, Lyria 2,
  Stable Audio cloud (сейчас — cleartext "endpoint not yet wired" / полные
  HTTP-клиенты написаны, активируются когда пользователь введёт ключ)

🚫 Не будет (по решению владельца):
- Подписка / монетизация (отложена до полной доделки)
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
| 4 | Расширить каталог | ✅ Сделано | 23 записи (8 voice + 10 music + 5 sound) |
| 5 | Magenta RealTime → TinyMusician | ✅ Сделано | 1.5 ГБ → 210 МБ, MIT, реалистичнее |
| 6 | Новые API-ключи в Settings | ✅ Сделано | Google, Suno, Stability |
| — | ModelsScreen UI-карточки для всех моделей | ✅ Сделано | Music: 8 local + 5 cloud, Sound: 4 local + 2 cloud |
| — | Brace check + документация | ✅ Сделано | MODELS.md переписан, CHANGELOG обновлён |

| # | Следующие | Статус | Комментарий |
|---|---|---|---|
| — | CI push | 📋 Сейчас | Commit + push + gh workflow run |
| — | Реальный TinyMusician inference | 📋 Очередь | ONNX int8 export от сообщества |
| — | Реальный ZipVoice inference | 📋 Очередь | sherpa-onnx Android runtime update |
| — | ElevenLabs Clone UI fix | 📋 Очередь | VoicesScreen.kt revert'нут, чинить заново |
| — | G2P для Kokoro (eSpeak-ng) | 📋 Очередь | espeak-ng-data уже скачивается |

## Архитектура каталога (2026-07-28)

```
GenerationModelCatalog
├── Voice (8):
│   ├── Local:  Kokoro, Piper, PocketTTS, ZipVoice
│   └── Cloud:  OpenAI, ElevenLabs, Gemini, Azure
├── Music (10):
│   ├── Local:  procedural DSP, TinyMusician Small, TinyMusician 100M,
│   │           GeneralUser SoundFont, MusicGen
│   └── Cloud:  OpenAI Music, ElevenLabs Music, Suno, Lyria 2,
│               Stable Audio 2.0
└── Sound (5):
    ├── Local:  procedural DSP, TinyMusician SFX, NSynth, Freesound
    └── Cloud:  ElevenLabs SFX, Stable Audio 2.0

Правила (AGENTS.md):
- Никаких моделей в APK. Скачиваются после установки.
- Только Local (на устройстве) и Cloud (публичный API).
- Поддержка голосом: всё через 11 локализаций strings.xml.
- Verified = реальный ARM64 smoke-test на устройстве прошёл.
```

## Следующие шаги (после Models v0.3.0)

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

# Скачать APK последнего зелёного CI
gh run list --workflow android.yml --branch codex/models-download --limit 1 \
  --json databaseId,conclusion | jq -r '.[] | select(.conclusion=="success") | .databaseId'

# Проверить Kokoro файлы
adb -s R5CN30LJS4W shell 'run-as com.t2v.debug ls files/models/'

# Установить APK
adb -s R5CN30LJS4W install -r /path/to/app-debug.apk

# Brace check
python3 -c "
import re
files = ['app/src/main/java/com/t2v/core/model/GenerationModelCatalog.kt', ...]
for p in files:
    with open(p) as f: s = f.read()
    s = re.sub(r'\\\$\\\{[^}]*\\\}', '', s)
    o, c = s.count('{'), s.count('}')
    print(f'{p}: {o} / {c}  {\"OK\" if o==c else \"MISMATCH\"}')"
```

## Чего точно не будет (на ближайшее время)

- ❌ Подписка / монетизация
- ❌ Локальные Chatterbox / Qwen3 / OmniVoice (только через remote host)
- ❌ Встроенный Python / MCP-сервер
- ❌ Faster Whisper на устройстве
- ❌ Публикация в Google Play (сейчас — APK через GitHub)
- ❌ Модели в APK (пользователь скачивает сам, на выбор)
