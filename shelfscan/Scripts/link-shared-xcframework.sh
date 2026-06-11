#!/usr/bin/env bash
# shelfscan/Scripts/link-shared-xcframework.sh
#
# Builds the ShelfScanShared XCFramework for iOS targets. The Xcode project
# already links the framework from the Gradle output path, so building it is
# all that is needed before opening the project in Xcode.
#
# Run on macOS only. The Linux dev environment cannot produce an XCFramework.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SHELFSCAN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$SHELFSCAN_DIR"

if command -v gradle21w >/dev/null 2>&1; then
    GRADLE=gradle21w
else
    GRADLE=./gradlew
fi

echo "Building XCFramework..."
"$GRADLE" :shared:assembleShelfScanSharedXCFramework

XCF_PATH="$SHELFSCAN_DIR/shared/build/XCFrameworks/release/ShelfScanShared.xcframework"
echo
echo "Built: $XCF_PATH"
echo
echo "The Xcode project already links the framework from this path, so:"
echo "  1. Open $SHELFSCAN_DIR/iosApp/iosApp.xcodeproj"
echo "  2. Build and run (Cmd+R)"
echo
echo "Re-run this script whenever the shared module changes."
