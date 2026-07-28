# Расширение T2V

## Добавить новый TTS-движок

1. Создайте `app/src/main/java/com/t2v/tts/engines/MyEngine.kt`:

```kotlin
class MyTtsEngine(
    private val apiKey: String,
) : AbstractHttpEngine(ENGINE_INFO) {
    override fun endpoint() = "https://api.example.com/tts"
    override fun headers() = mapOf("Authorization" to "Bearer $apiKey")
    override fun buildBody(req: TtsRequest) = buildJsonObject {
        put("text", req.text)
        put("voice", req.voice.voice)
    }
    override suspend fun listVoices() = listOf(
        VoiceInfo("default", "Default", "en", engineId = ENGINE_INFO.id),
    )
    companion object {
        val ENGINE_INFO = EngineInfo(
            id = "my_engine",
            displayName = "My Engine",
            kind = EngineInfo.EngineKind.Cloud,
            requiresApiKey = true,
        )
    }
}
```

2. Зарегистрируйте в `EngineRegistry`:

```kotlin
"my_engine" -> MyTtsEngine(
    apiKey = cfg["apiKey"] ?: return null,
),
```

3. Добавьте в `allEngineInfos()`:

```kotlin
add(MyTtsEngine.ENGINE_INFO)
```

4. Добавьте API-ключ в `SettingsRepository.Keys` и UI.

## Добавить новый генератор music/sound

1. Создайте `app/src/main/java/com/t2v/generators/impl/MyGenerator.kt`:

```kotlin
class MyMusicGenerator(private val context: Context) : Generator {
    override val id: String = "litert.my-music"
    override val displayName: String = "My Music (on-device)"
    override val category: GeneratorCategory = GeneratorCategory.Music
    override fun isAvailable(): Boolean = /* проверка модели */ true
    override suspend fun generate(request: GeneratorRequest): GeneratorResult {
        // 1. Загрузить модель (LiteRT/SherpaOnnx)
        // 2. Подготовить входной тензор
        // 3. Запустить inference
        // 4. Закодировать выход в WAV
    }
}
```

2. Зарегистрируйте в `GeneratorRegistry.all()`:

```kotlin
add(MyMusicGenerator(appContext))
```

3. Добавьте запись в `GenerationModelCatalog.entries` с `support=Verified`
   и `repository=<author>/<hf-model>`.

4. Если модель скачивается — добавьте в `HuggingFaceRepository.install()`
   логику обработки специфичных файлов.

5. Проведите ARM64 smoke-test на устройстве и вызовите
   `LiteRtModelInstaller.markSmokeTested(plan)`.

## Добавить новую локаль

1. Создайте `app/src/main/res/values-XX/strings.xml`.
2. Добавьте язык в `app/build.gradle.kts`:
   ```kotlin
   resourceConfigurations += listOf("en", "ru", ..., "xx")
   ```
3. Переведите строки (включая новые из текущей сессии:
   `markup_music`, `markup_sfx`, `settings_debug_*`, `models_download_button`).

## Добавить новый тип разметки

1. Добавьте `MarkupCommand.*` в `core/markup/LTVMarkupParser.kt`.
2. Добавьте правило в `parseCommand()`.
3. Добавьте состояние в `MarkupState`.
4. Добавьте подсветку в `ui/markup/MarkupHighlighter.kt`.
5. Добавьте кнопку в `ui/components/MarkupToolbar.kt`.
6. Напишите тест в `app/src/test/.../markup/`.

## Добавить XML-тег для аудио-вставки (`<music>` / `<sfx>`)

1. Добавьте `AudioTag.Category` enum в `core/markup/` (если нужен новый тип).
2. Расширьте `LTVMarkupParser.extractAudioTags()` для парсинга тега.
3. Расширьте `LTVMarkupParser.parseSpans()` чтобы рвать voice-чанк в теге.
4. Обновите `AudioTagInserter.insert()` для нового типа.
5. Создайте `Generator` для нового типа.
6. Добавьте `MarkupHighlighter`-правило для подсветки.
7. Добавьте `MarkupToolbar`-кнопку.
8. Локализуйте в 11 values-*.

## Добавить новый шаг в пайплайн

1. Создайте класс в `worker/` (например, `PostProcessStep.kt`).
2. Вызовите его в `GenerationPipeline.generate()` после `Encoding`.
3. Сохраните результат в БД.

## Добавить новый экран

1. Создайте `ui/screens/myscreen/MyScreen.kt` + `MyViewModel.kt`.
2. Добавьте `composable(Routes.MyScreen)` в `LTVNavHost.kt`.
3. Добавьте маршрут в `Routes`.
4. Добавьте иконку в `LTVScaffold` (если нужен bottom nav).

## Добавить новый источник текста

1. Создайте класс в `core/text/MySource.kt` (например, RSS, EPUB).
2. Реализуйте `Source` интерфейс с методом `import()`.
3. Добавьте UI-кнопку в `EditorScreen`.

## Добавить модель в каталог

1. Найдите готовую модель на Hugging Face (ONNX или TFLite, Android-совместимая).
2. Добавьте запись в `GenerationModelCatalog.entries`:
   ```kotlin
   Entry(
       id = "my-model",
       title = "My Model",
       categories = setOf(Category.Music),
       capabilities = setOf(Capability.MusicGeneration),
       requirements = Requirements(
           minimumRamMb = 2048,
           runtime = Runtime.SherpaOnnx,
           runtimeBundled = true,
       ),
       support = Support.Verified,
       approximateDownloadBytes = 250_000_000,
       license = "Apache-2.0",
       repository = "author/my-model",
       revision = "abc123",
       notes = "TFLite variant",
       tags = MY_TAGS,
   )
   ```
3. Реализуйте `Generator` или `TtsEngine`.
4. Проведите ARM64 smoke-test на устройстве.
5. Обновите `docs/AI_HANDOFF.md` и `CHANGELOG.md`.
