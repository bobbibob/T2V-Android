# T2V — Text to Voice & Audiobook

Standalone Android TTS + audiobook приложение. Поддерживает локальный
Kokoro/Piper TTS, облачные API (OpenAI, ElevenLabs, Gemini, Azure), и
3-дорожечный редактор с вставкой музыки/звуков через `<music>/<sfx>`
теги прямо в тексте.

## Что нового (2026-07-28)

- ✅ **Локальный Kokoro 82M** verified end-to-end на устройстве.
- ✅ **`<music>промпт</music>` и `<sfx>промпт</sfx>`** в тексте — клипы
  появляются на нужной тайм-позиции в AudioEditor.
- ✅ **3-дорожечный редактор** (VOICE / MUSIC / SOUND) с FFmpeg + LAME MP3.
- ✅ **15 языков Piper/VITS** (ru, en, de, fr, es, it, zh, ja, hi, bn, ar, ko, ...).
- ✅ **11 локализаций** UI.
- ✅ **Self-test кнопка** в Settings (🧪 Run <music>/<sfx> self-test).
- ✅ **DownloadableModelCard** — единый UI для скачивания любой модели.

## Что НЕ входит в текущую разработку

- ❌ Подписка / монетизация (отложена до v0.4.0+ по решению владельца).
- ❌ Google Play Billing, Firebase Auth, AAB-релиз.
- ❌ Модели в APK (пользователь скачивает сам через HuggingFace).

## Сборка (только через CI!)

```bash
# Push в свою ветку
git push origin codex/<your-branch>

# Запустить CI
gh workflow run android.yml --ref codex/<your-branch>

# Скачать APK (workaround для прерванного download)
gh api -H "Accept: application/vnd.github+json" \
  /repos/bobbibob/T2V-Android/actions/runs/<run-id>/artifacts \
  | jq -r '.artifacts[] | select(.name=="app-debug") | .id'
# (затем: curl -L -C - ... - см. docs/TROUBLESHOOTING.md)

# Установить
adb -s R5CN30LJS4W install -r app-debug.apk
```

## Структура

```
app/                  Android-приложение (Kotlin, Compose)
  src/main/java/com/t2v/
    core/             бизнес-логика (text, markup, audio, AudioTagInserter)
    tts/              локальные Android и облачные TTS-движки
    generators/       music/sound генераторы (NSynth, ElevenLabs SFX, procedural)
    data/             Room (6 DAO, 6 Entities) + DataStore Settings
    ui/               Compose-экраны (8: editor, generation, music, review,
                      voices, projects, settings, models)
    worker/           GenerationPipeline + GenerationService
    server/           HuggingFaceRepository (загрузка моделей)
    app/              LTVApplication + AppContainer (ручной DI)
  src/test/           JVM unit-тесты
  src/androidTest/    ART integration-тесты
docs/                 документация (24+ файлов)
tools/                install.sh, check_completeness.sh, inspect_layout.sh
```

## Документация

**См. `docs/AI_HANDOFF.md`** — главный оперативный контекст для следующего
ИИ-агента. Содержит:
- Что работает на устройстве (verified end-to-end)
- Что отложено (Kokoro зависание, `timelineStartMs=0`, ElevenLabs clone)
- Текущий план (MusicGen, sherpa-onnx cloning, auto-open editor)
- Полезные однострочники для отладки

Другие ключевые файлы:
- `docs/ROADMAP.md` — что сделано / что в работе / что дальше
- `docs/CHANGELOG.md` — история изменений
- `docs/MODELS.md` — каталог моделей и движков
- `docs/STATUS.md` — текущий статус
- `docs/ARCHITECTURE.md` — диаграммы компонентов
- `docs/LTV_MARKUP.md` — поведение разметки `{{...}}` и `<music>/<sfx>`
- `docs/TROUBLESHOOTING.md` — типичные проблемы

## Требования

- JDK 17
- Android SDK 34
- Android NDK (для onnxruntime-android / sherpa-onnx)
- Устройство arm64-v8a (большинство современных)

## Опционально

- Hugging Face account (бесплатный, для токена)
- Kokoro 82M (~369 МБ) — для локального TTS
- Piper voice (~65 МБ) — для русского / других языков
- API-ключи OpenAI / ElevenLabs / Gemini / Azure (для облачных)

## Лицензия

MIT (планируется)
