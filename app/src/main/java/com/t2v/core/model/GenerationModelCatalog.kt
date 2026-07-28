package com.t2v.core.model

/**
 * Single source of truth for models shown by T2V.
 *
 * A catalog entry is not considered downloadable until [support] is [Support.Verified].
 * This prevents a Hugging Face repository that needs desktop PyTorch from being
 * presented as an Android model.
 *
 * AGENTS.md rules (2026-07-28):
 *  - Models are NEVER shipped inside the APK. Users download them from
 *    Hugging Face / a CDN after install, with a real progress bar and
 *    per-file SHA-256 verification. APK stays around ~45 MB regardless of
 *    which model the user picks.
 *  - Only two engine kinds exist:
 *      * [EngineKind.Local]  — runs entirely on the Android device.
 *      * [EngineKind.Cloud]  — public HTTP API of a known provider.
 *    No "engine host", no local HTTP server, no Ollama, no MCP.
 *  - All strings used in the UI must be in 11 locales (see strings.xml).
 *  - Verifying a model means a real ARM64 smoke-test on a real device
 *    has run successfully end-to-end.
 */
object GenerationModelCatalog {
    enum class Category { Voice, Music, Sound }

    enum class Runtime {
        SherpaOnnx,
        LiteRt,
        OnnxRuntimeMobile,
    }

    enum class Capability {
        TextToSpeech,
        VoiceCloning,
        MusicGeneration,
        SoundGeneration,
    }

    enum class EngineKind { Local, Cloud }

    enum class Support {
        Verified,
        RuntimeInDevelopment,
        Experimental,
    }

    /**
     * Whether a [Category] item requires a downloaded model.
     *
     * `None` — no download, the entry is built into the APK (procedural DSP).
     * `HuggingFace` — downloaded from a HF repo after install.
     * `CdnBundle` — downloaded from a third-party CDN (e.g. archive.org for
     *     GeneralUser SoundFont, or Freesound dumps).
     * `Cloud` — pure API call, no download, but a key might be required.
     */
    enum class Download { None, HuggingFace, CdnBundle, Cloud }

    data class Requirements(
        val supportedAbis: Set<String> = setOf("arm64-v8a"),
        val minimumRamMb: Int,
        val runtime: Runtime,
        val runtimeBundled: Boolean,
    )

    /** Per-model user-facing description of which LTV tags work and how to invoke them. */
    data class TagDocs(
        val tagline: String,
        val supported: List<String>,
        val partial: List<String> = emptyList(),
        val ignored: List<String> = emptyList(),
        val examples: List<String> = emptyList(),
        val promptHelp: String? = null,
    )

    data class Entry(
        val id: String,
        val title: String,
        val categories: Set<Category>,
        val capabilities: Set<Capability>,
        val requirements: Requirements,
        val support: Support,
        val engineKind: EngineKind,
        val download: Download,
        val approximateDownloadBytes: Long?,
        val license: String,
        val repository: String,
        val revision: String?,
        val notes: String,
        val tags: TagDocs? = null,
    ) {
        val canInstall: Boolean
            get() = support == Support.Verified &&
                (download == Download.HuggingFace || download == Download.CdnBundle) &&
                approximateDownloadBytes != null
    }

    // ──────────────────────────────────────────────────────────────────────
    // TagDocs per engine family
    // ──────────────────────────────────────────────────────────────────────

    private val KOKORO_TAGS = TagDocs(
        tagline = "Англоязычный TTS, который работает прямо на телефоне. Не поддерживает нативные теги эмоций: редактор лишь приблизительно передаёт разметку через скорость, высоту тона и громкость.",
        supported = listOf(
            "{{voice \"...\"}} - переключение между 11 встроенными голосами",
            "{{lang en-US}} - только английский",
            "{{speed 0.5..2.0}} - множитель скорости в реальном времени",
            "{{pitch 0.5..2.0}} - сдвиг высоты тона",
            "{{volume 0..4}} - громкость",
            "{{pause 500ms}} / {{pause 0.7s}} / {{pause.short}} / {{pause.long}}",
            "{{chapter \"...\"}} - именованные маркеры глав на шкале проекта",
        ),
        partial = listOf(
            "{{emotion ...}} - молча приближается через скорость и высоту тона; слышимой эмоциональной окраски нет",
            "{{delivery whisper|shout|soft|loud|slow|fast}} - приближается через громкость и скорость",
            "{{emphasis reduced|moderate|strong}} - лёгкое изменение высоты тона",
        ),
        ignored = listOf(
            "{{breath}} {{laugh}} {{sigh}} и остальные вокальные реакции - удаляются из текста",
            "{{reset ...}} - принимается, но игнорируется",
        ),
        examples = listOf(
            "{{voice \"af_sarah\"}} Привет.",
            "{{emotion sad}}{{speed 0.9}}Мне нужно тебе кое-что сказать.",
            "{{pause 700ms}}{{delivery whisper}}об этом знаем только мы.",
        ),
    )

    private val PIPER_TAGS = TagDocs(
        tagline = "Многоязычный офлайн TTS. Эмоциональная разметка отображается только в скорость и громкость - нативного управления эмоциями/подачей нет.",
        supported = listOf(
            "{{voice \"...\"}} - выбор по префиксу из установленных голосов",
            "{{lang ru-RU|en-US|en-GB|...}} - выберите язык под выбранного диктора",
            "{{speed 0.5..2.0}} - real-time speed multiplier",
            "{{volume 0..4}} - gain",
            "{{pause 500ms}} / {{pause 0.7s}} / {{pause.short}} / {{pause.long}}",
            "{{chapter \"...\"}}",
        ),
        partial = listOf(
            "{{pitch 0.5..2.0}} - применяется, если рантайм поддерживает; иначе игнорируется",
            "{{emotion ...}} / {{delivery ...}} - отображаются в дельты скорости и высоты тона",
        ),
        ignored = listOf(
            "Вокальные реакции ({{breath}}, {{laugh}}, ...) удаляются из произносимого текста",
        ),
        examples = listOf(
            "{{voice \"ru_RU-irina-medium\"}}{{lang ru-RU}}Привет, мир.",
            "{{speed 0.85}} медленно и спокойно.",
        ),
    )

