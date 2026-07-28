# Contributing

Спасибо за интерес к T2V! Мы рады любому вкладу.

## Как внести вклад

1. **Issues** — сообщайте о багах, предлагайте фичи.
2. **Pull Requests** — фиксы багов, новые движки, локализации.
3. **Документация** — улучшения в `docs/`.
4. **Переводы** — добавление новых `values-<lang>/strings.xml`.

## Workflow

```bash
# 1. Fork + clone
git clone https://github.com/yourname/t2v-android.git
cd t2v-android

# 2. Создать ветку
git checkout -b feature/awesome

# 3. Править код
# 4. Запустить тесты
./gradlew :app:testDebugUnitTest

# 5. Commit + push
git commit -m "feat: add awesome feature"
git push origin feature/awesome

# 6. Открыть PR на GitHub
```

## Стиль кода

- Kotlin: официальный стиль (`ktlint` + `detekt`).
- Java interop: `@JvmStatic`, `@JvmName` где нужно.
- Коммиты: Conventional Commits (`feat:`, `fix:`, `docs:`).
- PR: один логический change.

## Что востребовано

- Имплементация G2P для Kokoro на устройстве.
- TTS-движки, которых ещё нет (Edge TTS, Deepgram, Cartesia).
- Новые локали (поддерживаются через `values-<lang>/strings.xml`).
- Голосовая галерея (синхронизация с GitHub-каталогом).
- Улучшение UI/UX.
- Оптимизация производительности (на 4-ГБ Android идёт тяжело).

