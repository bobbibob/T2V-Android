# Архитектура

T2V — самостоятельное Android-приложение. Допустимы только два типа TTS:

1. `Local` — модель и runtime выполняют синтез непосредственно на телефоне.
2. `Cloud` — приложение обращается к публичному API-провайдеру.

Пользовательские вычислительные серверы и engine-host не поддерживаются.
**Модели не включаются в APK** — пользователь скачивает их явно через
`HuggingFaceRepository`.

```text
Compose UI
    ↓
ViewModel / StateFlow
    ↓
GenerationPipeline
    ↓
EngineRegistry
    ├── Local Android runtime (sherpa-onnx, onnxruntime)
    └── Cloud API (HTTPS, OkHttp)
    ↓
WAV segments
    ↓
AudioTagInserter.insert(<music>/<sfx>)
    ↓
GeneratorRegistry
    ├── ProceduralAudioSynth (offline, DSP fallback)
    ├── NSynthSoundGenerator (LiteRT, smoke-test gate)
    ├── StableAudioMusicGenerator (LiteRT, procedural)
    └── ElevenLabsSoundEffectsGenerator (Cloud)
    ↓
3-track timeline (VOICE / MUSIC / SOUND)
    ↓
FFmpegBridge + LAME → MP3
```

## Уровни

| Уровень | Каталог | Назначение |
|---|---|---|
| Core | `core/` | текст, разметка, аудио, нормализация, субтитры, AudioTagInserter |
| Data | `data/` | Room и DataStore |
| TTS | `tts/` | локальные Android и облачные движки |
| Generators | `generators/` | music/sound генераторы (NSynth, ElevenLabs SFX, procedural) |
| Model catalog | `server/`, `core/model/` | проверка и загрузка файлов с Hugging Face, GenerationModelCatalog |
| Worker | `worker/` | пайплайн генерации и foreground service |
| UI | `ui/` | Compose-экраны и ViewModel |

Зависимости направлены от UI к бизнес-логике. `core/` не зависит от Android UI,
TTS или хранилища.

## Поток данных для аудиокниги

```text
User → Editor → save() → Room (ProjectEntity, rawText с <music>/<sfx>)
              ↓
        Generation → startGeneration()
              ↓
        TextProcessor.process()  → (sections, chunks, audioTags)
              ↓
        для каждого chunk:
              ├─ TTS engine (локальный Android или облачный API)
              ├─ AudioEncoder.writeWav() → WAV
              └─ AudioEncoder.writeSilence() для пауз
              ↓
        AudioTagInserter.insert(audioTags, audiobookId)
              ├─ для каждого <music>:
              │   ├─ generator = GeneratorRegistry.forCategory(Music).first()
              │   ├─ generator.generate(prompt, outputFile) → WAV
              │   ├─ positionFor(tag) = sum(pauseBeforeMs + durationMs) сегментов
              │   └─ Room.upsert(AudioTrackEntity, AudioClipEntity)
              └─ для каждого <sfx>: то же
              ↓
        FFmpegBridge.concat() / .encode() → MP3 (LAME 3.100)
              ↓
        Room (AudiobookEntity, SegmentEntity, AudioTrackEntity, AudioClipEntity)
              ↓
        AudioEditorScreen (3-дорожечный микшер)
              ↓
        FFmpegBridge.applyMusicDucking() → финальный mix.mp3
              ↓
        Share Intent / сохранение в MediaStore
```

## Где живут движки

| Движок | Где работает | Где код | Размер |
|---|---|---|---|
| Kokoro 82M | on-device, sherpa-onnx Android | `tts/engines/KokoroTtsEngine.kt` | ~369 МБ |
| Piper/VITS | on-device, sherpa-onnx Android | `tts/engines/PiperRussianTtsEngine.kt` | ~65 МБ × голос |
| OpenAI TTS | облако | `tts/engines/OpenAiTtsEngine.kt` | — |
| ElevenLabs | облако | `tts/engines/ElevenLabsTtsEngine.kt` | — |
| Gemini TTS | облако | `tts/engines/GeminiTtsEngine.kt` | — |
| Azure Speech | облако | `tts/engines/AzureTtsEngine.kt` | — |
| Custom HTTP | облако/локально (зависит от endpoint) | `tts/engines/CustomHttpTtsEngine.kt` | — |

## Где живут генераторы music/sound

| Генератор | Категория | Runtime | Статус |
|---|---|---|---|
| ProceduralAudioSynth (DSP fallback) | Music + Sound | — | всегда работает |
| StableAudioMusicGenerator | Music | LiteRT (procedural) | verified |
| StableAudioSoundGenerator | Sound | LiteRT (procedural) | verified |
| NSynthSoundGenerator | Sound | LiteRT (Magenta NSynth) | RuntimeInDevelopment |
| ElevenLabsSoundEffectsGenerator | Sound | Cloud API | requires API key |

## LTV-разметка

`LTVMarkupParser` — это pull-parser с поддержкой всех команд оригинала.
См. `docs/LTV_MARKUP.md`.

**Два стиля тегов:**
- `{{voice "..."}}`, `{{pause 500ms}}`, `{{speed 1.2}}` — фигурные
  скобки, для просодии/голоса/пауз.
- `<music>промпт</music>`, `<sfx>промпт</sfx>` — XML-стиль, для
  вставки аудио-клипов в точные позиции текста.

Подсветка в редакторе: `ui/markup/MarkupHighlighter.kt`.
Панель кнопок: `ui/components/MarkupToolbar.kt`.

## Политика локальных моделей

Расширение `.onnx`, `.gguf` или `.safetensors` само по себе не подтверждает
совместимость. В каталог допускается только точная ревизия модели, для которой:

- встроен Android-runtime;
- известен полный набор обязательных файлов;
- проверены ABI и требования к памяти;
- выполнен реальный синтез на Android-устройстве (`LiteRtModelInstaller.markSmokeTested()`).

`HuggingFaceRepository.VERIFIED_ANDROID_MODELS` динамически строится из
`GenerationModelCatalog.entries` — все записи с `repository` в формате
`author/name` (не начинающиеся с `http`). Kokoro гарантированно присутствует
как fallback, даже если каталог случайно отредактирован.

## Тесты

```
app/src/test/                        # unit (JVM)
  core/text/TextProcessorTest.kt
  core/markup/LTVMarkupParserTest.kt
  core/markup/LTVMarkupAudioTagsTest.kt  (NEW)
  core/audio/AudioMixerTest.kt
  core/subtitle/SubtitleWriterTest.kt
  core/normalization/TextNormalizerTest.kt
  core/normalization/Num2WordsTest.kt
  core/project/ProjectManagerTest.kt
  tts/RegistrySmokeTest.kt
  tts/engines/KokoroTtsEngineTest.kt
  generators/GeneratorRegistryTest.kt
  core/model/GenerationModelCatalogTest.kt

app/src/androidTest/                 # integration (ART)
  EndToEndSmokeTest.kt
```

Запуск (только через CI): push → `gh workflow run android.yml` →
`gh run view --log`.
