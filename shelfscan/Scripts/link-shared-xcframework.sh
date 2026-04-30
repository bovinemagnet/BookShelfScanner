#!/usr/bin/env bash
# shelfscan/Scripts/link-shared-xcframework.sh
#
# Builds the ShelfScanShared XCFramework for iOS targets and prints the
# path along with the manual steps a developer needs in Xcode to link it.
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
echo "Next steps in Xcode (one-time):"
echo "  1. Open $SHELFSCAN_DIR/iosApp/iosApp.xcodeproj"
echo "  2. Select target 'iosApp' -> General -> Frameworks, Libraries, and Embedded Content"
echo "  3. Click '+' -> Add Other... -> Add Files..."
echo "  4. Navigate to and select: $XCF_PATH"
echo "  5. Set Embed to 'Embed & Sign'"
echo "  6. Build and run (Cmd+R)"
