#!/usr/bin/env bash
# PostgreSQL DDL Studio - Linux Universal Launcher
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/../.." && pwd )"
cd "$DIR"

# Detect Java 17+
if ! command -v java &> /dev/null; then
    echo "[HATA] Sisteminizde Java bulunamadı. Lütfen Java 17 veya üstünü yükleyin: sudo apt install openjdk-17-jre"
    exit 1
fi

JAR_PATH="$DIR/target/postgres_ddl_export_console_java-1.0.0.jar"
if [ ! -f "$JAR_PATH" ]; then
    echo "[BİLGİ] JAR dosyası derleniyor..."
    if command -v mvn &> /dev/null; then
        mvn clean package -DskipTests
    fi
fi

echo "PostgreSQL DDL Studio Linux üzerinde başlatılıyor..."
exec java -jar "$JAR_PATH" "$@"
