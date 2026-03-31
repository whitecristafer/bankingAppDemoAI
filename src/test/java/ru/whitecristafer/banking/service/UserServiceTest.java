package ru.whitecristafer.banking.service;

import org.junit.jupiter.api.*;
import ru.whitecristafer.banking.db.DatabaseInitializer;
import ru.whitecristafer.banking.db.DatabaseManager;
import ru.whitecristafer.banking.model.User;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для UserService — проверяют регистрацию, аутентификацию и CRUD пользователей.
 *
 * Каждый тест работает с собственной изолированной базой данных в /tmp.
 * Тесты охватывают:
 * - регистрацию нового пользователя
 * - валидацию входных данных (пустой логин, слабый пароль, дубликат)
 * - аутентификацию (успешную и неуспешную)
 * - блокировку пользователя
 * - удаление пользователя
 * - назначение прав администратора
 *
 * @author whitecristafer
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserServiceTest {

    private static DatabaseManager dbManager;
    private static UserService userService;
    private static final String TEST_DB = System.getProperty("java.io.tmpdir") + "/banking_user_test.db";

    @BeforeAll
    static void setUpAll() {
        new File(TEST_DB).delete();
        System.setProperty("banking.db.url", "jdbc:sqlite:" + TEST_DB);
        DatabaseManager.resetInstance();
        dbManager = DatabaseManager.getInstance();
        DatabaseInitializer.initialize(dbManager.getConnection());
        userService = new UserService(dbManager);
    }

    @AfterAll
    static void tearDownAll() {
        System.clearProperty("banking.db.url");
        DatabaseManager.resetInstance();
        new File(TEST_DB).delete();
    }

    /**
     * Тест: успешная регистрация нового пользователя.
     */
    @Test
    @Order(1)
    void testRegisterSuccess() {
        User user = userService.register("testuser1", "password1", "Тестовый Пользователь", "test@example.com");

        assertNotNull(user, "Зарегистрированный пользователь не должен быть null");
        assertTrue(user.getId() > 0, "ID пользователя должен быть положительным");
        assertEquals("testuser1", user.getUsername());
        assertEquals("Тестовый Пользователь", user.getFullName());
        assertFalse(user.isAdmin(), "Новый пользователь не должен быть администратором");
        assertFalse(user.isBlocked(), "Новый пользователь не должен быть заблокирован");
    }

    /**
     * Тест: регистрация с пустым именем пользователя должна выбрасывать исключение.
     */
    @Test
    @Order(2)
    void testRegisterEmptyUsername() {
        assertThrows(IllegalArgumentException.class,
                () -> userService.register("", "password1", "Имя", "email@test.com"),
                "Пустой логин должен вызывать IllegalArgumentException");
    }

    /**
     * Тест: регистрация со слабым паролем (менее 6 символов).
     */
    @Test
    @Order(3)
    void testRegisterWeakPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> userService.register("user2", "abc", "Имя", "email@test.com"),
                "Слабый пароль должен вызывать IllegalArgumentException");
    }

    /**
     * Тест: регистрация с паролем без цифр должна выбрасывать исключение.
     */
    @Test
    @Order(4)
    void testRegisterPasswordWithoutDigit() {
        assertThrows(IllegalArgumentException.class,
                () -> userService.register("user3", "password", "Имя", "email@test.com"),
                "Пароль без цифр должен вызывать IllegalArgumentException");
    }

    /**
     * Тест: регистрация дублирующегося имени пользователя.
     */
    @Test
    @Order(5)
    void testRegisterDuplicateUsername() {
        userService.register("dupuser", "password1", "Дублирующийся", "dup@test.com");
        assertThrows(IllegalArgumentException.class,
                () -> userService.register("dupuser", "password2", "Дубль 2", "dup2@test.com"),
                "Дублирующийся логин должен вызывать IllegalArgumentException");
    }

    /**
     * Тест: успешная аутентификация пользователя.
     */
    @Test
    @Order(6)
    void testAuthenticateSuccess() {
        userService.register("authuser", "mypass1", "Auth User", "auth@test.com");
        User authenticated = userService.authenticate("authuser", "mypass1");

        assertNotNull(authenticated);
        assertEquals("authuser", authenticated.getUsername());
    }

    /**
     * Тест: аутентификация с неверным паролем.
     */
    @Test
    @Order(7)
    void testAuthenticateWrongPassword() {
        userService.register("pwduser", "correct1", "Password User", "pwd@test.com");
        assertThrows(IllegalArgumentException.class,
                () -> userService.authenticate("pwduser", "wrongpass"),
                "Неверный пароль должен вызывать IllegalArgumentException");
    }

    /**
     * Тест: аутентификация несуществующего пользователя.
     */
    @Test
    @Order(8)
    void testAuthenticateNonExistentUser() {
        assertThrows(IllegalArgumentException.class,
                () -> userService.authenticate("nonexistent", "password1"),
                "Несуществующий пользователь должен вызывать IllegalArgumentException");
    }

    /**
     * Тест: аутентификация заблокированного пользователя.
     */
    @Test
    @Order(9)
    void testAuthenticateBlockedUser() {
        User user = userService.register("blockeduser", "blocked1", "Blocked", "blocked@test.com");
        userService.setBlocked(user.getId(), true);

        assertThrows(IllegalStateException.class,
                () -> userService.authenticate("blockeduser", "blocked1"),
                "Заблокированный пользователь должен вызывать IllegalStateException");
    }

    /**
     * Тест: поиск пользователя по ID.
     */
    @Test
    @Order(10)
    void testFindById() {
        User created = userService.register("findme", "findme1", "Find Me", "find@test.com");
        Optional<User> found = userService.findById(created.getId());

        assertTrue(found.isPresent());
        assertEquals("findme", found.get().getUsername());
    }

    /**
     * Тест: поиск несуществующего пользователя по ID.
     */
    @Test
    @Order(11)
    void testFindByIdNotFound() {
        Optional<User> found = userService.findById(99999);
        assertTrue(found.isEmpty(), "Несуществующий пользователь должен возвращать пустой Optional");
    }

    /**
     * Тест: получение списка всех пользователей.
     */
    @Test
    @Order(12)
    void testFindAll() {
        List<User> users = userService.findAll();
        assertNotNull(users);
        assertFalse(users.isEmpty(), "Список пользователей не должен быть пустым");
    }

    /**
     * Тест: назначение прав администратора.
     */
    @Test
    @Order(13)
    void testSetAdmin() {
        User user = userService.register("admintest", "admintest1", "Admin Test", "admin@test.com");
        assertFalse(user.isAdmin());

        userService.setAdmin(user.getId(), true);
        Optional<User> updated = userService.findById(user.getId());
        assertTrue(updated.isPresent());
        assertTrue(updated.get().isAdmin(), "Пользователь должен стать администратором");
    }

    /**
     * Тест: смена пароля пользователя.
     */
    @Test
    @Order(14)
    void testChangePassword() {
        User user = userService.register("changepwd", "oldpwd1", "Change Pwd", "change@test.com");
        userService.changePassword(user.getId(), "newpwd2");

        // Старый пароль должен не работать
        assertThrows(IllegalArgumentException.class,
                () -> userService.authenticate("changepwd", "oldpwd1"));

        // Новый пароль должен работать
        User logged = userService.authenticate("changepwd", "newpwd2");
        assertNotNull(logged);
    }

    /**
     * Тест: удаление пользователя.
     */
    @Test
    @Order(15)
    void testDelete() {
        User user = userService.register("todelete", "delete1", "To Delete", "del@test.com");
        int id = user.getId();

        userService.delete(id);
        Optional<User> deleted = userService.findById(id);
        assertTrue(deleted.isEmpty(), "Удалённый пользователь не должен находиться в БД");
    }
}
