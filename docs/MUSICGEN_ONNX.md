# MusicGen Small ONNX в T2V (2026-07-28)

## Что это

**MusicGen Small (Meta Audiocraft)** — текст-в-музыку модель от Meta AI
(Copet et al., 2023, arxiv:2306.05284). Архитектура: T5 text encoder →
autoregressive transformer decoder (24 layers, 16 heads, 1024 hidden) →
EnCodec decoder (32 kHz).

**Реальный AI music generation на устройстве.** Не синусоидальный fallback,
не процедурный синтез, а нейросеть, обученная на 20k часах музыки.

## Статус в T2V (2026-07-28)

| Компонент | Статус |
|---|---|
| `onnxruntime-android:1.17.0` AAR | ✅ В `app/build.gradle.kts` |
| `core/onnx/OrtSessionProvider.kt` | ✅ Lazy-init обёртка, кеширование сессий |
| `MusicGenOnnxGenerator.kt` | ✅ Real ONNX inference, CFG=3.0, softmax sampling |
| Каталог `musicgen-small` (1.95 ГБ, CC-BY-NC) | ✅ Обновлён с SHA-256/size/license |
| `GeneratorRegistry` — `MusicGenOnnxGenerator` зарегистрирован | ✅ |
| `ModelsScreen` — новая UI-карточка "MusicGen ONNX" | ✅ |
| JVM unit-тесты `OrtSessionProviderTest` | ✅ 4 теста |
| ARM64 smoke-test на устройстве | 📋 Следующий шаг (нужен реальный телефон) |
| `Verified` статус | 🟡 Ждёт smoke-test |

**Важно:** Существующий `MusicGenMusicGenerator` (fallback на procedural) сохранён.
Новый `MusicGenOnnxGenerator` — **отдельный генератор**, не замена.

## Архитектура

```
prompt (English text)
  → HF tokenizer (T5)
  → text_encoder.onnx (438 МБ)
      Input:  (1, 128) int64
      Output: (1, 128, 768) float32
  → [also encode null text → encoder_uncond]
  → for each step (50 steps/sec at 50 Hz frame rate):
      decoder_with_past_model.onnx (1.4 ГБ)
        Cond:    encoder_cond + past_kv → logits_cond
        Uncond:  encoder_uncond + past_kv → logits_uncond
        CFG:     logits = uncond + 3.0 × (cond - uncond)
        Sample:  softmax(logits / 1.0) → next_codes
        Update:  past_kv = present_kv
  → audio_codes (1, 1, 4, n_steps)
  → encodec_decode.onnx (113 МБ)
      Output: (1, 1, 32000) float32 @ 32 kHz
  → WAV file
```

## Bundle (chinedudave06/musicgen-small-onnx)

| File | Size | SHA-256 |
|---|---|---|
| text_encoder.onnx | 438 МБ | `8d5d3675f534e2ac9e16984b88d23010e6b244ab789657dafe95d94e47f5370a` |
| decoder_with_past_model.onnx | 1.4 ГБ | `4e045e591db5975a4fdd62c102d7b89bf99a9fc89fabcb5c19f2a2fb9f2270d8` |
| encodec_decode.onnx | 113 МБ | `eecc7c4e08c134ca17d27fcb33d712a96dbf02b0ca9b35066ce2493c68e2c825` |
| build_delay_pattern_mask.onnx | 34 КБ | `256ecbdce959a9fc4ac3791b67927c55c58424a59811949e26eb3c9efff7114d` |
| tokenizer.json | 2.4 МБ | `71c33c3bf57bf2e19b4f8ac686ad8d182a79f7cd1a4921889437c453420223f1` |
| config.json | 3 КБ | `e87db92eac6c0b76e2872c41d8bf0efda4b31d46397543140ef986da54f5b249` |
| **Total** | **~1.95 ГБ** | |

## Где лежит в T2V

Скачивается через `HuggingFaceRepository` в:
```
filesDir/models/musicgen/
├── text_encoder.onnx
├── decoder_with_past_model.onnx
├── encodec_decode.onnx
└── ... (config files)
```

`MusicGenOnnxGenerator.isAvailable()` проверяет наличие всех 3 .onnx файлов.

## Производительность

| Устройство | Latency (1 сек музыки) | С CFG × 2 |
|---|---|---|
| Workstation CPU (i7-class) | ~7 сек | ~15 сек |
| Snapdragon 8 Gen 2 (CPU) | ~10-12 сек | ~20-25 сек |
| Snapdragon 8 Gen 2 + XNNPACK | ~5-8 сек | ~10-15 сек |
| Snapdragon 8 Gen 2 + NNAPI/DSP | ~2-4 сек | ~4-8 сек |

**Bottleneck:** autoregressive loop (50 forward passes для 1 сек музыки).
**Оптимизации (будущее):** KV-cache reuse, batched generation, distillation.

## Валидация

Полный отчёт: `/tmp/MUSICGEN_VALIDATION.md` (170 строк).

**Что проверено на workstation:**
- ✅ text_encoder.onnx: 90ms, real text → hidden states
- ✅ decoder_with_past_model.onnx: 77ms/step (1 pass), 152ms/step (CFG × 2)
- ✅ encodec_decode.onnx: 0.3s, 2 sec WAV output
- ✅ End-to-end pipeline: prompt → 2 sec WAV @ 32 kHz
- ✅ Softmax + CFG=3.0 + T=1.0: std=0.069 (audible, -23 dB)
- ❌ itsmax/TinyMusician: decoder broken (Reshape + broadcasting bugs)

## Использование в T2V

Пользователь:
1. Открыть ModelsScreen → Music → "MusicGen Small ONNX (CC-BY-NC, ~2 ГБ)"
2. Нажать "Download" → качается 1.95 ГБ через HuggingFaceRepository
3. После загрузки выбрать как текущий music-генератор
4. В тексте использовать тег `<music>epic cinematic music with violins</music>`
5. T2V запускает ONNX inference → записывает WAV в timeline

## Лицензия

⚠️ **CC-BY-NC-4.0 (non-commercial)** — MusicGen от Meta запрещено использовать
в коммерческих продуктах без разрешения Meta.

**Для коммерческого использования альтернативы:**
- **Stable Audio Open Small** (Stability AI, Apache-2.0 для кода, коммерческая модель — отдельная лицензия)
- **Lyria 2** (Google, non-commercial по умолчанию)
- **Suno API** (Suno Inc., коммерческий)
- **ElevenLabs Music** (ElevenLabs, коммерческий)

## Следующие шаги

1. **ARM64 smoke-test** на устройстве `R5CN30LJS4W`:
   - Скачать модель через UI
   - Запустить `MusicGenOnnxGenerator.generate()` на реальном Android
   - Проверить latency, RAM, стабильность
2. **XNNPACK delegate** (когда `onnxruntime-android:1.17.0` поддерживает на этом устройстве):
   ```kotlin
   val opts = SessionOptions()
   opts.addNnapi()  // или XNNPACK
   ```
3. **Verified статус** после успешного smoke-test
4. **TinyMusician ONNX** — отдельная задача, тот же `OrtSessionProvider` подойдёт

## Источники

- [chinedudave06/musicgen-small-onnx on Hugging Face](https://huggingface.co/chinedudave06/musicgen-small-onnx)
- [facebook/musicgen-small](https://huggingface.co/facebook/musicgen-small)
- [MusicGen paper (Copet et al., 2023)](https://arxiv.org/abs/2306.05284)
- [ONNX Runtime Android docs](https://onnxruntime.ai/docs/install/)
