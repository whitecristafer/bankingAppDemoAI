package ru.whitecristafer.banking.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.whitecristafer.banking.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Инициализатор базы данных — создаёт схему таблиц и начальные данные.
 *
 * Выполняется при первом запуске приложения (или если таблицы отсутствуют).
 * Создаёт:
 * - таблицу users (пользователи)
 * - таблицу accounts (банковские счета)
 * - таблицу transactions (история транзакций)
 * - индексы для ускорения запросов
 * - пользователя-администратора по умолчанию (admin / admin123)
 *
 * @author whitecristafer
 */
public class DatabaseInitializer {

    private static final Logger logger = LogManager.getLogger(DatabaseInitializer.class);

    /** SQL для создания таблицы пользователей */
    private static final String CREATE_USERS_TABLE = """
            CREATE TABLE IF NOT EXISTS users (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                username     TEXT    NOT NULL UNIQUE,
                password_hash TEXT   NOT NULL,
                full_name    TEXT    NOT NULL,
                email        TEXT,
                is_admin     INTEGER NOT NULL DEFAULT 0,
                is_blocked   INTEGER NOT NULL DEFAULT 0,
                created_at   TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
            )
            """;

    /** SQL для создания таблицы банковских счетов */
    private static final String CREATE_ACCOUNTS_TABLE = """
            CREATE TABLE IF NOT EXISTS accounts (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id        INTEGER NOT NULL,
                account_number TEXT    NOT NULL UNIQUE,
                balance        REAL    NOT NULL DEFAULT 0.0,
                currency       TEXT    NOT NULL DEFAULT 'RUB',
                account_type   TEXT    NOT NULL DEFAULT 'CHECKING',
                is_active      INTEGER NOT NULL DEFAULT 1,
                created_at     TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """;

    /** SQL для создания таблицы транзакций */
    private static final String CREATE_TRANSACTIONS_TABLE = """
            CREATE TABLE IF NOT EXISTS transactions (
                id                 INTEGER PRIMARY KEY AUTOINCREMENT,
                account_id         INTEGER NOT NULL,
                type               TEXT    NOT NULL,
                amount             REAL    NOT NULL,
                related_account_id INTEGER,
                description        TEXT,
                balance_after      REAL    NOT NULL,
                created_at         TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
                FOREIGN KEY (account_id) REFERENCES accounts(id)
            )
            """;

    /** Индексы для ускорения типичных запросов */
    private static final String[] CREATE_INDEXES = {
        "CREATE INDEX IF NOT EXISTS idx_accounts_user_id ON accounts(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_transactions_account_id ON transactions(account_id)",
        "CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions(created_at)"
    };

    /** SQL для создания таблицы виртуальных карт */
    private static final String CREATE_VIRTUAL_CARDS_TABLE = """
            CREATE TABLE IF NOT EXISTS virtual_cards (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id          INTEGER NOT NULL,
                account_id       INTEGER NOT NULL,
                card_number      TEXT    NOT NULL UNIQUE,
                card_holder_name TEXT    NOT NULL,
                expiry_month     INTEGER NOT NULL,
                expiry_year      INTEGER NOT NULL,
                cvv_hash         TEXT    NOT NULL,
                card_type        TEXT    NOT NULL DEFAULT 'DEBIT',
                is_active        INTEGER NOT NULL DEFAULT 1,
                created_at       TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
                FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
                FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
            )
            """;

    /** SQL для создания таблицы контактов */
    private static final String CREATE_CONTACTS_TABLE = """
            CREATE TABLE IF NOT EXISTS contacts (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id        INTEGER NOT NULL,
                name           TEXT    NOT NULL,
                phone          TEXT,
                account_number TEXT,
                notes          TEXT,
                created_at     TEXT    NOT NULL DEFAULT (datetime('now', 'localtime')),
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """;

