# Быстрый старт

## Android-приложение

### 1. Открыть в Android Studio

```bash
cd /path/to/t2v
# Откройте Android Studio → Open → выберите папку
# Дождитесь sync (Gradle скачает зависимости)
```

### 2. Собрать APK

```bash
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

### 3. Установить на устройство

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.t2v.debug/com.t2v.ui.MainActivity
```

### 4. Включить локальный Kokoro (опционально)

1. Скачайте `kokoro-v0_19.onnx` и `voices.bin` с
   https://huggingface.co/onnxcommunity/Kokoro
2. Положите в `app/src/main/assets/voices/kokoro/`
3. Пересоберите APK

Или скачайте прямо из приложения (в разработке).

### 5. Добавить облачные API

Settings → TTS engines → введите:
- OpenAI API Key
- ElevenLabs API Key
- Gemini API Key
- Azure (Subscription Key + Region)
- Custom HTTP (URL + body template)

## Тестирование без устройства

Unit-тесты:
```bash
./gradlew :app:testDebugUnitTest
```

Android (нужен эмулятор или устройство):
```bash
./gradlew :app:connectedAndroidTest
```

## Структура

```
t2v/
├── app/                          # Android-приложение
│   ├── src/main/java/com/t2v/
│   │   ├── core/                 # бизнес-логика (text, markup, audio)
│   │   ├── tts/                  # TTS-движки
│   │   ├── data/                 # Room, DataStore
│   │   ├── ui/                   # Compose-экраны
│   │   ├── worker/               # пайплайн генерации
│   │   └── server/               # загрузка проверенных моделей с Hugging Face
│   ├── src/test/                 # unit-тесты
│   ├── src/androidTest/          # интеграционные тесты
│   └── build.gradle.kts
├── docs/                         # документация
│   ├── PORTING.md
│   ├── ROADMAP.md
│   ├── LTV_MARKUP.md
│   └── QUICKSTART.md
└── tools/                        # вспомогательные скрипты
```
