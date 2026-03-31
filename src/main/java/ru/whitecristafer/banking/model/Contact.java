package ru.whitecristafer.banking.model;

/**
 * Модель контакта пользователя для быстрых переводов.
 * Хранит имя, телефон и номер счёта получателя.
 *
 * @author whitecristafer
 */
public class Contact {
    /** Уникальный идентификатор контакта */
    private int id;
    /** Идентификатор владельца контакта */
    private int userId;
    /** Имя контакта */
    private String name;
    /** Нормализованный российский номер телефона */
    private String phone;
    /** Номер счёта контакта */
    private String accountNumber;
    /** Заметки */
    private String notes;
    /** Дата создания */
    private String createdAt;

    public Contact() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Contact{id=" + id + ", name='" + name + "', phone='" + phone + "'}";
    }
}
