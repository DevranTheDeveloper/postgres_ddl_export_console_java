#!/usr/bin/env bash
# ========================================================================
#  🐧 PostgreSQL DDL Studio - Debian / Kali / Ubuntu .deb Package Builder
# ========================================================================
set -e

VERSION="5.5.8"
DEB_DIR="build_deb/postgresql-ddl-studio_${VERSION}_all"
OUTPUT_DEB="PostgreSQL-DDL-Studio-${VERSION}-Linux.deb"

rm -rf build_deb "$OUTPUT_DEB"
mkdir -p "$DEB_DIR/DEBIAN"
mkdir -p "$DEB_DIR/usr/bin"
mkdir -p "$DEB_DIR/usr/share/applications"
mkdir -p "$DEB_DIR/usr/share/icons/hicolor/256x256/apps"
mkdir -p "$DEB_DIR/usr/share/postgresql-ddl-studio"

# 1. Control file
cat << EOF > "$DEB_DIR/DEBIAN/control"
Package: postgresql-ddl-studio
Version: ${VERSION}
Section: devel
Priority: optional
Architecture: all
Depends: default-jre (>= 2:1.17) | openjdk-17-jre | openjdk-21-jre
Maintainer: DevranTheDeveloper <devran@github.com>
Description: PostgreSQL DDL Export Studio - Modern GUI for Schemas, ERD & Live Diff
 A modern, high-performance PostgreSQL Schema Explorer, visual ERD mapper,
 and live schema diff engine.
EOF

# 2. Files
if [ -f "target/postgres_ddl_export_console_java-1.0.0.jar" ]; then
    cp target/postgres_ddl_export_console_java-1.0.0.jar "$DEB_DIR/usr/share/postgresql-ddl-studio/PostgreSQL-DDL-Studio.jar"
elif [ -f "PostgreSQL-DDL-Studio.jar" ]; then
    cp PostgreSQL-DDL-Studio.jar "$DEB_DIR/usr/share/postgresql-ddl-studio/PostgreSQL-DDL-Studio.jar"
fi

cp scripts/linux/run.sh "$DEB_DIR/usr/share/postgresql-ddl-studio/run.sh"
chmod +x "$DEB_DIR/usr/share/postgresql-ddl-studio/run.sh"
cp src/main/resources/app_icon.png "$DEB_DIR/usr/share/icons/hicolor/256x256/apps/postgresql-ddl-studio.png"
cp src/main/resources/app_icon.png "$DEB_DIR/usr/share/postgresql-ddl-studio/app_icon.png"

# 3. Executable launcher in /usr/bin
cat << 'EOF' > "$DEB_DIR/usr/bin/postgresql-ddl-studio"
#!/usr/bin/env bash
exec /usr/share/postgresql-ddl-studio/run.sh "$@"
EOF
chmod +x "$DEB_DIR/usr/bin/postgresql-ddl-studio"

# 4. Desktop entry
cat << 'EOF' > "$DEB_DIR/usr/share/applications/postgres-ddl-studio.desktop"
[Desktop Entry]
Version=1.0
Type=Application
Name=PostgreSQL DDL Studio
Comment=Modern PostgreSQL Schema, ERD & Live Diff Studio
Exec=/usr/bin/postgresql-ddl-studio
Icon=postgresql-ddl-studio
Terminal=false
Categories=Development;Database;IDE;
StartupNotify=true
EOF

# 5. Build .deb with dpkg-deb or fallback
if command -v dpkg-deb &> /dev/null; then
    dpkg-deb --build "$DEB_DIR" "$OUTPUT_DEB"
    echo "🎉 .deb Paketi Başarıyla Oluşturuldu: $OUTPUT_DEB"
else
    echo "[BİLGİ] dpkg-deb sistemde yüklü değil, GitHub Actions CI üzerinde oluşturulacak."
fi
