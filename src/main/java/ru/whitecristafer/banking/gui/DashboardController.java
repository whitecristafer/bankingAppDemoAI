package ru.whitecristafer.banking.gui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.whitecristafer.banking.model.Account;
import ru.whitecristafer.banking.model.Transaction;
import ru.whitecristafer.banking.model.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Контроллер дашборда обычного пользователя (dashboard.fxml).
 *
 * Отображает:
 * - список счетов пользователя
 * - историю транзакций для выбранного счёта
 * - формы для пополнения, снятия и перевода средств
 *
 * Все операции выполняются через TransactionService и AccountService.
 * После каждой операции данные автоматически обновляются.
 *
 * @author whitecristafer
 */
public class DashboardController {

    private static final Logger logger = LogManager.getLogger(DashboardController.class);

    // ─── Метки ──────────────────────────────────────────────────────────────
    @FXML private Label welcomeLabel;
    @FXML private Label statusLabel;

    // ─── Таблица счетов ─────────────────────────────────────────────────────
    @FXML private TableView<Account> accountsTable;
    @FXML private TableColumn<Account, String> colAccountNumber;
    @FXML private TableColumn<Account, String> colBalance;
    @FXML private TableColumn<Account, String> colCurrency;
    @FXML private TableColumn<Account, String> colAccountType;
    @FXML private TableColumn<Account, String> colAccountStatus;

    // ─── Таблица транзакций ─────────────────────────────────────────────────
    @FXML private TableView<Transaction> transactionsTable;
    @FXML private TableColumn<Transaction, String> colTxType;
    @FXML private TableColumn<Transaction, String> colTxAmount;
    @FXML private TableColumn<Transaction, String> colTxDate;
    @FXML private TableColumn<Transaction, String> colTxDesc;

    // ─── Поля операций ──────────────────────────────────────────────────────
    @FXML private TextField depositAmountField;
    @FXML private TextField withdrawAmountField;
    @FXML private TextField transferToField;
    @FXML private TextField transferAmountField;
    @FXML private TextField transferDescField;

    /** Текущий авторизованный пользователь */
    private User currentUser;

    /**
     * Инициализация контроллера — вызывается JavaFX после загрузки FXML.
     * Настраивает таблицы и загружает данные текущего пользователя.
     */
    @FXML
    private void initialize() {
        currentUser = SessionContext.getCurrentUser();
        if (currentUser == null) {
            MainApp.loadScreen("/fxml/login.fxml");
            return;
        }

        welcomeLabel.setText("Добро пожаловать, " + currentUser.getFullName() + "!");
        setupAccountsTable();
        setupTransactionsTable();
        refreshAccounts();
    }

    /** Настраивает колонки таблицы счетов */
    private void setupAccountsTable() {
        colAccountNumber.setCellValueFactory(new PropertyValueFactory<>("accountNumber"));
        colBalance.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getBalance().setScale(2).toPlainString()));
        colCurrency.setCellValueFactory(new PropertyValueFactory<>("currency"));
        colAccountType.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getAccountType().name()));
        colAccountStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().isActive() ? "Активен" : "Заморожен"));

        // При выборе счёта загружаем его транзакции
        accountsTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, selected) -> {
                    if (selected != null) loadTransactions(selected.getId());
                });
    }

    /** Настраивает колонки таблицы транзакций */
    private void setupTransactionsTable() {
        colTxType.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getType().name()));
        colTxAmount.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getAmount().setScale(2).toPlainString()));
        colTxDate.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getCreatedAt() != null
                        ? c.getValue().getCreatedAt().substring(0, 16) : "—"));
        colTxDesc.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getDescription() != null
                        ? c.getValue().getDescription() : ""));
    }

    /**
     * Обновляет список счетов из базы данных.
     */
    private void refreshAccounts() {
        List<Account> accounts = MainApp.accountService.findByUserId(currentUser.getId());
        accountsTable.setItems(FXCollections.observableArrayList(accounts));
    }

    /**
     * Загружает транзакции для выбранного счёта.
     *
     * @param accountId идентификатор счёта
     */
    private void loadTransactions(int accountId) {
        List<Transaction> txs = MainApp.transactionService.getHistory(accountId, 50);
        transactionsTable.setItems(FXCollections.observableArrayList(txs));
    }

    /**
     * Обработчик кнопки "Пополнить" — выполняет депозит на выбранный счёт.
     */
    @FXML
    private void handleDeposit() {
        Account selected = accountsTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Выберите счёт в таблице", true); return; }

        try {
            BigDecimal amount = new BigDecimal(depositAmountField.getText().trim());
            MainApp.transactionService.deposit(selected.getId(), amount, null);
            showStatus(String.format("✓ Счёт пополнен на %.2f %s", amount.doubleValue(), selected.getCurrency()), false);
            depositAmountField.clear();
            refreshAccounts();
            loadTransactions(selected.getId());
        } catch (Exception e) {
            showStatus("Ошибка: " + e.getMessage(), true);
            logger.error("Ошибка пополнения в GUI", e);
        }
    }

    /**
     * Обработчик кнопки "Снять" — выполняет снятие с выбранного счёта.
     */
    @FXML
    private void handleWithdraw() {
        Account selected = accountsTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Выберите счёт в таблице", true); return; }

        try {
            BigDecimal amount = new BigDecimal(withdrawAmountField.getText().trim());
            MainApp.transactionService.withdraw(selected.getId(), amount, null);
            showStatus(String.format("✓ Снято %.2f %s", amount.doubleValue(), selected.getCurrency()), false);
            withdrawAmountField.clear();
            refreshAccounts();
            loadTransactions(selected.getId());
        } catch (Exception e) {
            showStatus("Ошибка: " + e.getMessage(), true);
            logger.error("Ошибка снятия в GUI", e);
        }
    }

    /**
     * Обработчик кнопки "Перевести" — выполняет перевод средств.
     */
    @FXML
    private void handleTransfer() {
        Account selected = accountsTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Выберите счёт-источник в таблице", true); return; }

        try {
            String toAccNum = transferToField.getText().trim().toUpperCase();
            BigDecimal amount = new BigDecimal(transferAmountField.getText().trim());
            String desc = transferDescField.getText().trim();

            Optional<Account> toAcc = MainApp.accountService.findByAccountNumber(toAccNum);
            if (toAcc.isEmpty()) { showStatus("Счёт получателя не найден: " + toAccNum, true); return; }

            MainApp.transactionService.transfer(selected.getId(), toAcc.get().getId(), amount,
                    desc.isEmpty() ? null : desc);
            showStatus(String.format("✓ Перевод %.2f → %s выполнен", amount.doubleValue(), toAccNum), false);
            transferToField.clear();
            transferAmountField.clear();
            transferDescField.clear();
            refreshAccounts();
            loadTransactions(selected.getId());
        } catch (Exception e) {
            showStatus("Ошибка: " + e.getMessage(), true);
            logger.error("Ошибка перевода в GUI", e);
        }
    }

    /**
     * Обработчик кнопки "Выйти" — очищает сессию и возвращает на экран входа.
     */
    @FXML
    private void handleLogout() {
        logger.info("Выход пользователя {} из GUI", currentUser.getUsername());
        SessionContext.clear();
        MainApp.loadScreen("/fxml/login.fxml");
    }

    /**
     * Отображает статусное сообщение.
     *
     * @param message текст сообщения
     * @param isError true — ошибка (красный), false — успех (зелёный)
     */
    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle(isError
                ? "-fx-text-fill: #e74c3c; -fx-font-weight: bold;"
                : "-fx-text-fill: #27ae60; -fx-font-weight: bold;");
    }
}
