package ru.whitecristafer.banking.model;

import java.math.BigDecimal;

/**
 * Модель данных банковского счёта.
 * Каждый пользователь имеет один или несколько счетов.
 * Хранит баланс, валюту и метаданные счёта.
 *
 * Поля соответствуют столбцам таблицы "accounts" в SQLite.
 *
 * @author whitecristafer
 */
public class Account {

    /** Уникальный идентификатор счёта (PRIMARY KEY в БД) */
    private int id;

    /** Идентификатор владельца счёта (FOREIGN KEY → users.id) */
    private int userId;

    /** Уникальный номер счёта (например, "ACC-000001") */
    private String accountNumber;

    /** Текущий баланс счёта */
    private BigDecimal balance;

    /** Валюта счёта (например, "RUB", "USD") */
    private String currency;

    /** Тип счёта: CHECKING (текущий) или SAVINGS (сберегательный) */
    private AccountType accountType;

    /** Дата открытия счёта (ISO-8601 строка) */
    private String createdAt;

    /** Признак активности счёта (false — счёт заморожен) */
    private boolean active;

    /** Подтип счёта */
    private AccountSubtype accountSubtype;
    /** Название организации (для юр. лиц) */
    private String legalEntityName;

    /**
     * Перечисление подтипов счетов.
     */
    public enum AccountSubtype {
        PERSONAL,  // личный
        CURRENT,   // расчётный (для юр. лиц)
        DEPOSIT    // депозитный
    }

    /**
     * Перечисление типов банковских счетов.
     */
    public enum AccountType {
        /** Текущий счёт — для повседневных операций */
        CHECKING,
        /** Сберегательный счёт — с возможностью начисления процентов */
        SAVINGS
    }

    /**
     * Конструктор по умолчанию — требуется для JDBC маппинга.
     */
    public Account() {}

    /**
     * Конструктор для создания нового счёта.
     *
     * @param userId      идентификатор владельца
     * @param accountNumber уникальный номер счёта
     * @param balance     начальный баланс
     * @param currency    валюта (например, "RUB")
     * @param accountType тип счёта
     */
    public Account(int userId, String accountNumber, BigDecimal balance,
                   String currency, AccountType accountType) {
        this.userId = userId;
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.currency = currency;
        this.accountType = accountType;
        this.active = true;
    }

    // Геттеры и сеттеры

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public AccountSubtype getAccountSubtype() { return accountSubtype; }
    public void setAccountSubtype(AccountSubtype accountSubtype) { this.accountSubtype = accountSubtype; }
    public String getLegalEntityName() { return legalEntityName; }
    public void setLegalEntityName(String legalEntityName) { this.legalEntityName = legalEntityName; }

    @Override
    public String toString() {
        return "Account{id=" + id + ", accountNumber='" + accountNumber +
               "', balance=" + balance + " " + currency + ", type=" + accountType + "}";
    }
}
