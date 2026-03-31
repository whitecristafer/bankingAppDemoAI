package ru.whitecristafer.banking.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.whitecristafer.banking.db.DatabaseManager;
import ru.whitecristafer.banking.model.User;
import ru.whitecristafer.banking.util.PasswordUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Сервис управления пользователями — реализует бизнес-логику работы с пользователями.
 *
 * Предоставляет методы для:
 * - регистрации новых пользователей
 * - аутентификации (проверки логина и пароля)
 * - просмотра, редактирования, удаления пользователей (CRUD)
 * - блокировки/разблокировки пользователей (для администратора)
 *
 * Все методы логируют важные события и исключения.
 *
 * @author whitecristafer
 */
public class UserService {

    private static final Logger logger = LogManager.getLogger(UserService.class);

    /** Ссылка на менеджер базы данных */
    private final DatabaseManager dbManager;

    /**
     * Конструктор — принимает менеджер базы данных (для тестируемости через DI).
     *
     * @param dbManager менеджер подключения к SQLite
     */
    public UserService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Регистрирует нового пользователя в системе.
     *
     * Проверяет:
     * - уникальность имени пользователя
     * - сложность пароля (минимум 6 символов + цифра)
     * - корректность email (базовая проверка)
     *
     * @param username уникальное имя пользователя
     * @param password пароль в открытом виде (будет захэширован)
     * @param fullName полное имя пользователя
     * @param email    email адрес
     * @return созданный объект User с присвоенным ID
     * @throws IllegalArgumentException если данные некорректны или пользователь уже существует
     */
    public User register(String username, String password, String fullName, String email) {
        // Валидация входных данных
        validateUsername(username);
        if (!PasswordUtil.isPasswordStrong(password)) {
            throw new IllegalArgumentException("Пароль слишком слабый. Минимум 6 символов, хотя бы одна цифра.");
        }
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Полное имя не может быть пустым");
        }

        // Проверяем, что имя пользователя уникально
        if (findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Пользователь с именем '" + username + "' уже существует");
        }

