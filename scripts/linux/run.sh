#!/usr/bin/env bash
# ========================================================================
#  🐘 PostgreSQL DDL Studio - Linux Smart Launcher (Kali / Ubuntu / Debian)
# ========================================================================
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$DIR"

# 0. Auto-apply pending update if present
if [ -f "$DIR/update_pending.jar" ]; then
    cp -f "$DIR/update_pending.jar" "$DIR/PostgreSQL-DDL-Studio.jar" 2>/dev/null || true
    rm -f "$DIR/update_pending.jar" 2>/dev/null || true
fi

# 1. Locate Application JAR
JAR_PATH=""
for candidate in \
    "$DIR/PostgreSQL-DDL-Studio.jar" \
    "$DIR/postgres_ddl_export_console_java-1.0.0.jar" \
    "$DIR/target/postgres_ddl_export_console_java-1.0.0.jar" \
    "$DIR/../../target/postgres_ddl_export_console_java-1.0.0.jar"; do
    if [ -f "$candidate" ]; then
        JAR_PATH="$candidate"
        break
    fi
done

if [ -z "$JAR_PATH" ]; then
    MSG="[HATA] PostgreSQL DDL Studio JAR dosyası bulunamadı!\nLütfen arşivin doğru çıkarıldığından emin olun."
    echo -e "$MSG"
    if command -v zenity &> /dev/null; then
        zenity --error --title="PostgreSQL DDL Studio" --text="$MSG" 2>/dev/null || true
    elif command -v kdialog &> /dev/null; then
        kdialog --error "$MSG" 2>/dev/null || true
    fi
    exit 1
fi

# 2. Locate Java 17+ Runtime on Linux
JAVA_CMD=""

# A. Check PATH java
if command -v java &> /dev/null; then
    VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [ "$VER" = "1" ]; then
        VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f2)
    fi
    if [ -n "$VER" ] && [ "$VER" -ge 17 ] 2>/dev/null; then
        JAVA_CMD="java"
    fi
fi

# B. Scan standard Linux JVM directories
if [ -z "$JAVA_CMD" ]; then
    for jvm_path in \
        /usr/lib/jvm/java-21-openjdk-*/bin/java \
        /usr/lib/jvm/java-17-openjdk-*/bin/java \
        /usr/lib/jvm/temurin-21-*/bin/java \
        /usr/lib/jvm/temurin-17-*/bin/java \
        /usr/lib/jvm/zulu-21-*/bin/java \
        /usr/lib/jvm/zulu-17-*/bin/java \
        /usr/lib/jvm/default-java/bin/java \
        /opt/jdk-17*/bin/java \
        /opt/jdk-21*/bin/java \
        "$HOME"/.jdks/*/bin/java \
        "$HOME"/.sdkman/candidates/java/current/bin/java; do
        if [ -x "$jvm_path" ]; then
            VER=$("$jvm_path" -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
            if [ "$VER" = "1" ]; then
                VER=$("$jvm_path" -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f2)
            fi
            if [ -n "$VER" ] && [ "$VER" -ge 17 ] 2>/dev/null; then
                JAVA_CMD="$jvm_path"
                break
            fi
        fi
    done
fi

# 3. If no Java 17+ found, alert user with exact distro command
if [ -z "$JAVA_CMD" ]; then
    ERROR_TITLE="Java 17 Gerekli (PostgreSQL DDL Studio)"
    ERROR_TEXT="PostgreSQL DDL Studio'yu çalıştırmak için Java 17 veya üstü gereklidir.\n\nKali / Debian / Ubuntu için terminalde çalıştırın:\n  sudo apt update && sudo apt install -y openjdk-17-jre\n\nFedora için:\n  sudo dnf install java-17-openjdk\n\nArch Linux için:\n  sudo pacman -S jre17-openjdk"

    echo -e "\n======================================================="
    echo -e " [UYARI] $ERROR_TITLE"
    echo -e "======================================================="
    echo -e "$ERROR_TEXT"
    echo -e "=======================================================\n"

    if command -v zenity &> /dev/null; then
        zenity --error --title="$ERROR_TITLE" --text="$ERROR_TEXT" --width=450 2>/dev/null || true
    elif command -v kdialog &> /dev/null; then
        kdialog --error "$ERROR_TEXT" --title "$ERROR_TITLE" 2>/dev/null || true
    elif command -v xmessage &> /dev/null; then
        echo -e "$ERROR_TEXT" | xmessage -file - -title "$ERROR_TITLE" -center -buttons OK:0 2>/dev/null || true
    fi

    exit 1
fi

# 4. Launch Application
echo "PostgreSQL DDL Studio Linux üzerinde başlatılıyor... (Java: $JAVA_CMD)"
exec "$JAVA_CMD" -jar "$JAR_PATH" "$@"
