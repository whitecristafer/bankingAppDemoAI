package ru.whitecristafer.banking.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.whitecristafer.banking.db.DatabaseManager;
import ru.whitecristafer.banking.model.Account;
import ru.whitecristafer.banking.model.Transaction;
import ru.whitecristafer.banking.model.Transaction.TransactionType;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис обработки транзакций — реализует бизнес-логику движения денежных средств.
 *
 * Предоставляет методы для:
 * - пополнения счёта (DEPOSIT)
 * - снятия средств (WITHDRAWAL)
 * - перевода между счетами (TRANSFER)
 * - просмотра истории транзакций
 *
 * Все финансовые операции выполняются в транзакциях БД для атомарности.
 * Ведётся подробный лог всех операций.
 *
 * @author whitecristafer
 */
public class TransactionService {

    private static final Logger logger = LogManager.getLogger(TransactionService.class);

    /** Менеджер базы данных */
    private final DatabaseManager dbManager;

    /** Сервис счетов для получения актуальных данных и обновления баланса */
    private final AccountService accountService;

    /**
     * Конструктор — принимает зависимости через DI.
     *
     * @param dbManager      менеджер подключения к SQLite
     * @param accountService сервис управления счетами
     */
    public TransactionService(DatabaseManager dbManager, AccountService accountService) {
        this.dbManager = dbManager;
        this.accountService = accountService;
    }

    /**
     * Пополняет баланс счёта (депозит).
     *
     * @param accountId   идентификатор счёта для пополнения
     * @param amount      сумма пополнения (должна быть положительной)
     * @param description описание операции (необязательно)
     * @return объект Transaction с данными выполненной операции
     * @throws IllegalArgumentException если счёт не найден или сумма некорректна
     * @throws IllegalStateException    если счёт неактивен
     */
    public Transaction deposit(int accountId, BigDecimal amount, String description) {
        validateAmount(amount);
        Account account = getActiveAccount(accountId);

        BigDecimal newBalance = account.getBalance().add(amount);
        return executeTransaction(accountId, TransactionType.DEPOSIT, amount, null,
                description != null ? description : "Пополнение счёта", newBalance);
    }