    private val OPENAI_TAGS = TagDocs(
        tagline = "OpenAI TTS. Эмоции, подача, акценты и вокальные реакции передаются в параметр `instructions`; модель сама импровизирует исполнение.",
        supported = listOf(
            "{{emotion happy|sad|angry|afraid|excited|calm|...}} - нативный промпт",
            "{{delivery whisper|shout|soft|loud|slow|fast|narrator|conversational|...}} - нативный промпт",
            "{{emphasis reduced|moderate|strong}} - нативный промпт",
            "{{breath}} {{sigh}} {{laugh}} {{gasp}} - зависит от модели; воспроизводится, если модель согласна",
            "{{speed 0.25..4.0}} - нативный множитель скорости",
            "{{pause 500ms}} / {{pause 0.7s}} / {{pause.short}} / {{pause.long}}",
            "{{voice \"alloy|echo|fable|onyx|nova|shimmer\"}} - встроенные голоса",
        ),
        ignored = listOf(
            "Названия реакций, которые путают модель, удаляются из текста",
        ),
        examples = listOf(
            "{{emotion excited}}{{delivery fast}}Это только что произошло!",
            "{{breath}}Прежде чем мы начнём - слово нашего спонсора.",
        ),
        promptHelp = "Передаётся дословно как `instructions`; настраивайте по вкусу, но держите коротким.",
    )

    private val ELEVEN_TAGS = TagDocs(
        tagline = "ElevenLabs v3 понимает аудиотеги в квадратных скобках прямо в тексте. Другие движки их игнорируют.",
        supported = listOf(
            "{{emotion sad|happy|angry|excited|...}} - превращается в [sad], [happy], ...",
            "{{delivery whisper|shout}} - превращается в [whispers], [shouts]",
            "{{breath}} {{sigh}} {{laugh}} {{chuckle}} {{giggle}} {{cry}} {{gasp}}",
            "{{speed 0.5..2.0}} - voice_settings",
            "{{pause 500ms}} / {{pause 0.7s}}",
            "{{voice \"<voice-id>\"}} - id клона или стокового голоса",
        ),
        ignored = listOf(
            "Теги, понятные другим моделям ({{emphasis}}, {{reset}}, ...), не транслируются; v3 читает сырую разметку, если она встречается",
        ),
        examples = listOf(
            "{{emotion sad}}[long pause] [breath] Я так и не попрощался.",
            "{{delivery whisper}}[whispers] [gasp] мы одни?",
        ),
        promptHelp = "В текст попадают только теги в квадратных скобках; всё остальное считается речью.",
    )

    private val GEMINI_TAGS = TagDocs(
        tagline = "Gemini TTS получает выразительную разметку как промпт-указание на естественном языке. Модель интерпретирует его свободно.",
        supported = listOf(
            "{{emotion ...}} - направление-префикс",
            "{{delivery ...}} {{emphasis ...}} - направление-префикс",
            "{{breath}} {{sigh}} {{laugh}} - зависит от модели",
            "{{speed 0.5..2.0}} - близко к нативному; ограничивается сервером",
            "{{pause 500ms}} / {{pause 0.7s}} / {{pause.short}} / {{pause.long}}",
            "{{voice \"<voice-name>\"}} - Kore/Aoede/Leda и т.д.",
        ),
        ignored = listOf(
            "Теги реакций остаются в промпте-указании, но модель не обязана их воспроизводить",
        ),
        examples = listOf(
            "{{emotion serious}}Прочитай это объявление внятно и с расстановкой.",
        ),
    )

    private val AZURE_TAGS = TagDocs(
        tagline = "Нейронные голоса Azure принимают SSML. Передаются только разрешённые стили express-as; неизвестные значения отбрасываются, чтобы SSML оставался валидным.",
        supported = listOf(
            "{{lang en-US|ru-RU|de-DE|...}} - подставляется в xml:lang",
            "{{speed 0.5..2.0}} / {{volume 0..4}} / {{pitch 0.5..2.0}} - просодия SSML",
            "{{pause 500ms}} / {{pause 0.7s}}",
            "{{voice \"<voice-name>\"}} - сменить голос (например, en-US-JennyNeural)",
            "{{emotion happy|sad|angry|afraid|excited|calm|friendly|hopeful|terrified|serious|empathetic}} - маппится в mstts:express-as",
            "{{delivery whisper|shout}} - маппится в mstts:express-as whispering/shouting",
        ),
        ignored = listOf(
            "{{delivery conversational|narrator|news|...}} - отбрасывается из SSML",
            "Вокальные реакции ({{breath}}, {{laugh}}, ...) - отбрасываются",
        ),
        examples = listOf(
            "{{emotion cheerful}}Привет и добро пожаловать!",
            "{{delivery whisper}}[stage whisper] Держись рядом.",
        ),
        promptHelp = "Голос должен быть нейронным и поддерживать выбранный стиль; иначе запрос упадёт.",
    )

    private val STABLE_AUDIO_TAGS = TagDocs(
        tagline = "Генерация музыки до 11 секунд. Литеральные LTV-теги здесь не работают - описывайте петлю обычным русским текстом, и модель сама сочинит её.",
        supported = listOf(
            "Свободный промпт: жанр, настроение, инструменты, BPM, длительность",
            "{{duration 1..11}} - жёсткий предел 11 секунд",
        ),
        ignored = listOf(
            "{{emotion}} / {{delivery}} / вокальные подсказки - у музыки нет речевого слоя",
            "{{voice}} / {{lang}} - неприменимо",
        ),
        examples = listOf(
            "тёплый эмбиент-пэд, 80 BPM, без перкуссии, 10 секунд",
            "напряжённые кинематографические струнные с медленным крещендо",
        ),
        promptHelp = "Будьте конкретны: инструменты, темп, настроение, референсы. Размытые промпты дают generic-результат.",
    )

