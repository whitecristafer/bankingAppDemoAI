package ru.whitecristafer.banking.service;

import org.junit.jupiter.api.*;
import ru.whitecristafer.banking.db.DatabaseInitializer;
import ru.whitecristafer.banking.db.DatabaseManager;
import ru.whitecristafer.banking.model.Account;
import ru.whitecristafer.banking.model.Account.AccountType;
import ru.whitecristafer.banking.model.Transaction;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для AccountService и TransactionService.
 *
 * Охватывают:
 * - создание счетов
 * - пополнение (deposit)
 * - снятие (withdrawal) включая недостаточность средств
 * - перевод между счетами (transfer) — проверка атомарности
 * - историю транзакций
 * - заморозку счёта
 * - удаление счёта
 *
 * @author whitecristafer
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountServiceTest {

    private static DatabaseManager dbManager;
    private static AccountService accountService;
    private static TransactionService transactionService;
    private static int testUserId;

    @BeforeAll
    static void setUpAll() {
        String testDb = System.getProperty("java.io.tmpdir") + "/banking_acc_test.db";
        new File(testDb).delete();
        System.setProperty("banking.db.url", "jdbc:sqlite:" + testDb);
        DatabaseManager.resetInstance();
        dbManager = DatabaseManager.getInstance();
        DatabaseInitializer.initialize(dbManager.getConnection());

        // Создаём тестового пользователя напрямую через SQL
        try (var conn = dbManager.getConnection();
             var pstmt = conn.prepareStatement(
                     "INSERT INTO users (username, password_hash, full_name, is_admin) VALUES ('acctest', 'hash', 'Acc Test', 0)",
                     java.sql.Statement.RETURN_GENERATED_KEYS)) {
            pstmt.executeUpdate();
            try (var keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) testUserId = keys.getInt(1);
            }
        } catch (Exception e) {
            throw new RuntimeException("Не удалось создать тестового пользователя", e);
        }

        accountService = new AccountService(dbManager);
        transactionService = new TransactionService(dbManager, accountService);
    }

    @AfterAll
    static void tearDownAll() {
        System.clearProperty("banking.db.url");
        DatabaseManager.resetInstance();
        new File(System.getProperty("java.io.tmpdir") + "/banking_acc_test.db").delete();
    }

    /**
     * Тест: создание счёта для пользователя.
     */
    @Test
    @Order(1)
    void testCreateAccount() {
        Account acc = accountService.createAccount(testUserId, "RUB", AccountType.CHECKING);

        assertNotNull(acc);
        assertTrue(acc.getId() > 0);
        assertTrue(acc.getAccountNumber().startsWith("ACC-"));
        assertEquals(0, acc.getBalance().compareTo(BigDecimal.ZERO));
        assertEquals("RUB", acc.getCurrency());
        assertEquals(AccountType.CHECKING, acc.getAccountType());
        assertTrue(acc.isActive());
    }

    /**
     * Тест: получение счетов пользователя.
     */
    @Test
    @Order(2)
    void testFindByUserId() {
        List<Account> accounts = accountService.findByUserId(testUserId);
        assertNotNull(accounts);
        assertFalse(accounts.isEmpty(), "Пользователь должен иметь хотя бы один счёт");
    }

    /**
     * Тест: пополнение счёта.
     */
    @Test
    @Order(3)
    void testDeposit() {
        Account acc = accountService.createAccount(testUserId, "RUB", AccountType.SAVINGS);
        BigDecimal amount = new BigDecimal("1000.50");

        Transaction tx = transactionService.deposit(acc.getId(), amount, "Тестовое пополнение");

        assertNotNull(tx);
        assertEquals(Transaction.TransactionType.DEPOSIT, tx.getType());
        assertEquals(0, tx.getAmount().compareTo(amount));

        // Проверяем баланс после пополнения
        Account updated = accountService.findById(acc.getId()).orElseThrow();
        assertEquals(0, updated.getBalance().compareTo(amount));
    }

    /**
     * Тест: снятие средств со счёта.
     */
    @Test
    @Order(4)
    void testWithdraw() {
        Account acc = accountService.createAccount(testUserId, "RUB", AccountType.CHECKING);
        transactionService.deposit(acc.getId(), new BigDecimal("500"), null);

        BigDecimal withdrawAmount = new BigDecimal("200");
        Transaction tx = transactionService.withdraw(acc.getId(), withdrawAmount, "Тестовое снятие");

        assertNotNull(tx);
        assertEquals(Transaction.TransactionType.WITHDRAWAL, tx.getType());

        Account updated = accountService.findById(acc.getId()).orElseThrow();
        assertEquals(0, updated.getBalance().compareTo(new BigDecimal("300")));
    }

    /**
     * Тест: снятие при недостаточном балансе должно выбрасывать исключение.
     */
    @Test
    @Order(5)
    void testWithdrawInsufficientFunds() {
        Account acc = accountService.createAccount(testUserId, "RUB", AccountType.CHECKING);
        transactionService.deposit(acc.getId(), new BigDecimal("100"), null);

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.withdraw(acc.getId(), new BigDecimal("500"), null),
                "Снятие при недостатке средств должно выбрасывать исключение");
    }

    /**
     * Тест: снятие нулевой суммы должно выбрасывать исключение.
     */
    @Test
    @Order(6)
    void testWithdrawZeroAmount() {
        Account acc = accountService.createAccount(testUserId, "RUB", AccountType.CHECKING);
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.withdraw(acc.getId(), BigDecimal.ZERO, null),
                "Нулевая сумма должна выбрасывать исключение");
    }

    /**
     * Тест: перевод средств между счетами (атомарность).
     */
    @Test
    @Order(7)
    void testTransfer() {
        Account from = accountService.createAccount(testUserId, "RUB", AccountType.CHECKING);
        Account to = accountService.createAccount(testUserId, "RUB", AccountType.SAVINGS);

        transactionService.deposit(from.getId(), new BigDecimal("1000"), null);

        transactionService.transfer(from.getId(), to.getId(), new BigDecimal("300"), "Тестовый перевод");

        Account fromUpdated = accountService.findById(from.getId()).orElseThrow();
        Account toUpdated = accountService.findById(to.getId()).orElseThrow();

        assertEquals(0, fromUpdated.getBalance().compareTo(new BigDecimal("700")));
        assertEquals(0, toUpdated.getBalance().compareTo(new BigDecimal("300")));
    }

    /**
     * Тест: перевод на тот же счёт должен выбрасывать исключение.
     */
    @Test
    @Order(8)
    void testTransferToSameAccount() {
        Account acc = accountService.createAccount(testUserId, "RUB", AccountType.CHECKING);
        transactionService.deposit(acc.getId(), new BigDecimal("100"), null);

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.transfer(acc.getId(), acc.getId(), new BigDecimal("50"), null),
                "Перевод на тот же счёт должен выбрасывать исключение");
    }

    /**
     * Тест: перевод при недостатке средств должен выбрасывать исключение
     * и НЕ изменять балансы (атомарность).
     */
    @Test
    @Order(9)
    void testTransferAtomicity() {
        Account from = accountService.createAccount(testUserId, "RUB", AccountType.CHECKING);
        Account to = accountService.createAccount(testUserId, "RUB", AccountType.CHECKING);
        transactionService.deposit(from.getId(), new BigDecimal("50"), null);

        assertThrows(IllegalArgumentException.class,
                () -> transactionService.transfer(from.getId(), to.getId(), new BigDecimal("200"), null));

        // Балансы не должны измениться
        assertEquals(0, accountService.findById(from.getId()).orElseThrow().getBalance()
                .compareTo(new BigDecimal("50")));
        assertEquals(0, accountService.findById(to.getId()).orElseThrow().getBalance()
                .compareTo(BigDecimal.ZERO));
    }

    /**
     * Тест: история транзакций возвращает правильное количество записей.
     */
    @Test
    @Order(10)
    void testTransactionHistory() {
        Account acc = accountService.createAccount(testUserId, "RUB", AccountType.CHECKING);
        transactionService.deposit(acc.getId(), new BigDecimal("100"), "Депозит 1");
        transactionService.deposit(acc.getId(), new BigDecimal("200"), "Депозит 2");
        transactionService.withdraw(acc.getId(), new BigDecimal("50"), "Снятие 1");

        List<Transaction> history = transactionService.getHistory(acc.getId(), 0);
        assertEquals(3, history.size(), "Должно быть 3 транзакции");
    }

    /**
     * Тест: заморозка счёта запрещает операции.
     */
    @Test
    @Order(11)
    void testFrozenAccountCannotTransact() {
        Account acc = accountService.createAccount(testUserId, "RUB", AccountType.CHECKING);
        transactionService.deposit(acc.getId(), new BigDecimal("500"), null);

        accountService.setActive(acc.getId(), false);

        assertThrows(IllegalStateException.class,
                () -> transactionService.deposit(acc.getId(), new BigDecimal("100"), null),
                "Замороженный счёт не должен принимать пополнение");
        assertThrows(IllegalStateException.class,
                () -> transactionService.withdraw(acc.getId(), new BigDecimal("50"), null),
                "Замороженный счёт не должен допускать снятие");
    }

    /**
     * Тест: нельзя удалить счёт с ненулевым балансом.
     */
    @Test
    @Order(12)
    void testDeleteAccountWithBalance() {
        Account acc = accountService.createAccount(testUserId, "RUB", AccountType.CHECKING);
        transactionService.deposit(acc.getId(), new BigDecimal("100"), null);

        assertThrows(IllegalStateException.class,
                () -> accountService.delete(acc.getId()),
                "Нельзя удалить счёт с ненулевым балансом");
    }

    /**
     * Тест: удаление пустого счёта.
     */
    @Test
    @Order(13)
    void testDeleteEmptyAccount() {
        Account acc = accountService.createAccount(testUserId, "USD", AccountType.SAVINGS);

        assertDoesNotThrow(() -> accountService.delete(acc.getId()),
                "Удаление пустого счёта не должно выбрасывать исключение");

        Optional<Account> deleted = accountService.findById(acc.getId());
        assertTrue(deleted.isEmpty(), "Удалённый счёт не должен находиться в БД");
    }

    /**
     * Тест: пополнение на отрицательную сумму должно выбрасывать исключение.
     */
    @Test
    @Order(14)
    void testDepositNegativeAmount() {
        Account acc = accountService.createAccount(testUserId, "RUB", AccountType.CHECKING);
        assertThrows(IllegalArgumentException.class,
                () -> transactionService.deposit(acc.getId(), new BigDecimal("-100"), null));
    }
}
