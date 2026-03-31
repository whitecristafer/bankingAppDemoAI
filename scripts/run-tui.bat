@echo off
chcp 65001 >nul
title BankingApp — TUI Mode
echo.
echo ╔═══════════════════════════════════════╗
echo ║   🏦 BankingApp — Запуск TUI режима  ║
echo ╚═══════════════════════════════════════╝
echo.

REM Переходим в корневую директорию проекта
cd /d "%~dp0.."

REM Проверяем наличие Java
java -version >nul 2>&1
if errorlevel 1 (
    echo ОШИБКА: Java не найдена. Установите JDK 17 или выше.
    echo Скачать: https://adoptium.net/
    pause
    exit /b 1
)

echo Сборка проекта...
call gradlew.bat compileJava -q
if errorlevel 1 (
    echo ОШИБКА сборки. Запустите gradlew.bat build для деталей.
    pause
    exit /b 1
)

echo Запуск TUI...
echo Управление: стрелки ↑↓ — навигация, Enter — выбор, Esc — назад
echo.
call gradlew.bat runTui
