package ru.whitecristafer.banking.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;

/**
 * Сервис проверки обновлений приложения.
 * Проверяет наличие новых релизов на GitHub.
 * При ошибке сети возвращает пустой Optional.
 *
 * @author whitecristafer
 */
public class UpdateService {

    private static final Logger logger = LogManager.getLogger(UpdateService.class);

    /** URL GitHub API для получения последнего релиза */
    private static final String GITHUB_API_URL =
        "https://api.github.com/repos/whitecristafer/bankingAppDemoAI/releases/latest";

    /** Таймаут HTTP-запроса (мс) */
    private static final int HTTP_TIMEOUT_MS = 5000;

    /** URL последнего релиза (кэшируется после запроса) */
    private String latestReleaseUrl = null;

    /**
     * Проверяет наличие новой версии приложения.
     * Сравнивает currentVersion с tag_name из GitHub API.
     *
     * @param currentVersion текущая версия приложения, например "v1.0.0"
     * @return Optional с новой версией, если доступна, иначе пустой Optional
     */
    public Optional<String> checkForUpdates(String currentVersion) {
        try {
            String response = fetchLatestRelease();
            if (response == null || response.isEmpty()) return Optional.empty();

            String tagName = extractJsonField(response, "tag_name");
            String htmlUrl = extractJsonField(response, "html_url");

            if (tagName == null) return Optional.empty();

            // Кэшируем URL
            latestReleaseUrl = htmlUrl;

            // Сравниваем версии (простое сравнение строк)
            if (!tagName.equalsIgnoreCase(currentVersion)) {
                logger.info("Доступна новая версия: {} (текущая: {})", tagName, currentVersion);
                return Optional.of(tagName);
            }
            logger.debug("Версия актуальна: {}", currentVersion);
        } catch (Exception e) {
            logger.warn("Не удалось проверить обновления: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Возвращает URL страницы последнего релиза на GitHub.
     *
     * @return URL или null если не загружен
     */
    public String getLatestReleaseUrl() {
        if (latestReleaseUrl != null) return latestReleaseUrl;
        try {
            String response = fetchLatestRelease();
            if (response != null) {
                latestReleaseUrl = extractJsonField(response, "html_url");
            }
        } catch (Exception e) {
            logger.warn("Не удалось получить URL релиза: {}", e.getMessage());
        }
        return latestReleaseUrl;
    }

    /**
     * Выполняет HTTP GET запрос к GitHub API.
     *
     * @return строка JSON-ответа или null при ошибке
     */
    private String fetchLatestRelease() {
        try {
            URL url = new URL(GITHUB_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(HTTP_TIMEOUT_MS);
            conn.setReadTimeout(HTTP_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setRequestProperty("User-Agent", "MagaBank-App");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                logger.warn("GitHub API вернул статус: {}", responseCode);
                return null;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            logger.warn("Ошибка HTTP-запроса к GitHub: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Извлекает значение поля из JSON-строки без сторонних библиотек.
     * Поддерживает только строковые значения.
     *
     * @param json  JSON-строка
     * @param field название поля
     * @return значение поля или null
     */
    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx == -1) return null;
        int colonIdx = json.indexOf(":", keyIdx);
        if (colonIdx == -1) return null;
        int startIdx = json.indexOf("\"", colonIdx);
        if (startIdx == -1) return null;
        int endIdx = json.indexOf("\"", startIdx + 1);
        if (endIdx == -1) return null;
        return json.substring(startIdx + 1, endIdx);
    }
}
