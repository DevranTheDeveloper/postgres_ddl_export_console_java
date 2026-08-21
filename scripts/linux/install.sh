#!/usr/bin/env bash
# ========================================================================
#  🐧 PostgreSQL DDL Studio - Linux One-Click Installer & Desktop Integrator
# ========================================================================
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
APP_NAME="PostgreSQL DDL Studio"
DESKTOP_FILE="$DIR/postgres-ddl-studio.desktop"
ICON_FILE="$DIR/app_icon.png"
TARGET_DIR="$HOME/.local/share/postgresql-ddl-studio"
BIN_LINK="$HOME/.local/bin/postgresql-ddl-studio"

echo "=== 🐘 PostgreSQL DDL Studio Kurulum Sihirbazı ==="

# 1. Check Java 17+
if ! command -v java &> /dev/null; then
    echo "[UYARI] Sistemde Java bulunamadı."
    echo "Lütfen şu komutla Java 17 kurun: sudo apt update && sudo apt install -y openjdk-17-jre"
fi

# 2. Copy application files to ~/.local/share/postgresql-ddl-studio
mkdir -p "$TARGET_DIR"
cp "$DIR/PostgreSQL-DDL-Studio.jar" "$TARGET_DIR/" 2>/dev/null || cp "$DIR/target/postgres_ddl_export_console_java-1.0.0.jar" "$TARGET_DIR/PostgreSQL-DDL-Studio.jar"
cp "$DIR/run.sh" "$TARGET_DIR/"
cp "$ICON_FILE" "$TARGET_DIR/"
chmod +x "$TARGET_DIR/run.sh"

# 3. Create Desktop shortcut in ~/.local/share/applications/
mkdir -p "$HOME/.local/share/applications"
cat << EOF > "$HOME/.local/share/applications/postgres-ddl-studio.desktop"
[Desktop Entry]
Version=1.0
Type=Application
Name=PostgreSQL DDL Studio
Comment=Modern PostgreSQL Schema, ERD & Live Diff Studio
Exec=$TARGET_DIR/run.sh
Icon=$TARGET_DIR/app_icon.png
Terminal=false
Categories=Development;Database;IDE;
StartupNotify=true
EOF

chmod +x "$HOME/.local/share/applications/postgres-ddl-studio.desktop"

# 4. Create command line symlink in ~/.local/bin
mkdir -p "$HOME/.local/bin"
ln -sf "$TARGET_DIR/run.sh" "$BIN_LINK"

# 5. Update Desktop database cache if tool exists
update-desktop-database "$HOME/.local/share/applications" 2>/dev/null || true

echo "======================================================="
echo "🎉 Kurulum Tamamlandı!"
echo " Artık uygulamayı:"
echo " 1. Kali / Linux 'Uygulamalar' menüsünden tek tıkla aratarak,"
echo " 2. Terminalde doğrudan 'postgresql-ddl-studio' yazarak,"
echo " açabilirsiniz!"
echo "======================================================="
