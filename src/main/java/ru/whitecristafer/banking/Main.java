package ru.whitecristafer.banking;

import javafx.application.Application;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.whitecristafer.banking.db.DatabaseInitializer;
import ru.whitecristafer.banking.db.DatabaseManager;
import ru.whitecristafer.banking.gui.MainApp;
import ru.whitecristafer.banking.service.AccountService;
import ru.whitecristafer.banking.service.CardService;
import ru.whitecristafer.banking.service.ContactService;
import ru.whitecristafer.banking.service.CurrencyService;
import ru.whitecristafer.banking.service.SettingsService;
import ru.whitecristafer.banking.service.TransactionService;
import ru.whitecristafer.banking.service.UserService;
import ru.whitecristafer.banking.tui.TuiApp;
import ru.whitecristafer.banking.util.AppPaths;

/**
 * Главная точка входа банковского приложения.
 *
 * Поддерживает два режима запуска, управляемых аргументами командной строки:
 * - --gui  : запуск JavaFX графического интерфейса (по умолчанию)
 * - --tui  : запуск терминального интерфейса (TUI) с цветными шрифтами и навигацией
 *
 * Пример запуска:
 *   java -jar bankingApp.jar --gui     # GUI-режим (по умолчанию)
 *   java -jar bankingApp.jar --tui     # TUI-режим
 *
 * Для запуска из IDEA: см. скрипты в папке scripts/ и Run Configurations в .idea/
 *
 * @author whitecristafer
 */
public class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);

    /**
     * Основной метод — точка входа JVM.
     * Определяет режим запуска по аргументам и запускает соответствующий интерфейс.
     *
     * @param args аргументы командной строки:
     *             --gui  — запуск GUI (JavaFX, по умолчанию)
     *             --tui  — запуск TUI (Lanterna, терминал)
     */
    public static void main(String[] args) {
        // Настраиваем путь к директории логов ДО инициализации Log4j2
        System.setProperty("banking.logs.dir", AppPaths.getLogsDir().getAbsolutePath());

        // Проверка операционной системы — MagaBank работает только на Windows
        boolean skipOsCheck = "true".equalsIgnoreCase(System.getProperty("banking.skip.os.check"));
        if (!skipOsCheck) {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (!os.contains("windows")) {
                System.err.println("Приложение MagaBank предназначено только для Windows. Запустите его на операционной системе Windows.");
                logger.error("Запуск на неподдерживаемой ОС: {}", os);
                System.exit(1);
            }
        }

        logger.info("============================================");
        logger.info("MagaBank — ООО «МагаБанк» — версия 1.0.0");
        logger.info("Разработчик: whitecristafer");
        logger.info("GitHub: https://github.com/whitecristafer/bankingAppDemoAI/");
        logger.info("AppData директория: {}", AppPaths.getAppDataDir().getAbsolutePath());
        logger.info("База данных: {}", AppPaths.getDatabaseFile().getAbsolutePath());
        logger.info("============================================");

        // Определяем режим запуска из аргументов
        boolean isTui = false;
        for (String arg : args) {
            if ("--tui".equalsIgnoreCase(arg)) {
                isTui = true;
                break;
            }
            if ("--gui".equalsIgnoreCase(arg)) {
                isTui = false;
                break;
            }
        }

        if (isTui) {
            logger.info("Режим запуска: TUI (терминальный интерфейс)");
            runTui();
        } else {
            logger.info("Режим запуска: GUI (графический интерфейс JavaFX)");
            runGui(args);
        }
    }

    /**
     * Запускает TUI-режим.
     * Инициализирует базу данных и все сервисы, затем запускает терминальный интерфейс.
     */
    private static void runTui() {
        DatabaseManager dbManager = null;
        try {
            dbManager = DatabaseManager.getInstance();
            DatabaseInitializer.initialize(dbManager.getConnection());

            UserService userService = new UserService(dbManager);
            AccountService accountService = new AccountService(dbManager);
            TransactionService transactionService = new TransactionService(dbManager, accountService);
            CardService cardService = new CardService(dbManager);
            ContactService contactService = new ContactService(dbManager);
            CurrencyService currencyService = new CurrencyService(dbManager);
            SettingsService settingsService = new SettingsService(dbManager);

            TuiApp tuiApp = new TuiApp(userService, accountService, transactionService,
                    cardService, contactService, currencyService, settingsService);
            tuiApp.start();
        } catch (Exception e) {
            logger.error("Критическая ошибка в TUI-режиме", e);
            System.err.println("Критическая ошибка: " + e.getMessage());
            System.exit(1);
        } finally {
            if (dbManager != null) {
                dbManager.closeConnection();
            }
            logger.info("TUI-режим завершён");
        }
    }

    /**
     * Запускает GUI-режим (JavaFX).
     * Инициализация БД и сервисов выполняется в MainApp.init().
     *
     * @param args аргументы командной строки (передаются в JavaFX Application)
     */
    private static void runGui(String[] args) {
        try {
            Application.launch(MainApp.class, args);
        } catch (Exception e) {
            logger.error("Критическая ошибка в GUI-режиме", e);
            System.err.println("Критическая ошибка при запуске GUI: " + e.getMessage());
            System.exit(1);
        }
    }
}
