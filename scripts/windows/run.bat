@echo off
REM PostgreSQL DDL Studio - Windows Launcher
title PostgreSQL DDL Studio

where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [HATA] Java bulunamadi. Lutfen Java 17 veya ustunu yukleyin.
    pause
    exit /b 1
)

cd /d "%~dp0\..\.."

if not exist "target\postgres_ddl_export_console_java-1.0.0.jar" (
    echo [BILGI] Uygulama derleniyor...
    call mvn clean package -DskipTests
)

start javaw -jar "target\postgres_ddl_export_console_java-1.0.0.jar" %*
exit /b 0
