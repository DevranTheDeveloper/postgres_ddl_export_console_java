@echo off
REM ========================================================================
REM  🐘 PostgreSQL DDL Studio - Windows Akıllı Başlatıcı (Launcher)
REM ========================================================================
title PostgreSQL DDL Studio

setlocal EnableDelayedExpansion

set "JAVA_CMD="

REM 1. Check bundled runtime if exists in app directory
if exist "%~dp0runtime\bin\javaw.exe" (
    set "JAVA_CMD=%~dp0runtime\bin\javaw.exe"
    goto :LAUNCH
)

REM 2. Check standard Java 17/21/22/23 installation directories on 64-bit Windows
for /d %%i in (
    "%ProgramFiles%\Eclipse Adoptium\jdk-17*"
    "%ProgramFiles%\Eclipse Adoptium\jre-17*"
    "%ProgramFiles%\Eclipse Adoptium\jdk-21*"
    "%ProgramFiles%\Eclipse Adoptium\jre-21*"
    "%ProgramFiles%\Java\jdk-17*"
    "%ProgramFiles%\Java\jdk-21*"
    "%ProgramFiles%\Java\jdk-22*"
    "%ProgramFiles%\Java\jdk-23*"
    "%ProgramFiles%\Microsoft\jdk-17*"
    "%ProgramFiles%\Microsoft\jdk-21*"
    "%ProgramFiles%\Amazon Corretto\jdk17*"
    "%ProgramFiles%\Amazon Corretto\jdk21*"
    "%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-17*"
    "%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-21*"
) do (
    if exist "%%i\bin\javaw.exe" (
        set "JAVA_CMD=%%i\bin\javaw.exe"
        goto :LAUNCH
    )
)

REM 3. Check JAVA_HOME if set
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javaw.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\javaw.exe"
        goto :LAUNCH
    )
)

REM 4. Check system java version in PATH
where java >nul 2>nul
if %errorlevel% equ 0 (
    for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
        set "RAW_VER=%%v"
    )
    set "RAW_VER=!RAW_VER:"=!"

    for /f "tokens=1,2 delims=." %%a in ("!RAW_VER!") do (
        if "%%a"=="1" (
            set "MAJOR_VER=%%b"
        ) else (
            set "MAJOR_VER=%%a"
        )
    )

    if defined MAJOR_VER (
        if !MAJOR_VER! GEQ 17 (
            where javaw >nul 2>nul
            if !errorlevel! equ 0 (
                set "JAVA_CMD=javaw"
            ) else (
                set "JAVA_CMD=java"
            )
            goto :LAUNCH
        )
    )
)

REM 5. If Java 17+ is not found, display clear explanation and open download page
echo.
echo ========================================================================
echo   [UYARI] PostgreSQL DDL Studio icin Java 17+ Gereklidir!
echo ========================================================================
echo.
echo   Sisteminizdeki mevcut Java surumu eski (Java 8/11) veya bulunamadi.
echo   Bu uygulama modern Java 17 LTS ile calismaktadir.
echo.
echo   Lutfen ucretsiz resmi OpenJDK 17 yukleyiniz:
echo   --^> https://adoptium.net/temurin/releases/?version=17
echo.
echo   Indirme sayfasi simdi aciliyor...
start https://adoptium.net/temurin/releases/?version=17
echo.
echo   Kurulumu tamamladiktan sonra uygulamayi yeniden baslatiniz.
echo ========================================================================
echo.
pause
exit /b 1

:LAUNCH
cd /d "%~dp0"
start "" "%JAVA_CMD%" -jar "PostgreSQL-DDL-Studio.jar" %*
exit /b 0
