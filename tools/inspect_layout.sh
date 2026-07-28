#!/usr/bin/env bash
# Печатает дерево проекта без сгенерированного кода.
set -e
cd "$(dirname "$0")/.."
find . -type d \( -name ".gradle" -o -name "build" -o -name ".idea" -o -name ".git" \) -prune -o -type f -print | \
    grep -vE '\.(gitignore|properties)$' | sort | head -200
echo ""
echo "=== File count by area ==="
echo "core/  : $(find app/src/main/java/com/t2v/core -name '*.kt' | wc -l | tr -d ' ')"
echo "tts/   : $(find app/src/main/java/com/t2v/tts -name '*.kt' | wc -l | tr -d ' ')"
echo "data/  : $(find app/src/main/java/com/t2v/data -name '*.kt' | wc -l | tr -d ' ')"
echo "ui/    : $(find app/src/main/java/com/t2v/ui -name '*.kt' | wc -l | tr -d ' ')"
echo "worker/: $(find app/src/main/java/com/t2v/worker -name '*.kt' | wc -l | tr -d ' ')"
echo "tests  : $(find app/src/test -name '*.kt' | wc -l | tr -d ' ')"
echo ""
echo "=== Lines of code ==="
find app/src -name '*.kt' | xargs wc -l | tail -1
