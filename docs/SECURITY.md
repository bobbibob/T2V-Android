# Безопасность

## Что мы делаем

- API-ключи хранятся в `DataStore` (потенциально — в `EncryptedSharedPreferences` через Tink).
- Трафик к публичным API — только HTTPS.
- HTTPS используется для встроенных облачных API и Hugging Face.
- HTTP может использоваться только явно настроенным Custom HTTP API.

## Что нужно сделать вам

### Защита API-ключей

API-ключи хранятся в DataStore. **Не включайте их в git**.

`.gitignore` уже исключает `local.properties` и keystore.

### Hugging Face token

Опциональный токен хранится в `SettingsRepository.engines["huggingface"]["token"]`.
Используется для скачивания приватных моделей и для снятия rate limit.
**Не включайте его в git.**

## Локальные модели

- Скачиваются с Hugging Face напрямую (или через прокси с токеном).
- Сохраняются в `files/models/<sha256-prefix>/` (Kokoro) или
  `files/models/litert/<modelId>/` (LiteRT).
- Это app-private storage; другие приложения не имеют доступа.
- При удалении приложения — модели стираются.

## Reporting vulnerabilities

Нашли уязвимость? Пишите на security@t2v.example.com
(замените на реальный адрес при публикации).
