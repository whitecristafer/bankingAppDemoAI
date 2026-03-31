package ru.whitecristafer.banking.util;

/**
 * Утилита для валидации российских документов и персональных данных.
 * Содержит проверки паспорта, СНИЛС, ИНН, email и имени.
 *
 * @author whitecristafer
 */
public class ValidationUtil {

    /** Приватный конструктор — утилитный класс */
    private ValidationUtil() {}

    /**
     * Проверяет корректность паспортных данных.
     * Серия — 4 цифры, номер — 6 цифр.
     *
     * @param series  серия паспорта
     * @param number  номер паспорта
     * @return true если данные корректны
     */
    public static boolean isValidPassport(String series, String number) {
        if (series == null || number == null) return false;
        return series.matches("\\d{4}") && number.matches("\\d{6}");
    }

    /**
     * Проверяет корректность СНИЛС с контрольной суммой.
     * Формат: XXX-XXX-XXX XX
     * Алгоритм: умножаем цифры 1-9 на веса 9,8,7,6,5,4,3,2,1, суммируем.
     * Если сумма > 101, sum %= 101. Если sum == 100 или 101 → КС = "00".
     * Иначе КС = sum, отформатированный как 2 цифры.
     *
     * @param snils СНИЛС в формате "XXX-XXX-XXX XX"
     * @return true если СНИЛС корректен
     */
    public static boolean isValidSnils(String snils) {
        if (snils == null) return false;
        // Убираем разделители
        String digits = snils.replaceAll("[^\\d]", "");
        if (digits.length() != 11) return false;

        int[] weights = {9, 8, 7, 6, 5, 4, 3, 2, 1};
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (digits.charAt(i) - '0') * weights[i];
        }
        if (sum > 101) sum %= 101;
        int checkDigits = Integer.parseInt(digits.substring(9));
        if (sum == 100 || sum == 101) {
            return checkDigits == 0;
        }
        return checkDigits == sum;
    }

    /**
     * Проверяет корректность ИНН с контрольной суммой.
     * Для юр. лиц — 10 цифр, для физ. лиц — 12 цифр.
     *
     * @param inn           строка ИНН
     * @param isLegalEntity true для юр. лица (10 цифр), false для физ. лица (12 цифр)
     * @return true если ИНН корректен
     */
    public static boolean isValidInn(String inn, boolean isLegalEntity) {
        if (inn == null) return false;
        String digits = inn.replaceAll("[^\\d]", "");
        if (isLegalEntity) {
            if (digits.length() != 10) return false;
            int[] w = {2, 4, 10, 3, 5, 9, 4, 6, 8};
            int sum = 0;
            for (int i = 0; i < 9; i++) sum += (digits.charAt(i) - '0') * w[i];
            int check = sum % 11 % 10;
            return check == (digits.charAt(9) - '0');
        } else {
            if (digits.length() != 12) return false;
            int[] w1 = {7, 2, 4, 10, 3, 5, 9, 4, 6, 8};
            int[] w2 = {3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8};
            int sum1 = 0, sum2 = 0;
            for (int i = 0; i < 10; i++) sum1 += (digits.charAt(i) - '0') * w1[i];
            for (int i = 0; i < 11; i++) sum2 += (digits.charAt(i) - '0') * w2[i];
            int n1 = sum1 % 11 % 10;
            int n2 = sum2 % 11 % 10;
            return n1 == (digits.charAt(10) - '0') && n2 == (digits.charAt(11) - '0');
        }
    }

    /**
     * Проверяет корректность email-адреса (базовая проверка по регулярному выражению).
     *
     * @param email строка email
     * @return true если email выглядит корректным
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) return false;
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    /**
     * Проверяет корректность имени (поддерживает русские и латинские буквы, пробелы).
     * Минимум 2 символа, только буквы и пробелы.
     *
     * @param name строка имени
     * @return true если имя корректно
     */
    public static boolean isValidName(String name) {
        if (name == null || name.trim().length() < 2) return false;
        return name.trim().matches("[\\p{L} ]+");
    }
}
