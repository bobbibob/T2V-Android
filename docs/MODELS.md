# Модели и движки

T2V поддерживает **только два типа моделей** (по AGENTS.md):
1. `Local` — модель и runtime выполняют синтез непосредственно на Android-устройстве.
2. `Cloud` — приложение обращается напрямую к публичному API-провайдеру.

**Серверы-прокси, engine-host, Ollama-host и подобные запрещены.**
**Модели не включаются в APK** — пользователь скачивает их явно.

## Полный каталог (2026-07-28)

### Voice (8 движков)

| ID | Тип | Источник | Размер | Статус |
|---|---|---|---|---|
| `kokoro-82m` | Local | HuggingFace (csukuangfj/kokoro-onnx-v1.0) | 369 МБ | ✅ Verified |
| `piper-vits` | Local | HuggingFace (k2-fsa/sherpa-onnx) | 65 МБ × голос | ✅ Verified, 15 языков |
| `pocket-tts-int8` | Local | HuggingFace (kyutai/pocket-tts) | 95 МБ | 🟡 RuntimeInDev |
| `zipvoice-distill-int8` | Local | HuggingFace (k2-fsa/ZipVoice) | 180 МБ | 🟡 RuntimeInDev |
| `openai-tts` | Cloud | OpenAI API | — | ✅ Verified |
| `elevenlabs-tts` | Cloud | ElevenLabs API | — | ✅ Verified |
| `gemini-tts` | Cloud | Google Gemini API | — | ✅ Verified |
| `azure-neural-tts` | Cloud | Microsoft Azure | — | ✅ Verified |

### Music (10 движков)

| ID | Тип | Источник | Размер | Статус | Лицензия |
|---|---|---|---|---|---|
| `tinymusician-small-44m` | Local | HuggingFace (asigalov61) | 180 МБ ONNX | 🟡 RuntimeInDev | MIT |
| `tinymusician-100m` | Local | HuggingFace (asigalov61) | 420 МБ ONNX | 🟡 RuntimeInDev | MIT |
| `generaluser-gs-soundfont` | Local | archive.org | 30 МБ | ✅ Verified | CC-BY-3.0 |
| `stable-audio-open-small` | Local | procedural DSP | 0 | ✅ Verified | T2V |
| `musicgen-small` | Local | HuggingFace (chinedudave06) | 3.7 ГБ | 🟡 RuntimeInDev | CC-BY-NC-4.0 ⚠️ |
| `openai-music` | Cloud | OpenAI API | — | 🟡 RuntimeInDev | OpenAI terms |
| `elevenlabs-music` | Cloud | ElevenLabs API | — | 🟡 RuntimeInDev | ElevenLabs terms |
| `suno-api` | Cloud | Suno API v1 | — | 🟡 RuntimeInDev | Suno terms |
| `stable-audio-cloud` | Cloud | Stability API | — | 🟡 RuntimeInDev | Stability terms |
| `lyria-2-gemini` | Cloud | Google Lyria 2 | — | 🟡 RuntimeInDev | Google terms (NC) |

### Sound / SFX (5 движков)

| ID | Тип | Источник | Размер | Статус | Лицензия |
|---|---|---|---|---|---|
| `stable-audio-clip` | Local | procedural DSP | 0 | ✅ Verified | T2V |
| `nsynth-wavenet` | Local | Magenta (GitHub) | 17 МБ | 🟡 RuntimeInDev | Apache-2.0 |
| `freesound-cc0-pack` | Local | Freesound dump | 150 МБ | 🟡 RuntimeInDev | CC0 |
| `elevenlabs-sound-clip` | Cloud | ElevenLabs API | — | 🟡 RuntimeInDev | ElevenLabs terms |
| `stable-audio-cloud` | Cloud | Stability API | — | 🟡 RuntimeInDev | Stability terms |

## Итого в каталоге

- **23 записи** в `GenerationModelCatalog.entries`
- **8 voice** (4 Local + 4 Cloud)
- **10 music** (5 Local + 5 Cloud)
- **5 sound** (3 Local + 2 Cloud)
- **18 скаффолдов** `Generator` / `TtsEngine` (некоторые — облачные, не Local)

## Removed

- ~~`bundled-music` / `bundled-sound`~~ — удалены
- ~~`magenta-realtime-2`~~ — удалён 2026-07-28 (1.5 ГБ, нет LiteRT-экспорта от Google, авторегрессионный декодер на Java = нереалистично)

## Архитектура скачивания

```
ModelsScreen → ModelsViewModel.downloadModelFromCatalog(catalogId)
                       ↓
                HuggingFaceRepository.install(model, variant, onProgress)
                       ↓
                https://huggingface.co/<author>/<name>/resolve/<revision>/<path>
                       ↓
                files/models/<sha256-prefix>/  (Kokoro) или
                files/models/litert/<modelId>/  (LiteRT) или
                files/soundfonts/<name>.sf2      (SoundFont)
                       ↓
                markInstalled(markSmokeTested для ARM64 runtime)
```