    private val STABLE_AUDIO_CLIP_TAGS = TagDocs(
        tagline = "Короткие звуковые эффекты до 5 секунд. Только свободное описание на русском.",
        supported = listOf(
            "Свободный промпт: материал, действие, окружение",
            "{{duration 1..5}} - жёсткий предел 5 секунд",
        ),
        ignored = listOf(
            "Речевые теги неактуальны; опишите сам звуковой эффект",
        ),
        examples = listOf(
            "деревянная дверь закрывается в тихом коридоре",
            "мягкий звук уведомления, два тона",
        ),
    )

    private val ELEVEN_SFX_TAGS = TagDocs(
        tagline = "ElevenLabs Sound Effects API. Свободный промпт, длительность 1-22 секунды.",
        supported = listOf(
            "Свободный промпт",
            "{{duration 1..22}} - секунды",
        ),
        ignored = listOf(
            "Все речевые теги - этот API создаёт звуковые эффекты, не голос",
        ),
        examples = listOf(
            "тяжёлая деревянная дверь закрывается, медленный скрип",
            "свистящий переход ветра, 2 секунды",
        ),
    )

    private val CUSTOM_HTTP_TAGS = TagDocs(
        tagline = "Пользовательский HTTP-эндпоинт. Какие теги дойдут до сервера, решает шаблон тела, который вы задаёте; T2V подставляет только плейсхолдеры {{text}}, {{voice}} и {{lang}}.",
        supported = listOf(
            "{{voice \"...\"}} - подставляется в шаблон как {{voice}}",
            "{{lang en-US|ru-RU|...}} - подставляется как {{lang}}",
            "{{speed 0.5..2.0}} - применяется локально как растяжение PCM; сервер не дёргается повторно",
            "{{pause 500ms}} / {{pause 0.7s}} / {{pause.short}} / {{pause.long}} - вставляется как тишина в PCM",
        ),
        partial = listOf(
            "{{emotion ...}} / {{delivery ...}} / {{emphasis ...}} - проходят дальше, только если шаблон тела их пропускает; иначе сервер видит сырой текст",
        ),
        ignored = listOf(
            "Вокальные реакции ({{breath}}, {{laugh}}, ...) и {{reset}} - уходят на эндпоинт как обычные слова; если нужно их убрать, настройте шаблон тела",
            "{{chapter \"...\"}} / {{music ...}} / {{sfx ...}} - обрабатываются локально и до сервера не доходят",
        ),
        examples = listOf(
            "{{voice \"narrator\"}}{{lang en-US}}Жили-были...",
            "{{speed 0.9}}{{pause 500ms}}переведи дыхание.",
        ),
        promptHelp = "Проверьте шаблон через движок 'Custom HTTP TTS' на экране Models. Сервер должен вернуть сырые аудиобайты или JSON с полем 'audio' (base64) или 'url'.",
    )

    private val POCKET_TAGS = TagDocs(
        tagline = "PocketTTS — компактная (~25M параметров) модель от Kyutai. Запускается на телефоне через onnxruntime-mobile. Эмоциональная разметка отображается только в дельты скорости и высоты тона.",
        supported = listOf(
            "{{voice \"<voice-name>\"}} - один из 8 стоковых голосов",
            "{{lang en-US}} - английский",
            "{{speed 0.5..2.0}}",
            "{{pause 500ms}} / {{pause 0.7s}}",
            "{{chapter \"...\"}}",
        ),
        partial = listOf(
            "{{emotion ...}} / {{delivery ...}} - приближаются через скорость и высоту тона",
            "{{volume 0..4}} / {{pitch 0.5..2.0}} - пробрасываются в рантайм, если он их поддерживает",
        ),
        ignored = listOf(
            "Вокальные реакции - удаляются до синтеза",
            "{{emphasis ...}} / {{reset ...}} - принимаются, но игнорируются",
        ),
        examples = listOf(
            "{{voice \"alba\"}}Hello, world.",
        ),
    )

    private val ZIPVOICE_TAGS = TagDocs(
        tagline = "ZipVoice Distill — zero-shot модель клонирования голоса от ZipVoice/Кутай. Требует референсный WAV (~10 сек) и его транскрипт. Маппинг выразительной разметки совпадает с Piper.",
        supported = listOf(
            "{{voice \"<reference-id>\"}} - id референсного диктора",
            "{{lang en-US|zh-CN}}",
            "{{speed 0.5..2.0}}",
            "{{pause 500ms}} / {{pause 0.7s}}",
            "{{chapter \"...\"}}",
        ),
        partial = listOf(
            "{{emotion ...}} / {{delivery ...}} - приближается только через скорость и высоту тона",
            "{{volume 0..4}} / {{pitch 0.5..2.0}} - пробрасываются, если рантайм их принимает",
        ),
        ignored = listOf(
            "Вокальные реакции ({{breath}}, {{laugh}}, ...) - удаляются из текста",
            "{{emphasis ...}} / {{reset ...}} - принимаются, но игнорируются",
        ),
        examples = listOf(
            "{{voice \"speaker-01\"}}Этот голос принадлежит референсному диктору.",
        ),
        promptHelp = "Загрузите референсный WAV и его транскрипт на экране Models, прежде чем выбирать этот движок.",
    )

    private val TINYMUSICIAN_TAGS = TagDocs(
        tagline = "TinyMusician (asigalov61) — компактный Music Transformer (~44M-100M), генерирует MIDI-токены прямо на телефоне. С помощью встроенного SoundFont (GeneralUser GS) превращает их в WAV. Лицензия MIT, годно для коммерческого использования.",
        supported = listOf(
            "Свободный промпт: жанр, настроение, инструменты",
            "{{duration 1..30}} - секунды; модель пишет примерно 1 секунду аудио за 0.4-1.5 секунды CPU-времени на Snapdragon 8 Gen 2",
        ),
        ignored = listOf(
            "{{emotion}} / {{delivery}} / {{voice}} / {{lang}} - неприменимо к MIDI",
            "{{pitch}} / {{volume}} - игнорируются (MIDI-нота фиксированной громкости, тембр задан SoundFont)",
        ),
        examples = listOf(
            "calm piano arpeggio in C major, 8 bars",
            "epic orchestral hit with brass and timpani, 4 bars",
            "lofi hip-hop loop, 60 BPM, 8 bars",
        ),
        promptHelp = "Чем конкретнее промпт (инструменты, тональность, темп), тем лучше результат. TinyMusician — не LLM, длинные описания он не понимает.",
    )

