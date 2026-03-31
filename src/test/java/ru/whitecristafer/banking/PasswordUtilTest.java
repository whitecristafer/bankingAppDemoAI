package ru.whitecristafer.banking;

import org.junit.jupiter.api.Test;
import ru.whitecristafer.banking.util.PasswordUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для утилиты PasswordUtil — проверяют хэширование и верификацию паролей.
 *
 * Проверяют:
 * - корректность хэширования BCrypt
 * - верификацию правильного пароля
 * - отклонение неверного пароля
 * - валидацию сложности пароля
 * - граничные случаи (null, пустая строка)
 *
 * @author whitecristafer
 */
class PasswordUtilTest {

    /**
     * Тест: хэш пароля не равен исходному паролю.
     */
    @Test
    void testHashNotEqualToPlaintext() {
        String plain = "mypassword1";
        String hashed = PasswordUtil.hash(plain);
        assertNotEquals(plain, hashed, "Хэш не должен совпадать с исходным паролем");
    }

    /**
     * Тест: хэш выглядит как BCrypt (начинается с $2b$).
     */
    @Test
    void testHashIsBcryptFormat() {
        String hashed = PasswordUtil.hash("testpass1");
        assertTrue(hashed.startsWith("$2"), "Хэш должен быть в формате BCrypt");
    }

    /**
     * Тест: верификация правильного пароля возвращает true.
     */
    @Test
    void testVerifyCorrectPassword() {
        String plain = "correct_password1";
        String hashed = PasswordUtil.hash(plain);
        assertTrue(PasswordUtil.verify(plain, hashed), "Правильный пароль должен проходить верификацию");
    }

    /**
     * Тест: верификация неверного пароля возвращает false.
     */
    @Test
    void testVerifyWrongPassword() {
        String hashed = PasswordUtil.hash("correct1");
        assertFalse(PasswordUtil.verify("wrong1", hashed), "Неверный пароль не должен проходить верификацию");
    }

    /**
     * Тест: два хэша одного пароля различаются (соль BCrypt).
     */
    @Test
    void testHashesAreDifferentForSamePassword() {
        String plain = "samepassword1";
        String hash1 = PasswordUtil.hash(plain);
        String hash2 = PasswordUtil.hash(plain);
        assertNotEquals(hash1, hash2, "Два хэша одного пароля должны различаться (соль)");
        // Но оба должны верифицироваться
        assertTrue(PasswordUtil.verify(plain, hash1));
        assertTrue(PasswordUtil.verify(plain, hash2));
    }

    /**
     * Тест: хэширование null-пароля выбрасывает исключение.
     */
    @Test
    void testHashNullPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordUtil.hash(null),
                "Хэширование null-пароля должно выбрасывать исключение");
    }

    /**
     * Тест: хэширование пустого пароля выбрасывает исключение.
     */
    @Test
    void testHashEmptyPassword() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordUtil.hash(""),
                "Хэширование пустого пароля должно выбрасывать исключение");
    }

    /**
     * Тест: верификация с null-паролем возвращает false (не исключение).
     */
    @Test
    void testVerifyNullPassword() {
        String hashed = PasswordUtil.hash("valid1");
        assertFalse(PasswordUtil.verify(null, hashed));
        assertFalse(PasswordUtil.verify("valid1", null));
    }

    /**
     * Тест: сильный пароль проходит валидацию.
     */
    @Test
    void testIsPasswordStrongValid() {
        assertTrue(PasswordUtil.isPasswordStrong("strong1"), "Пароль с 7+ символами и цифрой должен быть сильным");
        assertTrue(PasswordUtil.isPasswordStrong("abcde1"), "Ровно 6 символов с цифрой — сильный");
    }

    /**
     * Тест: слабые пароли не проходят валидацию.
     */
    @Test
    void testIsPasswordStrongInvalid() {
        assertFalse(PasswordUtil.isPasswordStrong("abc"), "Пароль короче 6 символов — слабый");
        assertFalse(PasswordUtil.isPasswordStrong("noCiph"), "Пароль без цифр — слабый");
        assertFalse(PasswordUtil.isPasswordStrong(null), "null — слабый пароль");
        assertFalse(PasswordUtil.isPasswordStrong(""), "Пустой пароль — слабый");
    }
}
