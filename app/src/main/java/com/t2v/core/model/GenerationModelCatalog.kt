package com.t2v.core.model

/**
 * Single source of truth for models shown by T2V.
 *
 * A catalog entry is not considered downloadable until [support] is [Support.Verified].
 * This prevents a Hugging Face repository that needs desktop PyTorch from being
 * presented as an Android model.
 */
object GenerationModelCatalog {
    enum class Category { Voice, Music, Sound }

    enum class Runtime {
        SherpaOnnx,
        LiteRt,
    }

    enum class Capability {
        TextToSpeech,
        VoiceCloning,
        MusicGeneration,
        SoundGeneration,
    }

    enum class Support {
        Verified,
        RuntimeInDevelopment,
        Experimental,
    }

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
        val approximateDownloadBytes: Long?,
        val license: String,
        val repository: String,
        val revision: String?,
        val notes: String,
        val tags: TagDocs? = null,
        /** BCP-47 language of a voice model ("" for non-voice entries). */
        val language: String = "",
        /**
         * True when installing must download every repository file (e.g. a
         * Piper/VITS voice whose `espeak-ng-data` support files have no
         * extension and would otherwise be filtered out by the weight/support
         * heuristics). The weight+support branch below is then bypassed.
         */
        val downloadAllFiles: Boolean = false,
    ) {
        val canInstall: Boolean
            get() = support == Support.Verified && approximateDownloadBytes != null
    }

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

    private val MUSIC_GEN_TAGS = TagDocs(
        tagline = "MusicGen через LiteRT — настоящая AI-генерация музыки на устройстве. Модель ещё не прошла ARM64 smoke-test, поэтому карточка отображается, но не выбирается.",
        supported = listOf(
            "Свободный промпт: жанр, инструменты, настроение, BPM",
            "{{duration 1..30}} - длительность в секундах",
        ),
        ignored = listOf(
            "{{emotion}} / {{delivery}} / вокальные подсказки - у музыки нет речевого слоя",
        ),
        examples = listOf(
            "uplifting synth-pop loop, 128 BPM, analog bass",
            "dark ambient drone with slow filter sweep",
        ),
        promptHelp = "Конкретность решает: инструменты, темп, настроение, стиль.",
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
        tagline = "PocketTTS пока не проверен на Android. Считайте маппинг тегов заглушкой до смоук-теста на устройстве.",
        supported = listOf(
            "{{voice \"...\"}} - id диктора из каталога",
            "{{lang en-US}}",
            "{{speed 0.5..2.0}}",
            "{{pause 500ms}} / {{pause 0.7s}}",
        ),
        partial = listOf(
            "{{emotion ...}} / {{delivery ...}} - отображаются только в дельты скорости и высоты тона",
        ),
        ignored = listOf(
            "Вокальные реакции - удаляются до синтеза",
        ),
        examples = listOf(
            "{{voice \"default\"}}Привет.",
        ),
    )

    private val ZIPVOICE_TAGS = TagDocs(
        tagline = "ZipVoice Distill - zero-shot модель клонирования голоса. Маппинг выразительной разметки совпадает с Piper, пока Android-рантайм не получит настоящую фонемную обработку.",
        supported = listOf(
            "{{voice \"<reference-id>\"}} - id референсного диктора",
            "{{lang en-US}}",
            "{{speed 0.5..2.0}}",
            "{{pause 500ms}} / {{pause 0.7s}}",
            "{{chapter \"...\"}}",
        ),
        partial = listOf(
            "{{emotion ...}} / {{delivery ...}} - приближается только через скорость и высоту тона",
            "{{volume 0..4}} / {{pitch 0.5..2.0}} - пробрасываются, только если рантайм их принимает",
        ),
        ignored = listOf(
            "Вокальные реакции ({{breath}}, {{laugh}}, ...) - удаляются из текста",
            "{{emphasis ...}} / {{reset ...}} - принимаются, но игнорируются",
        ),
        examples = listOf(
            "{{voice \"speaker-01\"}}Нужен референсный WAV и его транскрипт.",
        ),
        promptHelp = "Загрузите референсный WAV и его транскрипт на экране Models, прежде чем выбирать этот движок.",
    )

        val entries: List<Entry> = listOf(
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
            approximateDownloadBytes = 369_000_000,
            license = "Apache-2.0",
            repository = "csukuangfj/kokoro-onnx-v1.0",
            revision = null,
            notes = "English, 11 voices",
        ),
        Entry(
            id = "piper-vits",
            title = "Piper/VITS",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 1_024,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            tags = PIPER_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = 65_000_000,
            license = "Model-specific",
            repository = "k2-fsa/sherpa-onnx releases",
            revision = "tts-models",
            notes = "Each language and speaker is downloaded separately",
        ),
        Entry(
            id = "vits-piper-uk-ua",
            title = "Lada (uk-UA)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 1_024,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            tags = PIPER_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = 38_628_128,
            license = "MIT (Piper)",
            repository = "csukuangfj/vits-piper-uk_UA-lada-x_low",
            revision = null,
            notes = "Ukrainian, female, Piper x_low",
            language = "uk-UA",
            downloadAllFiles = true,
        ),
        Entry(
            id = "vits-piper-ca-es",
            title = "Upc Ona (ca-ES)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 1_024,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            tags = PIPER_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = 81_201_467,
            license = "MIT (Piper)",
            repository = "csukuangfj/vits-piper-ca_ES-upc_ona-medium",
            revision = null,
            notes = "Catalan, female, Piper medium",
            language = "ca-ES",
            downloadAllFiles = true,
        ),
        Entry(
            id = "vits-piper-cs-cz",
            title = "Jirka (cs-CZ)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 1_024,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            tags = PIPER_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = 81_201_646,
            license = "MIT (Piper)",
            repository = "csukuangfj/vits-piper-cs_CZ-jirka-medium",
            revision = null,
            notes = "Czech, male, Piper medium",
            language = "cs-CZ",
            downloadAllFiles = true,
        ),
        Entry(
            id = "vits-piper-da-dk",
            title = "Talesyntese (da-DK)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 1_024,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            tags = PIPER_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = 81_202_872,
            license = "MIT (Piper)",
            repository = "csukuangfj/vits-piper-da_DK-talesyntese-medium",
            revision = null,
            notes = "Danish, male, Piper medium",
            language = "da-DK",
            downloadAllFiles = true,
        ),
        Entry(
            id = "vits-piper-el-gr",
            title = "Rapunzelina (el-GR)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 1_024,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            tags = PIPER_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = 81_105_280,
            license = "MIT (Piper)",
            repository = "csukuangfj/vits-piper-el_GR-rapunzelina-low",
            revision = null,
            notes = "Greek, female, Piper low",
            language = "el-GR",
            downloadAllFiles = true,
        ),
        Entry(
            id = "vits-piper-fa-ir",
            title = "Ganji (fa-IR)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 1_024,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            tags = PIPER_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = 81_142_731,
            license = "MIT (Piper)",
            repository = "csukuangfj/vits-piper-fa_IR-ganji-medium",
            revision = null,
            notes = "Persian, male, Piper medium",
            language = "fa-IR",
            downloadAllFiles = true,
        ),
        Entry(
            id = "vits-piper-fi-fi",
            title = "Harri (fi-FI)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 1_024,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            tags = PIPER_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = 87_795_889,
            license = "MIT (Piper)",
            repository = "csukuangfj/vits-piper-fi_FI-harri-low",
            revision = null,
            notes = "Finnish, male, Piper low",
            language = "fi-FI",
            downloadAllFiles = true,
        ),
        Entry(
            id = "vits-piper-hu-hu",
            title = "Anna (hu-HU)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 1_024,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            tags = PIPER_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = 81_203_048,
            license = "MIT (Piper)",
            repository = "csukuangfj/vits-piper-hu_HU-anna-medium",
            revision = null,
            notes = "Hungarian, female, Piper medium",
            language = "hu-HU",
            downloadAllFiles = true,
        ),
        Entry(
            id = "vits-piper-nl-nl",
            title = "MLS (nl-NL)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 1_024,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            tags = PIPER_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = 94_586_316,
            license = "MIT (Piper)",
            repository = "csukuangfj/vits-piper-nl_NL-mls-medium",
            revision = null,
            notes = "Dutch, Piper medium",
            language = "nl-NL",
            downloadAllFiles = true,
        ),
        Entry(
            id = "vits-piper-pt-br",
            title = "Faber (pt-BR)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 1_024,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            tags = PIPER_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = 81_202_842,
            license = "MIT (Piper)",
            repository = "csukuangfj/vits-piper-pt_BR-faber-medium",
            revision = null,
            notes = "Portuguese (Brazil), male, Piper medium",
            language = "pt-BR",
            downloadAllFiles = true,
        ),
        Entry(
            id = "vits-piper-ro-ro",
            title = "Mihai (ro-RO)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 1_024,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            tags = PIPER_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = 81_202_873,
            license = "MIT (Piper)",
            repository = "csukuangfj/vits-piper-ro_RO-mihai-medium",
            revision = null,
            notes = "Romanian, male, Piper medium",
            language = "ro-RO",
            downloadAllFiles = true,
        ),
        Entry(
            id = "vits-piper-tr-tr",
            title = "Fahrettin (tr-TR)",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech),
            requirements = Requirements(
                minimumRamMb = 1_024,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            tags = PIPER_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = 81_203_050,
            license = "MIT (Piper)",
            repository = "csukuangfj/vits-piper-tr_TR-fahrettin-medium",
            revision = null,
            notes = "Turkish, male, Piper medium",
            language = "tr-TR",
            downloadAllFiles = true,
        ),
        Entry(
            id = "pocket-tts-int8",
            title = "PocketTTS INT8",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech, Capability.VoiceCloning),
            requirements = Requirements(
                minimumRamMb = 3_072,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            tags = POCKET_TAGS,
            support = Support.RuntimeInDevelopment,
            approximateDownloadBytes = null,
            license = "Model-specific",
            repository = "k2-fsa/sherpa-onnx releases",
            revision = null,
            notes = "Do not enable before upgrading and smoke-testing the Android runtime",
        ),
        Entry(
            id = "zipvoice-distill-int8",
            title = "ZipVoice Distill INT8",
            categories = setOf(Category.Voice),
            capabilities = setOf(Capability.TextToSpeech, Capability.VoiceCloning),
            requirements = Requirements(
                minimumRamMb = 4_096,
                runtime = Runtime.SherpaOnnx,
                runtimeBundled = true,
        ),
            tags = ZIPVOICE_TAGS,
            support = Support.Experimental,
            approximateDownloadBytes = null,
            license = "Model-specific",
            repository = "k2-fsa/sherpa-onnx releases",
            revision = null,
            notes = "Requires reference WAV and transcript; Android device test pending",
        ),
        Entry(
            id = "stable-audio-open-small",
            title = "On-device synth (music)",
            categories = setOf(Category.Music, Category.Sound),
            capabilities = setOf(Capability.MusicGeneration, Capability.SoundGeneration),
            requirements = Requirements(
                minimumRamMb = 256,
                runtime = Runtime.LiteRt,
                runtimeBundled = true,
        ),
            tags = STABLE_AUDIO_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = null,
            license = "T2V procedural synth (no external model)",
            repository = "",
            revision = null,
            notes = "Procedural synthesis from prompt keywords; no download; up to 11 seconds",
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
            approximateDownloadBytes = null,
            license = "OpenAI Music terms",
            repository = "https://platform.openai.com/docs/guides/audio",
            revision = null,
            notes = "Reserved for OpenAI Music; not selectable until cloud API is wired in.",
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
            approximateDownloadBytes = null,
            license = "ElevenLabs terms",
            repository = "https://api.elevenlabs.io/v1/sound-generation",
            revision = null,
            notes = "Cloud-only ElevenLabs sound generator; requires API key.",
        ),
        Entry(
            id = "stable-audio-clip",
            title = "On-device synth (sound)",
            categories = setOf(Category.Sound),
            capabilities = setOf(Capability.SoundGeneration),
            requirements = Requirements(
                minimumRamMb = 256,
                runtime = Runtime.LiteRt,
                runtimeBundled = true,
        ),
            tags = STABLE_AUDIO_CLIP_TAGS,
            support = Support.Verified,
            approximateDownloadBytes = null,
            license = "T2V procedural synth (no external model)",
            repository = "",
            revision = null,
            notes = "Procedural SFX synthesis; no download; up to 5 seconds",
        ),
        Entry(
            id = "nsynth-wavenet",
            title = "Magenta NSynth (on-device LiteRT)",
            categories = setOf(Category.Sound),
            capabilities = setOf(Capability.SoundGeneration),
            requirements = Requirements(
                minimumRamMb = 1_024,
                runtime = Runtime.LiteRt,
                runtimeBundled = false,
        ),
            tags = STABLE_AUDIO_CLIP_TAGS,
            support = Support.RuntimeInDevelopment,
            approximateDownloadBytes = 17_000_000L,
            license = "Apache-2.0 (Magenta NSynth)",
            repository = "https://github.com/magenta/magenta/tree/main/magenta/models/nsynth",
            revision = null,
            notes = "Short instrument-tone SFX (4 sec mono 8 kHz). Will be selectable once the " +
                "ARM64 smoke-test on a real device confirms inference time and SHA-256.",
        ),
        Entry(
            id = "musicgen-small",
            title = "MusicGen (on-device music)",
            categories = setOf(Category.Music),
            capabilities = setOf(Capability.MusicGeneration),
            requirements = Requirements(
                minimumRamMb = 3_072,
                runtime = Runtime.LiteRt,
                runtimeBundled = false,
            ),
            tags = MUSIC_GEN_TAGS,
            support = Support.RuntimeInDevelopment,
            approximateDownloadBytes = 422_000_000L,
            license = "CC-BY-NC 4.0 (MusicGen-small)",
            repository = "wide-video/musicgen-small-v1.0.0",
            revision = null,
            notes = "Real AI music generation via MusicGen-small LiteRT export. Not selectable until " +
                "the ARM64 smoke-test confirms inference and SHA-256.",
        ),
    )


    /**
     * Lookup for non-catalog generators (Bundled/ElevenLabs SFX). The map is
     * keyed by generator id so the UI can resolve a "selected music/sound
     * generator" choice back to a TagDocs block.
     */
    private val GENERATOR_TAGS: Map<String, TagDocs> = mapOf(
        "elevenlabs.sound" to ELEVEN_SFX_TAGS,
        "litert.stable-audio-open-small.music" to STABLE_AUDIO_TAGS,
        "litert.stable-audio-clip.sound" to STABLE_AUDIO_CLIP_TAGS,
        "nsynth-wavenet" to STABLE_AUDIO_CLIP_TAGS,
        "musicgen-small" to MUSIC_GEN_TAGS,
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

    /**
     * A repository is usable by [com.t2v.server.HuggingFaceRepository] only when
     * it is a real Hugging Face id in the form `author/name`. Release stubs
     * like `k2-fsa/sherpa-onnx releases` and http(s) URLs are excluded.
     */
    fun isHuggingFaceRepository(modelId: String): Boolean =
        repositoryFor(modelId)?.let { repo ->
            repo.contains('/') &&
                !repo.startsWith("http", ignoreCase = true) &&
                repo.none { it.isWhitespace() }
        } ?: false

    /**
     * Local voice entries (SherpaOnnx runtime) that can be discovered as
     * engines once their model directory is installed. Returns entries in a
     * stable catalog order; callers filter by their own availability probe.
     */
    fun localVoiceModelEntries(): List<Entry> = entries.filter { entry ->
        Category.Voice in entry.categories &&
            entry.requirements.runtime == Runtime.SherpaOnnx &&
            isHuggingFaceRepository(entry.id)
    }

    fun licenseFor(modelId: String): String? =
        entries.firstOrNull { it.id == modelId }?.license?.takeIf { it.isNotBlank() }
}