## Рекомендации по выбору

### Голос (Voice)

| Сценарий | Рекомендация |
|---|---|
| Приватность / офлайн | Kokoro 82M (en) или Piper/VITS (15 языков) |
| Лучшее качество (en) | ElevenLabs Multilingual v2 |
| Лучшее качество (ru) | ElevenLabs Multilingual v2 (русские голоса) |
| Бюджетно | OpenAI gpt-4o-mini-tts ($15/1M симв) |
| Бесплатно, клонирование | Kokoro сейчас, ZipVoice Distill — когда sherpa-onnx обновится |

### Музыка (Music)

| Сценарий | Рекомендация |
|---|---|
| Полный офлайн, MIT | TinyMusician Small (180 МБ) + GeneralUser SoundFont (30 МБ) |
| Длинные треки (минуты) | ElevenLabs Music / Suno / Lyria 2 (cloud) |
| Короткие джинглы/переходы | TinyMusician SFX через percussive канал |
| Промышленный пайплайн | Stable Audio 2 (cloud) или Lyria 2 (cloud) |

### Звуки (Sound / SFX)

| Сценарий | Рекомендация |
|---|---|
| Полный офлайн, CC0 | Freesound Pack (150 МБ) — 30 000 готовых звуков |
| Уникальные тембры | Magenta NSynth (4-сек ноты, 17 МБ) |
| Короткие перкуссионные | TinyMusician SFX через SoundFont |
| Длинные/сложные SFX | ElevenLabs Sound Effects API (cloud) или Stable Audio (cloud) |

## Как добавить новую модель

1. **Найти готовую модель** на Hugging Face / GitHub:
   - Формат файла: ONNX (для SherpaOnnx или onnxruntime-mobile) или
     TFLite (для LiteRT).
   - Размер: реалистичный для мобильного (от 17 МБ NSynth до 3.7 ГБ MusicGen).
   - Лицензия: разрешает коммерческое использование (MIT, Apache-2.0, CC0, CC-BY).
     CC-BY-NC — только для некоммерческого (помечаем в UI).
2. **Проверить, что runtime поддерживает модель**:
   - `sherpa-onnx` Android — см. [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx).
   - `onnxruntime-mobile` AAR — стандартный, добавляется в `app/build.gradle.kts`.
   - `LiteRT / TFLite` — встроен в APK, лимит ~100-200 МБ.
3. **Добавить запись в `GenerationModelCatalog.entries`**:
   ```kotlin
   Entry(
       id = "my-model",
       title = "My Model",
       categories = setOf(Category.Music),
       capabilities = setOf(Capability.MusicGeneration),
       requirements = Requirements(
           minimumRamMb = 1024,
           runtime = Runtime.OnnxRuntimeMobile,
           runtimeBundled = false,
       ),
       engineKind = EngineKind.Local,
       download = Download.HuggingFace,
       support = Support.RuntimeInDevelopment,
       approximateDownloadBytes = 200_000_000L,
       license = "Apache-2.0",
       repository = "author/my-model",
       revision = null,
       notes = "Описание",
   )
   ```
4. **Реализовать `Generator`** (если это music/sound) или `TtsEngine` (если voice).
5. **Зарегистрировать** в `GeneratorRegistry.all()` или `EngineRegistry.createEngine()`.
6. **Добавить UI-карточку** в `ModelsScreen` (для music/sound) или
   в `VoicesScreen` (для voice).
7. **Провести ARM64 smoke-test** на устройстве:
   - Скачать модель через `HuggingFaceRepository.install()`.
   - Запустить inference на тестовом промпте.
   - Убедиться, что WAV валидный и SHA-256 совпадает.
   - Вызвать `LiteRtModelInstaller.markSmokeTested(plan)`.
8. **Перевести** в `Support.Verified`.
9. **Обновить `docs/AI_HANDOFF.md` и `CHANGELOG.md`**.

## История изменений каталога

- 2026-07-23: Kokoro 82M, Piper/VITS (4 русских) — `Verified`
- 2026-07-26: добавлены Piper на 13 языков, PocketTTS, ZipVoice,
  Stable Audio Open Small, NSynth, ElevenLabs SFX, OpenAI Music —
  `RuntimeInDevelopment` / `Experimental`
- 2026-07-27: Bundled music/sound удалены
- 2026-07-28: 
  - `HuggingFaceRepository.VERIFIED_ANDROID_MODELS` стал динамическим
  - Magenta RealTime 2 удалён (1.5 ГБ, нет LiteRT-экспорта, авторегрессионный)
  - Добавлено поле `EngineKind` (Local/Cloud) и `Download` (None/HuggingFace/CdnBundle/Cloud)
  - Добавлены: TinyMusician Small/100M/SFX, GeneralUser SoundFont, Freesound Pack,
    OpenAI Music, ElevenLabs Music, Suno API, Lyria 2, Stable Audio cloud
  - **Итого: 23 записи в каталоге** (8 voice + 10 music + 5 sound)
