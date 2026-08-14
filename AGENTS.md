# Repository Guidelines

Guidelines for AI agents and human contributors working on **T2V**, a standalone Android TTS and audiobook application.

## КРИТИЧЕСКИ ВАЖНО: Никогда не нагружай машину разработчика

**НИКОГДА** не запускай на локальной машине разработчика ничего тяжёлого: gradle/gradlew, java, kotlin-daemon, эмуляторы (qemu/emulator), ollama, компиляции, unit/instrumented-тесты, сборки, `adb install/push` тяжёлых файлов. Это запрещено. Нарушение = риск сжечь процессор/Mac разработчика.

Вместо этого:
- **Все сборки и тесты** выполняются ТОЛЬКО через **GitHub Actions** (`.github/workflows/android.yml`): push в `main` → CI сам собирает APK и прогоняет `testDebugUnitTest`. Команды: `git add/commit/push`, просмотр статуса через `gh run`/`gh api` (лёгкие, разрешены).
- Готовые артефакты получать через `gh run download 31707059660 -n app-debug` (лёгкая загрузка, разрешено).
- Модели на устройство заливать малыми реже (см. ниже) и только по явной команде пользователя.
- Не поднимай фоновые процессы, демоны, не запускай adb-сервер в контейнере, если не просили.
- Категорически нельзя: `./gradlew`, `java`, `kotlin-daemon`, `emulator`, `ollama`, фоновая компиляция.

## Project Structure & Module Organization

```
t2v/
├── app/                        Android-приложение (Kotlin, Compose)
│   ├── src/main/java/com/t2v/
│   │   ├── core/               бизнес-логика (text, markup, audio, subtitle, normalization, project)
│   │   ├── tts/                локальные Android и облачные TTS-движки
│   │   ├── data/               Room (6 DAO, 6 Entities) + DataStore Settings
│   │   ├── ui/                 Compose-экраны (8: editor, generation, music, review, voices, projects, settings, models)
│   │   ├── worker/             GenerationPipeline + GenerationService
│   │   ├── server/             проверка и загрузка локальных моделей с Hugging Face
│   │   ├── util/               LocaleHelper, Permissions, AudioPlayer
│   │   └── app/                LTVApplication + AppContainer (ручной DI)
│   ├── src/main/assets/        Kokoro-модель, FFmpeg-бинарь (см. README каждого)
│   ├── src/main/res/values*/   strings.xml (11 локалей)
│   ├── src/test/               JVM unit-тесты (7 классов)
│   └── src/androidTest/        ART integration-тесты
├── docs/                       документация (PORTING, ROADMAP, LTV_MARKUP, FAQ, …)
└── tools/                      install.sh, check_completeness.sh, inspect_layout.sh
```

**Слои (зависимости только вверх)**: `core/` → `tts/` → `worker/` → `ui/`. Обратные ссылки запрещены.

## Build, Test, and Development Commands

```bash
# Сборка debug APK (главная цель CI)
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk (~40 МБ)

# Unit-тесты (есть 1-2 фейла, APK не блокируется)
./gradlew :app:testDebugUnitTest

# Интеграционные тесты (нужен эмулятор/устройство)
./gradlew :app:connectedDebugAndroidTest

# Линт
./gradlew :app:lint

# Установка на устройство
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Скачать готовый APK из CI
gh run download 29977084836 -n app-debug

```

CI: `.github/workflows/android.yml`. Триггер: push в main, PR, или `gh workflow run android.yml`.

## Coding Style & Naming Conventions

- **Kotlin**: официальный стиль JetBrains (4 пробела, без табов). Включите `ktlint` в IDE.
- **Имена пакетов**: `com.t2v.<layer>.<feature>` (`com.t2v.tts.engines`).
- **Классы**: `PascalCase`. ViewModel'и — `XxxViewModel`. Sealed-иерархии — `Xxx`.
- **Файлы**: имя совпадает с главным классом (`KokoroTtsEngine.kt`).
- **Composable-функции**: `PascalCase` (как в Material 3).
- **Тесты**: `XxxTest.kt`, методы — обратные кавычки с пробелами: `` `parses voice commands` ``.
- **JSON-ключи**: `camelCase` в Kotlin, `snake_case` в wire-формах.
- **Imports**: без wildcard'ов; только в начале файла; сортируются автоматически.
- **Локализация**: новая строка → `values/strings.xml` И в 10 `values-<lang>/`. **Не дублируйте** — будет ошибка `mergeDebugResources`.

