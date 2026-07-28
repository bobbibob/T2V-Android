# Производительность

## Ожидаемые метрики

| Сценарий | Устройство | Ожидание |
|---|---|---|
| Холодный старт | Pixel 6 (8/128) | < 1.5 с |
| Загрузка Kokoro модели | Pixel 6 | 1–2 с |
| Генерация 1 страницы (1800 симв) | Pixel 6, Kokoro NNAPI | ~ 10–15 с |
| Генерация 1 страницы | Pixel 6, OpenAI | ~ 3–5 с (зависит от сети) |
| Генерация главы (~ 30 страниц) | Pixel 6, Kokoro | ~ 5–8 мин |
| Mix главы с музыкой + ducking | Pixel 6 | < 5 с |
| Размер APK (без Kokoro) | — | < 10 МБ |
| Размер APK (с Kokoro) | — | ~ 30 МБ |
| RAM во время генерации | Pixel 6 | < 250 МБ |

## Оптимизации

### Уже сделано
- WAV → MP3 через нативный ffmpeg-kit (без JNI-обёрток).
- Короткие PCM-массивы вместо numpy (экономия памяти).
- Coroutines + Flow вместо QThread + сигналов.
- DataStore (асинхронный) вместо SharedPreferences.
- Room с индексами на всех внешних ключах.

### Что можно улучшить
- **Streaming TTS**: генерировать сегменты по мере готовности, не ждать всех.
- **GPU-кеш**: предкомпиляция Kokoro для конкретного устройства.
- **Параллельная генерация**: до 4 чанков одновременно (multicore).
- **Incremental mix**: не держать всё аудио в памяти, микшировать на лету.

## Профилирование

### Android Studio Profiler
- CPU: вкладка CPU, выбрать `com.t2v`, посмотреть hot methods.
- Memory: проверка утечек через LeakCanary.
- Network: посмотреть запросы к API.

### ADB
```bash
# Логи
adb logcat -s t2v:V LTVPipeline:V

# Профилирование
adb shell am profile start com.t2v.debug /data/local/tmp/ltv.trace
# (выполнить сценарий)
adb shell am profile stop com.t2v.debug
adb pull /data/local/tmp/ltv.trace
```

### Systrace
```bash
python $ANDROID_HOME/platform-tools/systrace/systrace.py \
  --time=10 -o trace.html sched gfx view
```

## Бенчмарк Kokoro

Сравнение времени генерации 1000 символов на Pixel 6 (24 kHz):

| Бэкенд | Время | Realtime factor |
|---|---|---|
| CPU (XNNPACK) | ~ 8 с | ~ 0.5x |
| NNAPI (GPU) | ~ 5 с | ~ 0.8x |
| NNAPI (NPU) | ~ 3 с | ~ 1.3x |

Где `realtime factor` = время аудио / время генерации. > 1.0 — быстрее
реального времени.

