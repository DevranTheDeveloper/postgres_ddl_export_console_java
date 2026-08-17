#!/bin/bash
set -e

VOL_NAME="PostgreSQL DDL Studio"
APP_NAME="PostgreSQL DDL Studio.app"
APP_PATH="/Users/devransever/Desktop/$APP_NAME"
OUTPUT_DMG="/Users/devransever/Desktop/PostgreSQL-DDL-Studio-5.4.0-macOS.dmg"
BG_IMG="scripts/macos/dmg_bg.png"
BG_IMG_2X="scripts/macos/dmg_bg@2x.png"

TMP_DMG="/tmp/temp_build.dmg"
MOUNT_DIR="/Volumes/$VOL_NAME"

echo "=== 🍏 macOS Drag-and-Drop DMG Kurulum Paketi Oluşturuluyor ==="

# 1. Clean previous artifacts
rm -f "$OUTPUT_DMG" "$TMP_DMG"
hdiutil detach "$MOUNT_DIR" 2>/dev/null || true

# 2. Calculate required size
APP_SIZE_MB=$(du -sm "$APP_PATH" | cut -f1)
DMG_SIZE_MB=$((APP_SIZE_MB + 50))

# 3. Create temporary read-write DMG
hdiutil create -size "${DMG_SIZE_MB}m" -volname "$VOL_NAME" -fs HFS+ "$TMP_DMG"

# 4. Mount the DMG
DEVICE=$(hdiutil attach -readwrite -noverify -noautoopen "$TMP_DMG" | grep Apple_HFS | awk '{print $1}')
echo "Mount edildi: $DEVICE -> $MOUNT_DIR"

# 5. Copy App & Applications Symlink
cp -R "$APP_PATH" "$MOUNT_DIR/"
ln -s /Applications "$MOUNT_DIR/Applications"

# 6. Add background folder & images
mkdir -p "$MOUNT_DIR/.background"
cp "$BG_IMG" "$MOUNT_DIR/.background/bg.png"
cp "$BG_IMG_2X" "$MOUNT_DIR/.background/bg@2x.png" 2>/dev/null || true

# 7. Use AppleScript to set Finder window size, icon positions, and view options
echo "Finder pencere düzeni ve simge konumları ayarlanıyor..."
osascript << EOF
tell application "Finder"
    tell disk "$VOL_NAME"
        open
        set current view of container window to icon view
        set toolbar visible of container window to false
        set statusbar visible of container window to false
        set the bounds of container window to {300, 150, 900, 550}
        
        set theViewOptions to the icon view options of container window
        set arrangement of theViewOptions to not arranged
        set icon size of theViewOptions to 110
        set background picture of theViewOptions to file ".background:bg.png"
        
        -- Set icon positions (Left: App, Right: Applications)
        set position of item "$APP_NAME" of container window to {150, 180}
        set position of item "Applications" of container window to {450, 180}
        
        update without registering applications
        delay 2
        close
    end tell
end tell
EOF

# 8. Sync disk changes
sync
sleep 2

# 9. Unmount
hdiutil detach "$DEVICE"

# 10. Convert to ultra-compressed read-only DMG (UDZO)
hdiutil convert "$TMP_DMG" -format UDZO -imagekey zlib-level=9 -o "$OUTPUT_DMG"
rm -f "$TMP_DMG"

echo "🎉 DMG Başarıyla Oluşturuldu: $OUTPUT_DMG"
ls -lh "$OUTPUT_DMG"
