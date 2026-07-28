# Глоссарий

## TTS (Text-to-Speech)
Преобразование текста в речь. Иногда называется «синтез речи».

## ASR (Automatic Speech Recognition)
Распознавание речи. Используется в Faster Whisper для верификации.

## G2P (Grapheme-to-Phoneme)
Преобразование букв в фонемы. Необходимо для качественного TTS.

## NNAPI (Android Neural Networks API)
Android API для запуска нейросетей на NPU/GPU. Используется
onnxruntime-android для ускорения Kokoro.

## XNNPACK
Библиотека ускорения CPU-инференса для float-моделей. Используется
onnxruntime как fallback.

## ONNX (Open Neural Network Exchange)
Открытый формат моделей. Поддерживается ORT, PyTorch, TensorFlow.

## ORT (ONNX Runtime)
Microsoft-овский движок инференса ONNX-моделей.

## sherpa-onnx
K2-FSA-овский движок инференса ONNX-моделей, специально для
Android (лёгкий, без ORT-зависимостей). T2V использует его для
Kokoro и Piper/VITS.

## LiteRT / TFLite
Google-овский формат моделей и движок инференса для мобильных.
T2V планирует использовать для будущих Magenta NSynth и Stable
Audio моделей (когда пройдёт ARM64 smoke-test).

## NSynth
Magenta-овская модель для генерации коротких тональных аудио-клипов
(4 сек mono 8 kHz). В T2V зарегистрирована, но `isAvailable()=false`
пока не пройден device smoke-test.

## WAV
Несжатый аудиоформат. Используется как промежуточный в T2V.

## MP3
Сжатый аудиоформат. Финальный формат экспорта (через FFmpeg + LAME).

## PCM (Pulse-Code Modulation)
Способ хранения аудио в виде последовательности значений амплитуды.
16-bit PCM = 2 байта на сэмпл.

## Sample rate
Частота дисретизации. 24 kHz = 24 000 сэмплов в секунду. Kokoro
использует 24 kHz; в T2V по умолчанию 22050 Hz mono.

## Bitrate
Битрейт MP3. 192 kbps — стандарт для подкастов.

## LAME 3.100
MP3-кодек, собирается в CI из исходников с проверкой SHA-256,
линкуется в Android FFmpeg для настоящего MP3-экспорта.

## `<music>` / `<sfx>` теги
XML-стиль разметки в T2V. Содержимое тега — это промпт для генератора
музыки или звукового эффекта. `LTVMarkupParser.parseSpans()` рвёт
voice-чанк в каждом теге; `AudioTagInserter` генерирует WAV и кладёт
`AudioClipEntity` в Room на нужной дорожке.

## `AudioTagInserter`
Компонент в `core/audio/`, который после TTS-синтеза берёт список
`AudioTag` (из `TextProcessor.process()`) и для каждого генерирует
WAV через `GeneratorRegistry`, потом пишет в Room.

## `GeneratorRegistry`
Аналог `EngineRegistry` для music/sound. Содержит все `Generator`
(NSynth, Stable Audio, ElevenLabs SFX, ProceduralAudioSynth).
Используется `AudioTagInserter` через `GeneratorRegistry.forCategory(Music/Sound)`.

## `HuggingFaceRepository`
Клиент Hugging Face, скачивает файлы моделей в `files/models/<sha256>/`
(для Kokoro) или `files/models/litert/<modelId>/` (для LiteRT).
Динамически строит `VERIFIED_ANDROID_MODELS` из `GenerationModelCatalog`.

## `GenerationModelCatalog`
Единый типизированный источник для Voice/Music/Sound моделей с
`Support.Verified` / `RuntimeInDevelopment` / `Experimental` статусами.
Содержит id, title, repository, revision, размер, лицензию,
runtime, ABI, минимальную RAM, и TagDocs.

## `GenerationPipeline`
Пайплайн в `worker/`, выполняет TTS для всех чанков, запускает
`AudioTagInserter` для тегов, склеивает WAV, микширует, кодирует в MP3.
Прогресс: `Phase.Processing / Synthesizing / Encoding / Completed`.
`audioTagClips: Int` — количество вставленных music/sound клипов.

## `DownloadableModelCard`
Composable в `ui/screens/models/ModelsScreen.kt` — единая карточка
для скачивания любой модели из `GenerationModelCatalog` через
единый flow (Download / progress / cancel / Select).

## `markSmokeTested()`
Метод в `LiteRtModelInstaller` — пишет sidecar файл, означающий что
модель прошла реальный ARM64 inference на устройстве. До этого
`NSynthSoundGenerator.isAvailable() = false` и refuses to run.

## Voice cloning
Создание копии голоса по референсному аудио-сэмплу (5-30 сек). В T2V
планируется через sherpa-onnx TTS с reference audio (PocketTTS /
ZipVoice). Сейчас UI ElevenLabs Clone не работает (отложено).

## `3-track editor`
UI в `ui/screens/editor/AudioEditorScreen.kt` с тремя дорожками
VOICE / MUSIC / SOUND. Каждая дорожка — `AudioTrackEntity` в Room,
каждый клип — `AudioClipEntity`.

## Timeline position
`timelineStartMs` в `AudioClipEntity` — позиция клипа на общей шкале
времени аудиокниги. Сейчас `AudioTagInserter` ставит 0 для music/sound
клипов (баг). Должен вычисляться как сумма `pauseBeforeMs + durationMs`
уже сгенерированных voice-сегментов.
