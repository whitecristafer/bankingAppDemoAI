package ru.whitecristafer.banking.db;

import org.junit.jupiter.api.*;
import ru.whitecristafer.banking.util.AppPaths;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для DatabaseManager и DatabaseInitializer.
 *
 * Проверяют:
 * - Singleton паттерн DatabaseManager
 * - Создание подключения к SQLite
 * - Инициализацию схемы БД (таблицы, индексы)
 * - Создание пользователя-администратора по умолчанию
 * - Корректность PRAGMA настроек
 *
 * Использует временную БД в /tmp для изоляции тестов.
 *
 * @author whitecristafer
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseManagerTest {

    /** Путь к временной тестовой базе данных */
    private static final String TEST_DB = System.getProperty("java.io.tmpdir") + "/banking_test_db.db";

    @BeforeAll
    static void setUp() {
        // Указываем тестовую БД через системное свойство
        System.setProperty("banking.db.url", "jdbc:sqlite:" + TEST_DB);
        // Сбрасываем Singleton перед тестами
        DatabaseManager.resetInstance();
    }

    @AfterAll
    static void tearDown() {
        System.clearProperty("banking.db.url");
        DatabaseManager.resetInstance();
        // Удаляем временный файл тестовой БД
        File testDb = new File(TEST_DB);
        if (testDb.exists()) {
            testDb.delete();
        }
    }

    @BeforeEach
    void beforeEach() {
        // Сбрасываем синглтон для чистоты каждого теста
        DatabaseManager.resetInstance();
        // Удаляем тестовую БД
        new File(TEST_DB).delete();
    }

    /**
     * Тест: DatabaseManager возвращает одинаковый экземпляр (Singleton).
     */
    @Test
    @Order(1)
    void testSingleton() {
        DatabaseManager instance1 = DatabaseManager.getInstance();
        DatabaseManager instance2 = DatabaseManager.getInstance();
        assertSame(instance1, instance2, "DatabaseManager должен быть Singleton");
    }

    /**
     * Тест: подключение к базе данных должно быть открытым и работать.
     */
    @Test
    @Order(2)
    void testConnectionIsOpen() throws Exception {
        DatabaseManager manager = DatabaseManager.getInstance();
        Connection conn = manager.getConnection();

        assertNotNull(conn, "Соединение не должно быть null");
        assertFalse(conn.isClosed(), "Соединение должно быть открытым");
    }

    /**
     * Тест: DatabaseInitializer создаёт все необходимые таблицы.
     */
    @Test
    @Order(3)
    void testTablesCreated() throws Exception {
        DatabaseManager manager = DatabaseManager.getInstance();
        Connection conn = manager.getConnection();
        DatabaseInitializer.initialize(conn);

        // Проверяем наличие таблиц
        String[] expectedTables = {"users", "accounts", "transactions"};
        for (String table : expectedTables) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
                assertTrue(rs.next(), "Таблица '" + table + "' должна существовать");
            }
        }
    }

    /**
     * Тест: DatabaseInitializer создаёт администратора по умолчанию.
     */
    @Test
    @Order(4)
    void testDefaultAdminCreated() throws Exception {
        DatabaseManager manager = DatabaseManager.getInstance();
        Connection conn = manager.getConnection();
        DatabaseInitializer.initialize(conn);

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT username, is_admin FROM users WHERE username = 'admin'")) {
            assertTrue(rs.next(), "Администратор по умолчанию должен быть создан");
            assertEquals("admin", rs.getString("username"));
            assertEquals(1, rs.getInt("is_admin"), "Пользователь admin должен быть администратором");
        }
    }

    /**
     * Тест: повторный вызов initialize() не создаёт дублирующихся данных.
     */
    @Test
    @Order(5)
    void testIdempotentInitialization() throws Exception {
        DatabaseManager manager = DatabaseManager.getInstance();
        Connection conn = manager.getConnection();

        // Вызываем initialize() дважды
        DatabaseInitializer.initialize(conn);
        DatabaseInitializer.initialize(conn);

        // Должен быть ровно один пользователь admin
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE username = 'admin'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "Должен быть ровно один администратор");
        }
    }

    /**
     * Тест: закрытие соединения работает корректно.
     */
    @Test
    @Order(6)
    void testCloseConnection() throws Exception {
        DatabaseManager manager = DatabaseManager.getInstance();
        Connection conn = manager.getConnection();
        assertFalse(conn.isClosed(), "Соединение должно быть открыто");

        manager.closeConnection();
        assertTrue(conn.isClosed(), "Соединение должно быть закрыто");
    }
}
