@echo off
REM =========================================================
REM  🐘 PostgreSQL DDL Studio - Windows Portable Launcher
REM =========================================================
title PostgreSQL DDL Studio

setlocal EnableDelayedExpansion

REM Check Java Runtime
where javaw >nul 2>nul
if %errorlevel% neq 0 (
    where java >nul 2>nul
    if !errorlevel! neq 0 (
        echo.
        echo [HATA] Sisteminizde Java 17 veya ustu bulunamadi!
        echo Lutfen OpenJDK 17+ yukleyin: https://adoptium.net/
        echo.
        pause
        exit /b 1
    )
    start "" java -jar "%~dp0PostgreSQL-DDL-Studio.jar" %*
    exit /b 0
)

start "" javaw -jar "%~dp0PostgreSQL-DDL-Studio.jar" %*
exit /b 0
