package ru.whitecristafer.banking.util;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Утилита для безопасного хэширования и проверки паролей.
 * Использует алгоритм BCrypt с настраиваемым "стоимостным фактором" (cost factor).
 *
 * BCrypt — рекомендуемый алгоритм для хранения паролей:
 * - автоматически добавляет соль (salt)
 * - адаптивный: можно увеличить сложность при росте мощности CPU
 * - устойчив к атакам перебором
 *
 * @author whitecristafer
 */
public class PasswordUtil {

    /**
     * Стоимостной фактор BCrypt (2^cost итераций хэширования).
     * 12 — хороший баланс безопасности и производительности.
     */
    private static final int BCRYPT_COST = 12;

    /**
     * Приватный конструктор — класс статичный, экземпляры не нужны.
     */
    private PasswordUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Хэширует пароль с использованием BCrypt.
     *
     * @param plainPassword пароль в открытом виде (не должен быть null или пустым)
     * @return хэш пароля в формате BCrypt (60 символов)
     * @throws IllegalArgumentException если пароль null или пустой
     */
    public static String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Пароль не может быть пустым");
        }
        return BCrypt.withDefaults().hashToString(BCRYPT_COST, plainPassword.toCharArray());
    }

    /**
     * Проверяет соответствие пароля в открытом виде сохранённому хэшу BCrypt.
     *
     * @param plainPassword пароль в открытом виде для проверки
     * @param hashedPassword хэш пароля из базы данных
     * @return true если пароль совпадает с хэшем, false в противном случае
     */
    public static boolean verify(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }
        BCrypt.Result result = BCrypt.verifyer().verify(plainPassword.toCharArray(), hashedPassword);
        return result.verified;
    }

    /**
     * Проверяет, является ли пароль достаточно сложным.
     * Требования: минимум 6 символов, хотя бы одна цифра.
     *
     * @param password проверяемый пароль
     * @return true если пароль соответствует требованиям безопасности
     */
    public static boolean isPasswordStrong(String password) {
        if (password == null || password.length() < 6) {
            return false;
        }
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        return hasDigit;
    }
}
