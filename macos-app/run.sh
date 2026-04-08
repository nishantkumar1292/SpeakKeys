#!/bin/bash
# Build and run SpeakKeys from a stable location so Accessibility permission persists.
# Usage: ./run.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INSTALL_DIR="$HOME/Applications"
APP_NAME="SpeakKeys.app"

echo "Building SpeakKeys..."
xcodebuild -project "$SCRIPT_DIR/SpeakKeys.xcodeproj" -scheme SpeakKeys -configuration Debug build -quiet

BUILD_DIR=$(xcodebuild -project "$SCRIPT_DIR/SpeakKeys.xcodeproj" -scheme SpeakKeys -showBuildSettings 2>/dev/null | grep " BUILT_PRODUCTS_DIR" | awk '{print $3}')

echo "Installing to $INSTALL_DIR/$APP_NAME..."
mkdir -p "$INSTALL_DIR"

# Kill any running instance
pkill -x SpeakKeys 2>/dev/null || true
sleep 0.5

# Copy to stable location (same path = permission persists)
rm -rf "$INSTALL_DIR/$APP_NAME"
cp -R "$BUILD_DIR/$APP_NAME" "$INSTALL_DIR/$APP_NAME"

echo "Launching SpeakKeys..."
open "$INSTALL_DIR/$APP_NAME"

echo ""
echo "Done! If this is first run, grant Accessibility permission to:"
echo "  $INSTALL_DIR/$APP_NAME"
echo "Then quit and re-run this script."
