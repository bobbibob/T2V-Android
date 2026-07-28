# Troubleshooting

## Сборка

### Локальный Gradle не работает
- **Не запускайте** `./gradlew`, `gradle assembleDebug`, `kotlinc` локально.
  На машине разработчика нет Android SDK. Все сборки — только через
  GitHub Actions (`gh workflow run android.yml`).
- Если CI падает с ошибкой компиляции — `gh run view <id> --log-failed`
  → `grep "error:\|e: file:"` покажет строки с проблемами.

### `InfoTarget` (или другой private data class) не виден в публичной @Composable
- Kotlin: "public function exposes its internal/private parameter type
  argument X". Решение: уберите модификатор `private`/`internal` —
  оставьте `data class` без модификатора (тогда он public).

### `error: 'public' function exposes its 'internal' parameter type argument`
- Та же проблема, что и выше. Используйте `data class` (без
  модификатора) или `internal data class` для shared типов внутри модуля.

### Missing imports после `git pull`
- Часто Compose-функции добавляют новые `import`. Запустите
  `gh workflow run` и смотрите `gh run view --log-failed` — компилятор
  точно скажет, какие импорты нужны.

## Запуск

### APK не устанавливается
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` — старый debug-ключ не совпадает
  с новым. Нужно удалить приложение:
  `adb -s <serial> uninstall com.t2v.debug`
- Внимание: удаление стирает скачанные модели и настройки.

### Приложение крашится при старте
- `adb logcat | grep t2v` покажет stacktrace.
- Проверьте `AndroidManifest.xml`: `android:name=".app.LTVApplication"`.

### "Engine not found" при выборе
- Установите API-ключ в Settings → TTS engines.
- Для локального режима убедитесь, что модель полностью скачана.

### "Network error" при генерации через облачный движок
- Проверьте интернет.
- Убедитесь, что API-ключ валидный.
- OpenAI / ElevenLabs / Gemini / Azure могут возвращать 401/403 при
  неверном ключе.

## Kokoro

### Kokoro не работает
- Проверьте наличие файлов в `files/models/8ae649d98c616269e26efb10/`:
  ```bash
  adb -s <serial> shell 'run-as com.t2v.debug ls files/models/8ae649d98c616269e26efb10/'
  # Ожидается: LICENSE, espeak-ng-data/, model.onnx, tokens.txt, voices.bin
  ```
- Если файлов нет — скачайте через Settings → Models → Kokoro → Download.

### Kokoro inference зависает на длинных текстах
- Известный баг: audiobook #3 (текст >3 000 символов) застрял в `running`
  после self-test в `Settings → 🧪 Run <music>/<sfx> self-test`.
- Возможные причины: out-of-memory, ONNX-сессия не возвращается,
  конфликт с предыдущей сессией.
- Workaround: сократите текст до <3 000 символов или подождите ~5 минут
  и посмотрите logcat.

## Audio tags (`<music>` / `<sfx>`)

### Клипы появляются, но на позиции 0
- Известный баг: `timelineStartMs=0` для всех music/sound клипов.
- Причина: `AudioTagInserter.insert()` вызывается ДО генерации
  voice-сегментов в `GenerationPipeline.kt:109`, когда `durationMs=0`
  у всех сегментов. Должен вызываться после цикла.
- Workaround: переставьте клипы вручную в AudioEditor после генерации.

### Клипы не появляются вообще
- Проверьте БД:
  ```bash
  adb -s <serial> exec-out run-as com.t2v.debug sh -c \
    "cat databases/t2v.db databases/t2v.db-wal databases/t2v.db-shm" \
    > /tmp/t2v.db
  sqlite3 /tmp/t2v.db "PRAGMA wal_checkpoint(FULL);"
  sqlite3 -header -separator '|' /tmp/t2v.db \
    "SELECT * FROM audio_tracks WHERE audiobookId = <id>;"
  ```
- Если tracks пустые — значит `AudioTagInserter` не отработал.
  Возможно `selectedMusicGenerator` / `selectedSoundGenerator` в
  Settings пустые.

## Self-test

### Self-test не запускается
- Settings → 🧪 Run <music>/<sfx> self-test → нажмите кнопку.
- Под кнопкой появится текст типа "audiobookId=2 status=completed
  segments=2 music+sound clips=2" если всё ОК.
- Если "FAILED: ..." — посмотрите текст ошибки, обычно это
  "No projects in DB" (создайте проект сначала) или
  "No TTS engine available" (скачайте Kokoro или добавьте API-ключ).

## ADB

### `adb: device not found`
- Проверьте USB-подключение: `adb devices`.
- Если устройство видно но `unauthorized` — разблокируйте экран и
  подтвердите RSA-ключ.

### `adb: failed to stat remote object ... Permission denied`
- Для приватных файлов приложения используйте `run-as`:
  ```bash
  adb -s <serial> exec-out run-as com.t2v.debug cat <path> > /tmp/file
  ```

## Server host (legacy)

> Удалено в 2026-07-26. T2V больше не поддерживает engine-host.

## APK download из CI

### `gh run download` обрывается на 30+ МБ
- Workaround: используйте curl с продолжением:
  ```bash
  # Получить ID артефакта
  gh api -H "Accept: application/vnd.github+json" \
    /repos/bobbibob/T2V-Android/actions/runs/<run-id>/artifacts \
    | jq -r '.artifacts[] | select(.name=="app-debug") | .id'
  # Скачать
  for i in $(seq 1 30); do
    curl -s -L -C - -o /tmp/app-debug.zip \
      "https://api.github.com/repos/bobbibob/T2V-Android/actions/artifacts/<id>/zip" \
      -H "Authorization: Bearer $(gh auth token)"
    SIZE=$(stat -f%z /tmp/app-debug.zip)
    [ "$SIZE" -ge 43000000 ] && break
  done
  # Распаковать
  unzip /tmp/app-debug.zip -d /tmp/t2v-apk
  # Установить
  adb -s <serial> install -r /tmp/t2v-apk/app-debug.apk
  ```
