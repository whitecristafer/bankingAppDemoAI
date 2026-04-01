@echo off
chcp 65001 >nul
title BankingApp — Build and Test
echo.
echo ╔══════════════════════════════════════════╗
echo ║   🏗️  BankingApp — Сборка и тесты        ║
echo ╚══════════════════════════════════════════╝
echo.

cd /d "%~dp0.."

java -version >nul 2>&1
if errorlevel 1 (
    echo ОШИБКА: Java не найдена. Установите JDK 17 или выше.
    pause
    exit /b 1
)

echo [1/3] Очистка предыдущей сборки...
call gradlew.bat clean -q

echo [2/3] Компиляция и тесты...
call gradlew.bat build
if errorlevel 1 (
    echo.
    echo ✗ Сборка завершилась с ошибками. Проверьте вывод выше.
    pause
    exit /b 1
)

echo.
echo [3/3] Создание исполняемого JAR...
call gradlew.bat fatJar -q

echo.
echo ╔═══════════════════════════════════════════════════════╗
echo ║  ✓ Сборка успешно завершена!                         ║
echo ║    Исполняемый JAR: build\libs\bankingApp-all.jar    ║
echo ║    Запуск GUI: java -jar build\libs\bankingApp-all.jar --gui    ║
echo ║    Запуск TUI: java -jar build\libs\bankingApp-all.jar --tui    ║
echo ╚═══════════════════════════════════════════════════════╝
echo.
pause
