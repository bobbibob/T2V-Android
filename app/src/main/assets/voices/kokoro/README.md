# Kokoro model assets

Поместите сюда файлы модели Kokoro для локальной генерации на устройстве:

```
voices/kokoro/
├── kokoro.onnx        # ~150 MB, модель TTS (v0.19 или v1.0)
├── voices.bin         # ~25 MB, словарь голосов (256-dim style vectors)
└── tokens.txt         # опционально, словарь токенов для G2P
```

## Где скачать

- **Официальный репозиторий**: https://github.com/hexgrad/kokoro-onnx
- **Модели на HuggingFace**: https://huggingface.co/onnx-community/Kokoro

Рекомендуемая модель: `kokoro-v0_19.onnx` (24 kHz, ~150 MB).

## Альтернатива: G2P через eSpeak-ng

Для качественного произношения можно подключить eSpeak-ng через JNI,
но в текущей версии используется упрощённый ASCII-фолбэк
(см. `KokoroTtsEngine.tokenize()`).

## Размер APK

С Kokoro: ~30-35 MB (модель не входит в APK, скачивается отдельно).
Без Kokoro: < 5 MB (только облачные движки).
