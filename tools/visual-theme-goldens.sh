#!/usr/bin/env bash
set -euo pipefail

mode="${1:-verify}"
if [[ "$mode" != "record" && "$mode" != "verify" ]]; then
  echo "Usage: $0 [record|verify]"
  exit 2
fi

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
adb_bin="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
test_class="helium314.keyboard.theme.VisualThemeGoldenTest"
gradle_args=(
  :app:connectedDebugNoMinifyAndroidTest
  "-Pandroid.testInstrumentationRunnerArguments.class=$test_class"
)

cd "$repo_root"
if [[ "$mode" == "record" ]]; then
  ./gradlew "${gradle_args[@]}" \
    -Pandroid.testInstrumentationRunnerArguments.recordVisualThemeGoldens=true
  destination="app/src/androidTest/assets/visual-theme-goldens"
  mkdir -p "$destination"
  "$adb_bin" pull \
    /sdcard/Android/data/helium314.keyboard.huboard.debug/files/visual-theme-goldens/. \
    "$destination"
  echo "Recorded visual-theme goldens in $destination"
else
  ./gradlew "${gradle_args[@]}"
fi
