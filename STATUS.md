# Текущий статус

**Дата обновления:** 2026-08-20
**Ветка:** `main` (CI зелёный)
**APK:** ~44 МБ
**Тесты:** зелёные (test + build)
**Устройство:** `R5CN30LJS4W` (Samsung S20 Ultra, root Magisk, PIN 1557)

## Что сделано (сессия 2026-08-19/20)

```
✅ Сделано:
- MusicGen ARM64 smoke-test прошёл (3 теста, 26 сек)
- ElevenLabs clone UI: фикс JSON-парсинга
- WorkManager: ModelDownloadWorker для фоновых загрузок
- Tablet-адаптация: NavigationRail для больших экранов
- Material You dynamic colors (Android 12+)
- SRT/ASS экспорт субтитров + Share Intent
- Auto-TTS: определение языка + подбор голоса
- VoiceGallerySync: каталог голосов из GitHub
- Inline парные теги: <whisper>текст</whisper>, <sad>текст</sad> и т.д.
- Контекстная панель тегов по движку
- Генерация: проверки движка, показ ошибок в UI
- UI: выбранный движок сверху, пример промпта
- 26+ новых JVM тестов
- WebSocket связь с агентами через хаб

✅ Проверено на устройстве:
- T2V запускается ✅
- Текст вводится ✅
- MarkupToolbar работает ✅ (Шёпот, Крик, Мягко, Громко)
- Нет крашей в logcat ✅
- Кнопка "Сгенерировать" требует выбора папки (не активна без неё)

❌ Не протестировано из-за WhatsApp-уведомлений:
- Полный цикл генерации (нужна папка проекта)
- Material You colors на устройстве
- SRT/ASS экспорт
- Share Intent
- Auto-TTS
- Inline теги в реальной генерации

🚧 Не готово:
- Drag/trim клипов в TimelineView (только текстовый ввод)
- sherpa-onnx клонирование голоса (каркас, модель не подключена)
- Voice gallery sync UI (VoiceGallerySync готов, экран не подключён)
- Background downloads UI (Worker готов, экран очереди нет)
- Compose UI Test (только JVM тесты)
```

## Связь с агентами
- Хаб: http://78.40.197.208:8888
- WebSocket: ws://78.40.197.208:8888/ws/t2v
- Boss — главный, слушаться во всём
- Alina — владелица телефона, координировать через хаб
- Robert — владелец проекта

## Следующие шаги
1. Протестировать полный цикл генерации на устройстве (когда WhatsApp не мешает)
2. Drag/trim клипов в TimelineView
3. sherpa-onnx клонирование голоса
4. Voice gallery sync UI
5. Background downloads UI очередь