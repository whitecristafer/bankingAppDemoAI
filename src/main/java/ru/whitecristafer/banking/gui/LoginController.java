package ru.whitecristafer.banking.gui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.whitecristafer.banking.model.User;

/**
 * Контроллер экрана авторизации (login.fxml).
 *
 * Обрабатывает:
 * - ввод логина и пароля
 * - аутентификацию через UserService
 * - навигацию на экран пользователя или администратора
 * - отображение ошибок авторизации
 *
 * После успешного входа сохраняет текущего пользователя в SessionContext
 * и переходит на соответствующий экран.
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
     * Устанавливает обработчик Enter на поле пароля.
     */
    @FXML
    private void initialize() {
        errorLabel.setText("");
        // Нажатие Enter в поле пароля = нажатие кнопки Войти
        passwordField.setOnAction(e -> handleLogin());
    }

    /**
     * Обработчик нажатия кнопки "Войти".
     * Аутентифицирует пользователя и переходит на нужный экран.
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
            // Сохраняем текущего пользователя в сессии
            SessionContext.setCurrentUser(user);
            logger.info("Успешный вход в GUI: {}", user.getUsername());

            // Переходим на соответствующий экран
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
     * Отображает сообщение об ошибке под полем пароля.
     *
     * @param message текст ошибки
     */
    private void showError(String message) {
        errorLabel.setText("⚠ " + message);
        errorLabel.setStyle("-fx-text-fill: #e74c3c;");
    }
}
