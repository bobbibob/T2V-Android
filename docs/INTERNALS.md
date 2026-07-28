# Internals

Описание внутренних компонентов T2V (обновлено 2026-07-28).

## Lifecycle

```
MainActivity.onCreate
  └─ LTVApplication (инициализация DI)
       ├─ AppDatabase (Room, lazy)
       ├─ SettingsRepository (DataStore, lazy)
       ├─ EngineRegistry (lazy, читает settings)
       ├─ GeneratorRegistry (lazy, для music/sound)
       ├─ GenerationPipeline (lazy, с AudioTagInserter)
       └─ TextProcessor (lazy)

  └─ setContent { LTVApp() }
       └─ LTVTheme
            └─ LTVNavHost
                 ├─ EditorScreen
                 ├─ ProjectsScreen
                 ├─ GenerationScreen
                 ├─ ReviewScreen
                 ├─ MusicMixScreen / AudioEditorScreen (3-track)
                 ├─ VoicesScreen
                 ├─ SettingsScreen
                 └─ ModelsScreen
```

## Потоки данных (аудиокнига с audio tags)

```
User → Editor → save() → Room (ProjectEntity, rawText с <music>/<sfx>)
              ↓
        Generation → startGeneration()
              ↓
        TextProcessor.process()  → ProcessResult(sections, chunks, audioTags)
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

| Движок | Где работает | Где код |
|---|---|---|
| Kokoro | on-device, sherpa-onnx Android | `tts/engines/KokoroTtsEngine.kt` |
| Piper/VITS | on-device, sherpa-onnx Android | `tts/engines/PiperRussianTtsEngine.kt` |
| OpenAI | облако | `tts/engines/OpenAiTtsEngine.kt` |
| ElevenLabs | облако | `tts/engines/ElevenLabsTtsEngine.kt` |
| Gemini TTS | облако | `tts/engines/GeminiTtsEngine.kt` |
| Azure Speech | облако | `tts/engines/AzureTtsEngine.kt` |
| Custom HTTP | зависит | `tts/engines/CustomHttpTtsEngine.kt` |

## Где живут генераторы music/sound

| Генератор | Категория | Runtime | Где код |
|---|---|---|---|
| ProceduralAudioSynth | Music + Sound | DSP | `generators/synth/ProceduralAudioSynth.kt` |
| StableAudioMusicGenerator | Music | LiteRT (DSP) | `generators/impl/StableAudioMusicGenerator.kt` |
| StableAudioSoundGenerator | Sound | LiteRT (DSP) | `generators/impl/StableAudioSoundGenerator.kt` |
| NSynthSoundGenerator | Sound | LiteRT (Magenta NSynth) | `generators/impl/NSynthSoundGenerator.kt` |
| ElevenLabsSoundEffectsGenerator | Sound | Cloud API | `generators/impl/ElevenLabsSoundEffectsGenerator.kt` |

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

## AudioTagInserter

`core/audio/AudioTagInserter.kt`:
- Вызывается из `GenerationPipeline.generate()` ПОСЛЕ цикла voice-сегментов
  (планируется; сейчас вызывается ДО — отсюда баг `timelineStartMs=0`).
- Берёт `List<AudioTag>` из `TextProcessor.process()`.
- Для каждого тега: выбирает `Generator` из `GeneratorRegistry.forCategory(...)`,
  генерирует WAV, вычисляет `positionFor(tag)` (сумма
  `pauseBeforeMs + durationMs` уже сгенерированных сегментов), пишет
  `AudioTrackEntity` + `AudioClipEntity` в Room.
- Volume по умолчанию — из `AudioEditProject` defaults; редактируется
  в AudioEditor.

## Тесты

```
app/src/test/                        # unit (JVM)
  core/text/TextProcessorTest.kt
  core/markup/LTVMarkupParserTest.kt
  core/markup/LTVMarkupAudioTagsTest.kt
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

## Полезные SQL для отладки

```sql
-- Снимок всех audiobook'ов с тегами
SELECT a.id, a.title, a.status, a.segmentsDone || '/' || a.segmentsTotal AS segs,
       (SELECT COUNT(*) FROM audio_clips WHERE trackId = a.id || '-music') AS music,
       (SELECT COUNT(*) FROM audio_clips WHERE trackId = a.id || '-sound') AS sfx
FROM audiobooks a ORDER BY a.id DESC;

-- Все клипы последней генерации
SELECT c.id, c.trackId, c.timelineStartMs, c.sourceEndMs, c.markupTagId
FROM audio_clips c
WHERE c.trackId LIKE '%-music' OR c.trackId LIKE '%-sound'
ORDER BY c.id;

-- Проверка что Kokoro скачан
SELECT * FROM models WHERE id = 'csukuangfj/kokoro-en-v0_19';
-- (если пусто — модель не скачана, иди в Models → Voice → Kokoro)
```
