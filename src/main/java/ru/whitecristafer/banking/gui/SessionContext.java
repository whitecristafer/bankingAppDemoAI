package ru.whitecristafer.banking.gui;

import ru.whitecristafer.banking.model.User;

/**
 * Контекст сессии — хранит данные о текущем авторизованном пользователе.
 *
 * Используется для передачи информации о вошедшем пользователе
 * между контроллерами JavaFX без необходимости передавать её явно.
 *
 * В рамках MVP-приложения реализован как статический класс.
 * В полноценном приложении следует использовать DI-контейнер (Spring, CDI).
 *
 * @author whitecristafer
 */
public class SessionContext {

    /** Текущий авторизованный пользователь */
    private static User currentUser;

    /**
     * Приватный конструктор — класс статичный, экземпляры не нужны.
     */
    private SessionContext() {}

    /**
     * Устанавливает текущего авторизованного пользователя.
     *
     * @param user объект User, вошедший в систему
     */
    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    /**
     * Возвращает текущего авторизованного пользователя.
     *
     * @return объект User или null если пользователь не авторизован
     */
    public static User getCurrentUser() {
        return currentUser;
    }

    /**
     * Очищает сессию — вызывается при выходе из системы.
     */
    public static void clear() {
        currentUser = null;
    }

    /**
     * Проверяет, авторизован ли текущий пользователь.
     *
     * @return true если пользователь авторизован
     */
    public static boolean isLoggedIn() {
        return currentUser != null;
    }
}
