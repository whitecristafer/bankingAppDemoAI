package ru.whitecristafer.banking.model;

/**
 * Модель виртуальной банковской карты.
 * Привязана к банковскому счёту и пользователю.
 * Хранит данные карты: номер, держатель, срок действия, тип.
 * CVV хранится в хэшированном виде (BCrypt).
 *
 * @author whitecristafer
 */
public class VirtualCard {
    /** Перечисление типов банковских карт */
    public enum CardType {
        /** Дебетовая карта — списание с собственных средств */
        DEBIT,
        /** Кредитная карта — использование кредитного лимита */
        CREDIT
    }

    private int id;
    private int userId;
    private int accountId;
    /** Номер карты в формате "XXXX XXXX XXXX XXXX" */
    private String cardNumber;
    /** Имя держателя карты латиницей */
    private String cardHolderName;
    /** Месяц истечения срока действия (1-12) */
    private int expiryMonth;
    /** Год истечения срока действия (например, 2027) */
    private int expiryYear;
    /** Хэш CVV-кода (BCrypt) — не хранится в открытом виде */
    private String cvvHash;
    private CardType cardType;
    /** Признак активности карты */
    private boolean active;
    private String createdAt;

    public VirtualCard() {}

    // Геттеры и сеттеры
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }
    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
    public String getCardHolderName() { return cardHolderName; }
    public void setCardHolderName(String cardHolderName) { this.cardHolderName = cardHolderName; }
    public int getExpiryMonth() { return expiryMonth; }
    public void setExpiryMonth(int expiryMonth) { this.expiryMonth = expiryMonth; }
    public int getExpiryYear() { return expiryYear; }
    public void setExpiryYear(int expiryYear) { this.expiryYear = expiryYear; }
    public String getCvvHash() { return cvvHash; }
    public void setCvvHash(String cvvHash) { this.cvvHash = cvvHash; }
    public CardType getCardType() { return cardType; }
    public void setCardType(CardType cardType) { this.cardType = cardType; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "VirtualCard{id=" + id + ", cardNumber='" + cardNumber + "', type=" + cardType + ", active=" + active + "}";
    }
}
