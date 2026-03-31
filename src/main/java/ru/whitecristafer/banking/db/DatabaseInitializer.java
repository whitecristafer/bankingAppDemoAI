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
