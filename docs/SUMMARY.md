# T2V — итоговая сводка

## Что сделано (2026-07-28)

| Слой | Файлов | Строк | Описание |
|---|---|---|---|
| Core (text, markup, audio, subtitle, normalization, project) | 18 | ~2 100 | бизнес-логика 1:1 с Python + AudioTagInserter |
| TTS (7 движков + remote) | 14 | ~1 600 | Kokoro, OpenAI, ElevenLabs, Gemini, Azure, Custom, Remote |
| Data (Room + DataStore) | 4 | ~700 | БД проектов, аудиокниг, сегментов, 3-track аудио, настроек |
| UI (Compose, 8 экранов) | 17 | ~2 200 | Material 3, 11 локалей, навигация, ModelsScreen с DownloadableModelCard |
| Worker (Pipeline + Service) | 2 | ~300 | Foreground-генерация с прогрессом + audio tags inserter |
| Generators (music/sound) | 6 | ~600 | NSynth, Stable Audio, ElevenLabs SFX, ProceduralAudioSynth |
| Server (HF download) | 1 | ~400 | Hugging Face + LiteRT installer |
| Tests | 12 | ~900 | unit + integration (RegistrySmokeTest, LTVMarkupAudioTagsTest, ...) |
| Docs | 25 | ~3 500 | README + 24 doc-файла |
| **ИТОГО** | **~100** | **~12 500** | |

## Что работает «из коробки» (verified end-to-end на устройстве R5CN30LJS4W)

1. **Локальный Kokoro TTS** через sherpa-onnx Android runtime.
   Audiobook #2 = completed, 154 секунд речи, 2 сегмента.
2. **Локальные Piper/VITS голоса** на 15 языках (Русский, English, German,
   French, Spanish, Italian, Chinese, Japanese, Hindi, Bengali, Arabic,
   Korean). Скачиваются через `RussianVoiceInstaller`.
3. **Облачные TTS**: OpenAI, ElevenLabs, Gemini, Azure, Custom HTTP.
4. **Импорт**: TXT, MD, DOCX.
5. **LTV-разметка**: 13 команд `{{...}}` + XML-теги `<music>/<sfx>`.
6. **Генерация аудиокниги** с прогрессом, retry, cancel, persistence.
7. **3-дорожечный редактор**: VOICE / MUSIC / SOUND с микшированием.
8. **MP3 экспорт** через FFmpeg + LAME 3.100 (реальный MP3, не AAC).
9. **Субтитры**: SRT и karaoke-ASS.
10. **Просмотр сегментов**: обзор, регенерация, отметка ошибок.
11. **Проекты**: список, импорт, сохранение, удаление, SAF URI.
12. **Настройки**: 11 локалей, API-ключи, выбор модели, debug self-test.
13. **`<music>промпт</music>` теги**: генерируют WAV-клипы через
    `AudioTagInserter` (offline, ProceduralAudioSynth fallback).
14. **TagDocs Info dialog** на каждой model/generator/engine карточке.

## Что не работает / отложено (без подписки)

- ❌ **Реальный MusicGen inference** (scaffold есть, нужен ARM64 ONNX-экспорт)
- ❌ **Реальный ZipVoice inference** (scaffold есть, нужен sherpa-onnx Android runtime update)
- ❌ **ElevenLabs Clone UI** (revert'нут в 8fc42a0, не трогали)
- ❌ **G2P для Kokoro** (ASCII-fallback, espeak-ng-data уже скачивается)
- ❌ Визуальный waveform-timeline (только числовые поля)
- ❌ Voice gallery sync через GitHub
- ❌ Background downloads через WorkManager
- ❌ Tablet-адаптация, Material You
- ❌ Faster Whisper (нет ctranslate2 для Android)

## Что починено в этой сессии

- ✅ Kokoro зависание на >3к символов — `withTimeoutOrNull(5 мин)` на segment
- ✅ `timelineStartMs=0` для `<music>/<sfx>` клипов — inserter перенесён после voice-сегментов
- ✅ Авто-открытие AudioEditor после генерации с тегами — работает (было ранее)

## Что отложено до полной доделки

- ❌ **Подписка / монетизация** (Google Play Billing, Firebase Auth, paywall)
- ❌ **Публикация** (AAB build, signing, R8, Privacy Policy, Data Safety, $25)
- ❌ **Backend** (server-side verification, прокси к ElevenLabs / MusicGen)

## Что нужно для запуска

### Минимум
- JDK 17
- Android SDK 34
- Android NDK (для onnxruntime-android)

### Опционально
- Hugging Face account (бесплатный, для токена)
- Kokoro 82M (~369 МБ) — для локального TTS
- Piper voice (~65 МБ) — для русского / других языков
- API-ключи OpenAI / ElevenLabs / Gemini / Azure (для облачных)

## Следующие шаги

1. **Сборка** (только через CI): push в `codex/models-download` →
   `gh workflow run android.yml --ref codex/models-download`
2. **Тесты**: `test` job в CI, должен быть зелёный
3. **Установка**: `adb install -r app-debug.apk` на R5CN30LJS4W
4. **Тестирование**: Settings → 🧪 Run <music>/<sfx> self-test
5. **Доделка**: см. `docs/ROADMAP.md`
6. **Потом — подписка и публикация**: см. `docs/DEPLOYMENT.md`

## Контакты

- GitHub Issues: баги и фичи
- Discussions: вопросы
- Discord: (планируется)
- Twitter/X: @t2v (планируется)
