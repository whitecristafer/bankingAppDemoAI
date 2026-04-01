package ru.whitecristafer.banking.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.whitecristafer.banking.db.DatabaseManager;
import ru.whitecristafer.banking.model.CurrencyRate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Сервис курсов валют — загружает курсы ЦБ РФ и хранит их в кэше (SQLite).
 * При отсутствии сети использует резервные курсы.
 * Поддерживает конвертацию с комиссией 1.5%.
 *
 * @author whitecristafer
 */
public class CurrencyService {

    private static final Logger logger = LogManager.getLogger(CurrencyService.class);

    /** URL API ЦБ РФ для получения курсов валют */
    private static final String CBR_URL = "https://www.cbr.ru/scripts/XML_daily.asp";

    /** Таймаут подключения к ЦБ РФ (мс) */
    private static final int HTTP_TIMEOUT_MS = 5000;

    /** Комиссия при конвертации валют (%) */
    private static final double EXCHANGE_COMMISSION = 1.5;

    /** Резервные курсы на случай недоступности ЦБ РФ */
    private static final Map<String, Double> FALLBACK_RATES = Map.of(
            "USD", 90.0,
            "EUR", 98.0,
            "CNY", 12.5
    );

    /** Поддерживаемые коды валют */
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "CNY");

    /** Менеджер базы данных */
    private final DatabaseManager dbManager;

    /**
     * Конструктор сервиса валют.
     *
     * @param dbManager менеджер базы данных
     */
    public CurrencyService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Возвращает текущие курсы валют — сначала из кэша, потом из ЦБ РФ.
     * Если дата последнего обновления — сегодня, возвращает из кэша.
     *
     * @return карта курсов: ключ — код валюты, значение — объект CurrencyRate
     */
    public Map<String, CurrencyRate> getCurrentRates() {
        // Пробуем получить из кэша (если сегодняшние данные есть)
        Map<String, CurrencyRate> cached = getCachedRates();
        if (!cached.isEmpty()) {
            logger.debug("Курсы валют загружены из кэша");
            return cached;
        }
        // Загружаем из ЦБ РФ
        try {
            fetchAndSaveRates();
            cached = getCachedRates();
            if (!cached.isEmpty()) return cached;
        } catch (Exception e) {
            logger.warn("Не удалось загрузить курсы из ЦБ РФ, использую резервные: {}", e.getMessage());
        }
        // Возвращаем резервные курсы
        return getFallbackRates();
    }

    /**
     * Загружает курсы валют из ЦБ РФ и сохраняет в БД.
     * Парсит XML-ответ ЦБ РФ.
     *
     * @throws Exception при ошибке сети или парсинга
     */
    public void fetchAndSaveRates() throws Exception {
        logger.info("Загрузка курсов валют из ЦБ РФ...");
        String xml = fetchXmlFromCbr();
        List<CurrencyRate> rates = parseXml(xml);
        for (CurrencyRate rate : rates) {
            if (SUPPORTED_CURRENCIES.contains(rate.getCurrencyCode())) {
                saveRate(rate);
            }
        }
        logger.info("Курсы валют обновлены в БД: {} записей", rates.size());
    }

    /**
     * Конвертирует сумму из одной валюты в другую с учётом комиссии 1.5%.
     * Если fromCurrency == toCurrency, комиссия не применяется.
     *
     * @param amount       сумма для конвертации
     * @param fromCurrency исходная валюта (например, "USD")
     * @param toCurrency   целевая валюта (например, "RUB")
     * @return сконвертированная сумма
     */
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) return amount;

        Map<String, CurrencyRate> rates = getCurrentRates();

        // Конвертируем в рубли
        double amountInRub;
        if ("RUB".equalsIgnoreCase(fromCurrency)) {
            amountInRub = amount.doubleValue();
        } else {
            CurrencyRate fromRate = rates.get(fromCurrency.toUpperCase());
            if (fromRate == null) throw new IllegalArgumentException("Неизвестная валюта: " + fromCurrency);
            amountInRub = amount.doubleValue() * fromRate.getRate().doubleValue() / fromRate.getNominal();
        }

        // Конвертируем из рублей в целевую валюту
        double result;
        if ("RUB".equalsIgnoreCase(toCurrency)) {
            result = amountInRub;
        } else {
            CurrencyRate toRate = rates.get(toCurrency.toUpperCase());
            if (toRate == null) throw new IllegalArgumentException("Неизвестная валюта: " + toCurrency);
            result = amountInRub / toRate.getRate().doubleValue() * toRate.getNominal();
        }

        // Применяем комиссию
        result = result * (1.0 - EXCHANGE_COMMISSION / 100.0);
        return BigDecimal.valueOf(result).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Возвращает процент комиссии за конвертацию.
     *
     * @return комиссия (1.5%)
     */
    public double getExchangeCommission() {
        return EXCHANGE_COMMISSION;
    }

    /**
     * Загружает XML с сайта ЦБ РФ.
     *
     * @return строка XML
     * @throws Exception при ошибке сети
     */
    private String fetchXmlFromCbr() throws Exception {
        URL url = new URL(CBR_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(HTTP_TIMEOUT_MS);
        conn.setReadTimeout(HTTP_TIMEOUT_MS);
        conn.setRequestMethod("GET");

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "windows-1251"))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
        }
        return sb.toString();
    }

    /**
     * Парсит XML-ответ ЦБ РФ и извлекает курсы валют.
     *
     * @param xml строка XML
     * @return список объектов CurrencyRate
     */
    private List<CurrencyRate> parseXml(String xml) {
        List<CurrencyRate> rates = new ArrayList<>();
        // Простой парсинг XML без сторонних библиотек
        String[] valuteBlocks = xml.split("<Valute ");
        String today = LocalDate.now().toString();

        for (int i = 1; i < valuteBlocks.length; i++) {
            String block = valuteBlocks[i];
            try {
                String charCode = extractTag(block, "CharCode");
                String name = extractTag(block, "Name");
                String nominalStr = extractTag(block, "Nominal");
                String valueStr = extractTag(block, "Value");

                if (charCode == null || valueStr == null) continue;
                // ЦБ РФ использует запятую как разделитель
                valueStr = valueStr.replace(",", ".");
                nominalStr = nominalStr != null ? nominalStr.trim() : "1";

                CurrencyRate rate = new CurrencyRate();
                rate.setCurrencyCode(charCode.trim());
                rate.setCurrencyName(name != null ? name.trim() : charCode.trim());
                rate.setRate(new BigDecimal(valueStr.trim()));
                rate.setNominal(Integer.parseInt(nominalStr.trim()));
                rate.setDate(today);
                rates.add(rate);
            } catch (Exception e) {
                logger.debug("Ошибка парсинга блока валюты: {}", e.getMessage());
            }
        }
        return rates;
    }

    /**
     * Извлекает значение XML-тега из строки.
     *
     * @param block XML-блок
     * @param tag   название тега
     * @return содержимое тега или null
     */
    private String extractTag(String block, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int start = block.indexOf(open);
        int end = block.indexOf(close);
        if (start == -1 || end == -1) return null;
        return block.substring(start + open.length(), end);
    }

    /**
     * Сохраняет курс валюты в базу данных.
     *
     * @param rate объект CurrencyRate
     */
    private void saveRate(CurrencyRate rate) {
        String sql = """
            INSERT INTO currency_rates (currency_code, currency_name, rate, nominal, rate_date)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, rate.getCurrencyCode());
            pstmt.setString(2, rate.getCurrencyName());
            pstmt.setDouble(3, rate.getRate().doubleValue());
            pstmt.setInt(4, rate.getNominal());
            pstmt.setString(5, rate.getDate());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.debug("Ошибка сохранения курса {}: {}", rate.getCurrencyCode(), e.getMessage());
        }
    }

    /**
     * Возвращает кэшированные курсы из БД (только актуальные — за сегодня).
     *
     * @return карта курсов или пустая карта если нет кэша
     */
    private Map<String, CurrencyRate> getCachedRates() {
        Map<String, CurrencyRate> rates = new HashMap<>();
        String today = LocalDate.now().toString();
        String sql = """
            SELECT * FROM currency_rates WHERE rate_date = ?
            ORDER BY fetched_at DESC
            """;
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, today);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    CurrencyRate rate = mapRowToRate(rs);
                    rates.putIfAbsent(rate.getCurrencyCode(), rate);
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка чтения кэша курсов: {}", e.getMessage());
        }
        return rates;
    }

    /**
     * Создаёт карту резервных курсов.
     *
     * @return карта резервных курсов
     */
    private Map<String, CurrencyRate> getFallbackRates() {
        Map<String, CurrencyRate> fallback = new HashMap<>();
        FALLBACK_RATES.forEach((code, rateValue) -> {
            CurrencyRate rate = new CurrencyRate();
            rate.setCurrencyCode(code);
            rate.setCurrencyName(code.equals("USD") ? "Доллар США" :
                                 code.equals("EUR") ? "Евро" : "Юань Женьминьби");
            rate.setRate(BigDecimal.valueOf(rateValue));
            rate.setNominal(1);
            rate.setDate(LocalDate.now().toString());
            fallback.put(code, rate);
        });
        return fallback;
    }

    /**
     * Преобразует строку ResultSet в объект CurrencyRate.
     *
     * @param rs ResultSet с данными
     * @return объект CurrencyRate
     * @throws SQLException при ошибке чтения
     */
    private CurrencyRate mapRowToRate(ResultSet rs) throws SQLException {
        CurrencyRate rate = new CurrencyRate();
        rate.setId(rs.getInt("id"));
        rate.setCurrencyCode(rs.getString("currency_code"));
        rate.setCurrencyName(rs.getString("currency_name"));
        rate.setRate(BigDecimal.valueOf(rs.getDouble("rate")));
        rate.setNominal(rs.getInt("nominal"));
        rate.setDate(rs.getString("rate_date"));
        rate.setFetchedAt(rs.getString("fetched_at"));
        return rate;
    }
}
