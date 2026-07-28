# TinyMusician в T2V (2026-07-28)

## Что это

[TinyMusician](https://huggingface.co/asigalov61/TinyMusician) (asigalov61,
февраль 2025, arxiv:2502.12945) — компактный **Music Transformer decoder-only**,
генерирующий MIDI-токены. Лицензия **MIT** (коммерчески свободная).

**Параметры:**

| Variant | Params | fp32 | int8 | Качество |
|---|---|---|---|---|
| Small | 44M | 180 МБ | 90 МБ | draft (драфт) |
| 100M | 100M | 400 МБ | 200 МБ | okay |
| Pretrained 3L-128E | 100M | 400 МБ | 200 МБ | okay |

**На Snapdragon 8 Gen 2:** ~0.4-1.5 сек CPU-времени на 1 сек аудио (Small).
Реально работает на телефоне.

## Статус в T2V

| Компонент | Статус |
|---|---|
| MIDI-стек (парсер, декодер, рендерер) | ✅ Готов |
| `MidiRenderer` (sine fallback) | ✅ Работает |
| `SoundFontRenderer` (sample-based) | ✅ Готов, ждёт SoundFont |
| `DrumKit` (sine drums) | ✅ Работает |
| `fallbackFromPrompt()` (mood chords) | ✅ Работает |
| Real ONNX inference | 🟡 Ждём экспорт |
| ONNX-экспорт скрипт | ✅ `tools/export_tinymusician_onnx.py` |
| SoundFont installer (CDN) | ✅ Готов |
| SoundFont в каталоге | ✅ `generaluser-gs-soundfont` |

## Архитектура (когда появится ONNX)

```
MidiRenderer (fallback) ─┐
                          ├─→ TinyMusicianMusicGenerator
SoundFontRenderer ────────┘         ↓
                                   generate(request):
                                     1. fallbackFromPrompt(prompt)  // mood chords
                                          ↓
                                     2. SoundFontRenderer.render(seq)  // если SF есть
                                          ИЛИ
                                        MidiRenderer.renderSine(seq)
                                          ↓
                                     3. AudioEncoder.encodePcm16MonoWav()
```

**Когда появится ONNX**, шаг 1 заменяется на:
```kotlin
val tokens = onnxRuntime.run("input_ids" to tokenizedPrompt)  // [seq_len]
val sequence = TinyMusicianMidiDecoder.decode(tokens)
```

## Экспорт ONNX (tools/export_tinymusician_onnx.py)

```bash
# 1. Один раз на рабочей станции с GPU:
pip install torch onnx onnxruntime transformers huggingface_hub
python tools/export_tinymusician_onnx.py \
    --variant small \
    --output-dir ./tinymusician-small-onnx \
    --upload --hf-token $HF_TOKEN

# 2. Скрипт автоматически:
#    - клонирует asigalov61/TinyMusician
#    - загружает pytorch_model.bin
#    - экспортирует в ONNX opset 17 (ARM64-совместимый)
#    - квантизует в int8 (~2x сжатие)
#    - пишет SHA-256 для каждого файла
#    - опционально заливает в HF репо asigalov61/TinyMusician-ONNX
```

**Не запускается в CI** (PyTorch + onnxruntime не в Android Gradle setup).
Это ручной шаг перед коммитом.

## Как попадает в T2V

После того как скрипт залил `model.onnx` в HF:

1. В `GenerationModelCatalog.entries` обновляем
   `tinymusician-small-44m.repository = "asigalov61/TinyMusician-ONNX"` и
   `approximateDownloadBytes` (фактический размер после quantize).
2. `GenerationModelCatalog.tagDocsFor("tinymusician-small-44m")` обновляем —
   `RuntimeInDevelopment` → `Verified` после прохождения ARM64 smoke-test.
3. T2V-юзер качает модель через `ModelsScreen → Music → TinyMusician Small → Download`.
4. При следующей генерации `TinyMusicianMusicGenerator` вместо
   `fallbackFromPrompt` запускает `onnxruntime-mobile` inference.

## Тесты

- `TinyMusicianMidiDecoderTest`: 7 unit-тестов
  - fallback для epic/sad/happy/calm/lofi прогрессий
  - токен-декодирование (нотная последовательность)
  - renderSine: empty → silence, with-note → ненулевой PCM, drum channel → DrumKit
- `SoundFontRenderer` / `SoundFontParser` / `DrumKit` — без unit-тестов
  пока нет (требуют реальных SF2 файлов). Будет покрыто после smoke-test.

## Лицензия

- TinyMusician: **MIT** (asigalov61) — коммерчески свободная
- GeneralUser GS SoundFont: **CC-BY-3.0** (S. Christian Collins) — нужна атрибуция в About
- T2V-код в `core/midi/`: **T2V** (наш код)
- `tools/export_tinymusician_onnx.py`: **T2V** (наш код)
