#!/bin/bash
set -euo pipefail

# ─────────────────────────────────────────────────────────
# SpeakKeys macOS — Build, Sign, Notarize, Package as DMG
# ─────────────────────────────────────────────────────────
#
# Prerequisites:
#   1. Apple Developer account ($99/year)
#   2. Developer ID Application certificate installed in Keychain
#   3. App-specific password stored in Keychain:
#      xcrun notarytool store-credentials "SpeakKeys-Notary" \
#        --apple-id "your@email.com" \
#        --team-id "YOUR_TEAM_ID" \
#        --password "app-specific-password"
#
# Usage:
#   ./scripts/build-release.sh
#
# Configuration (edit these):
TEAM_ID="${TEAM_ID:-YOUR_TEAM_ID}"
SIGNING_IDENTITY="${SIGNING_IDENTITY:-Developer ID Application: Your Name ($TEAM_ID)}"
NOTARY_PROFILE="${NOTARY_PROFILE:-SpeakKeys-Notary}"
# ─────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_DIR/build"
APP_NAME="SpeakKeys"
ARCHIVE_PATH="$BUILD_DIR/$APP_NAME.xcarchive"
EXPORT_DIR="$BUILD_DIR/export"
DMG_PATH="$BUILD_DIR/$APP_NAME.dmg"

# Clean previous build
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

echo "==> Building archive..."
xcodebuild archive \
    -project "$PROJECT_DIR/SpeakKeys.xcodeproj" \
    -scheme "$APP_NAME" \
    -configuration Release \
    -archivePath "$ARCHIVE_PATH" \
    DEVELOPMENT_TEAM="$TEAM_ID" \
    CODE_SIGN_IDENTITY="$SIGNING_IDENTITY" \
    CODE_SIGN_STYLE=Manual \
    | xcbeautify 2>/dev/null || tail -20

echo "==> Exporting app from archive..."
# Create export options plist
cat > "$BUILD_DIR/ExportOptions.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>
    <string>developer-id</string>
    <key>teamID</key>
    <string>$TEAM_ID</string>
    <key>signingStyle</key>
    <string>manual</string>
    <key>signingCertificate</key>
    <string>Developer ID Application</string>
</dict>
</plist>
PLIST

xcodebuild -exportArchive \
    -archivePath "$ARCHIVE_PATH" \
    -exportPath "$EXPORT_DIR" \
    -exportOptionsPlist "$BUILD_DIR/ExportOptions.plist"

APP_PATH="$EXPORT_DIR/$APP_NAME.app"

echo "==> Verifying code signature..."
codesign --verify --deep --strict "$APP_PATH"
codesign -dv --verbose=4 "$APP_PATH" 2>&1 | head -5

echo "==> Submitting for notarization..."
xcrun notarytool submit "$APP_PATH" \
    --keychain-profile "$NOTARY_PROFILE" \
    --wait

echo "==> Stapling notarization ticket..."
xcrun stapler staple "$APP_PATH"

echo "==> Creating DMG..."
# Create a temporary DMG directory with Applications symlink
DMG_STAGING="$BUILD_DIR/dmg-staging"
mkdir -p "$DMG_STAGING"
cp -R "$APP_PATH" "$DMG_STAGING/"
ln -s /Applications "$DMG_STAGING/Applications"

hdiutil create -volname "$APP_NAME" \
    -srcfolder "$DMG_STAGING" \
    -ov -format UDZO \
    "$DMG_PATH"

# Sign and notarize the DMG too
codesign --sign "$SIGNING_IDENTITY" "$DMG_PATH"
xcrun notarytool submit "$DMG_PATH" \
    --keychain-profile "$NOTARY_PROFILE" \
    --wait
xcrun stapler staple "$DMG_PATH"

echo ""
echo "==> Done! DMG ready at: $DMG_PATH"
echo "    Size: $(du -h "$DMG_PATH" | cut -f1)"
