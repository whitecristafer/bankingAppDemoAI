package ru.whitecristafer.banking.gui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.whitecristafer.banking.model.Account;
import ru.whitecristafer.banking.model.User;

import java.util.List;

/**
 * Контроллер панели администратора (admin.fxml).
 *
 * Предоставляет полный CRUD для:
 * - Пользователей: создание, просмотр, блокировка, удаление, смена роли
 * - Счетов: создание, просмотр, заморозка, удаление
 * - Транзакций: просмотр всей истории операций
 *
 * Все изменения немедленно сохраняются в SQLite через сервисный слой.
 *
 * @author whitecristafer
 */
public class AdminController {

    private static final Logger logger = LogManager.getLogger(AdminController.class);

    // ─── Таблица пользователей ───────────────────────────────────────────────
    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, Integer> colUserId;
    @FXML private TableColumn<User, String> colUsername;
    @FXML private TableColumn<User, String> colFullName;
    @FXML private TableColumn<User, String> colEmail;
    @FXML private TableColumn<User, String> colRole;
    @FXML private TableColumn<User, String> colUserStatus;

    // ─── Таблица счетов ─────────────────────────────────────────────────────
    @FXML private TableView<Account> accountsTable;
    @FXML private TableColumn<Account, Integer> colAccId;
    @FXML private TableColumn<Account, Integer> colAccUserId;
    @FXML private TableColumn<Account, String> colAccNumber;
    @FXML private TableColumn<Account, String> colAccBalance;
    @FXML private TableColumn<Account, String> colAccCurrency;
    @FXML private TableColumn<Account, String> colAccType;
    @FXML private TableColumn<Account, String> colAccStatus;

    // ─── Форма создания пользователя ────────────────────────────────────────
    @FXML private TextField newUsernameField;
    @FXML private PasswordField newPasswordField;
    @FXML private TextField newFullNameField;
    @FXML private TextField newEmailField;
    @FXML private CheckBox newIsAdminCheck;

    // ─── Форма создания счёта ───────────────────────────────────────────────
    @FXML private TextField newAccUserIdField;
    @FXML private ComboBox<String> newAccCurrencyCombo;
    @FXML private ComboBox<String> newAccTypeCombo;

    // ─── Статус ──────────────────────────────────────────────────────────────
    @FXML private Label adminStatusLabel;
    @FXML private Label adminNameLabel;

    /**
     * Инициализация контроллера — настраивает таблицы и загружает данные.
     */
    @FXML
    private void initialize() {
        User admin = SessionContext.getCurrentUser();
        if (admin == null) { MainApp.loadScreen("/fxml/login.fxml"); return; }
        adminNameLabel.setText("Администратор: " + admin.getUsername());

        setupUsersTable();
        setupAccountsTable();
        setupComboBoxes();
        refreshUsers();
        refreshAccounts();
    }

