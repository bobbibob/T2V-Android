#!/usr/bin/env bash
# Проверка полноты: какие компоненты из ТЗ реализованы, какие нет.
set -e
cd "$(dirname "$0")/.."

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

check() {
    if [ -e "$1" ]; then
        echo -e "${GREEN}✓${NC} $2"
    else
        echo -e "${RED}✗${NC} $2 (missing: $1)"
    fi
}

echo "=== Android core ==="
check "app/src/main/java/com/t2v/core/text/TextProcessor.kt"        "TextProcessor"
check "app/src/main/java/com/t2v/core/markup/LTVMarkupParser.kt"    "LTVMarkupParser"
check "app/src/main/java/com/t2v/core/audio/AudioEncoder.kt"         "AudioEncoder (WAV)"
check "app/src/main/java/com/t2v/core/audio/AudioMixer.kt"           "AudioMixer"
check "app/src/main/java/com/t2v/core/audio/FFmpegBridge.kt"        "FFmpegBridge"
check "app/src/main/java/com/t2v/core/audio/WaveformExtractor.kt"   "WaveformExtractor"
check "app/src/main/java/com/t2v/core/subtitle/SubtitleWriter.kt"   "SubtitleWriter (SRT/ASS)"
check "app/src/main/java/com/t2v/core/normalization/TextNormalizer.kt" "TextNormalizer"
check "app/src/main/java/com/t2v/core/normalization/Num2Words.kt"   "Num2Words (multilang)"
check "app/src/main/java/com/t2v/core/project/ProjectManager.kt"    "ProjectManager (DOCX/TXT/MD)"

echo ""
echo "=== TTS engines ==="
check "app/src/main/java/com/t2v/tts/engines/KokoroTtsEngine.kt"    "Kokoro (on-device)"
check "app/src/main/java/com/t2v/tts/engines/OpenAiTtsEngine.kt"    "OpenAI"
check "app/src/main/java/com/t2v/tts/engines/ElevenLabsTtsEngine.kt" "ElevenLabs"
check "app/src/main/java/com/t2v/tts/engines/GeminiTtsEngine.kt"    "Gemini"
check "app/src/main/java/com/t2v/tts/engines/AzureTtsEngine.kt"     "Azure"
check "app/src/main/java/com/t2v/tts/engines/CustomHttpTtsEngine.kt" "Custom HTTP"

echo ""
echo "=== Data ==="
check "app/src/main/java/com/t2v/data/AppDatabase.kt"                "Room database"
check "app/src/main/java/com/t2v/data/Entities.kt"                  "Entities"
check "app/src/main/java/com/t2v/data/Daos.kt"                      "DAOs"
check "app/src/main/java/com/t2v/data/SettingsRepository.kt"         "Settings (DataStore)"

echo ""
echo "=== UI ==="
check "app/src/main/java/com/t2v/ui/MainActivity.kt"                "MainActivity"
check "app/src/main/java/com/t2v/ui/navigation/LTVNavHost.kt"       "Navigation"
check "app/src/main/java/com/t2v/ui/theme/Theme.kt"                 "Theme"
check "app/src/main/java/com/t2v/ui/screens/editor/EditorScreen.kt" "Editor"
check "app/src/main/java/com/t2v/ui/screens/generation/GenerationScreen.kt" "Generation"
check "app/src/main/java/com/t2v/ui/screens/music/MusicMixScreen.kt" "Music Mix"
check "app/src/main/java/com/t2v/ui/screens/review/ReviewScreen.kt" "Review"
check "app/src/main/java/com/t2v/ui/screens/voices/VoicesScreen.kt" "Voices"
check "app/src/main/java/com/t2v/ui/screens/projects/ProjectsScreen.kt" "Projects"
check "app/src/main/java/com/t2v/ui/screens/settings/SettingsScreen.kt" "Settings"
check "app/src/main/java/com/t2v/ui/waveform/WaveformCanvas.kt"     "Waveform canvas"
check "app/src/main/java/com/t2v/ui/markup/MarkupHighlighter.kt"    "Markup highlighter"
check "app/src/main/java/com/t2v/ui/components/MarkupToolbar.kt"    "Markup toolbar"

echo ""
echo "=== Tests ==="
check "app/src/test/java/com/t2v/core/text/TextProcessorTest.kt"    "TextProcessor test"
check "app/src/test/java/com/t2v/core/markup/LTVMarkupParserTest.kt" "Markup test"
check "app/src/test/java/com/t2v/core/audio/AudioMixerTest.kt"       "Mixer test"
check "app/src/test/java/com/t2v/core/subtitle/SubtitleWriterTest.kt" "Subtitle test"
check "app/src/test/java/com/t2v/core/normalization/TextNormalizerTest.kt" "Normalizer test"
check "app/src/test/java/com/t2v/core/normalization/Num2WordsTest.kt" "Num2Words test"
check "app/src/test/java/com/t2v/tts/RegistrySmokeTest.kt"           "TTS smoke test"
check "app/src/androidTest/java/com/t2v/EndToEndSmokeTest.kt"        "Android e2e test"

echo ""
echo "=== Locales ==="
for lang in en ru es fr de it pt zh ja hi ar; do
    check "app/src/main/res/values-$lang/strings.xml" "values-$lang/strings.xml"
done

echo ""
echo "=== Docs ==="
check "README.md" "README"
check "docs/PORTING.md" "PORTING"
check "docs/ROADMAP.md" "ROADMAP"
check "docs/LTV_MARKUP.md" "LTV_MARKUP"
check "docs/QUICKSTART.md" "QUICKSTART"
check "docs/INTERNALS.md" "INTERNALS"
check "docs/ARCHITECTURE.md" "ARCHITECTURE"

echo ""
echo "=== Summary ==="
echo "Kotlin:  $(find app/src/main/java -name '*.kt' | wc -l | tr -d ' ') files"
echo "Tests:   $(find app/src/test app/src/androidTest -name '*.kt' 2>/dev/null | wc -l | tr -d ' ') files"
echo "Lines:   $(find app/src -name '*.kt' -exec cat {} \; | wc -l | tr -d ' ') total"
