package ru.whitecristafer.banking.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Утилита для получения путей к директориям приложения.
 * Хранит данные в %APPDATA%\BankingApp на Windows,
 * и в ~/.bankingapp на Linux/macOS (для разработки).
 *
 * Используется для определения пути к:
 * - файлу базы данных SQLite
 * - директории логов
 * - конфигурационным файлам
 *
 * @author whitecristafer
 */
public class AppPaths {

    private static final Logger logger = LogManager.getLogger(AppPaths.class);

    /** Имя директории приложения */
    private static final String APP_DIR_NAME = "BankingApp";

    /** Имя файла базы данных */
    public static final String DB_FILE_NAME = "banking.db";

    /** Имя директории логов */
    public static final String LOGS_DIR_NAME = "logs";

    /**
     * Возвращает корневую директорию приложения в AppData/домашней директории.
     * На Windows: %APPDATA%\BankingApp
     * На Linux/macOS: ~/.bankingapp
     *
     * @return объект File, указывающий на корневую директорию приложения
     */
    public static File getAppDataDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path appDataPath;

        if (os.contains("win")) {
            // Windows: используем %APPDATA%
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isEmpty()) {
                appData = System.getProperty("user.home");
            }
            appDataPath = Paths.get(appData, APP_DIR_NAME);
        } else {
            // Linux/macOS: используем домашнюю директорию (для разработки)
            appDataPath = Paths.get(System.getProperty("user.home"), ".bankingapp");
        }

        File dir = appDataPath.toFile();
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                logger.info("Создана директория приложения: {}", dir.getAbsolutePath());
            }
        }
        return dir;
    }

    /**
     * Возвращает путь к файлу базы данных SQLite.
     *
     * @return File - путь к файлу banking.db
     */
    public static File getDatabaseFile() {
        return new File(getAppDataDir(), DB_FILE_NAME);
    }

    /**
     * Возвращает путь к директории логов приложения.
     *
     * @return File - директория logs внутри AppData приложения
     */
    public static File getLogsDir() {
        File logsDir = new File(getAppDataDir(), LOGS_DIR_NAME);
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        return logsDir;
    }

    /**
     * Возвращает строку JDBC URL для подключения к SQLite базе данных.
     *
     * @return строка вида "jdbc:sqlite:/path/to/banking.db"
     */
    public static String getDatabaseUrl() {
        return "jdbc:sqlite:" + getDatabaseFile().getAbsolutePath();
    }
}