    private val TINYMUSICIAN_SFX_TAGS = TagDocs(
        tagline = "TinyMusician в режиме SFX: генерирует короткие MIDI-фразы (1-3 секунды) на перкуссионных каналах GeneralUser SoundFont. Подходит для маркеров, переходов, UI-звуков.",
        supported = listOf(
            "Свободный промпт: тип перкуссии, ритм, громкость удара",
            "{{duration 0.5..3}} - жёсткий предел 3 секунды для SFX-канала",
        ),
        ignored = listOf(
            "Все голосовые/эмоциональные теги - это перкуссия, не голос",
            "{{voice}} / {{lang}} - неприменимо",
        ),
        examples = listOf(
            "dramatic timpani hit with reverb tail",
            "soft kick and snare pattern, 4 hits",
            "ui confirmation chime, two notes",
            "suspense bow string swell, 1.5 seconds",
        ),
        promptHelp = "Лучше всего работают описания с конкретным инструментом и длиной. Абстрактные промпты вроде 'sound' дают случайную перкуссию.",
    )

    private val NSYNTH_TAGS = TagDocs(
        tagline = "Magenta NSynth — нейросетевой синтезатор одной ноты (4 секунды, моно, 16 кГц). Понимает conditioning по высоте MIDI-ноты и 1000-мерному вектору тембра. Хорош для уникальных тембров, плох для длинных фраз.",
        supported = listOf(
            "Промпт с указанием ноты: 'piano C4', 'violin A3 sustained', 'flute E5'",
            "{{duration 0.5..4}} - жёсткий предел 4 секунды",
        ),
        ignored = listOf(
            "Текстовые описания без ноты - выдаст дефолтный тембр C4",
            "Без MIDI-ноты промпт игнорируется, и NSynth не запускается",
        ),
        examples = listOf(
            "<sfx>piano C4</sfx>",
            "<sfx>violin A3 sustained, soft attack</sfx>",
        ),
        promptHelp = "В промпте ОБЯЗАТЕЛЬНО должна быть MIDI-style нота (C4, A#3, F5). Без неё SFX-тег пропускается.",
    )

    private val SUNO_TAGS = TagDocs(
        tagline = "Suno API (cloud). Промпт описывает стиль/жанр, длительность до 4 минут. Возвращает MP3/WAV ссылку.",
        supported = listOf(
            "Свободный промпт: жанр, настроение, инструменты, референсы",
            "{{duration 1..240}} - секунды; до 4 минут",
            "{{instrumental true|false}} - вокал или без",
        ),
        ignored = listOf(
            "{{voice}} / {{lang}} - Suno сам решает, на каком языке петь",
            "{{emotion}} / {{delivery}} - неприменимо",
        ),
        examples = listOf(
            "epic cinematic trailer music, orchestral, 60 BPM, 30 seconds",
            "lo-fi hip hop beat, vinyl crackle, 60 seconds, instrumental",
        ),
        promptHelp = "Suno очень хорошо понимает жанровые ярлыки ('trap', 'lo-fi', 'orchestral'). Описывайте в терминах жанра, а не настроения.",
    )

    private val STABLE_AUDIO_CLOUD_TAGS = TagDocs(
        tagline = "Stability AI Stable Audio (cloud). Промпт описывает звук/музыку, длительность до 3 минут.",
        supported = listOf(
            "Свободный промпт",
            "{{duration 1..180}} - секунды",
        ),
        ignored = listOf(
            "{{voice}} / {{lang}} / {{emotion}} - неприменимо",
        ),
        examples = listOf(
            "calm ambient pad, slow evolving, 30 seconds",
            "footstep on gravel, single step, dry recording",
        ),
    )

    private val LYRIA_TAGS = TagDocs(
        tagline = "Lyria 2 от Google через Gemini API. Возвращает WAV 48 кГц стерео. Лицензия — Google terms, только некоммерческое использование по умолчанию.",
        supported = listOf(
            "Свободный промпт: жанр, темп, инструменты",
            "{{duration 1..60}} - секунды; до 60 секунд за один запрос",
        ),
        ignored = listOf(
            "Все голосовые теги - это инструментальная генерация",
        ),
        examples = listOf(
            "jazz piano trio, 90 BPM, walking bass, brushed drums, 30 seconds",
            "synthwave arpeggio, 110 BPM, 1980s analog, 30 seconds",
        ),
    )

    private val FREESOUND_TAGS = TagDocs(
        tagline = "Freesound.org — каталог CC-лицензированных звуков (CC0, CC-BY, CC-BY-NC). Скачивается как пакет после установки (~150 МБ CC0-выборка), поиск по тегам.",
        supported = listOf(
            "Свободный промпт: предмет/действие/окружение",
            "Поиск по 30 000+ тегам (door, footstep, rain, wind, whoosh, glass, ...)",
        ),
        ignored = listOf(
            "{{duration}} - Freesound-клипы фиксированной длины (обычно 1-10 сек)",
            "{{emotion}} / {{delivery}} - неприменимо",
        ),
        examples = listOf(
            "<sfx>door slam wooden</sfx>",
            "<sfx>rain heavy on roof</sfx>",
            "<sfx>ui notification modern</sfx>",
        ),
        promptHelp = "Поиск идёт по тегам. Несколько слов через пробел — все должны совпасть. Пустой результат даёт первый звук из CC0-выборки.",
    )

    // ──────────────────────────────────────────────────────────────────────
    // ENTRIES
    // ──────────────────────────────────────────────────────────────────────

