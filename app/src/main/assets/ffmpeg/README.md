# FFmpeg executable

FFmpeg не хранится в Git и не распаковывается в `filesDir`: Android 10+
запрещает запуск изменяемых файлов приложения.

GitHub Actions собирает закреплённый FFmpeg 7.1.1 из официального репозитория
скриптом `tools/build_ffmpeg_android.sh`, проверяет commit и помещает executable
под именем `libffmpeg_exec.so` в `jniLibs/arm64-v8a`. Gradle извлекает его в
read-only `nativeLibraryDir`, откуда `FFmpegBridge` может безопасно выполнить
его через `ProcessBuilder`.

На текущем этапе APK поддерживает только `arm64-v8a`.
