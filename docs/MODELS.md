# Модели и движки

T2V поддерживает **только два типа моделей** (по AGENTS.md):
1. `Local` — модель и runtime выполняют синтез непосредственно на Android-устройстве.
2. `Cloud` — приложение обращается напрямую к публичному API-провайдеру.

**Серверы-прокси, engine-host, Ollama-host и подобные запрещены.**
**Модели не включаются в APK** — пользователь скачивает их явно.

## Текущий каталог (`GenerationModelCatalog`)

### Verified (можно скачать и использовать)

| ID | Категория | Runtime | Размер | Статус |
|---|---|---|---|---|
| `kokoro-82m` | Voice | SherpaOnnx (встроен) | ~369 МБ | ✅ Скачивается и работает |
| `piper-vits` | Voice | SherpaOnnx (встроен) | ~65 МБ × голос | ✅ 15 языков скачиваются |

### RuntimeInDevelopment (registered, но `isAvailable()=false` пока нет smoke-test)

| ID | Категория | Runtime | Размер | Что нужно |
|---|---|---|---|---|
| `pocket-tts-int8` | Voice (cloning) | SherpaOnnx | неизвестно | Smoke-test на устройстве |
| `zipvoice-distill-int8` | Voice (cloning) | SherpaOnnx | неизвестно | Smoke-test + UI для reference audio |
| `nsynth-wavenet` | Sound (SFX) | LiteRT | ~17 МБ | ARM64 smoke-test + `markSmokeTested()` |
| `stable-audio-open-small` | Music + Sound (procedural) | LiteRT (DSP) | 0 | Процедурный, не AI; уже работает через `ProceduralAudioSynth` |
| `stable-audio-clip` | Sound (procedural) | LiteRT (DSP) | 0 | Процедурный, не AI; уже работает |
| `openai-music` | Music (cloud) | — | — | Только UI, не подключён |
| `elevenlabs-sound-clip` | Sound (cloud, SFX API) | — | — | Нужен API-ключ |
| `musicgen-small` | Music (AI, LiteRT) | LiteRT | ~422 МБ | Каркас + карточка в UI; **кнопка «Скачать» скрыта** (`RuntimeInDevelopment`) — нет публичного tflite-экспорта; репозиторий `wide-video/...` содержит только ONNX, поэтому `HuggingFaceRepository.install` отклоняет его (нет `.tflite`) |

### Removed (deprecated)

- ~~`bundled-music` / `bundled-sound`~~ — удалены, оставлен только процедурный DSP.

## Архитектура скачивания

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

`HuggingFaceRepository.VERIFIED_ANDROID_MODELS` — теперь динамический:
формируется из `GenerationModelCatalog.entries` (все записи с
`repository` в формате `author/name` и не начинающиеся с `http`).
Kokoro гарантированно присутствует как fallback.

## Рекомендации по выбору

### Для приватности / офлайн
- **Kokoro 82M** (369 МБ) — лучшее соотношение качества и размера.
  Скачивается через `ModelsScreen → Voice → Kokoro → Download`.
- **Piper/VITS** (65 МБ × голос) — для русского/других 15 языков.

### Для качества (без ограничений)
- **ElevenLabs Multilingual v2** — топ-1, нужен API-ключ.
- **OpenAI gpt-4o-mini-tts** — хорошее качество, низкая цена ($15/1M симв).
- **Gemini 2.5 Flash TTS** — быстро, дёшево.

### Для русского языка
- **Kokoro** (голоса `bf_*`) — хорошее качество.
- **ElevenLabs** (русские голоса) — лучшее.
- **Piper/VITS irina-medium** — нативный русский, 65 МБ, офлайн.

### Для длинных книг
- **OpenAI gpt-4o-mini-tts** — $15 за 1М симв (≈ 30 часов аудио).
- **ElevenLabs** — $5-22 за 1М симв (зависит от тарифа).
- **Kokoro** — бесплатно после загрузки модели.

### Для подкастов с музыкой
- Любой voice-движок + `{{music}}` теги в тексте +
  `AudioMixer.applyMusicDucking()` (через FFmpeg + LAME).

## Как добавить новую модель

1. **Найти готовую модель** на Hugging Face:
   - Формат файла: ONNX (для SherpaOnnx или onnxruntime) или
     TFLite (для LiteRT).
   - Размер: реалистичный для мобильного (до 500 МБ).
   - Лицензия: разрешает коммерческое использование.
2. **Проверить, что runtime поддерживает модель**:
   - SherpaOnnx Android — см. [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx).
   - LiteRT / TFLite — см. [Magenta NSynth](https://github.com/magenta/magenta).
3. **Добавить запись в `GenerationModelCatalog.entries`**:
   ```kotlin
   Entry(
       id = "my-model",
       title = "My Model",
       categories = setOf(Category.Music),
       capabilities = setOf(Capability.MusicGeneration),
       requirements = Requirements(
           minimumRamMb = 2048,
           runtime = Runtime.SherpaOnnx,
           runtimeBundled = true,
       ),
       support = Support.Verified,
       approximateDownloadBytes = 250_000_000,
       license = "Apache-2.0",
       repository = "author/my-model",
       revision = "abc123",
       notes = "TFLite variant",
   )
   ```
4. **Реализовать `Generator` или `TtsEngine`** и зарегистрировать.
5. **Провести ARM64 smoke-test** на устройстве:
   - Скачать модель через `HuggingFaceRepository.install()`.
   - Запустить inference на тестовом промпте.
   - Убедиться, что WAV валидный и SHA-256 совпадает.
   - Вызвать `LiteRtModelInstaller.markSmokeTested(plan)`.
6. **Обновить `docs/AI_HANDOFF.md` и `CHANGELOG.md`**.

## Каталог не показывает

- Серверные / PyTorch модели без Android runtime.
- Модели со статусом `Experimental` (ZipVoice) — нужен smoke-test.
- Модели с пустым `repository` или начинающимся с `http` (только Info).

## История изменений каталога

- 2026-07-23: Kokoro 82M, Piper/VITS (4 русских) — `Verified`
- 2026-07-26: добавлены Piper на 13 языков, PocketTTS, ZipVoice,
  Stable Audio Open Small, NSynth, ElevenLabs SFX, OpenAI Music —
  `RuntimeInDevelopment` / `Experimental`
- 2026-07-27: Bundled music/sound удалены
- 2026-07-28: `HuggingFaceRepository.VERIFIED_ANDROID_MODELS` стал
  динамическим (из каталога)