## Testing Guidelines

- **Фреймворк**: JUnit 4 (unit), AndroidJUnit4 (integration).
- **Что покрывать**: всё в `core/`, edge cases в `tts/engines/`.
- **Тесты детерминированы**: не вызывать сеть; `Random(seed)` для воспроизводимости.
- **Запуск**:
  ```bash
  ./gradlew :app:testDebugUnitTest
  ```
- **Smoke-тест движков** (`RegistrySmokeTest`): только метаданные `EngineInfo`.
- **Coverage цель**: > 60% в `core/`, > 40% в `tts/`.
- **Известные фейлы**: нет. Ранее падавшие тесты (writeSilence, pause 0.7s, clean, currencies, Num2Words) починены — см. ROADMAP.

## Commit & Pull Request Guidelines

- **Conventional Commits**: `feat:`, `fix:`, `docs:`, `ci:`, `refactor:`, `chore:`.
- **Один коммит = одна логическая правка**.
- **Заголовок**: imperative mood, ≤ 72 символов.
- **PR**: ссылка на issue (`Closes #N`), краткое "что и почему", скриншоты для UI.
- **Перед PR**: `tools/check_completeness.sh`, обновить `docs/CHANGELOG.md`, прогнать тесты.

## Текущий план работы (см. `docs/ROADMAP.md`)

1. **Сейчас**: подключить реальный FFmpeg-бинарь в `assets/ffmpeg/`.
2. **Потом**: подключить Kokoro-модель в `assets/voices/kokoro/`.
3. **Потом**: тесты на реальном устройстве.
4. **Потом**: UI-тесты, нормализация edge cases, полировка.
5. **В самом конце** (или никогда): публикация в Google Play / F-Droid.

## Agent-Specific Instructions

- **Не коммитьте крупные бинарники** (Kokoro .onnx, FFmpeg-бинарь) — только плейсхолдеры.
- **Compose-экраны**: передавайте `LocalContext.current` явно в `ViewModelFactory`; не полагайтесь на глобальный контекст.
- **При добавлении TTS-движка**: реализуйте `TtsEngine`, зарегистрируйте в `EngineRegistry.createEngine()` + `allEngineInfos()`, добавьте API-ключ в `SettingsRepository.Keys`, добавьте `EngineInfo` с правильным `EngineKind` (Local/Cloud).
- **При изменении LTV-разметки**: обновите парсер (`LTVMarkupParser`), подсветку (`MarkupHighlighter`), панель кнопок (`MarkupToolbar`) и тесты.
- **Compose Material 3 1.2.x**: `NavigationBarItem` помечен как `@ExperimentalMaterial3Api`. Используйте `@OptIn(ExperimentalMaterial3Api::class)` или собственную реализацию (как `BottomNavButton` в `LTVScaffold.kt`).
- **FFmpeg**: `FFmpegBridge` запускает нативный бинарь через `Runtime.exec()`. Бинарь лежит в `assets/ffmpeg/<abi>/ffmpeg`. Никаких JNI или AAR-зависимостей.
- **override suspend fun** в реализациях `TtsEngine`: всегда указывайте `: Unit` явно, иначе Kotlin не считает override корректным.
- **JSON в kotlinx.serialization**: для `JsonObject?.get(key)?.jsonPrimitive` используйте `if (p.isString) p.content else null` вместо extension-функций.
- **Regex со спецсимволами** (например, `\S\n`): используйте raw string `Regex("""[^\S\n]+""")`, иначе Kotlin выдаёт "Illegal escape".
- **strings.xml**: не дублируйте ключи — будет `Found item String/<name> more than one time` в `mergeDebugResources`.
- **Импорты в Kotlin**: только в начале файла. Если IDE вставил посреди — будет `imports are only allowed in the beginning of file`.
- **WAV read**: используйте `RandomAccessFile` с ручным little-endian чтением (`(b1 shl 8) or b0`). `DataInputStream.readShortLe()` есть, но на разных JVM-платформах ведёт себя по-разному в unit-тестах.
- **Room**: `exportSchema = false` обязательно, иначе KSP падает в CI.
- **ModelsScreen** (`ui/screens/models/ModelsScreen.kt`): показывает только модели, которые скачиваются и запускаются на Android. Серверные модели запрещены.
- **Архитектура TTS**: допустимы только `Local` (выполнение на телефоне) и `Cloud` (публичный API-провайдер). Отдельные пользовательские engine-host/server-host запрещены.
