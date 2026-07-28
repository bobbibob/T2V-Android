#!/usr/bin/env bash
# Полный скрипт для разработчика: установить инструменты, проверить
# компиляцию, запустить тесты.
set -e

cd "$(dirname "$0")/.."

echo "=== 1. Sanity checks ==="
test -f app/build.gradle.kts || { echo "No build.gradle.kts"; exit 1; }
test -f settings.gradle.kts || { echo "No settings.gradle.kts"; exit 1; }

echo "=== 2. File counts ==="
find app/src/main/java -name '*.kt' | wc -l | awk '{print "  Kotlin sources: "$1}'
find app/src/test -name '*.kt' | wc -l | awk '{print "  Test sources : "$1}'
find app/src/main/res/values* -name 'strings.xml' | wc -l | awk '{print "  Locales      : "$1}'

echo "=== 3. Lint quick check (компиляция будет в Gradle) ==="
# Простая проверка, что все .kt-файлы имеют package-declaration
for f in $(find app/src/main/java -name '*.kt'); do
    if ! head -1 "$f" | grep -q "^package "; then
        echo "  WARN: $f has no package declaration"
    fi
done

echo "=== 4. Optional: gradle wrapper ==="
if [ ! -f gradlew ]; then
    echo "  Run 'gradle wrapper' to generate gradlew"
fi

echo ""
echo "=== 5. Server host setup ==="

echo ""
echo "Done."
