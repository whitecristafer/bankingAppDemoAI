package ru.whitecristafer.banking.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.whitecristafer.banking.db.DatabaseManager;

import java.sql.*;

/**
 * Сервис настроек приложения.
 * Хранит и извлекает настройки из таблицы app_settings (ключ-значение).
 * Поддерживаемые настройки:
 * - theme: "dark" (по умолчанию) или "light"
 * - fontScale: от 0.8 до 1.4 (по умолчанию 1.0)
 * - uiScale: от 0.8 до 1.3 (по умолчанию 1.0)
 *
 * @author whitecristafer
 */
public class SettingsService {

    private static final Logger logger = LogManager.getLogger(SettingsService.class);

    /** Ключ настройки темы */
    public static final String KEY_THEME = "theme";
    /** Ключ масштаба шрифта */
    public static final String KEY_FONT_SCALE = "fontScale";
    /** Ключ масштаба интерфейса */
    public static final String KEY_UI_SCALE = "uiScale";

    /** Тема по умолчанию */
    private static final String DEFAULT_THEME = "dark";
    /** Масштаб шрифта по умолчанию */
    private static final double DEFAULT_FONT_SCALE = 1.0;
    /** Масштаб интерфейса по умолчанию */
    private static final double DEFAULT_UI_SCALE = 1.0;

    /** Менеджер базы данных */
    private final DatabaseManager dbManager;

    /**
     * Конструктор сервиса настроек.
     *
     * @param dbManager менеджер базы данных
     */
    public SettingsService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Получает значение настройки по ключу.
     *
     * @param key          ключ настройки
     * @param defaultValue значение по умолчанию, если ключ не найден
     * @return значение настройки или defaultValue
     */
    public String getSetting(String key, String defaultValue) {
        String sql = "SELECT value FROM app_settings WHERE key = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, key);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getString("value");
            }
        } catch (SQLException e) {
            logger.error("Ошибка чтения настройки '{}': {}", key, e.getMessage());
        }
        return defaultValue;
    }

    /**
     * Сохраняет значение настройки.
     *
     * @param key   ключ настройки
     * @param value значение
     */
    public void setSetting(String key, String value) {
        String sql = "INSERT OR REPLACE INTO app_settings (key, value) VALUES (?, ?)";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.executeUpdate();
            logger.debug("Настройка '{}' = '{}'", key, value);
        } catch (SQLException e) {
            logger.error("Ошибка сохранения настройки '{}': {}", key, e.getMessage());
        }
    }

    /**
     * Возвращает текущую тему интерфейса.
     *
     * @return "dark" или "light"
     */
    public String getTheme() {
        return getSetting(KEY_THEME, DEFAULT_THEME);
    }

    /**
     * Устанавливает тему интерфейса.
     *
     * @param theme "dark" или "light"
     */
    public void setTheme(String theme) {
        setSetting(KEY_THEME, theme);
    }

    /**
     * Возвращает масштаб шрифта (диапазон 0.8–1.4).
     *
     * @return коэффициент масштаба шрифта
     */
    public double getFontScale() {
        try {
            return Double.parseDouble(getSetting(KEY_FONT_SCALE, String.valueOf(DEFAULT_FONT_SCALE)));
        } catch (NumberFormatException e) {
            return DEFAULT_FONT_SCALE;
        }
    }

    /**
     * Устанавливает масштаб шрифта с ограничением диапазона [0.8, 1.4].
     *
     * @param scale коэффициент масштаба
     */
    public void setFontScale(double scale) {
        double clamped = Math.max(0.8, Math.min(1.4, scale));
        setSetting(KEY_FONT_SCALE, String.valueOf(clamped));
    }

    /**
     * Возвращает масштаб интерфейса (диапазон 0.8–1.3).
     *
     * @return коэффициент масштаба интерфейса
     */
    public double getUiScale() {
        try {
            return Double.parseDouble(getSetting(KEY_UI_SCALE, String.valueOf(DEFAULT_UI_SCALE)));
        } catch (NumberFormatException e) {
            return DEFAULT_UI_SCALE;
        }
    }

    /**
     * Устанавливает масштаб интерфейса с ограничением диапазона [0.8, 1.3].
     *
     * @param scale коэффициент масштаба
     */
    public void setUiScale(double scale) {
        double clamped = Math.max(0.8, Math.min(1.3, scale));
        setSetting(KEY_UI_SCALE, String.valueOf(clamped));
    }
}
