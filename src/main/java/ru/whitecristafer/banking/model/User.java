package ru.whitecristafer.banking.model;

/**
 * Модель данных пользователя банковского приложения.
 * Хранит учётные данные и метаданные пользователя.
 *
 * Поля соответствуют столбцам таблицы "users" в SQLite.
 * Пользователь может быть обычным клиентом или администратором.
 *
 * @author whitecristafer
 */
public class User {

    /** Уникальный идентификатор пользователя (PRIMARY KEY в БД) */
    private int id;

    /** Уникальное имя пользователя для входа в систему */
    private String username;

    /** Хэш пароля (BCrypt). Никогда не хранится в открытом виде */
    private String passwordHash;

    /** Полное имя пользователя для отображения */
    private String fullName;

    /** Email пользователя */
    private String email;

    /** Признак администратора: true — администратор, false — обычный пользователь */
    private boolean admin;

    /** Дата создания учётной записи (ISO-8601 строка, например "2024-01-15T10:30:00") */
    private String createdAt;

    /** Признак блокировки учётной записи */
    private boolean blocked;

    /**
     * Конструктор по умолчанию — требуется для JDBC маппинга.
     */
    public User() {}

    /**
     * Полный конструктор для создания нового пользователя.
     *
     * @param username    уникальное имя пользователя
     * @param passwordHash хэш пароля (BCrypt)
     * @param fullName    полное имя
     * @param email       email адрес
     * @param admin       является ли администратором
     */
    public User(String username, String passwordHash, String fullName, String email, boolean admin) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.email = email;
        this.admin = admin;
        this.blocked = false;
    }

    // Геттеры и сеттеры

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', fullName='" + fullName +
               "', admin=" + admin + ", blocked=" + blocked + "}";
    }
}