    /** Настраивает колонки таблицы пользователей */
    private void setupUsersTable() {
        colUserId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colFullName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colRole.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().isAdmin() ? "Администратор" : "Пользователь"));
        colUserStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().isBlocked() ? "Заблокирован" : "Активен"));
    }

    /** Настраивает колонки таблицы счетов */
    private void setupAccountsTable() {
        colAccId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colAccUserId.setCellValueFactory(new PropertyValueFactory<>("userId"));
        colAccNumber.setCellValueFactory(new PropertyValueFactory<>("accountNumber"));
        colAccBalance.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getBalance().setScale(2).toPlainString()));
        colAccCurrency.setCellValueFactory(new PropertyValueFactory<>("currency"));
        colAccType.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getAccountType().name()));
        colAccStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().isActive() ? "Активен" : "Заморожен"));
    }

    /** Заполняет выпадающие списки значениями */
    private void setupComboBoxes() {
        newAccCurrencyCombo.setItems(FXCollections.observableArrayList("RUB", "USD", "EUR"));
        newAccCurrencyCombo.setValue("RUB");
        newAccTypeCombo.setItems(FXCollections.observableArrayList("CHECKING", "SAVINGS"));
        newAccTypeCombo.setValue("CHECKING");
    }

    /**
     * Обновляет таблицу пользователей из БД.
     */
    private void refreshUsers() {
        List<User> users = MainApp.userService.findAll();
        usersTable.setItems(FXCollections.observableArrayList(users));
    }

    /**
     * Обновляет таблицу счетов из БД.
     */
    private void refreshAccounts() {
        List<Account> accounts = MainApp.accountService.findAll();
        accountsTable.setItems(FXCollections.observableArrayList(accounts));
    }

    /**
     * Обработчик кнопки "Создать пользователя".
     * Создаёт нового пользователя с указанными данными.
     */
    @FXML
    private void handleCreateUser() {
        try {
            User newUser = MainApp.userService.register(
                    newUsernameField.getText(),
                    newPasswordField.getText(),
                    newFullNameField.getText(),
                    newEmailField.getText()
            );
            if (newIsAdminCheck.isSelected()) {
                MainApp.userService.setAdmin(newUser.getId(), true);
            }
            showStatus("✓ Пользователь '" + newUser.getUsername() + "' создан (ID=" + newUser.getId() + ")", false);
            clearUserForm();
            refreshUsers();
            logger.info("Создан пользователь: {}", newUser.getUsername());
        } catch (Exception e) {
            showStatus("Ошибка: " + e.getMessage(), true);
            logger.error("Ошибка создания пользователя в AdminController", e);
        }
    }

    /**
     * Обработчик кнопки "Заблокировать/Разблокировать выбранного пользователя".
     */
    @FXML
    private void handleToggleBlock() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Выберите пользователя", true); return; }
        if (selected.getId() == SessionContext.getCurrentUser().getId()) {
            showStatus("Нельзя заблокировать самого себя", true); return;
        }
        try {
            MainApp.userService.setBlocked(selected.getId(), !selected.isBlocked());
            String action = selected.isBlocked() ? "разблокирован" : "заблокирован";
            showStatus("✓ Пользователь " + selected.getUsername() + " " + action, false);
            refreshUsers();
        } catch (Exception e) {
            showStatus("Ошибка: " + e.getMessage(), true);
        }
    }

    /**
     * Обработчик кнопки "Удалить пользователя".
     * Запрашивает подтверждение перед удалением.
     */
    @FXML
    private void handleDeleteUser() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Выберите пользователя", true); return; }
        if (selected.getId() == SessionContext.getCurrentUser().getId()) {
            showStatus("Нельзя удалить самого себя", true); return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Подтверждение удаления");
        confirm.setHeaderText("Удалить пользователя?");
        confirm.setContentText("Пользователь '" + selected.getUsername() +
                "' и все его счета будут удалены безвозвратно.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    MainApp.userService.delete(selected.getId());
                    showStatus("✓ Пользователь " + selected.getUsername() + " удалён", false);
                    refreshUsers();
                    refreshAccounts();
                } catch (Exception e) {
                    showStatus("Ошибка: " + e.getMessage(), true);
                }
            }
        });
    }

    /**
     * Обработчик кнопки "Создать счёт".
     * Создаёт новый счёт для указанного пользователя.
     */
    @FXML
    private void handleCreateAccount() {
        try {
            int userId = Integer.parseInt(newAccUserIdField.getText().trim());
            String currency = newAccCurrencyCombo.getValue();
            Account.AccountType type = Account.AccountType.valueOf(newAccTypeCombo.getValue());

            MainApp.userService.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + userId));

            Account acc = MainApp.accountService.createAccount(userId, currency, type);
            showStatus("✓ Счёт создан: " + acc.getAccountNumber(), false);
            newAccUserIdField.clear();
            refreshAccounts();
        } catch (Exception e) {
            showStatus("Ошибка: " + e.getMessage(), true);
            logger.error("Ошибка создания счёта в AdminController", e);
        }
    }

    /**
     * Обработчик кнопки "Заморозить/Разморозить счёт".
     */
    @FXML
    private void handleToggleAccount() {
        Account selected = accountsTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Выберите счёт", true); return; }
        try {
            MainApp.accountService.setActive(selected.getId(), !selected.isActive());
            String action = selected.isActive() ? "заморожен" : "разморожен";
            showStatus("✓ Счёт " + selected.getAccountNumber() + " " + action, false);
            refreshAccounts();
        } catch (Exception e) {
            showStatus("Ошибка: " + e.getMessage(), true);
        }
    }

    /**
     * Обработчик кнопки "Удалить счёт".
     */
    @FXML
    private void handleDeleteAccount() {
        Account selected = accountsTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Выберите счёт", true); return; }
        try {
            MainApp.accountService.delete(selected.getId());
            showStatus("✓ Счёт " + selected.getAccountNumber() + " удалён", false);
            refreshAccounts();
        } catch (Exception e) {
            showStatus("Ошибка: " + e.getMessage(), true);
        }
    }

    /**
     * Обработчик кнопки "Выйти" — возвращает на экран входа.
     */
    @FXML
    private void handleLogout() {
        logger.info("Выход администратора {} из GUI", SessionContext.getCurrentUser().getUsername());
        SessionContext.clear();
        MainApp.loadScreen("/fxml/login.fxml");
    }

    /** Очищает форму создания пользователя */
    private void clearUserForm() {
        newUsernameField.clear();
        newPasswordField.clear();
        newFullNameField.clear();
        newEmailField.clear();
        newIsAdminCheck.setSelected(false);
    }

    /**
     * Отображает статусное сообщение.
     *
     * @param message текст сообщения
     * @param isError true — ошибка, false — успех
     */
    private void showStatus(String message, boolean isError) {
        adminStatusLabel.setText(message);
        adminStatusLabel.setStyle(isError
                ? "-fx-text-fill: #e74c3c; -fx-font-weight: bold;"
                : "-fx-text-fill: #27ae60; -fx-font-weight: bold;");
    }
}