    /** SQL для создания таблицы курсов валют */
    private static final String CREATE_CURRENCY_RATES_TABLE = """
            CREATE TABLE IF NOT EXISTS currency_rates (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                currency_code TEXT    NOT NULL,
                currency_name TEXT    NOT NULL,
                rate          REAL    NOT NULL,
                nominal       INTEGER NOT NULL DEFAULT 1,
                rate_date     TEXT    NOT NULL,
                fetched_at    TEXT    NOT NULL DEFAULT (datetime('now', 'localtime'))
            )
            """;

    /** SQL для создания таблицы настроек приложения */
    private static final String CREATE_APP_SETTINGS_TABLE = """
            CREATE TABLE IF NOT EXISTS app_settings (
                key        TEXT PRIMARY KEY,
                value      TEXT NOT NULL,
                updated_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
            )
            """;

    /** Дополнительные индексы для новых таблиц */
    private static final String[] CREATE_NEW_INDEXES = {
        "CREATE INDEX IF NOT EXISTS idx_virtual_cards_user_id    ON virtual_cards(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_virtual_cards_account_id ON virtual_cards(account_id)",
        "CREATE INDEX IF NOT EXISTS idx_contacts_user_id         ON contacts(user_id)",
        "CREATE INDEX IF NOT EXISTS idx_currency_rates_date      ON currency_rates(rate_date)"
    };

    /**
     * Инициализирует базу данных: создаёт таблицы и заполняет начальными данными.
     * Безопасно вызывать повторно — использует IF NOT EXISTS.
     *
     * @param connection активное подключение к SQLite
     */
    public static void initialize(Connection connection) {
        logger.info("Инициализация схемы базы данных...");
        try {
            createTables(connection);
            createIndexes(connection);
            migrateUsersTable(connection);
            createNewTables(connection);
            createNewIndexes(connection);
            createDefaultAdmin(connection);
            logger.info("База данных успешно инициализирована");
        } catch (SQLException e) {
            logger.error("Ошибка инициализации базы данных", e);
            throw new RuntimeException("Ошибка инициализации БД: " + e.getMessage(), e);
        }
    }

    /**
     * Создаёт все необходимые таблицы в БД.
     *
     * @param connection подключение к SQLite
     * @throws SQLException при ошибке создания таблиц
     */
    private static void createTables(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_USERS_TABLE);
            logger.debug("Таблица 'users' создана или уже существует");

            stmt.execute(CREATE_ACCOUNTS_TABLE);
            logger.debug("Таблица 'accounts' создана или уже существует");

