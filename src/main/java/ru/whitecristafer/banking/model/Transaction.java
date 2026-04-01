package ru.whitecristafer.banking.model;

import java.math.BigDecimal;

/**
 * Модель данных транзакции банковского счёта.
 * Хранит информацию о каждом движении средств:
 * пополнение, снятие, перевод между счетами.
 *
 * Поля соответствуют столбцам таблицы "transactions" в SQLite.
 *
 * @author whitecristafer
 */
public class Transaction {

    /** Уникальный идентификатор транзакции (PRIMARY KEY в БД) */
    private int id;

    /** Идентификатор счёта, с которого/на который выполнена операция */
    private int accountId;

    /** Тип транзакции */
    private TransactionType type;

    /** Сумма транзакции (всегда положительная) */
    private BigDecimal amount;

    /**
     * Идентификатор счёта получателя/отправителя для переводов.
     * Null для пополнений и снятий.
     */
    private Integer relatedAccountId;

    /** Описание транзакции (опционально) */
    private String description;

    /** Дата и время транзакции (ISO-8601 строка) */
    private String createdAt;

    /** Баланс счёта после выполнения транзакции */
    private BigDecimal balanceAfter;

    /**
     * Перечисление типов транзакций.
     */
    public enum TransactionType {
        /** Пополнение счёта (депозит) */
        DEPOSIT,
        /** Снятие средств */
        WITHDRAWAL,
        /** Перевод на другой счёт */
        TRANSFER
    }

    /**
     * Конструктор по умолчанию — требуется для JDBC маппинга.
     */
    public Transaction() {}

    /**
     * Конструктор для создания новой транзакции.
     *
     * @param accountId         идентификатор счёта
     * @param type              тип транзакции
     * @param amount            сумма операции
     * @param relatedAccountId  связанный счёт (для переводов, иначе null)
     * @param description       описание операции
     * @param balanceAfter      баланс после операции
     */
    public Transaction(int accountId, TransactionType type, BigDecimal amount,
                       Integer relatedAccountId, String description, BigDecimal balanceAfter) {
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.relatedAccountId = relatedAccountId;
        this.description = description;
        this.balanceAfter = balanceAfter;
    }

    // Геттеры и сеттеры

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }

    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Integer getRelatedAccountId() { return relatedAccountId; }
    public void setRelatedAccountId(Integer relatedAccountId) { this.relatedAccountId = relatedAccountId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }

    @Override
    public String toString() {
        return "Transaction{id=" + id + ", accountId=" + accountId + ", type=" + type +
               ", amount=" + amount + ", createdAt='" + createdAt + "'}";
    }
}