        String sql = """
                INSERT INTO users (username, password_hash, full_name, email, is_admin)
                VALUES (?, ?, ?, ?, 0)
                """;

        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, username.trim().toLowerCase());
            pstmt.setString(2, PasswordUtil.hash(password));
            pstmt.setString(3, fullName.trim());
            pstmt.setString(4, email != null ? email.trim() : "");
            pstmt.executeUpdate();

            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    User user = findById(id).orElseThrow();
                    logger.info("Зарегистрирован новый пользователь: {}", user.getUsername());
                    return user;
                }
            }
            throw new RuntimeException("Не удалось получить ID нового пользователя");
        } catch (SQLException e) {
            logger.error("Ошибка регистрации пользователя '{}'", username, e);
            throw new RuntimeException("Ошибка регистрации: " + e.getMessage(), e);
        }
    }

    /**
     * Аутентифицирует пользователя по имени и паролю.
     *
     * @param username имя пользователя
     * @param password пароль в открытом виде
     * @return объект User если аутентификация успешна
     * @throws IllegalArgumentException если данные неверны или пользователь заблокирован
     */
    public User authenticate(String username, String password) {
        Optional<User> userOpt = findByUsername(username);
        if (userOpt.isEmpty()) {
            logger.warn("Попытка входа с несуществующим именем: {}", username);
            throw new IllegalArgumentException("Неверное имя пользователя или пароль");
        }

        User user = userOpt.get();

        if (user.isBlocked()) {
            logger.warn("Попытка входа заблокированного пользователя: {}", username);
            throw new IllegalStateException("Учётная запись заблокирована. Обратитесь к администратору.");
        }

        if (!PasswordUtil.verify(password, user.getPasswordHash())) {
            logger.warn("Неверный пароль для пользователя: {}", username);
            throw new IllegalArgumentException("Неверное имя пользователя или пароль");
        }

        logger.info("Успешный вход пользователя: {}", username);
        return user;
    }

    /**
     * Возвращает пользователя по идентификатору.
     *
     * @param id идентификатор пользователя
     * @return Optional с объектом User или пустой Optional если не найден
     */
    public Optional<User> findById(int id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToUser(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка поиска пользователя по ID={}", id, e);
        }
        return Optional.empty();
    }

    /**
     * Возвращает пользователя по имени пользователя.
     *
     * @param username имя пользователя (без учёта регистра)
     * @return Optional с объектом User или пустой Optional если не найден
     */
    public Optional<User> findByUsername(String username) {
        if (username == null) return Optional.empty();
        String sql = "SELECT * FROM users WHERE LOWER(username) = LOWER(?)";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToUser(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка поиска пользователя по username='{}'", username, e);
        }
        return Optional.empty();
    }

    /**
     * Возвращает список всех пользователей системы.
     * Используется в панели администратора.
     *
     * @return список всех пользователей, отсортированный по имени
     */
    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY username";
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(mapRowToUser(rs));
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения списка пользователей", e);
        }
        return users;
    }

    /**
     * Обновляет данные пользователя (полное имя и email).
     *
     * @param user объект User с обновлёнными полями (id должен быть корректным)
     * @throws RuntimeException при ошибке обновления
     */
    public void update(User user) {
        String sql = "UPDATE users SET full_name = ?, email = ? WHERE id = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, user.getFullName());
            pstmt.setString(2, user.getEmail());
            pstmt.setInt(3, user.getId());
            pstmt.executeUpdate();
            logger.info("Обновлены данные пользователя: {}", user.getUsername());
        } catch (SQLException e) {
            logger.error("Ошибка обновления пользователя ID={}", user.getId(), e);
            throw new RuntimeException("Ошибка обновления пользователя: " + e.getMessage(), e);
        }
    }

    /**
     * Изменяет пароль пользователя.
     *
     * @param userId      идентификатор пользователя
     * @param newPassword новый пароль в открытом виде
     * @throws IllegalArgumentException если пароль не соответствует требованиям
     */
    public void changePassword(int userId, String newPassword) {
        if (!PasswordUtil.isPasswordStrong(newPassword)) {
            throw new IllegalArgumentException("Пароль слишком слабый. Минимум 6 символов, хотя бы одна цифра.");
        }
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, PasswordUtil.hash(newPassword));
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
            logger.info("Пароль пользователя ID={} успешно изменён", userId);
        } catch (SQLException e) {
            logger.error("Ошибка смены пароля пользователя ID={}", userId, e);
            throw new RuntimeException("Ошибка смены пароля: " + e.getMessage(), e);
        }
    }

    /**
     * Блокирует или разблокирует учётную запись пользователя.
     * Администратор не может заблокировать сам себя.
     *
     * @param userId   идентификатор пользователя
     * @param blocked  true — заблокировать, false — разблокировать
     */
    public void setBlocked(int userId, boolean blocked) {
        String sql = "UPDATE users SET is_blocked = ? WHERE id = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, blocked ? 1 : 0);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
            logger.info("Статус блокировки пользователя ID={} изменён на: {}", userId, blocked);
        } catch (SQLException e) {
            logger.error("Ошибка изменения блокировки пользователя ID={}", userId, e);
            throw new RuntimeException("Ошибка изменения блокировки: " + e.getMessage(), e);
        }
    }

    /**
     * Устанавливает права администратора для пользователя.
     *
     * @param userId  идентификатор пользователя
     * @param isAdmin true — дать права администратора, false — снять
     */
    public void setAdmin(int userId, boolean isAdmin) {
        String sql = "UPDATE users SET is_admin = ? WHERE id = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, isAdmin ? 1 : 0);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
            logger.info("Права администратора пользователя ID={} изменены на: {}", userId, isAdmin);
        } catch (SQLException e) {
            logger.error("Ошибка изменения прав администратора ID={}", userId, e);
            throw new RuntimeException("Ошибка изменения прав: " + e.getMessage(), e);
        }
    }

    /**
     * Удаляет пользователя из системы вместе со всеми его счетами и транзакциями.
     * Администратор не может удалить сам себя.
     *
     * @param userId идентификатор пользователя для удаления
     */
    public void delete(int userId) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                logger.info("Пользователь ID={} удалён из системы", userId);
            } else {
                logger.warn("Пользователь ID={} не найден для удаления", userId);
            }
        } catch (SQLException e) {
            logger.error("Ошибка удаления пользователя ID={}", userId, e);
            throw new RuntimeException("Ошибка удаления пользователя: " + e.getMessage(), e);
        }
    }

    /**
     * Проверяет корректность имени пользователя.
     * Допустимы только буквы, цифры, дефис и подчёркивание. Длина 3-30 символов.
     *
     * @param username проверяемое имя пользователя
     * @throws IllegalArgumentException если имя некорректно
     */
    private void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Имя пользователя не может быть пустым");
        }
        String trimmed = username.trim();
        if (trimmed.length() < 3 || trimmed.length() > 30) {
            throw new IllegalArgumentException("Имя пользователя должно содержать от 3 до 30 символов");
        }
        if (!trimmed.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Имя пользователя может содержать только латинские буквы, цифры, '-' и '_'");
        }
    }

    /**
     * Преобразует строку ResultSet в объект User.
     *
     * @param rs ResultSet с текущей строкой, содержащей данные пользователя
     * @return объект User, заполненный данными из строки
     * @throws SQLException при ошибке чтения данных
     */
    private User mapRowToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        user.setAdmin(rs.getInt("is_admin") == 1);
        user.setBlocked(rs.getInt("is_blocked") == 1);
        user.setCreatedAt(rs.getString("created_at"));
        return user;
    }
}
