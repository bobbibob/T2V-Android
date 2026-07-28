# Release Notes 0.1.0 (alpha)

**Дата**: 2026-07-23
**Кодовое имя**: First Light

Первая публичная alpha-версия T2V. Содержит MVP для превращения
длинного текста в MP3-аудиокниги с TTS.

## Что внутри

### Core
- TextProcessor с детекцией глав/уроков и безопасным чанкованием
- LTVMarkupParser с 13 командами (`{{voice}}`, `{{pause}}`, `{{speed}}`, и т.д.)
- AudioEncoder (WAV 16-bit PCM I/O)
- AudioMixer (voice + music + ducking + fade + normalize)
- FFmpegBridge (обёртка над ffmpeg-kit)
- WaveformExtractor (min/max envelope)
- SubtitleWriter (SRT + karaoke-ASS)
- TextNormalizer + Num2Words (5 языков: en, ru, es, fr, de)
- ProjectManager (импорт TXT/MD/DOCX)

### TTS-движки
- Kokoro (on-device, ORT Android, NNAPI)
- OpenAI, ElevenLabs, Gemini, Azure (облачные)
- Custom HTTP (настраиваемый прокси)
- Remote Host (для Piper, Chatterbox, Qwen3, OmniVoice)

### UI
- 7 экранов на Jetpack Compose + Material 3
- 11 локализаций UI
- Подсветка LTV-разметки
- Markdown-панель кнопок
- Waveform-канвас
- Аудио-плеер для превью

### Data
- Room (6 entities, 6 DAOs)
- DataStore (настройки)
- Миграция схемы (для будущих версий)

### Server-host
- Python FastAPI бэкенд
- Упрощённый эндпоинт `/synthesize`
- Совместимость с engine_host.py из оригинала

### Тесты
- 8 unit-тестов (JVM)
- 1 integration-тест (ART)
- Smoke-тесты движков

### Документация
- README, PORTING, ROADMAP, LTV_MARKUP, QUICKSTART
- ARCHITECTURE, INTERNALS, EN_ENGINE_HOST
- TESTING, DEPLOYMENT, SECURITY
- FAQ, COMPARISON, MODELS, MARKETING
- TROUBLESHOOTING, PERFORMANCE, EXTENDING
- VERSIONING, CHANGELOG, REFERENCES, INDEX, GLOSSARY

## Известные ограничения

- Kokoro G2P — ASCII-fallback (нужен eSpeak-ng / Misaki).
- Нет Faster Whisper-верификации на устройстве.
- DOCX-импорт через ручной ZIP-парсер (лучше использовать Apache POI).
- Нет background WorkManager (только каркас GenerationService).
- Полноценный timeline-микшер не реализован (упрощённый 1+1).
- Нет iOS-версии.

## Что планируется в 0.2.0

- Реальный G2P для Kokoro (eSpeak-ng / Misaki).
- Стриминг TTS.
- Apache POI для DOCX.
- UI-тесты (Compose UI Test).
- Покрытие тестами > 80%.
- Background WorkManager.

## Благодарности

- [estebanstifli](https://github.com/estebanstifli) — оригинальный T2V.
- [bobbibob](https://github.com/bobbibob) — форк, послуживший основой.
- [hexgrad](https://github.com/hexgrad) — Kokoro-ONNX.
- [Arthenica](https://github.com/Arthenica) — ffmpeg-kit.

