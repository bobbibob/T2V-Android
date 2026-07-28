# Документация T2V

Полный индекс документации (обновлено 2026-07-28).

## Быстрый старт
- [README](../README.md) — обзор проекта
- [QUICKSTART](QUICKSTART.md) — собрать и запустить за 5 минут

## Архитектура и портирование
- [PORTING](PORTING.md) — что портировано из оригинала
- [ROADMAP](ROADMAP.md) — текущий статус и планы
- [ARCHITECTURE](ARCHITECTURE.md) — диаграммы компонентов
- [INTERNALS](INTERNALS.md) — как устроено внутри
- [LTV_MARKUP](LTV_MARKUP.md) — поведение разметки
- [MODELS](MODELS.md) — каталог моделей и движков

## Журнал и контекст
- [AI_HANDOFF](AI_HANDOFF.md) — **главный оперативный контекст** для следующего
  ИИ-агента. Обязательно прочитать перед началом работы.
- [CHANGELOG](CHANGELOG.md) — история изменений
- [SUMMARY](SUMMARY.md) — итоговая сводка
- [STATUS](../STATUS.md) — текущий статус (что готово / что в работе)

## Разработка
- [EXTENDING](EXTENDING.md) — как добавить новый движок/экран/локализацию
- [TESTING](TESTING.md) — тестирование
- [PERFORMANCE](PERFORMANCE.md) — производительность и оптимизации
- [TROUBLESHOOTING](TROUBLESHOOTING.md) — решение типичных проблем
- [VERSIONING](VERSIONING.md) — политика версий

## Деплоймент
- [DEPLOYMENT](DEPLOYMENT.md) — публикация в Google Play и F-Droid
  (отложено до v0.3.0+, см. [ROADMAP](ROADMAP.md))
- [SECURITY](SECURITY.md) — безопасность

## Продукт
- [FAQ](FAQ.md) — частые вопросы
- [COMPARISON](COMPARISON.md) — сравнение с конкурентами
- [MARKETING](MARKETING.md) — план продвижения

## Ссылки
- [REFERENCES](REFERENCES.md) — внешние ссылки и ресурсы
- [glossary](glossary.md) — термины

## Структура проекта

```
t2v/
├── app/                        Android-приложение (Kotlin, Compose)
│   ├── src/main/java/com/t2v/
│   │   ├── core/               бизнес-логика (text, markup, audio, AudioTagInserter)
│   │   ├── tts/                локальные Android и облачные TTS-движки
│   │   ├── generators/         music/sound генераторы (NSynth, ElevenLabs SFX, procedural)
│   │   ├── data/               Room (6 DAO, 6 Entities) + DataStore Settings
│   │   ├── ui/                 Compose-экраны (8: editor, generation, music, review,
│   │   │                       voices, projects, settings, models)
│   │   ├── worker/             GenerationPipeline + GenerationService
│   │   ├── server/             проверка и загрузка локальных моделей с Hugging Face
│   │   ├── util/               LocaleHelper, Permissions, AudioPlayer
│   │   └── app/                LTVApplication + AppContainer (ручной DI)
│   ├── src/main/assets/        Kokoro-модель, FFmpeg-бинарь (см. README каждого)
│   ├── src/main/res/values*/   strings.xml (11 локалей)
│   ├── src/test/               JVM unit-тесты (12 классов)
│   └── src/androidTest/        ART integration-тесты
├── docs/                       эта документация
└── tools/                      install.sh, check_completeness.sh, inspect_layout.sh
```

## Что НЕ делаем (по решению владельца от 2026-07-28)

- ❌ Подписка / монетизация (отложена до полной доделки приложения)
- ❌ Google Play Billing, Firebase Auth
- ❌ AAB-релиз / signing / R8
- ❌ Privacy Policy URL / Data Safety Form
- ❌ Модели в APK / Asset Packs (пользователь скачивает сам через HF)
- ❌ Серверные TTS-движки / engine-host
