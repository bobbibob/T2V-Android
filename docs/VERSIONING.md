# Версионирование

T2V следует [Semantic Versioning](https://semver.org/).

## Формат

`MAJOR.MINOR.PATCH[-PRERELEASE][+BUILD]`

- **MAJOR** — несовместимые изменения API
- **MINOR** — новая функциональность (обратно совместимая)
- **PATCH** — баг-фиксы
- **PRERELEASE** — `alpha`, `beta`, `rc1`
- **BUILD** — метаданные сборки (CI номер, дата)

## Текущая версия

`0.1.0-alpha` (см. `app/build.gradle.kts` → `versionName`).

## Политика

- API приложения (для будущих плагинов) — стабильное с `1.0.0`.
- TTS-движки (`tts/engines/`) — могут менять внутренности в `0.x`.
- Core (`core/`) — стабильное с `0.5.0`.
- Маркеры прогресса:
  - `0.1.0-alpha` — MVP, тестируется внутри
  - `0.5.0` — feature-complete, API стабилизирован
  - `1.0.0` — релиз в Google Play

## Changelog

См. `docs/CHANGELOG.md` — там подробный список изменений.

