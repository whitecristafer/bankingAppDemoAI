package ru.whitecristafer.banking.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.whitecristafer.banking.db.DatabaseManager;
import ru.whitecristafer.banking.model.Contact;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Сервис управления контактами пользователя для быстрых переводов.
 * Поддерживает CRUD-операции над контактами.
 *
 * @author whitecristafer
 */
public class ContactService {

    private static final Logger logger = LogManager.getLogger(ContactService.class);

    /** Менеджер базы данных */
    private final DatabaseManager dbManager;

    /**
     * Конструктор сервиса контактов.
     *
     * @param dbManager менеджер базы данных
     */
    public ContactService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Добавляет новый контакт для пользователя.
     *
     * @param userId        идентификатор владельца контакта
     * @param name          имя контакта
     * @param phone         номер телефона контакта
     * @param accountNumber номер счёта контакта
     * @param notes         заметки
     * @return созданный объект Contact с присвоенным ID
     */
    public Contact addContact(int userId, String name, String phone, String accountNumber, String notes) {
        String sql = "INSERT INTO contacts (user_id, name, phone, account_number, notes) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, userId);
            pstmt.setString(2, name);
            pstmt.setString(3, phone);
            pstmt.setString(4, accountNumber);
            pstmt.setString(5, notes);
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    logger.info("Добавлен контакт '{}' для пользователя ID={}", name, userId);
                    return findById(id).orElseThrow();
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка добавления контакта", e);
            throw new RuntimeException("Ошибка добавления контакта: " + e.getMessage(), e);
        }
        throw new RuntimeException("Не удалось создать контакт");
    }

    /**
     * Возвращает все контакты пользователя.
     *
     * @param userId идентификатор пользователя
     * @return список контактов
     */
    public List<Contact> findByUserId(int userId) {
        List<Contact> contacts = new ArrayList<>();
        String sql = "SELECT * FROM contacts WHERE user_id = ? ORDER BY name";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) contacts.add(mapRowToContact(rs));
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения контактов пользователя ID={}", userId, e);
        }
        return contacts;
    }

    /**
     * Возвращает контакт по идентификатору.
     *
     * @param id идентификатор контакта
     * @return Optional с объектом Contact
     */
    public Optional<Contact> findById(int id) {
        String sql = "SELECT * FROM contacts WHERE id = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRowToContact(rs));
            }
        } catch (SQLException e) {
            logger.error("Ошибка поиска контакта по ID={}", id, e);
        }
        return Optional.empty();
    }

    /**
     * Ищет контакт пользователя по номеру телефона.
     *
     * @param userId идентификатор пользователя
     * @param phone  номер телефона
     * @return Optional с найденным контактом
     */
    public Optional<Contact> findByPhone(int userId, String phone) {
        String sql = "SELECT * FROM contacts WHERE user_id = ? AND phone = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setString(2, phone);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRowToContact(rs));
            }
        } catch (SQLException e) {
            logger.error("Ошибка поиска контакта по телефону", e);
        }
        return Optional.empty();
    }

    /**
     * Удаляет контакт по идентификатору.
     *
     * @param contactId идентификатор контакта
     */
    public void deleteContact(int contactId) {
        String sql = "DELETE FROM contacts WHERE id = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, contactId);
            pstmt.executeUpdate();
            logger.info("Контакт ID={} удалён", contactId);
        } catch (SQLException e) {
            logger.error("Ошибка удаления контакта ID={}", contactId, e);
            throw new RuntimeException("Ошибка удаления контакта: " + e.getMessage(), e);
        }
    }

    /**
     * Обновляет данные контакта.
     *
     * @param contact объект Contact с обновлёнными данными
     */
    public void updateContact(Contact contact) {
        String sql = "UPDATE contacts SET name = ?, phone = ?, account_number = ?, notes = ? WHERE id = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, contact.getName());
            pstmt.setString(2, contact.getPhone());
            pstmt.setString(3, contact.getAccountNumber());
            pstmt.setString(4, contact.getNotes());
            pstmt.setInt(5, contact.getId());
            pstmt.executeUpdate();
            logger.info("Контакт ID={} обновлён", contact.getId());
        } catch (SQLException e) {
            logger.error("Ошибка обновления контакта ID={}", contact.getId(), e);
            throw new RuntimeException("Ошибка обновления контакта: " + e.getMessage(), e);
        }
    }

    /**
     * Преобразует строку ResultSet в объект Contact.
     *
     * @param rs ResultSet с данными
     * @return объект Contact
     * @throws SQLException при ошибке чтения
     */
    private Contact mapRowToContact(ResultSet rs) throws SQLException {
        Contact c = new Contact();
        c.setId(rs.getInt("id"));
        c.setUserId(rs.getInt("user_id"));
        c.setName(rs.getString("name"));
        c.setPhone(rs.getString("phone"));
        c.setAccountNumber(rs.getString("account_number"));
        c.setNotes(rs.getString("notes"));
        c.setCreatedAt(rs.getString("created_at"));
        return c;
    }
}
