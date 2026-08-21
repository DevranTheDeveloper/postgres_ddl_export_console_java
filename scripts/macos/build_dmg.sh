#!/bin/bash
set -e

VOL_NAME="PostgreSQL DDL Studio"
APP_NAME="PostgreSQL DDL Studio.app"

if [ -n "$APP_PATH" ] && [ -d "$APP_PATH" ]; then
    RESOLVED_APP_PATH="$APP_PATH"
elif [ -d "/Users/devransever/Desktop/$APP_NAME" ]; then
    RESOLVED_APP_PATH="/Users/devransever/Desktop/$APP_NAME"
elif [ -d "$APP_NAME" ]; then
    RESOLVED_APP_PATH="$APP_NAME"
else
    # Build the .app directory on the fly
    RESOLVED_APP_PATH="/tmp/$APP_NAME"
    rm -rf "$RESOLVED_APP_PATH"
    mkdir -p "$RESOLVED_APP_PATH/Contents/MacOS"
    mkdir -p "$RESOLVED_APP_PATH/Contents/Resources"
    cp src/main/resources/AppIcon.icns "$RESOLVED_APP_PATH/Contents/Resources/AppIcon.icns"
    cp target/postgres_ddl_export_console_java-1.0.0.jar "$RESOLVED_APP_PATH/Contents/MacOS/app.jar"
    
    cp scripts/macos/launcher "$RESOLVED_APP_PATH/Contents/MacOS/launcher"
    chmod 755 "$RESOLVED_APP_PATH/Contents/MacOS/launcher"
    
    cat << 'EOF' > "$RESOLVED_APP_PATH/Contents/Info.plist"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key>
    <string>PostgreSQL DDL Studio</string>
    <key>CFBundleDisplayName</key>
    <string>PostgreSQL DDL Studio</string>
    <key>CFBundleIdentifier</key>
    <string>com.ddlexporter.app</string>
    <key>CFBundleVersion</key>
    <string>5.5.5</string>
    <key>CFBundleShortVersionString</key>
    <string>5.5.5</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleExecutable</key>
    <string>launcher</string>
    <key>CFBundleIconFile</key>
    <string>AppIcon</string>
    <key>NSHumanReadableCopyright</key>
    <string>Copyright © 2026 Devran Sever. All rights reserved.</string>
    <key>LSMinimumSystemVersion</key>
    <string>10.15</string>
</dict>
</plist>
EOF
    xattr -cr "$RESOLVED_APP_PATH" 2>/dev/null || true
    codesign --force --deep --sign - "$RESOLVED_APP_PATH" 2>/dev/null || true
fi

OUTPUT_DMG="${OUTPUT_DMG:-/Users/devransever/Desktop/PostgreSQL-DDL-Studio-5.5.5-macOS.dmg}"
BG_IMG="scripts/macos/dmg_bg.png"
BG_IMG_2X="scripts/macos/dmg_bg@2x.png"

TMP_DMG="/tmp/temp_build_$$.dmg"
MOUNT_DIR="/Volumes/$VOL_NAME"

echo "=== 🍏 macOS Drag-and-Drop DMG Kurulum Paketi Oluşturuluyor ==="
echo "App Yolu: $RESOLVED_APP_PATH"
echo "Çıktı DMG: $OUTPUT_DMG"

# 1. Clean previous artifacts
rm -f "$OUTPUT_DMG" "$TMP_DMG"
hdiutil detach "$MOUNT_DIR" 2>/dev/null || true

# 2. Calculate required size
APP_SIZE_MB=$(du -sm "$RESOLVED_APP_PATH" | awk '{print $1}')
DMG_SIZE_MB=$((APP_SIZE_MB + 50))

# 3. Create temporary read-write DMG
hdiutil create -size "${DMG_SIZE_MB}m" -volname "$VOL_NAME" -fs HFS+ "$TMP_DMG"

# 4. Mount the DMG
DEVICE=$(hdiutil attach -readwrite -noverify -noautoopen "$TMP_DMG" | grep Apple_HFS | awk '{print $1}')
echo "Mount edildi: $DEVICE -> $MOUNT_DIR"

# 5. Copy App & Applications Symlink
cp -R "$RESOLVED_APP_PATH" "$MOUNT_DIR/"
ln -s /Applications "$MOUNT_DIR/Applications"

# 6. Add background folder & images
mkdir -p "$MOUNT_DIR/.background"
cp "$BG_IMG" "$MOUNT_DIR/.background/bg.png" 2>/dev/null || true
cp "$BG_IMG_2X" "$MOUNT_DIR/.background/bg@2x.png" 2>/dev/null || true

# 7. Use AppleScript to set Finder window size, icon positions, and view options
echo "Finder pencere düzeni ve simge konumları ayarlanıyor..."
osascript << EOF || true
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
sleep 1

# 9. Unmount
hdiutil detach "$DEVICE" -force || hdiutil detach "$MOUNT_DIR" -force

# 10. Convert to ultra-compressed read-only DMG (UDZO)
hdiutil convert "$TMP_DMG" -format UDZO -imagekey zlib-level=9 -o "$OUTPUT_DMG"
rm -f "$TMP_DMG"

echo "🎉 DMG Başarıyla Oluşturuldu: $OUTPUT_DMG"
ls -lh "$OUTPUT_DMG"
