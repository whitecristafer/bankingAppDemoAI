package ru.whitecristafer.banking.util;

/**
 * Утилита для работы с российскими телефонными номерами.
 * Поддерживает нормализацию, валидацию и форматирование.
 *
 * @author whitecristafer
 */
public class PhoneUtil {

    /** Приватный конструктор — утилитный класс, не предназначен для создания экземпляров */
    private PhoneUtil() {}

    /**
     * Нормализует телефонный номер к формату +7XXXXXXXXXX.
     * Принимает форматы: +7..., 8..., 7..., со скобками, пробелами, тире.
     *
     * @param phone входной номер телефона
     * @return нормализованный номер вида +7XXXXXXXXXX или null если нельзя нормализовать
     */
    public static String normalize(String phone) {
        if (phone == null || phone.isBlank()) return null;
        // Убираем все не-цифровые символы (пробелы, скобки, тире, плюс)
        String digits = phone.replaceAll("[^\\d]", "");
        if (digits.isEmpty()) return null;
        // Если начинается с 8, заменяем на 7
        if (digits.startsWith("8") && digits.length() == 11) {
            digits = "7" + digits.substring(1);
        }
        // Если 10 цифр — добавляем 7
        if (digits.length() == 10) {
            digits = "7" + digits;
        }
        // Должно быть 11 цифр начиная с 7
        if (digits.length() != 11 || !digits.startsWith("7")) return null;
        return "+" + digits;
    }

    /**
     * Проверяет, является ли номер допустимым российским телефонным номером.
     * После нормализации должен соответствовать +7[0-9]{10}.
     *
     * @param phone номер телефона для проверки
     * @return true если номер допустим
     */
    public static boolean isValid(String phone) {
        String normalized = normalize(phone);
        if (normalized == null) return false;
        return normalized.matches("\\+7[0-9]{10}");
    }

    /**
     * Форматирует номер телефона в вид "+7 (XXX) XXX-XX-XX".
     *
     * @param phone номер телефона
     * @return отформатированный номер или null если номер некорректен
     */
    public static String format(String phone) {
        String normalized = normalize(phone);
        if (normalized == null) return null;
        // +7XXXXXXXXXX -> +7 (XXX) XXX-XX-XX
        String digits = normalized.substring(2); // убираем +7
        return String.format("+7 (%s) %s-%s-%s",
                digits.substring(0, 3),
                digits.substring(3, 6),
                digits.substring(6, 8),
                digits.substring(8, 10));
    }

    /**
     * Проверяет, является ли номер мобильным российским.
     * Мобильные номера: 3-й символ кода оператора 3-9.
     *
     * @param phone номер телефона
     * @return true если номер мобильный
     */
    public static boolean isValidMobile(String phone) {
        String normalized = normalize(phone);
        if (normalized == null) return false;
        // +7XXXXXXXXXXX — 3й символ после +7
        char thirdDigit = normalized.charAt(2);
        return thirdDigit >= '3' && thirdDigit <= '9';
    }
}
