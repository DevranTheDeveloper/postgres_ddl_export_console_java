#!/bin/bash
# ========================================================================
#  🍏 PostgreSQL DDL Studio - macOS Gatekeeper / Güvenlik İzin Aracı
# ========================================================================
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "=========================================================="
echo "  🐘 PostgreSQL DDL Studio - macOS Güvenlik İzin Sihirbazı"
echo "=========================================================="
echo ""
echo "macOS Gatekeeper ve Karantina koruması temizleniyor..."

# 1. Clear quarantine on /Applications if copied
xattr -cr "/Applications/PostgreSQL DDL Studio.app" 2>/dev/null || true
xattr -d com.apple.quarantine "/Applications/PostgreSQL DDL Studio.app" 2>/dev/null || true

# 2. Clear quarantine on local DMG if running from volume
xattr -cr "$DIR/PostgreSQL DDL Studio.app" 2>/dev/null || true
xattr -d com.apple.quarantine "$DIR/PostgreSQL DDL Studio.app" 2>/dev/null || true

echo "İzinler başarıyla tanımlandı! Uygulama açılıyor..."

if [ -d "/Applications/PostgreSQL DDL Studio.app" ]; then
    open "/Applications/PostgreSQL DDL Studio.app"
elif [ -d "$DIR/PostgreSQL DDL Studio.app" ]; then
    open "$DIR/PostgreSQL DDL Studio.app"
fi

osascript -e 'display notification "PostgreSQL DDL Studio güvenlik kilidi başarıyla açıldı!" with title "PostgreSQL DDL Studio"' 2>/dev/null || true

echo ""
echo "=========================================================="
echo "  ✅ Tamamlandı! Bu terminal penceresini kapatabilirsiniz."
echo "=========================================================="
exit 0