    /**
     * Снимает средства со счёта.
     *
     * @param accountId   идентификатор счёта для снятия
     * @param amount      сумма снятия (должна быть положительной)
     * @param description описание операции (необязательно)
     * @return объект Transaction с данными выполненной операции
     * @throws IllegalArgumentException если счёт не найден, сумма некорректна, или недостаточно средств
     * @throws IllegalStateException    если счёт неактивен
     */
    public Transaction withdraw(int accountId, BigDecimal amount, String description) {
        validateAmount(amount);
        Account account = getActiveAccount(accountId);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "Недостаточно средств. Баланс: " + account.getBalance() +
                    ", запрошено: " + amount);
        }

        BigDecimal newBalance = account.getBalance().subtract(amount);
        return executeTransaction(accountId, TransactionType.WITHDRAWAL, amount, null,
                description != null ? description : "Снятие средств", newBalance);
    }

    /**
     * Выполняет перевод средств с одного счёта на другой.
     * Операция атомарна: оба изменения выполняются в одной транзакции БД.
     *
     * @param fromAccountId идентификатор счёта отправителя
     * @param toAccountId   идентификатор счёта получателя
     * @param amount        сумма перевода (должна быть положительной)
     * @param description   описание перевода (необязательно)
     * @return объект Transaction операции списания (для счёта отправителя)
     * @throws IllegalArgumentException если счёта не найдены, недостаточно средств
     * @throws IllegalStateException    если счёт неактивен
     */
    public Transaction transfer(int fromAccountId, int toAccountId, BigDecimal amount, String description) {
        if (fromAccountId == toAccountId) {
            throw new IllegalArgumentException("Нельзя переводить средства на тот же счёт");
        }
        validateAmount(amount);

        Account fromAccount = getActiveAccount(fromAccountId);
        Account toAccount = getActiveAccount(toAccountId);

        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "Недостаточно средств для перевода. Баланс: " + fromAccount.getBalance() +
                    ", запрошено: " + amount);
        }

        String desc = description != null ? description : "Перевод средств";
        Connection conn = dbManager.getConnection();

        try {
            // Начинаем транзакцию БД для атомарности перевода
            conn.setAutoCommit(false);

            BigDecimal newFromBalance = fromAccount.getBalance().subtract(amount);
            BigDecimal newToBalance = toAccount.getBalance().add(amount);

            // Списание с отправителя
            Transaction debit = insertTransaction(conn, fromAccountId, TransactionType.TRANSFER,
                    amount, toAccountId, "Перевод → " + toAccount.getAccountNumber() + ": " + desc,
                    newFromBalance);
            updateBalance(conn, fromAccountId, newFromBalance);

            // Зачисление получателю
            insertTransaction(conn, toAccountId, TransactionType.TRANSFER,
                    amount, fromAccountId, "Перевод ← " + fromAccount.getAccountNumber() + ": " + desc,
                    newToBalance);
            updateBalance(conn, toAccountId, newToBalance);

            conn.commit();
            logger.info("Перевод выполнен: {} → {}, сумма={}", fromAccountId, toAccountId, amount);
            return debit;
        } catch (Exception e) {
            try { conn.rollback(); } catch (SQLException ex) { logger.error("Ошибка отката транзакции", ex); }
            logger.error("Ошибка перевода средств с {} на {}", fromAccountId, toAccountId, e);
            throw new RuntimeException("Ошибка перевода: " + e.getMessage(), e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException e) { logger.error("Ошибка восстановления autocommit", e); }
        }
    }

    /**
     * Возвращает историю транзакций для указанного счёта.
     *
     * @param accountId идентификатор счёта
     * @param limit     максимальное количество записей (0 — без ограничений)
     * @return список транзакций, отсортированный по дате (новые первые)
     */
    public List<Transaction> getHistory(int accountId, int limit) {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT * FROM transactions WHERE account_id = ? ORDER BY created_at DESC" +
                     (limit > 0 ? " LIMIT " + limit : "");
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, accountId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) transactions.add(mapRowToTransaction(rs));
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения истории транзакций для счёта ID={}", accountId, e);
        }
        return transactions;
    }

    /**
     * Возвращает все транзакции системы (для панели администратора).
     *
     * @param limit максимальное количество записей (0 — без ограничений)
     * @return список всех транзакций, отсортированный по дате (новые первые)
     */
    public List<Transaction> getAllTransactions(int limit) {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT * FROM transactions ORDER BY created_at DESC" +
                     (limit > 0 ? " LIMIT " + limit : "");
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) transactions.add(mapRowToTransaction(rs));
        } catch (SQLException e) {
            logger.error("Ошибка получения всех транзакций", e);
        }
        return transactions;
    }

    /**
     * Выполняет простую транзакцию (deposit/withdrawal) — вспомогательный метод.
     * Атомарно обновляет баланс и записывает транзакцию в одном SQL-вызове.
     */
    private Transaction executeTransaction(int accountId, TransactionType type, BigDecimal amount,
                                           Integer relatedAccountId, String description,
                                           BigDecimal newBalance) {
        Connection conn = dbManager.getConnection();
        try {
            conn.setAutoCommit(false);
            Transaction tx = insertTransaction(conn, accountId, type, amount,
                    relatedAccountId, description, newBalance);
            updateBalance(conn, accountId, newBalance);
            conn.commit();
            logger.info("Транзакция {}: счёт={}, сумма={}, новый баланс={}", type, accountId, amount, newBalance);
            return tx;
        } catch (Exception e) {
            try { conn.rollback(); } catch (SQLException ex) { logger.error("Ошибка отката", ex); }
            logger.error("Ошибка транзакции {} для счёта ID={}", type, accountId, e);
            throw new RuntimeException("Ошибка транзакции: " + e.getMessage(), e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException e) { logger.error("Ошибка восстановления autocommit", e); }
        }
    }

    /**
     * Вставляет запись транзакции в таблицу transactions.
     */
    private Transaction insertTransaction(Connection conn, int accountId, TransactionType type,
                                          BigDecimal amount, Integer relatedAccountId,
                                          String description, BigDecimal balanceAfter) throws SQLException {
        String sql = """
                INSERT INTO transactions (account_id, type, amount, related_account_id, description, balance_after)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, accountId);
            pstmt.setString(2, type.name());
            pstmt.setDouble(3, amount.doubleValue());
            if (relatedAccountId != null) pstmt.setInt(4, relatedAccountId);
            else pstmt.setNull(4, Types.INTEGER);
            pstmt.setString(5, description);
            pstmt.setDouble(6, balanceAfter.doubleValue());
            pstmt.executeUpdate();

            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    Transaction tx = new Transaction(accountId, type, amount, relatedAccountId,
                            description, balanceAfter);
                    tx.setId(keys.getInt(1));
                    return tx;
                }
            }
        }
        throw new SQLException("Не удалось создать запись транзакции");
    }

    /**
     * Обновляет баланс счёта в таблице accounts.
     */
    private void updateBalance(Connection conn, int accountId, BigDecimal newBalance) throws SQLException {
        String sql = "UPDATE accounts SET balance = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, newBalance.doubleValue());
            pstmt.setInt(2, accountId);
            pstmt.executeUpdate();
        }
    }

    /**
     * Возвращает активный счёт или выбрасывает исключение.
     */
    private Account getActiveAccount(int accountId) {
        Account account = accountService.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Счёт не найден: " + accountId));
        if (!account.isActive()) {
            throw new IllegalStateException("Счёт " + account.getAccountNumber() + " неактивен");
        }
        return account;
    }

    /**
     * Проверяет корректность суммы транзакции.
     */
    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма транзакции должна быть положительной");
        }
    }

    /**
     * Преобразует строку ResultSet в объект Transaction.
     */
    private Transaction mapRowToTransaction(ResultSet rs) throws SQLException {
        Transaction tx = new Transaction();
        tx.setId(rs.getInt("id"));
        tx.setAccountId(rs.getInt("account_id"));
        tx.setType(TransactionType.valueOf(rs.getString("type")));
        tx.setAmount(BigDecimal.valueOf(rs.getDouble("amount")));
        int relatedId = rs.getInt("related_account_id");
        if (!rs.wasNull()) tx.setRelatedAccountId(relatedId);
        tx.setDescription(rs.getString("description"));
        tx.setBalanceAfter(BigDecimal.valueOf(rs.getDouble("balance_after")));
        tx.setCreatedAt(rs.getString("created_at"));
        return tx;
    }
}
