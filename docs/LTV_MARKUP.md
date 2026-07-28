# LTV-разметка

Документ описывает поведение разметки в **T2V**, которое полностью
совместимо с десктопной версией [T2V](https://github.com/estebanstifli/T2V).
См. оригинальный `docs/LTV_MARKUP.md` для углублённых примеров.

## Два стиля тегов

T2V поддерживает **два разных стиля тегов** в одном тексте:

### 1. `{{ ... }}` — фигурные скобки, для просодии/голоса/пауз

```
{{chapter "Lesson 1"}}
{{voice "Serena - Spanish"}}
Bienvenido a esta lección.

{{pause 900ms}}
{{voice "Sohee - English"}}
Now listen to the same idea in English.

{{speed 0.92}}
{{volume 80%}}
This part is slower and softer.

{{emotion sad}}{{delivery whisper}}Я не уверена, что нам стоит туда идти.
{{reset all}}Обычное повествование продолжается.
```

### 2. `<music>...</music>` / `<sfx>...</sfx>` — XML-стиль, для вставки аудио

```
Текст до музыки.
<music>тёплый эмбиент-пэд, 80 BPM, 10 секунд</music>
Текст после музыки.
Дверь открылась со скрипом:
<sfx>старая деревянная дверь, скрип петель, 2 секунды</sfx>
и за ней стоял...
```

**Семантика:**
- `{{...}}` — модифицируют как рендерится следующая речь
- `<music>/<sfx>` — вставляют **отдельный аудио-клип** в это место таймлайна

## Команды `{{...}}` (полный список)

| Команда | Аргументы | Поведение |
|---|---|---|
| `{{chapter "..."}}` | строка | Установить текущую главу/раздел |
| `{{voice "..."}}` | строка | Сменить голос (поиск по началу имени) |
| `{{lang es}}` | BCP-47 | Сменить язык для движка |
| `{{pause 700ms}}` | число + ms\|s | Вставить тишину в аудио |
| `{{pause 0.7s}}` | то же | В секундах |
| `{{pause.long}}` | — | 1500 мс |
| `{{pause.short}}` | — | 300 мс |
| `{{pause random 500 1200}}` | min, max | Случайная пауза в диапазоне |
| `{{speed 0.92}}` | 0.5..2.0 | Множитель скорости |
| `{{volume 80%}}` | процент или множитель | Громкость (1.0 = 100%) |
| `{{pitch 1.1}}` | множитель | Pitch |
| `{{emotion happy}}` | строка | Эмоциональная окраска |
| `{{emotion "quietly joyful"}}` | строка в кавычках | Кастомная эмоция |
| `{{delivery whisper\|shout\|soft\|loud\|slow\|fast}}` | — | Подача |
| `{{breath}}` `{{laugh}}` `{{sigh}}` `{{gasp}}` | — | Одноразовые вокальные реакции |
| `{{reset emotion\|delivery\|prosody\|voice\|all}}` | — | Сбросить состояние |
| `{{sfx "name" 200ms}}` | имя, смещение | (legacy) Вставить звуковой эффект |
| `{{music "calm" -2dB}}` | имя, громкость | (legacy) Фоновая музыка |
| `{{cmd key=value}}` | пары | Кастомные параметры |

## Теги `<music>` / `<sfx>`

| Тег | Атрибуты | Поведение |
|---|---|---|
| `<music>промпт</music>` | — | Вставить музыкальный клип в это место |
| `<sfx>промпт</sfx>` | — | Вставить звуковой эффект в это место |

**Промпт** — это свободный текст на русском (или любом другом) языке.
Передаётся в `Generator.generate(prompt, outputFile)`.

**Громкость и скорость клипа** — из настроек выбранной модели (settings
`selected_music_generator` / `selected_sound_generator`), редактируются
в AudioEditor как у любого другого клипа (gain, fade, mute, solo).

**Поведение парсера:**
- `LTVMarkupParser.parseSpans()` рвёт voice-чанк в каждом теге: всё до
  `<` — конец предыдущего voice-чанка, всё после `>` — начало нового.
- Тег никогда не попадает в произносимый текст.
- `parseSpans()` помечает каждый разрыв через `MarkupSpan.trailingAudioTag`.
- `TextProcessor.process()` возвращает `ProcessResult(sections, chunks, audioTags)`.
- `GenerationPipeline` после цикла voice-сегментов вызывает
  `AudioTagInserter.insert(audioTags, audiobookId)`.
- `timelineStartMs` = сумма `pauseBeforeMs + durationMs` уже сгенерированных
  сегментов (см. `AudioTagInserter.positionFor(tag)`).

**Доступные генераторы:**

| Категория | Generator | Когда доступен |
|---|---|---|
| Music | `StableAudioMusicGenerator` (procedural) | всегда |
| Music | будущий MusicGen через ONNX | когда скачана модель |
| Sound | `StableAudioSoundGenerator` (procedural) | всегда |
| Sound | `NSynthSoundGenerator` (Magenta NSynth) | после ARM64 smoke-test |
| Sound | `ElevenLabsSoundEffectsGenerator` | с ElevenLabs API-ключом |

Если ни один генератор не доступен, `AudioTagInserter` пропускает тег
без падения.

## Пример целиком

```text
{{chapter "Глава 1: Тайна старого дома"}}
{{voice "ru_RU-irina-medium"}}
Ирина шла по тёмной аллее, и сердце её билось всё чаще.

<music>тёмный эмбиент с напряжёнными струнными, 70 BPM, 10 секунд</music>

<emotion anxious</emotion><delivery whisper>Что-то здесь не так...</delivery>

<sfx>скрип старой деревянной двери на петлях, 1.5 секунды</sfx>

{{emotion surprised}}{{gasp}}Кто здесь?!

{{pause 800ms}}

Это была лишь тень от ветра.
```

## Реализация

- Парсер: `app/src/main/java/com/t2v/core/markup/LTVMarkupParser.kt`
- Состояние: `MarkupState` (прикрепляется к каждому `TextChunk`)
- Audio tags: `extractAudioTags()` + `parseSpans().trailingAudioTag`
- Audio inserter: `app/src/main/java/com/t2v/core/audio/AudioTagInserter.kt`
- Подсветка в редакторе: `app/src/main/java/com/t2v/ui/markup/MarkupHighlighter.kt`
- Панель кнопок: `app/src/main/java/com/t2v/ui/components/MarkupToolbar.kt`

## Тесты

- `app/src/test/java/com/t2v/core/markup/LTVMarkupParserTest.kt` — все `{{...}}` команды
- `app/src/test/java/com/t2v/core/markup/LTVMarkupAudioTagsTest.kt` — `<music>/<sfx>` теги
- `app/src/test/java/com/t2v/core/text/TextProcessorTest.kt` — `ProcessResult` с audioTags

## Известные баги

- **`timelineStartMs=0` для всех music/sound клипов.** Причина:
  `AudioTagInserter.insert()` сейчас вызывается ДО voice-сегментов
  (в `GenerationPipeline.kt:109`), когда `durationMs=0` для всех
  segments. Должен вызываться после цикла. Workaround: переставляйте
  клипы вручную в AudioEditor.
- **Kokoro inference зависает** на длинных текстах (>3 000 символов).
  Не починено.
