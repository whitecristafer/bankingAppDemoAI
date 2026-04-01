package ru.whitecristafer.banking.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты утилиты для работы с российскими номерами телефонов.
 * Проверяет нормализацию, валидацию и форматирование.
 *
 * @author whitecristafer
 */
class PhoneUtilTest {

    /**
     * Нормализация номера в формате +7...
     */
    @Test
    void testNormalizeWithPlus7() {
        assertEquals("+79001234567", PhoneUtil.normalize("+79001234567"));
    }

    /**
     * Нормализация номера начинающегося с 8
     */
    @Test
    void testNormalizeWith8() {
        assertEquals("+79001234567", PhoneUtil.normalize("89001234567"));
    }

    /**
     * Нормализация номера начинающегося с 7 (без плюса)
     */
    @Test
    void testNormalizeWith7() {
        assertEquals("+79001234567", PhoneUtil.normalize("79001234567"));
    }

    /**
     * Нормализация номера с форматированием (пробелы, скобки, тире)
     */
    @Test
    void testNormalizeFormatted() {
        assertEquals("+79001234567", PhoneUtil.normalize("8(900)123-45-67"));
        assertEquals("+79001234567", PhoneUtil.normalize("+7 900 123-45-67"));
        assertEquals("+79001234567", PhoneUtil.normalize("7 (900) 123 45 67"));
    }

    /**
     * Нормализация 10-значного номера (без кода страны)
     */
    @Test
    void testNormalize10Digits() {
        assertEquals("+79001234567", PhoneUtil.normalize("9001234567"));
    }

    /**
     * Нормализация null и пустых значений
     */
    @Test
    void testNormalizeNullOrBlank() {
        assertNull(PhoneUtil.normalize(null));
        assertNull(PhoneUtil.normalize(""));
        assertNull(PhoneUtil.normalize("   "));
    }

    /**
     * Нормализация некорректного номера возвращает null
     */
    @Test
    void testNormalizeInvalid() {
        assertNull(PhoneUtil.normalize("123"));
        assertNull(PhoneUtil.normalize("abcde"));
    }

    /**
     * Валидация корректного российского мобильного номера
     */
    @Test
    void testIsValidTrue() {
        assertTrue(PhoneUtil.isValid("+79001234567"));
        assertTrue(PhoneUtil.isValid("89001234567"));
    }

    /**
     * Валидация некорректного номера
     */
    @Test
    void testIsValidFalse() {
        assertFalse(PhoneUtil.isValid(null));
        assertFalse(PhoneUtil.isValid(""));
        assertFalse(PhoneUtil.isValid("123456"));
        assertFalse(PhoneUtil.isValid("+1234567890"));
    }

    /**
     * Форматирование в читаемый вид: +7 (XXX) XXX-XX-XX
     */
    @Test
    void testFormat() {
        String formatted = PhoneUtil.format("+79001234567");
        assertNotNull(formatted);
        assertTrue(formatted.contains("900"), "Должен содержать код оператора");
    }

    /**
     * Форматирование null возвращает null или пустую строку
     */
    @Test
    void testFormatNull() {
        String result = PhoneUtil.format(null);
        assertTrue(result == null || result.isEmpty());
    }
}