            stmt.execute(CREATE_TRANSACTIONS_TABLE);
            logger.debug("Таблица 'transactions' создана или уже существует");
        }
    }

    /**
     * Создаёт индексы для оптимизации запросов.
     *
     * @param connection подключение к SQLite
     * @throws SQLException при ошибке создания индексов
     */
    private static void createIndexes(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            for (String indexSql : CREATE_INDEXES) {
                stmt.execute(indexSql);
            }
            logger.debug("Индексы созданы");
        }
    }

    /**
     * Выполняет миграцию таблиц users и accounts — добавляет новые столбцы.
     * Использует addColumnIfNotExists для безопасного повторного запуска.
     *
     * @param connection подключение к SQLite
     * @throws SQLException при критической ошибке миграции
     */
    private static void migrateUsersTable(Connection connection) throws SQLException {
        // Новые поля таблицы users
        addColumnIfNotExists(connection, "users", "phone",                 "TEXT");
        addColumnIfNotExists(connection, "users", "passport_series",       "TEXT");
        addColumnIfNotExists(connection, "users", "passport_number",       "TEXT");
        addColumnIfNotExists(connection, "users", "passport_issued_by",    "TEXT");
        addColumnIfNotExists(connection, "users", "passport_issued_date",  "TEXT");
        addColumnIfNotExists(connection, "users", "snils",                 "TEXT");
        addColumnIfNotExists(connection, "users", "inn",                   "TEXT");
        addColumnIfNotExists(connection, "users", "client_type",           "TEXT NOT NULL DEFAULT 'INDIVIDUAL'");
        addColumnIfNotExists(connection, "users", "legal_entity_name",     "TEXT");

        // Новые поля таблицы accounts
        addColumnIfNotExists(connection, "accounts", "account_subtype",    "TEXT NOT NULL DEFAULT 'PERSONAL'");
        addColumnIfNotExists(connection, "accounts", "legal_entity_name",  "TEXT");

        logger.debug("Миграция таблиц users/accounts завершена");
    }

    /**
     * Создаёт новые таблицы: virtual_cards, contacts, currency_rates, app_settings.
     *
     * @param connection подключение к SQLite
     * @throws SQLException при ошибке создания таблиц
     */
    private static void createNewTables(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_VIRTUAL_CARDS_TABLE);
            logger.debug("Таблица 'virtual_cards' создана или уже существует");

            stmt.execute(CREATE_CONTACTS_TABLE);
            logger.debug("Таблица 'contacts' создана или уже существует");

            stmt.execute(CREATE_CURRENCY_RATES_TABLE);
            logger.debug("Таблица 'currency_rates' создана или уже существует");

            stmt.execute(CREATE_APP_SETTINGS_TABLE);
            logger.debug("Таблица 'app_settings' создана или уже существует");
        }
    }

    /**
     * Создаёт дополнительные индексы для новых таблиц.
     *
     * @param connection подключение к SQLite
     * @throws SQLException при ошибке создания индексов
     */
    private static void createNewIndexes(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            for (String indexSql : CREATE_NEW_INDEXES) {
                stmt.execute(indexSql);
            }
            logger.debug("Дополнительные индексы созданы");
        }
    }

    /**
     * Добавляет столбец в таблицу, если он ещё не существует.
     * Безопасен для повторного вызова (не вызывает исключение если столбец уже есть).
     *
     * @param conn       подключение к SQLite
     * @param table      имя таблицы
     * @param column     имя столбца
     * @param definition определение типа столбца (например, "TEXT" или "INTEGER DEFAULT 0")
     */
    private static void addColumnIfNotExists(Connection conn, String table, String column, String definition) {
        // Проверяем наличие столбца через PRAGMA table_info
        String checkSql = "PRAGMA table_info(" + table + ")";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkSql)) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    // Столбец уже существует
                    return;
                }
            }
        } catch (SQLException e) {
            logger.warn("Не удалось проверить столбец {}.{}: {}", table, column, e.getMessage());
            return;
        }

        // Добавляем столбец
        String alterSql = "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(alterSql);
            logger.debug("Добавлен столбец {}.{}", table, column);
        } catch (SQLException e) {
            logger.warn("Не удалось добавить столбец {}.{}: {}", table, column, e.getMessage());
        }
    }

    /**
     * Создаёт пользователя-администратора по умолчанию, если ни одного
     * пользователя ещё нет в базе данных.
     *
     * Учётные данные по умолчанию: admin / admin123
     * ВАЖНО: необходимо сменить пароль при первом входе!
     *
     * @param connection подключение к SQLite
     * @throws SQLException при ошибке работы с БД
     */
    private static void createDefaultAdmin(Connection connection) throws SQLException {
        // Проверяем, есть ли уже пользователи
        String countSql = "SELECT COUNT(*) FROM users";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next() && rs.getInt(1) > 0) {
                logger.debug("Пользователи уже существуют, пропускаем создание администратора по умолчанию");
                return;
            }
        }

        // Создаём администратора
        String insertAdmin = """
                INSERT INTO users (username, password_hash, full_name, email, is_admin)
                VALUES (?, ?, ?, ?, 1)
                """;
        try (PreparedStatement pstmt = connection.prepareStatement(insertAdmin)) {
            String passwordHash = PasswordUtil.hash("admin123");
            pstmt.setString(1, "admin");
            pstmt.setString(2, passwordHash);
            pstmt.setString(3, "Администратор системы");
            pstmt.setString(4, "admin@banking.local");
            pstmt.executeUpdate();
            logger.info("Создан администратор по умолчанию: login=admin, password=admin123");
            logger.warn("ВАЖНО: Смените пароль администратора после первого входа!");
        }
    }
}
