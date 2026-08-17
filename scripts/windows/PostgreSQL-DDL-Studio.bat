@echo off
REM ========================================================================
REM  🐘 PostgreSQL DDL Studio - Windows Akıllı Başlatıcı (Launcher)
REM ========================================================================
title PostgreSQL DDL Studio

setlocal EnableDelayedExpansion

set "JAVA_CMD="

REM 1. Check JAVA_HOME if set
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javaw.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\javaw.exe"
    ) else if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
    )
)

REM 2. Check standard system path
if "%JAVA_CMD%"=="" (
    where javaw >nul 2>nul
    if !errorlevel! equ 0 (
        set "JAVA_CMD=javaw"
    ) else (
        where java >nul 2>nul
        if !errorlevel! equ 0 (
            set "JAVA_CMD=java"
        )
    )
)

REM 3. Check common Java 17/21 installation directories
if "%JAVA_CMD%"=="" (
    for /d %%i in ("%ProgramFiles%\Eclipse Adoptium\jdk-17*" "%ProgramFiles%\Eclipse Adoptium\jdk-21*" "%ProgramFiles%\Java\jdk-17*" "%ProgramFiles%\Java\jdk-21*" "%ProgramFiles%\Microsoft\jdk-17*" "%ProgramFiles%\Amazon Corretto\jdk17*") do (
        if exist "%%i\bin\javaw.exe" (
            set "JAVA_CMD=%%i\bin\javaw.exe"
        )
    )
)

REM 4. If still no java found, show error and open download page
if "%JAVA_CMD%"=="" (
    echo.
    echo ========================================================================
    echo  [UYARI] Sisteminizde Java 17 veya daha guncel bir Java bulunamadi!
    echo ========================================================================
    echo.
    echo  PostgreSQL DDL Studio calismak icin Java 17+ gerektirir.
    echo  Lutfen ucretsiz OpenJDK 17 yukleyin:
    echo  https://adoptium.net/temurin/releases/?version=17
    echo.
    echo  Indirme sayfasi simdi aciliyor...
    start https://adoptium.net/temurin/releases/?version=17
    echo.
    pause
    exit /b 1
)

REM 5. Check Java Version
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "RAW_VER=%%v"
)
set "RAW_VER=%RAW_VER:"=%"

REM Extract major version (handles 17.0.x, 21.0.x, or 1.8.0)
for /f "tokens=1,2 delims=." %%a in ("%RAW_VER%") do (
    if "%%a"=="1" (
        set "MAJOR_VER=%%b"
    ) else (
        set "MAJOR_VER=%%a"
    )
)

if defined MAJOR_VER (
    if %MAJOR_VER% LSS 17 (
        echo.
        echo ========================================================================
        echo  [UYARI] Mevcut Java surumunuz (Java %RAW_VER%) bu uygulama icin eski!
        echo ========================================================================
        echo.
        echo  PostgreSQL DDL Studio en az Java 17 gerektirir.
        echo  Lutfen ucretsiz Java 17 LTS yukleyin:
        echo  https://adoptium.net/temurin/releases/?version=17
        echo.
        echo  Indirme sayfasi simdi aciliyor...
        start https://adoptium.net/temurin/releases/?version=17
        echo.
        pause
        exit /b 1
    )
)

REM 6. Launch Application
cd /d "%~dp0"
start "" "%JAVA_CMD%" -jar "PostgreSQL-DDL-Studio.jar" %*
exit /b 0
