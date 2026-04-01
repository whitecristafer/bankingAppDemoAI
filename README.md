# 🏦 BankingApp Demo AI

**Демонстрационное банковское приложение** для att.edu.ru / temp project  
Разработчик: **whitecristafer**

---

## 📋 Описание

Полнофункциональное банковское MVP-приложение на Java с двумя интерфейсами:
- **GUI** — современный тёмный интерфейс на **JavaFX**
- **TUI** — цветной терминальный интерфейс на **Lanterna** (управление стрелками + Enter)

Данные хранятся локально в **SQLite** в директории `%APPDATA%\BankingApp\` (Windows).  
Все события и ошибки записываются в **логи** (`%APPDATA%\BankingApp\logs\`).

---

## 🚀 Быстрый старт

### Требования

- **Java 17+** (рекомендуется [Eclipse Temurin](https://adoptium.net/))
- Windows 10/11 (основная платформа) или Linux/macOS (разработка)

### Запуск из командной строки (Windows)

```bat
# GUI-режим (по умолчанию)
scripts\run-gui.bat

# TUI-режим (терминал)
scripts\run-tui.bat

# Сборка и тесты
scripts\build.bat
```

### Запуск через Gradle

```bash
# GUI-режим
./gradlew runGui

# TUI-режим
./gradlew runTui

# Только тесты
./gradlew test

# Полная сборка с тестами
./gradlew build

# Создать fat-JAR (все зависимости внутри)
./gradlew fatJar

# Запуск fat-JAR
java -jar build/libs/bankingApp-all.jar --gui
java -jar build/libs/bankingApp-all.jar --tui
```

### Запуск из IntelliJ IDEA

В папке `.idea/runConfigurations/` уже подготовлены конфигурации:
- **BankingApp — GUI** — запуск графического интерфейса
- **BankingApp — TUI** — запуск терминального интерфейса  
- **BankingApp — Tests** — запуск всех тестов

---

## 🔐 Учётные данные по умолчанию

| Логин | Пароль   | Роль            |
|-------|----------|-----------------|
| admin | admin123 | Администратор   |

> ⚠️ **Смените пароль после первого входа!**

---

## 🖥️ Интерфейсы

### GUI (JavaFX)

- **Экран входа** — авторизация по логину и паролю
- **Дашборд пользователя** — просмотр счетов, пополнение, снятие, переводы, история
- **Панель администратора** — полный CRUD пользователей и счетов

### TUI (Lanterna)

- Цветной терминальный интерфейс с псевдографикой
- Навигация: `↑↓` — перемещение по меню, `Enter` — выбор, `Esc` — назад
- Поддержка Windows CMD, PowerShell и ANSI-терминалов

---

## 🏗️ Архитектура

```
src/main/java/ru/whitecristafer/banking/
├── Main.java                    # Точка входа (--gui / --tui)
├── model/
│   ├── User.java                # Модель пользователя
│   ├── Account.java             # Модель банковского счёта
│   └── Transaction.java         # Модель транзакции
├── db/
│   ├── DatabaseManager.java     # Singleton подключения к SQLite
│   └── DatabaseInitializer.java # Создание схемы БД и начальных данных
├── service/
│   ├── UserService.java         # CRUD + аутентификация пользователей
│   ├── AccountService.java      # CRUD + управление счетами
│   └── TransactionService.java  # Депозит, снятие, перевод, история
├── gui/
│   ├── MainApp.java             # JavaFX Application, контекст сервисов
│   ├── LoginController.java     # Контроллер экрана входа
│   ├── DashboardController.java # Контроллер дашборда пользователя
│   ├── AdminController.java     # Контроллер панели администратора
│   └── SessionContext.java      # Сессия текущего пользователя
├── tui/
│   ├── TuiApp.java              # TUI-приложение (Lanterna)
│   └── TuiScreen.java           # Вспомогательный класс отрисовки
└── util/
    ├── AppPaths.java            # Пути к AppData, БД, логам
    └── PasswordUtil.java        # Хэширование BCrypt, валидация
```

### Хранилище данных (SQLite)

- **users** — пользователи (id, username, password_hash, full_name, email, is_admin, is_blocked)
- **accounts** — счета (id, user_id, account_number, balance, currency, account_type, is_active)
- **transactions** — история операций (id, account_id, type, amount, description, balance_after)

---

## 🛡️ Безопасность

- Пароли хранятся в виде **BCrypt хэша** (cost=12), никогда в открытом виде
- Поддерживаются **внешние ключи** SQLite для целостности данных
- Валидация входных данных на уровне сервиса
- Транзакции БД для атомарности финансовых операций
- Логирование всех ошибок и исключений в `errors.log`

---

## 🧪 Тесты

```bash
./gradlew test
```

Покрытие тестами:
- `UserServiceTest` — 15 тестов: регистрация, аутентификация, CRUD, блокировка
- `AccountServiceTest` — 14 тестов: создание счётов, депозит, снятие, переводы, атомарность
- `PasswordUtilTest` — 10 тестов: BCrypt хэширование, верификация, валидация
- `DatabaseManagerTest` — 6 тестов: Singleton, подключение, схема БД

---

## 📦 Зависимости

| Библиотека | Версия | Назначение |
|---|---|---|
| JavaFX | 17 | GUI |
| Lanterna | 3.1.1 | TUI (цветной терминал) |
| SQLite JDBC | 3.45.1.0 | Локальное хранилище |
| Log4j2 | 2.23.1 | Логирование |
| BCrypt | 0.10.2 | Хэширование паролей |
| JUnit 5 | 5.10.2 | Тестирование |
| Mockito | 5.10.0 | Мокирование в тестах |

---

## 📁 Файлы данных (Windows)

```
%APPDATA%\BankingApp\
├── banking.db          # База данных SQLite
└── logs\
    ├── banking.log     # Основные логи приложения
    └── errors.log      # Только ошибки и исключения
```

---

© 2024 whitecristafer — BankingApp MVP for att.edu.ru

