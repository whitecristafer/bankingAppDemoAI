package ru.whitecristafer.banking.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.whitecristafer.banking.db.DatabaseInitializer;
import ru.whitecristafer.banking.db.DatabaseManager;
import ru.whitecristafer.banking.service.AccountService;
import ru.whitecristafer.banking.service.CardService;
import ru.whitecristafer.banking.service.ContactService;
import ru.whitecristafer.banking.service.CurrencyService;
import ru.whitecristafer.banking.service.SettingsService;
import ru.whitecristafer.banking.service.TransactionService;
import ru.whitecristafer.banking.service.UpdateService;
import ru.whitecristafer.banking.service.UserService;

import java.util.Objects;

/**
 * Главный класс JavaFX-приложения — точка входа для GUI-режима.
 *
 * Отвечает за:
 * - инициализацию JavaFX-приложения
 * - загрузку первого экрана (экрана входа)
 * - хранение глобальных экземпляров сервисов (контекст приложения)
 * - настройку размеров и заголовка окна
 *
 * Сервисы хранятся как статические поля для доступа из всех контроллеров.
 * В полноценном продакшн-приложении следует использовать DI-фреймворк (например, Spring).
 *
 * @author whitecristafer
 */
public class MainApp extends Application {

    private static final Logger logger = LogManager.getLogger(MainApp.class);

    /** Глобальный менеджер БД — используется всеми контроллерами */
    private static DatabaseManager dbManager;

    /** Сервис пользователей — CRUD + аутентификация */
    public static UserService userService;

    /** Сервис счетов — CRUD + финансовые операции */
    public static AccountService accountService;

    /** Сервис транзакций — депозит, снятие, перевод, история */
    public static TransactionService transactionService;

    /** Сервис виртуальных карт */
    public static CardService cardService;

    /** Сервис контактов пользователя */
    public static ContactService contactService;

    /** Сервис курсов валют */
    public static CurrencyService currencyService;

    /** Сервис настроек приложения */
    public static SettingsService settingsService;

    /** Главный Stage (окно приложения) — используется для смены экранов */
    private static Stage primaryStage;

    /**
     * Инициализирует сервисы приложения до запуска JavaFX.
     * Вызывается JavaFX автоматически перед start().
     */
    @Override
    public void init() {
        logger.info("Инициализация сервисов приложения...");
        dbManager = DatabaseManager.getInstance();
        DatabaseInitializer.initialize(dbManager.getConnection());
        userService = new UserService(dbManager);
        accountService = new AccountService(dbManager);
        transactionService = new TransactionService(dbManager, accountService);
        cardService = new CardService(dbManager);
        contactService = new ContactService(dbManager);
        currencyService = new CurrencyService(dbManager);
        settingsService = new SettingsService(dbManager);
        logger.info("Сервисы инициализированы");
    }

    /**
     * Запускает JavaFX-приложение — загружает экран входа.
     *
     * @param stage главное окно приложения (передаётся JavaFX-рантаймом)
     * @throws Exception при ошибке загрузки FXML
     */
    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        stage.setTitle("🏦 MagaBank — ООО «МагаБанк»");
        stage.setMinWidth(900);
        stage.setMinHeight(620);

        // Асинхронная проверка обновлений при старте
        new Thread(() -> new UpdateService().checkForUpdates("v1.0.0")).start();

        loadScreen("/fxml/login.fxml");

        stage.show();
        logger.info("GUI-приложение запущено");
    }

    /**
     * Вызывается при закрытии приложения.
     * Закрывает соединение с базой данных.
     */
    @Override
    public void stop() {
        logger.info("Завершение работы приложения...");
        if (dbManager != null) {
            dbManager.closeConnection();
        }
        logger.info("Приложение завершено");
    }

    /**
     * Загружает FXML-экран и устанавливает его в главное окно.
     * Используется контроллерами для навигации между экранами.
     *
     * @param fxmlPath путь к FXML-файлу (от корня classpath, например "/fxml/login.fxml")
     * @throws RuntimeException при ошибке загрузки FXML
     */
    public static void loadScreen(String fxmlPath) {
        try {
            Parent root = FXMLLoader.load(Objects.requireNonNull(
                    MainApp.class.getResource(fxmlPath)));
            // Определяем тему: light или dark
            String theme = (settingsService != null) ? settingsService.getTheme() : "dark";
            String cssFile = "light".equalsIgnoreCase(theme) ? "/css/style-light.css" : "/css/style.css";
            Scene scene = primaryStage.getScene();
            if (scene == null) {
                scene = new Scene(root, 1000, 700);
                try {
                    scene.getStylesheets().add(
                            Objects.requireNonNull(MainApp.class.getResource(cssFile)).toExternalForm());
                } catch (NullPointerException e) {
                    logger.warn("CSS-файл не найден: {}", cssFile);
                }
                primaryStage.setScene(scene);
            } else {
                scene.setRoot(root);
                scene.getStylesheets().clear();
                try {
                    scene.getStylesheets().add(
                            Objects.requireNonNull(MainApp.class.getResource(cssFile)).toExternalForm());
                } catch (NullPointerException e) {
                    logger.warn("CSS-файл не найден: {}", cssFile);
                }
            }
            logger.debug("Загружен экран: {}", fxmlPath);
        } catch (Exception e) {
            logger.error("Ошибка загрузки экрана: {}", fxmlPath, e);
            throw new RuntimeException("Не удалось загрузить экран: " + fxmlPath, e);
        }
    }

    /**
     * Возвращает главное окно приложения.
     *
     * @return объект Stage — главное окно JavaFX
     */
    public static Stage getPrimaryStage() {
        return primaryStage;
    }
}
