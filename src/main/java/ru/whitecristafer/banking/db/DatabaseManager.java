package ru.whitecristafer.banking.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.whitecristafer.banking.util.AppPaths;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Менеджер подключения к базе данных SQLite.
 * Реализует паттерн Singleton — в приложении существует одно подключение к БД.
 *
 * Отвечает за:
 * - создание и хранение единственного подключения к SQLite
 * - настройку прагм SQLite (WAL, foreign keys)
 * - закрытие подключения при завершении работы
 *
 * Все операции с БД должны выполняться через {@link #getConnection()}.
 *
 * @author whitecristafer
 */
public class DatabaseManager {

    private static final Logger logger = LogManager.getLogger(DatabaseManager.class);

    /** Единственный экземпляр менеджера (Singleton) */
    private static DatabaseManager instance;

    /** Активное подключение к SQLite */
    private Connection connection;

    /**
     * Приватный конструктор — инициализирует подключение к базе данных.
     * Вызывается единожды при первом обращении к {@link #getInstance()}.
     */
    private DatabaseManager() {
        initConnection();
    }

    /**
     * Возвращает единственный экземпляр DatabaseManager (потокобезопасный Singleton).
     *
     * @return экземпляр DatabaseManager
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Инициализирует JDBC подключение к файлу SQLite.
     * Путь к файлу БД определяется через {@link AppPaths#getDatabaseUrl()},
     * но может быть переопределён через системное свойство "banking.db.url" (для тестов).
     * Настраивает прагмы для производительности и целостности данных.
     */
    private void initConnection() {
        try {
            // Позволяем переопределить URL базы данных через системное свойство (для тестов)
            String customUrl = System.getProperty("banking.db.url");
            String url = (customUrl != null && !customUrl.isEmpty()) ? customUrl : AppPaths.getDatabaseUrl();
            logger.info("Подключение к базе данных: {}", url);
            connection = DriverManager.getConnection(url);

            // Включаем поддержку внешних ключей в SQLite
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                // WAL режим — лучшая производительность для конкурентного чтения
                stmt.execute("PRAGMA journal_mode = WAL");
                // Синхронизация — нормальный режим (баланс скорости и надёжности)
                stmt.execute("PRAGMA synchronous = NORMAL");
            }

            logger.info("База данных успешно подключена");
        } catch (SQLException e) {
            logger.error("Ошибка подключения к базе данных", e);
            throw new RuntimeException("Не удалось подключиться к базе данных: " + e.getMessage(), e);
        }
    }

    /**
     * Возвращает активное подключение к базе данных.
     * При разрыве соединения автоматически переподключается.
     *
     * @return активный объект {@link Connection}
     * @throws RuntimeException если не удалось восстановить соединение
     */
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                logger.warn("Соединение с БД потеряно, переподключаемся...");
                initConnection();
            }
        } catch (SQLException e) {
            logger.error("Ошибка проверки состояния соединения с БД", e);
            initConnection();
        }
        return connection;
    }

    /**
     * Закрывает подключение к базе данных.
     * Должен вызываться при завершении работы приложения.
     */
    public void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Соединение с базой данных закрыто");
            } catch (SQLException e) {
                logger.error("Ошибка закрытия соединения с базой данных", e);
            }
        }
    }

    /**
     * Сбрасывает Singleton — используется только в тестах.
     * В продакшне не применять!
     */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.closeConnection();
            instance = null;
        }
    }
}
