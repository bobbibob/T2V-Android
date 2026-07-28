# Деплоймент

> ⚠️ **Публикация отложена.** Владелец сказал: «сначала доделаем полностью
> приложение, потом решим с подпиской» (2026-07-28). Эта страница —
> план на будущее (v0.4.0+), не актуальная задача.

## Google Play

### Подготовка

1. Зарегистрировать аккаунт разработчика Google Play (25 USD, единоразово).
2. Создать приложение в Google Play Console.
3. Сгенерировать ключ подписи:
   ```bash
   keytool -genkey -v -keystore t2v.keystore \
     -alias t2v -keyalg RSA -keysize 2048 -validity 10000
   ```
4. Положить keystore в безопасное место (НЕ коммитить).
5. Настроить `~/.gradle/gradle.properties`:
   ```properties
   T2V_UPLOAD_STORE_FILE=path/to/t2v.keystore
   T2V_UPLOAD_KEY_ALIAS=t2v
   T2V_UPLOAD_STORE_PASSWORD=****
   T2V_UPLOAD_KEY_PASSWORD=****
   ```

### Сборка release-AAB

```bash
./gradlew :app:bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

### Загрузка в Google Play

1. Google Play Console → Release → Production → Create new release.
2. Загрузить AAB.
3. Заполнить release notes, version code, target API.
4. Submit for review.

### Privacy Policy

- Нужен публичный URL с privacy policy (например, GitHub Pages).
- Описывает какие данные собираются, куда отправляются, контакты.
- Без неё Google Play не примет приложение.

### Data Safety Form

Google Play Console требует заполнить:
- Какие personal data собираются (audio recordings, voice clones, ...)
- Для чего (app functionality, personalization)
- Передаются ли third parties (no)
- Можно ли удалить данные (да, через Settings)

## F-Droid

F-Droid принимает обычные APK, подписанные F-Droid-ключом. См.
[f-droid.org](https://f-droid.org) для требований.

## Текущий статус (2026-07-28)

| Что | Статус |
|---|---|
| APK debug build | ✅ Собирается через CI |
| AAB release build | ❌ Не настроен (нужен signing) |
| Keystore | ❌ Не создан |
| Privacy Policy | ❌ Не написан |
| Data Safety Form | ❌ Не заполнен |
| Google Play Console | ❌ Не создан |
| Подписка (Play Billing) | ❌ Не подключена (отложено до v0.4.0+) |

## Прямое распространение (сейчас)

Вместо публикации в магазины — APK через GitHub Actions:

```bash
# Скачать APK последнего зелёного CI
gh run list --workflow android.yml --branch codex/models-download --limit 1 \
  --json databaseId,conclusion | jq -r '.[] | select(.conclusion=="success") | .databaseId'
# (затем: gh api ... artifacts, curl -L -C - -o ...)
```

Пользователь сам устанавливает через `adb install -r` или
sideload на устройстве.
