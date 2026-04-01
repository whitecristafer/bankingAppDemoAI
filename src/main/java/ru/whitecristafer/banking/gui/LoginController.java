package ru.whitecristafer.banking.gui;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.whitecristafer.banking.model.Account;
import ru.whitecristafer.banking.model.User;

import java.util.Optional;

/**
 * Контроллер экрана авторизации (login.fxml).
 * Обрабатывает вход и регистрацию новых пользователей.
 *
 * @author whitecristafer
 */
public class LoginController {

    private static final Logger logger = LogManager.getLogger(LoginController.class);

    /** Поле ввода логина */
    @FXML private TextField usernameField;

    /** Поле ввода пароля */
    @FXML private PasswordField passwordField;

    /** Кнопка входа */
    @FXML private Button loginButton;

    /** Метка для отображения ошибок */
    @FXML private Label errorLabel;

    /**
     * Инициализация контроллера — вызывается JavaFX после загрузки FXML.
     */
    @FXML
    private void initialize() {
        errorLabel.setText("");
        // Нажатие Enter в поле пароля = нажатие кнопки Войти
        passwordField.setOnAction(e -> handleLogin());
    }

    /**
     * Обработчик нажатия кнопки "Войти".
     */
    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Введите логин и пароль");
            return;
        }

        try {
            User user = MainApp.userService.authenticate(username, password);
            SessionContext.setCurrentUser(user);
            logger.info("Успешный вход в GUI: {}", user.getUsername());

            if (user.isAdmin()) {
                MainApp.loadScreen("/fxml/admin.fxml");
            } else {
                MainApp.loadScreen("/fxml/dashboard.fxml");
            }
        } catch (Exception e) {
            showError(e.getMessage());
            logger.warn("Неудачная попытка входа в GUI для '{}'", username);
            passwordField.clear();
        }
    }

    /**
     * Обработчик нажатия кнопки "Регистрация" — открывает диалог регистрации.
     */
    @FXML
    private void handleRegister() {
        // Диалог сбора данных нового пользователя
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Регистрация нового пользователя");
        dialog.setHeaderText("Введите данные для регистрации");

        ButtonType registerButtonType = new ButtonType("Зарегистрироваться", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(registerButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField regUsername = new TextField();
        regUsername.setPromptText("Логин");
        PasswordField regPassword = new PasswordField();
        regPassword.setPromptText("Пароль");
        TextField regFullName = new TextField();
        regFullName.setPromptText("Полное имя");
        TextField regEmail = new TextField();
        regEmail.setPromptText("Email");
        TextField regPhone = new TextField();
        regPhone.setPromptText("Телефон (необязательно)");

        grid.add(new Label("Логин:"), 0, 0);
        grid.add(regUsername, 1, 0);
        grid.add(new Label("Пароль:"), 0, 1);
        grid.add(regPassword, 1, 1);
        grid.add(new Label("Полное имя:"), 0, 2);
        grid.add(regFullName, 1, 2);
        grid.add(new Label("Email:"), 0, 3);
        grid.add(regEmail, 1, 3);
        grid.add(new Label("Телефон:"), 0, 4);
        grid.add(regPhone, 1, 4);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == registerButtonType) {
            String phone = regPhone.getText().trim();
            try {
                // Регистрируем пользователя
                User newUser = MainApp.userService.register(
                        regUsername.getText().trim(),
                        regPassword.getText(),
                        regFullName.getText().trim(),
                        regEmail.getText().trim(),
                        phone.isEmpty() ? null : phone
                );

                // Автоматически создаём счёт CHECKING и SAVINGS в рублях
                Account checking = MainApp.accountService.createAccount(newUser.getId(), "RUB", Account.AccountType.CHECKING);
                MainApp.accountService.createAccount(newUser.getId(), "RUB", Account.AccountType.SAVINGS);

                // Пробуем создать виртуальную карту, привязанную к текущему счёту
                try {
                    if (MainApp.cardService != null) {
                        MainApp.cardService.createCard(newUser.getId(), checking.getId(), newUser.getFullName());
                    }
                } catch (Exception cardEx) {
                    logger.warn("Не удалось создать карту при регистрации: {}", cardEx.getMessage());
                }

                logger.info("Зарегистрирован новый пользователь: {}", newUser.getUsername());
                showSuccess("Регистрация успешна! Войдите с новыми данными.");
                usernameField.setText(regUsername.getText().trim());
            } catch (Exception e) {
                showError("Ошибка регистрации: " + e.getMessage());
                logger.error("Ошибка регистрации нового пользователя", e);
            }
        }
    }

    /**
     * Отображает сообщение об ошибке.
     *
     * @param message текст ошибки
     */
    private void showError(String message) {
        errorLabel.setText("⚠ " + message);
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");
    }

    /**
     * Отображает сообщение об успехе.
     *
     * @param message текст успеха
     */
    private void showSuccess(String message) {
        errorLabel.setText("✓ " + message);
        errorLabel.setStyle("-fx-text-fill: #27ae60;");
    }
}
