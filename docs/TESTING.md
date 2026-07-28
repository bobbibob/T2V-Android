# Тестирование

## Структура

```
app/src/test/             # JVM unit-тесты (быстрые, ~5 с)
  core/text/              #   - TextProcessor
  core/markup/            #   - LTVMarkupParser
  core/audio/             #   - AudioEncoder, AudioMixer
  core/subtitle/          #   - SubtitleWriter
  core/normalization/     #   - TextNormalizer, Num2Words
  core/project/           #   - ProjectManager
  tts/                    #   - EngineInfo smoke

app/src/androidTest/      # ART integration (медленные, ~30 с)
  EndToEndSmokeTest       #   - core-модули под Android
```

## Запуск

```bash
# Все unit-тесты
./gradlew :app:testDebugUnitTest

# Все android-тесты (нужен эмулятор или устройство)
./gradlew :app:connectedDebugAndroidTest

# Конкретный тест
./gradlew :app:testDebugUnitTest --tests "com.t2v.core.markup.LTVMarkupParserTest"

# С coverage
./gradlew :app:createDebugUnitTestCoverageReport
# → app/build/reports/coverage/test/debug/index.html
```

## Что покрыто

- ✅ TextProcessor: section detection, chunking, regex
- ✅ LTVMarkupParser: все команды + edge cases
- ✅ AudioMixer: gain, fade, ducking, normalize
- ✅ SubtitleWriter: SRT/ASS time format, karaoke
- ✅ TextNormalizer: числа, валюты, проценты
- ✅ Num2Words: en, ru, es, fr, de
- ✅ ProjectManager: txt import
- ✅ EngineInfo: smoke
- ✅ Android e2e: TextProcessor + LTVMarkupParser на ART

## Что нужно добавить

- UI-тесты (Compose UI Test).
- Тесты микшера с реальными WAV-файлами.
- Тесты Room (in-memory database).
- Тесты загрузки моделей (с mock HTTP).
- Скриншот-тесты (Paparazzi или Roborazzi).