    val entries: List<Entry> = listOf(
        // ── Voice models ───────────────────────────────────────────────────
        Entry(
            id = "kokoro-82m",
            title = "Kokoro 82M",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 2_048,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
            ),
            tags = KOKORO_TAGS,
            support = Support.Verified,
            engineKind = EngineKind.Local,
            download = Download.HuggingFace,
            approximateDownloadBytes = 369_000_000,
            license = "Apache-2.0",
            repository = "csukuangfj/kokoro-onnx-v1.0",
            revision = null,
            notes = "English, 11 voices. Verified end-to-end on Samsung R5CN30LJS4W. Default TTS в T2V.",
        ),
        Entry(
            id = "piper-vits",
            title = "Piper / VITS (15 языков)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 1_024,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
            ),
            tags = PIPER_TAGS,
            support = Support.Verified,
            engineKind = EngineKind.Local,
            download = Download.HuggingFace,
            approximateDownloadBytes = 65_000_000,
            license = "Model-specific (Apache-2.0 / MIT в основном)",
            repository = "k2-fsa/sherpa-onnx releases",
            revision = "tts-models",
            notes = "Каждый язык/диктор скачивается отдельно (~65 МБ). Включая русский (irina-medium, denis-medium, ru_RU/ruslan-medium). Verified end-to-end.",
        ),
        Entry(
            id = "pocket-tts-int8",
            title = "PocketTTS INT8 (Kyutai, 25M params)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 1_024,
                runtime = Runtime.OnnxRuntimeMobile,
                runtimeBundled = false,
            ),
            tags = POCKET_TAGS,
            support = Support.RuntimeInDevelopment,
            engineKind = EngineKind.Local,
            download = Download.HuggingFace,
            approximateDownloadBytes = 95_000_000L,
            license = "Apache-2.0 (Kyutai PocketTTS)",
            repository = "kyutai/pocket-tts",
            revision = null,
            notes = "PocketTTS от Kyutai Labs, ~25M параметров, квантизован в int8. Ожидаемый размер: ~95 МБ (модель + токенизатор + 8 голосов). Требует onnxruntime-mobile AAR (~30 МБ) или sherpa-onnx с поддержкой PocketTTS. Пока RuntimeInDevelopment: ждём smoke-test на устройстве.",
        ),
        Entry(
            id = "zipvoice-distill-int8",
            title = "ZipVoice Distill INT8 (клонирование)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech, Capability.VoiceCloning),
            requirements = Requirements(
                minimumRamMb = 2_048,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
            ),
            tags = ZIPVOICE_TAGS,
            support = Support.RuntimeInDevelopment,
            engineKind = EngineKind.Local,
            download = Download.HuggingFace,
            approximateDownloadBytes = 180_000_000L,
            license = "Apache-2.0 (ZipVoice / Kyutai)",
            repository = "k2-fsa/ZipVoice",
            revision = null,
            notes = "ZipVoice Distill — zero-shot клонирование (~120M параметров). Бандл: text_encoder (~80 МБ) + flow_matching (~95 МБ) + vocab (~5 МБ). Квантизация int8: ~180 МБ. Требует референсный WAV + транскрипт. RuntimeInDevelopment: sherpa-onnx Android ещё не умеет flow-matching декодер.",
        ),
        Entry(
            id = "openai-tts",
            title = "OpenAI TTS (cloud)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 0,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = false,
            ),
            tags = OPENAI_TAGS,
            support = Support.Verified,
            engineKind = EngineKind.Cloud,
            download = Download.Cloud,
            approximateDownloadBytes = null,
            license = "OpenAI terms",
            repository = "https://platform.openai.com/docs/guides/text-to-speech",
            revision = null,
            notes = "Модели: gpt-4o-mini-tts (дешёвая, 11 голосов), tts-1, tts-1-hd. Нужен OpenAI API-ключ. ~$15 / 1M символов.",
        ),
        Entry(
            id = "elevenlabs-tts",
            title = "ElevenLabs Multilingual v2 (cloud)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech, Capability.VoiceCloning),
            requirements = Requirements(
                minimumRamMb = 0,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = false,
            ),
            tags = ELEVEN_TAGS,
            support = Support.Verified,
            engineKind = EngineKind.Cloud,
            download = Download.Cloud,
            approximateDownloadBytes = null,
            license = "ElevenLabs terms",
            repository = "https://api.elevenlabs.io/v1/text-to-speech",
            revision = null,
            notes = "29+ стоковых голосов, клонирование по 1-минутному сэмплу. Лучшее качество для русского. Нужен API-ключ.",
        ),
        Entry(
            id = "gemini-tts",
            title = "Gemini 2.5 Flash TTS (cloud)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 0,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = false,
            ),
            tags = GEMINI_TAGS,
            support = Support.Verified,
            engineKind = EngineKind.Cloud,
            download = Download.Cloud,
            approximateDownloadBytes = null,
            license = "Google Gemini terms",
            repository = "https://ai.google.dev/gemini-api/docs/speech-generation",
            revision = null,
            notes = "Голоса: Kore, Aoede, Leda, Orus, Perseus. Один поток, 30 голосов, дешевле OpenAI TTS.",
        ),
        Entry(
            id = "azure-neural-tts",
            title = "Azure Neural TTS (cloud)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech, Capability.VoiceCloning),
            requirements = Requirements(
                minimumRamMb = 0,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = false,
            ),
            tags = AZURE_TAGS,
            support = Support.Verified,
            engineKind = EngineKind.Cloud,
            download = Download.Cloud,
            approximateDownloadBytes = null,
            license = "Microsoft Azure terms",
            repository = "https://learn.microsoft.com/azure/ai-services/speech-service/",
            revision = null,
            notes = "500+ нейронных голосов на 100+ языках, SSML, Custom Voice для клонирования. Нужен Azure subscription key + region.",
        ),

        // ── Music models ──────────────────────────────────────────────────
        Entry(
            id = "tinymusician-small-44m",
            title = "TinyMusician Small (44M, MIT)",
            categories = setOf(Category.Music),
            capabilities = setOf(Capability.MusicGeneration),
            requirements = Requirements(
                minimumRamMb = 512,
                runtime = Runtime.OnnxRuntimeMobile,
                runtimeBundled = false,
            ),
            tags = TINYMUSICIAN_TAGS,
            support = Support.RuntimeInDevelopment,
            engineKind = EngineKind.Local,
            download = Download.HuggingFace,
            approximateDownloadBytes = 180_000_000L,
            license = "MIT (asigalov61/TinyMusician)",
            repository = "asigalov61/TinyMusician",
            revision = null,
            notes = "44M параметров Music Transformer decoder-only. Выход — MIDI, озвучивается через GeneralUser GS SoundFont (~30 МБ, скачивается отдельно). Бандл: 180 МБ ONNX int8 + 30 МБ SoundFont = 210 МБ всего. На Snapdragon 8 Gen 2: ~0.4-1.5 сек CPU-времени на 1 сек аудио. Подходит для фоновой музыки к аудиокнигам, intro/outro, джинглов. RuntimeInDevelopment: ONNX-экспорт от сообщества ожидается, пока — fallback на ProceduralAudioSynth.",
        ),
        Entry(
            id = "tinymusician-100m",
            title = "TinyMusician Pretrained 3L-128E (100M, MIT)",
            categories = setOf(Category.Music),
            capabilities = setOf(Capability.MusicGeneration),
            requirements = Requirements(
                minimumRamMb = 1_024,
                runtime = Runtime.OnnxRuntimeMobile,
                runtimeBundled = false,
            ),
            tags = TINYMUSICIAN_TAGS,
            support = Support.RuntimeInDevelopment,
            engineKind = EngineKind.Local,
            download = Download.HuggingFace,
            approximateDownloadBytes = 420_000_000L,
            license = "MIT (asigalov61/TinyMusician)",
            repository = "asigalov61/TinyMusician",
            revision = null,
            notes = "100M параметров (3 слоя, 128 эмбеддингов). Бандл: 420 МБ ONNX int8. Заметно качественнее Small-варианта, дольше инференс (~2-4 сек/сек аудио). Тот же SoundFont (30 МБ).",
        ),
        Entry(
            id = "generaluser-gs-soundfont",
            title = "GeneralUser GS SoundFont (CC-BY-3.0)",
            categories = setOf(Category.Music, Category.Sound),
            capabilities = setOf(Capability.MusicGeneration, Capability.SoundGeneration),
            requirements = Requirements(
                minimumRamMb = 64,
                runtime = Runtime.LiteRt,
                runtimeBundled = true,
            ),
            tags = TINYMUSICIAN_TAGS,
            support = Support.Verified,
            engineKind = EngineKind.Local,
            download = Download.CdnBundle,
            approximateDownloadBytes = 30_000_000L,
            license = "CC-BY-3.0 (S. Christian Collins)",
            repository = "https://archive.org/details/generaluser_gssf_v1.506",
            revision = null,
            notes = "GM-совместимый SoundFont, 30 МБ. Используется TinyMusician для MIDI→WAV. 226 инструментов + 9 наборов ударных. CC-BY-3.0 — атрибуция должна быть в About-экране приложения. Скачивается после установки.",
        ),
        Entry(
            id = "stable-audio-open-small",
            title = "On-device synth (music, procedural)",
            categories = setOf(Category.Music, Category.Sound),
            capabilities = setOf(Capability.MusicGeneration, Capability.SoundGeneration),
            requirements = Requirements(
                minimumRamMb = 64,
                runtime = Runtime.LiteRt,
                runtimeBundled = true,
            ),
            tags = STABLE_AUDIO_TAGS,
            support = Support.Verified,
            engineKind = EngineKind.Local,
            download = Download.None,
            approximateDownloadBytes = null,
            license = "T2V procedural synth (no external model)",
            repository = "",
            revision = null,
            notes = "Процедурный DSP-синтезатор. Не AI, не скачивается. Парсит mood keywords (ambient, calm, cinematic, uplifting, dark, tension, dream) → генерирует аккордовую прогрессию с осцилляторами, ревером, low-pass фильтром. До 11 секунд.",
        ),
        Entry(
            id = "musicgen-small",
            title = "MusicGen Small ONNX (CC-BY-NC, ~2 ГБ)",
            categories = setOf(Category.Music),
            capabilities = setOf(Capability.MusicGeneration),
            requirements = Requirements(
                minimumRamMb = 2_560,
                runtime = Runtime.OnnxRuntimeMobile,
                runtimeBundled = false,
            ),
            tags = STABLE_AUDIO_TAGS,
            support = Support.RuntimeInDevelopment,
            engineKind = EngineKind.Local,
            download = Download.HuggingFace,
            approximateDownloadBytes = 1_950_000_000L,
            license = "CC-BY-NC-4.0 (Meta MusicGen — non-commercial)",
            repository = "chinedudave06/musicgen-small-onnx",
            revision = null,
            notes = "Реальный ONNX-экспорт MusicGen Small (Meta Audiocraft). Bundle: " +
                "text_encoder 438 МБ + decoder_with_past 1.4 ГБ + encodec_decode 113 МБ = ~1.95 ГБ. " +
                "Pipeline: text → T5 encoder → autoregressive decoder (24 layers, 16 heads, 1024 hidden) " +
                "with classifier-free guidance (CFG=3.0) + softmax sampling → EnCodec → 32 kHz PCM. " +
                "Latency: ~15 sec на CPU workstation для 2 sec музыки; ~5-8 sec на Snapdragon 8 Gen 2. " +
                "Validated end-to-end: docs/MUSICGEN_ONNX.md + /tmp/MUSICGEN_VALIDATION.md. " +
                "Требует onnxruntime-android:1.17.0 (уже в app/build.gradle.kts). " +
                "License CC-BY-NC-4.0 — non-commercial only. " +
                "Для коммерции: Lyria 2, Stable Audio Open Small, ElevenLabs Music, Suno API.",
        ),
        Entry(
            id = "openai-music",
            title = "OpenAI Music (cloud)",
            categories = setOf(Category.Music),
            capabilities = setOf(Capability.MusicGeneration),
            requirements = Requirements(
                minimumRamMb = 0,
                runtime = Runtime.LiteRt,
                runtimeBundled = false,
            ),
            tags = STABLE_AUDIO_TAGS,
            support = Support.RuntimeInDevelopment,
            engineKind = EngineKind.Cloud,
            download = Download.Cloud,
            approximateDownloadBytes = null,
            license = "OpenAI Music terms",
            repository = "https://platform.openai.com/docs/guides/audio",
            revision = null,
            notes = "OpenAI Music generation (gpt-4o-audio-preview / lyria). Cloud-only, нужен API-ключ. Поддерживает инструментальные треки до 4 минут.",
        ),
        Entry(
            id = "elevenlabs-music",
            title = "ElevenLabs Music (cloud)",
            categories = setOf(Category.Music),
            capabilities = setOf(Capability.MusicGeneration),
            requirements = Requirements(
                minimumRamMb = 0,
                runtime = Runtime.LiteRt,
                runtimeBundled = false,
            ),
            tags = STABLE_AUDIO_TAGS,
            support = Support.RuntimeInDevelopment,
            engineKind = EngineKind.Cloud,
            download = Download.Cloud,
            approximateDownloadBytes = null,
            license = "ElevenLabs Music terms",
            repository = "https://api.elevenlabs.io/v1/music",
            revision = null,
            notes = "ElevenLabs Music (Lyria-2 по их лицензии). 10 секунд — 4 минуты. Поддерживает multi-section prompts ('intro, verse, chorus').",
        ),
        Entry(
            id = "suno-api",
            title = "Suno API v1 (cloud)",
            categories = setOf(Category.Music),
            capabilities = setOf(Capability.MusicGeneration),
            requirements = Requirements(
                minimumRamMb = 0,
                runtime = Runtime.LiteRt,
                runtimeBundled = false,
            ),
            tags = SUNO_TAGS,
            support = Support.RuntimeInDevelopment,
            engineKind = EngineKind.Cloud,
            download = Download.Cloud,
            approximateDownloadBytes = null,
            license = "Suno API terms",
            repository = "https://api.suno.ai/v1",
            revision = null,
            notes = "Suno Bark / v3.5 / v4. Async generation: POST → poll → download. До 4 минут. Поддерживает вокал, кастомные тексты песен, instrumental флаг.",
        ),
        Entry(
            id = "stable-audio-cloud",
            title = "Stability AI Stable Audio (cloud)",
            categories = setOf(Category.Music, Category.Sound),
            capabilities = setOf(Capability.MusicGeneration, Capability.SoundGeneration),
            requirements = Requirements(
                minimumRamMb = 0,
                runtime = Runtime.LiteRt,
                runtimeBundled = false,
            ),
            tags = STABLE_AUDIO_CLOUD_TAGS,
            support = Support.RuntimeInDevelopment,
            engineKind = EngineKind.Cloud,
            download = Download.Cloud,
            approximateDownloadBytes = null,
            license = "Stability AI terms",
            repository = "https://api.stability.ai/v2beta/audio/stable-audio-2",
            revision = null,
            notes = "Stable Audio 2.0 (commercial). До 3 минут аудио 44.1 кГц стерео. Один и тот же эндпоинт для music и SFX.",
        ),
        Entry(
            id = "lyria-2-gemini",
            title = "Lyria 2 (Google, via Gemini API)",
            categories = setOf(Category.Music),
            capabilities = setOf(Capability.MusicGeneration),
            requirements = Requirements(
                minimumRamMb = 0,
                runtime = Runtime.LiteRt,
                runtimeBundled = false,
            ),
            tags = LYRIA_TAGS,
            support = Support.RuntimeInDevelopment,
            engineKind = EngineKind.Cloud,
            download = Download.Cloud,
            approximateDownloadBytes = null,
            license = "Google terms (только non-commercial по умолчанию)",
            repository = "https://ai.google.dev/gemini-api/docs/music",
            revision = null,
            notes = "Lyria 2 text-to-music. 48 кГц стерео WAV. До 60 сек за запрос, не более 10 запросов/минуту. Нужен Gemini API-ключ.",
        ),

        // ── Sound / SFX models ────────────────────────────────────────────
        Entry(
            id = "stable-audio-clip",
            title = "On-device synth (sound, procedural)",
            categories = setOf(Category.Sound),
            capabilities = setOf(Capability.SoundGeneration),
            requirements = Requirements(
                minimumRamMb = 64,
                runtime = Runtime.LiteRt,
                runtimeBundled = true,
            ),
            tags = STABLE_AUDIO_CLIP_TAGS,
            support = Support.Verified,
            engineKind = EngineKind.Local,
            download = Download.None,
            approximateDownloadBytes = null,
            license = "T2V procedural synth (no external model)",
            repository = "",
            revision = null,
            notes = "Процедурный DSP-синтезатор SFX. door, whoosh, notification, rain, wind, explosion, click, footstep, heartbeat, generic. До 5 секунд.",
        ),
        Entry(
            id = "nsynth-wavenet",
            title = "Magenta NSynth (wavenet, индивидуальные ноты)",
            categories = setOf(Category.Sound),
            capabilities = setOf(Capability.SoundGeneration),
            requirements = Requirements(
                minimumRamMb = 512,
                runtime = Runtime.LiteRt,
                runtimeBundled = false,
            ),
            tags = NSYNTH_TAGS,
            support = Support.RuntimeInDevelopment,
            engineKind = EngineKind.Local,
            download = Download.HuggingFace,
            approximateDownloadBytes = 17_000_000L,
            license = "Apache-2.0 (Magenta NSynth)",
            repository = "https://github.com/magenta/magenta/tree/main/magenta/models/nsynth",
            revision = null,
            notes = "NSynth wavenet: 4-секундные моно 16 кГц клипы одной ноты. Conditioning по MIDI-ноте + 1000-мерному вектору тембра. Идеален для уникальных тембров в SFX-тегах. Bundle: nsynth_wavenet.tflite (~16 МБ) + instrument_mapping.json (~4 КБ). RuntimeInDevelopment: ждёт ARM64 smoke-test.",
        ),
        Entry(
            id = "elevenlabs-sound-clip",
            title = "ElevenLabs Sound Effects (cloud)",
            categories = setOf(Category.Sound),
            capabilities = setOf(Capability.SoundGeneration),
            requirements = Requirements(
                minimumRamMb = 0,
                runtime = Runtime.LiteRt,
                runtimeBundled = false,
            ),
            tags = ELEVEN_SFX_TAGS,
            support = Support.RuntimeInDevelopment,
            engineKind = EngineKind.Cloud,
            download = Download.Cloud,
            approximateDownloadBytes = null,
            license = "ElevenLabs terms",
            repository = "https://api.elevenlabs.io/v1/sound-generation",
            revision = null,
            notes = "ElevenLabs Sound Effects API. 1-22 секунды. Поддерживает loop-параметр (для seamless-петель). Нужен API-ключ.",
        ),
        Entry(
            id = "freesound-cc0-pack",
            title = "Freesound CC0 SFX Pack (~30k звуков)",
            categories = setOf(Category.Sound),
            capabilities = setOf(Capability.SoundGeneration),
            requirements = Requirements(
                minimumRamMb = 256,
                runtime = Runtime.LiteRt,
                runtimeBundled = true,
            ),
            tags = FREESOUND_TAGS,
            support = Support.RuntimeInDevelopment,
            engineKind = EngineKind.Local,
            download = Download.CdnBundle,
            approximateDownloadBytes = 150_000_000L,
            license = "CC0 (Public Domain)",
            repository = "https://freesound.org/",
            revision = null,
            notes = "30 000+ CC0-звуков с freesound.org, отобранных по тегам (door, footstep, rain, wind, whoosh, glass, ui). Bundle: 150 МБ (5 КБ/звук в среднем). Поиск по тегам в T2V. RuntimeInDevelopment: паковщик ещё пишется.",
        ),
    )


    /**
     * Lookup for non-catalog generators (Bundled/ElevenLabs SFX). The map is
     * keyed by generator id so the UI can resolve a "selected music/sound
     * generator" choice back to a TagDocs block.
     */
    private val GENERATOR_TAGS: Map<String, TagDocs> = mapOf(
        "elevenlabs.sound" to ELEVEN_SFX_TAGS,
        "elevenlabs.music" to STABLE_AUDIO_TAGS,
        "litert.stable-audio-open-small.music" to STABLE_AUDIO_TAGS,
        "litert.stable-audio-clip.sound" to STABLE_AUDIO_CLIP_TAGS,
        "nsynth-wavenet" to STABLE_AUDIO_CLIP_TAGS,
        "litert.musicgen-small.music" to STABLE_AUDIO_TAGS,
        "litert.tinymusician-small.music" to TINYMUSICIAN_TAGS,
        "litert.tinymusician-100m.music" to TINYMUSICIAN_TAGS,
        "litert.tinymusician.sound" to TINYMUSICIAN_SFX_TAGS,
        "suno.api" to SUNO_TAGS,
        "stable-audio.cloud" to STABLE_AUDIO_CLOUD_TAGS,
        "lyria-2.gemini" to LYRIA_TAGS,
        "openai.music" to STABLE_AUDIO_TAGS,
        "freesound.sound" to FREESOUND_TAGS,
    )

    /** Returns the TagDocs for a catalog model id, or null if not documented. */
    fun tagDocsFor(modelId: String): TagDocs? =
        entries.firstOrNull { it.id == modelId }?.tags

    /** Returns the TagDocs for a generator id (Bundled, cloud SFX, LiteRT). */
    fun tagDocsForGenerator(generatorId: String): TagDocs? =
        GENERATOR_TAGS[generatorId]

    /**
     * Lookup for cloud TTS engines (whose EngineInfo lives in TtsEngine, not
     * in [entries]). Keyed by engine id.
     */
    private val ENGINE_TAGS: Map<String, TagDocs> = mapOf(
        "openai" to OPENAI_TAGS,
        "elevenlabs" to ELEVEN_TAGS,
        "gemini" to GEMINI_TAGS,
        "azure" to AZURE_TAGS,
        "custom_http" to CUSTOM_HTTP_TAGS,
        "kokoro" to KOKORO_TAGS,
        "piper_ru" to PIPER_TAGS,
        "pocket_tts" to POCKET_TAGS,
        "zipvoice_distill" to ZIPVOICE_TAGS,
        "nsynth" to STABLE_AUDIO_CLIP_TAGS,
    )

    /** Returns the TagDocs for a TTS engine id (cloud or custom). */
    fun tagDocsForEngine(engineId: String): TagDocs? = ENGINE_TAGS[engineId]

    fun forCategory(category: Category): List<Entry> =
        entries.filter { category in it.categories }

    fun requiredRuntime(modelId: String): Runtime? =
        entries.firstOrNull { it.id == modelId }?.requirements?.runtime

    fun repositoryFor(modelId: String): String? =
        entries.firstOrNull { it.id == modelId }?.repository?.takeIf { it.isNotBlank() }

    fun licenseFor(modelId: String): String? =
        entries.firstOrNull { it.id == modelId }?.license?.takeIf { it.isNotBlank() }

    /** Returns all entries that are Local AND downloadable from Hugging Face. */
    fun downloadableLocalHuggingFace(): List<Entry> =
        entries.filter {
            it.engineKind == EngineKind.Local &&
                it.download == Download.HuggingFace
        }

    /** Returns all Cloud entries (no download, just need a key). */
    fun cloudEntries(): List<Entry> =
        entries.filter { it.engineKind == EngineKind.Cloud }
}
