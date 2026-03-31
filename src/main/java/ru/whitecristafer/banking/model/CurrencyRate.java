package ru.whitecristafer.banking.model;

import java.math.BigDecimal;

/**
 * Модель курса валюты от ЦБ РФ.
 * Хранит курс относительно рубля, номинал и дату котировки.
 *
 * @author whitecristafer
 */
public class CurrencyRate {
    /** Уникальный идентификатор записи */
    private int id;
    /** Код валюты, например "USD", "EUR", "CNY" */
    private String currencyCode;
    /** Наименование валюты, например "Доллар США" */
    private String currencyName;
    /** Курс к рублю */
    private BigDecimal rate;
    /** Номинал от ЦБ РФ */
    private int nominal;
    /** Дата котировки в формате ISO */
    private String date;
    /** Метка времени загрузки из ЦБ */
    private String fetchedAt;

    public CurrencyRate() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public String getCurrencyName() { return currencyName; }
    public void setCurrencyName(String currencyName) { this.currencyName = currencyName; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
    public int getNominal() { return nominal; }
    public void setNominal(int nominal) { this.nominal = nominal; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(String fetchedAt) { this.fetchedAt = fetchedAt; }

    @Override
    public String toString() {
        return "CurrencyRate{code='" + currencyCode + "', rate=" + rate + ", nominal=" + nominal + "}";
    }
}
