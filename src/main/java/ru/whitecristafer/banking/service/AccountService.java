package ru.whitecristafer.banking.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.whitecristafer.banking.db.DatabaseManager;
import ru.whitecristafer.banking.model.Account;
import ru.whitecristafer.banking.model.Account.AccountType;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сервис управления банковскими счетами — реализует бизнес-логику работы со счетами.
 *
 * Предоставляет методы для:
 * - создания новых счетов
 * - получения счетов пользователя
 * - пополнения и снятия средств
 * - активации/деактивации счетов
 * - полного CRUD по счетам (для администратора)
 *
 * @author whitecristafer
 */
public class AccountService {

    private static final Logger logger = LogManager.getLogger(AccountService.class);

    /** Счётчик для генерации уникальных номеров счетов */
    private static final AtomicInteger accountCounter = new AtomicInteger(1);

    /** Ссылка на менеджер базы данных */
    private final DatabaseManager dbManager;

    /**
     * Конструктор — принимает менеджер базы данных (для тестируемости через DI).
     *
     * @param dbManager менеджер подключения к SQLite
     */
    public AccountService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Создаёт новый банковский счёт для пользователя.
     * Суммарное количество счетов и карт не может превышать 10.
     *
     * @param userId      идентификатор владельца счёта
     * @param currency    валюта счёта (например, "RUB", "USD")
     * @param accountType тип счёта (CHECKING или SAVINGS)
     * @return созданный объект Account с присвоенным ID и номером счёта
     * @throws IllegalStateException если достигнут общий лимит счетов и карт
     */
    public Account createAccount(int userId, String currency, AccountType accountType) {
        // Проверяем суммарный лимит счетов + карт
        if (getTotalAccountsAndCards(userId) >= 10) {
            throw new IllegalStateException("Достигнут максимальный лимит счетов и карт (10)");
        }
        String accountNumber = generateAccountNumber();
        String sql = """
                INSERT INTO accounts (user_id, account_number, balance, currency, account_type)
                VALUES (?, ?, 0.0, ?, ?)
                """;
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, userId);
            pstmt.setString(2, accountNumber);
            pstmt.setString(3, currency.toUpperCase());
            pstmt.setString(4, accountType.name());
            pstmt.executeUpdate();

            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    Account account = findById(keys.getInt(1)).orElseThrow();
                    logger.info("Создан счёт {} для пользователя ID={}", accountNumber, userId);
                    return account;
                }
            }
            throw new RuntimeException("Не удалось получить ID нового счёта");
        } catch (SQLException e) {
            logger.error("Ошибка создания счёта для пользователя ID={}", userId, e);
            throw new RuntimeException("Ошибка создания счёта: " + e.getMessage(), e);
        }
    }

    /**
     * Возвращает счёт по его идентификатору.
     *
     * @param id идентификатор счёта
     * @return Optional с объектом Account или пустой Optional если не найден
     */
    public Optional<Account> findById(int id) {
        String sql = "SELECT * FROM accounts WHERE id = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRowToAccount(rs));
            }
        } catch (SQLException e) {
            logger.error("Ошибка поиска счёта по ID={}", id, e);
        }
        return Optional.empty();
    }

    /**
     * Возвращает счёт по его номеру.
     *
     * @param accountNumber уникальный номер счёта
     * @return Optional с объектом Account или пустой Optional если не найден
     */
    public Optional<Account> findByAccountNumber(String accountNumber) {
        String sql = "SELECT * FROM accounts WHERE account_number = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, accountNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRowToAccount(rs));
            }
        } catch (SQLException e) {
            logger.error("Ошибка поиска счёта по номеру '{}'", accountNumber, e);
        }
        return Optional.empty();
    }

    /**
     * Возвращает список всех счетов конкретного пользователя.
     *
     * @param userId идентификатор пользователя
     * @return список счетов пользователя, отсортированный по дате создания
     */
    public List<Account> findByUserId(int userId) {
        List<Account> accounts = new ArrayList<>();
        String sql = "SELECT * FROM accounts WHERE user_id = ? ORDER BY created_at";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) accounts.add(mapRowToAccount(rs));
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения счетов пользователя ID={}", userId, e);
        }
        return accounts;
    }

    /**
     * Возвращает список всех счетов в системе (для панели администратора).
     *
     * @return список всех счетов
     */
    public List<Account> findAll() {
        List<Account> accounts = new ArrayList<>();
        String sql = "SELECT * FROM accounts ORDER BY user_id, created_at";
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) accounts.add(mapRowToAccount(rs));
        } catch (SQLException e) {
            logger.error("Ошибка получения списка всех счетов", e);
        }
        return accounts;
    }

    /**
     * Активирует или деактивирует (замораживает) счёт.
     *
     * @param accountId идентификатор счёта
     * @param active    true — активировать, false — заморозить
     */
    public void setActive(int accountId, boolean active) {
        String sql = "UPDATE accounts SET is_active = ? WHERE id = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, active ? 1 : 0);
            pstmt.setInt(2, accountId);
            pstmt.executeUpdate();
            logger.info("Статус активности счёта ID={} изменён на: {}", accountId, active);
        } catch (SQLException e) {
            logger.error("Ошибка изменения статуса счёта ID={}", accountId, e);
            throw new RuntimeException("Ошибка изменения статуса счёта: " + e.getMessage(), e);
        }
    }

    /**
     * Удаляет счёт из системы (только если баланс равен нулю).
     *
     * @param accountId идентификатор счёта для удаления
     * @throws IllegalStateException если на счёте есть средства
     */
    public void delete(int accountId) {
        Account account = findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Счёт не найден: " + accountId));

        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Нельзя удалить счёт с ненулевым балансом");
        }

        String sql = "DELETE FROM accounts WHERE id = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, accountId);
            pstmt.executeUpdate();
            logger.info("Счёт ID={} удалён", accountId);
        } catch (SQLException e) {
            logger.error("Ошибка удаления счёта ID={}", accountId, e);
            throw new RuntimeException("Ошибка удаления счёта: " + e.getMessage(), e);
        }
    }

    /**
     * Генерирует уникальный номер счёта в формате ACC-XXXXXX.
     * Проверяет уникальность номера в базе данных.
     *
     * @return уникальный номер счёта
     */
    private String generateAccountNumber() {
        String number;
        do {
            number = String.format("ACC-%06d", accountCounter.getAndIncrement());
        } while (findByAccountNumber(number).isPresent());
        return number;
    }

    /**
     * Возвращает суммарное количество счетов и карт пользователя.
     * Используется для проверки общего лимита (не более 10).
     * Если таблица virtual_cards ещё не создана — учитываются только счета.
     *
     * @param userId идентификатор пользователя
     * @return суммарное количество счетов и карт
     */
    public int getTotalAccountsAndCards(int userId) {
        // Считаем количество счетов
        int accountCount = 0;
        String accountSql = "SELECT COUNT(*) FROM accounts WHERE user_id = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(accountSql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) accountCount = rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Ошибка подсчёта счетов пользователя ID={}", userId, e);
        }

        // Считаем количество карт (таблица может ещё не существовать)
        int cardCount = 0;
        String cardSql = "SELECT COUNT(*) FROM virtual_cards WHERE user_id = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(cardSql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) cardCount = rs.getInt(1);
            }
        } catch (SQLException e) {
            // Таблица virtual_cards может отсутствовать на момент проверки
            logger.debug("Таблица virtual_cards недоступна при подсчёте: {}", e.getMessage());
        }

        return accountCount + cardCount;
    }

    /**
     * Преобразует строку ResultSet в объект Account.
     *
     * @param rs ResultSet с текущей строкой, содержащей данные счёта
     * @return объект Account, заполненный данными из строки
     * @throws SQLException при ошибке чтения данных
     */
    private Account mapRowToAccount(ResultSet rs) throws SQLException {
        Account account = new Account();
        account.setId(rs.getInt("id"));
        account.setUserId(rs.getInt("user_id"));
        account.setAccountNumber(rs.getString("account_number"));
        account.setBalance(BigDecimal.valueOf(rs.getDouble("balance")));
        account.setCurrency(rs.getString("currency"));
        account.setAccountType(AccountType.valueOf(rs.getString("account_type")));
        account.setActive(rs.getInt("is_active") == 1);
        account.setCreatedAt(rs.getString("created_at"));
        return account;
    }
}
