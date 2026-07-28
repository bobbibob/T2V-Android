# FAQ

## Q: Почему Kokoro, а не Piper/Chatterbox на устройстве?

**Kokoro-ONNX** — единственный движок, для которого есть готовые
сборки ORT (ONNX Runtime) под Android с NNAPI-ускорением.

- **Piper** — нативный бинарник, который нужно пересобирать под
  `aarch64-linux-android` через NDK. Можно, но непросто.
- **Chatterbox, Qwen3, OmniVoice** — все на PyTorch. Модели 0.5–2 ГБ,
  плюс сам PyTorch 500 МБ. APK стал бы > 2 ГБ.

Поэтому локальный каталог показывает только модели с проверенным Android-runtime.
Серверные TTS-модели не поддерживаются.

## Q: Можно ли без интернета?

**Да**, если использовать Kokoro. Скачайте модель один раз (~150 МБ),
положите в `assets/voices/kokoro/`, и приложение будет работать офлайн.

OpenAI / ElevenLabs / Gemini / Azure — облачные, требуют интернет.

## Q: Какие локали поддерживаются?

11 локалей strings.xml: en, ru, es, fr, de, it, pt, zh, ja, hi, ar.

TTS зависит от движка:
- Kokoro: en, es, fr, it, pt, ja, zh (через специальные голоса)
- OpenAI: en, es, fr, de, it, pt, ja, zh, hi, ar (многоязычная модель)
- ElevenLabs: multilingual v2 — все основные
- Gemini: 30+ языков
- Azure: 100+ голосов на 50+ языков

## Q: Где хранятся сгенерированные файлы?

```
/data/data/com.t2v/files/audiobooks/<id>/
  ├── seg_00000.wav
  ├── seg_00001.wav
  ├── ...
  ├── audiobook.mp3   (финальная склейка)
  └── mix.mp3         (микс с музыкой)
```

Доступ к этим файлам из других приложений: Settings → Output directory.

## Q: Как добавить новый TTS-движок?

1. Создайте класс в `app/src/main/java/com/t2v/tts/engines/MyEngine.kt`,
   реализующий `TtsEngine`.
2. Зарегистрируйте в `EngineRegistry.createEngine()`.
3. Добавьте `EngineInfo` в `EngineRegistry.allEngineInfos()`.
4. Добавьте переводы названия в `strings.xml`.

## Q: Можно ли использовать Whisper-верификацию?

Пока нет: для используемого варианта Faster Whisper отсутствует Android-runtime.

## Q: Как работает LTV-разметка в Compose?

`LTVMarkupParser` парсит `{{...}}` команды, `MarkupHighlighter`
подсвечивает их в редакторе. Каждый `TextChunk` содержит
`markupState` с актуальным голосом/скоростью/громкостью/паузами.

См. `docs/LTV_MARKUP.md`.

## Q: Что с проектом на 100+ глав?

`TextProcessor.splitSections` обрабатывает любой объём текста — главы
определяются по regex, не по количеству. Чанкование — по размеру,
не по структуре.

## Q: Где скачать Kokoro-модель?

- HuggingFace: https://huggingface.co/onnx-community/Kokoro
- Рекомендуемая: `kokoro-v0_19.onnx` (24 kHz, ~150 МБ)

Положите в `app/src/main/assets/voices/kokoro/` и пересоберите APK.

## Q: Можно ли обновлять модели без пересборки APK?

Да, используя `Context.filesDir` вместо `assets/`. См.
`KokoroTtsEngine` — он читает из `filesDir/voices/kokoro/`.
Модель можно скачать через `VoiceCatalogStub.downloadVoice()`.
