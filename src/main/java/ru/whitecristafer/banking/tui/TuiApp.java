package ru.whitecristafer.banking.tui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.whitecristafer.banking.model.Account;
import ru.whitecristafer.banking.model.Transaction;
import ru.whitecristafer.banking.model.User;
import ru.whitecristafer.banking.service.AccountService;
import ru.whitecristafer.banking.service.TransactionService;
import ru.whitecristafer.banking.service.UserService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Терминальный интерфейс пользователя (TUI) банковского приложения.
 *
 * Реализован с использованием библиотеки Lanterna 3.x, которая обеспечивает:
 * - цветной вывод в терминале
 * - навигацию стрелками, Enter и Space
 * - адаптивный (масштабируемый) экран
 * - совместимость с Windows CMD, PowerShell и Unix-терминалами
 *
 * Структура TUI:
 * 1. Экран авторизации
 * 2. Главное меню пользователя (просмотр счетов, операции)
 * 3. Панель администратора (CRUD пользователей и счетов)
 *
 * @author whitecristafer
 */
public class TuiApp {

    private static final Logger logger = LogManager.getLogger(TuiApp.class);

    /** Сервисы — инжектируются через конструктор */
    private final UserService userService;
    private final AccountService accountService;
    private final TransactionService transactionService;

    /** Lanterna Screen — основной экран TUI */
    private Screen screen;

    /** Текущий авторизованный пользователь (null если не авторизован) */
    private User currentUser;

    /** Флаг работы основного цикла */
    private boolean running = true;

    /**
     * Конструктор — принимает все необходимые сервисы.
     *
     * @param userService        сервис пользователей
     * @param accountService     сервис счетов
     * @param transactionService сервис транзакций
     */
    public TuiApp(UserService userService, AccountService accountService,
                  TransactionService transactionService) {
        this.userService = userService;
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    /**
     * Расширенный конструктор со всеми сервисами.
     *
     * @param userService        сервис пользователей
     * @param accountService     сервис счетов
     * @param transactionService сервис транзакций
     * @param cardService        сервис карт (может быть null)
     * @param contactService     сервис контактов (может быть null)
     * @param currencyService    сервис валют (может быть null)
     * @param settingsService    сервис настроек (может быть null)
     */
    public TuiApp(UserService userService, AccountService accountService,
                  TransactionService transactionService,
                  ru.whitecristafer.banking.service.CardService cardService,
                  ru.whitecristafer.banking.service.ContactService contactService,
                  ru.whitecristafer.banking.service.CurrencyService currencyService,
                  ru.whitecristafer.banking.service.SettingsService settingsService) {
        this.userService = userService;
        this.accountService = accountService;
        this.transactionService = transactionService;
        // Дополнительные сервисы игнорируются в базовой TUI-реализации
    }

    /**
     * Запускает TUI-приложение.
     * Инициализирует Lanterna terminal, настраивает экран и запускает основной цикл.
     */
    public void start() {
        try {
            DefaultTerminalFactory factory = new DefaultTerminalFactory();
            // Устанавливаем начальный размер терминала
            factory.setInitialTerminalSize(new TerminalSize(120, 35));
            Terminal terminal = factory.createTerminal();
            screen = new TerminalScreen(terminal);
            screen.startScreen();
            screen.setCursorPosition(null); // Скрываем курсор

            logger.info("TUI запущен");
            runMainLoop();

        } catch (IOException e) {
            logger.error("Ошибка запуска TUI", e);
            System.err.println("Ошибка запуска терминального интерфейса: " + e.getMessage());
        } finally {
            if (screen != null) {
                try {
                    screen.stopScreen();
                } catch (IOException e) {
                    logger.error("Ошибка остановки экрана TUI", e);
                }
            }
        }
    }

    /**
     * Основной цикл TUI — показывает экраны входа и меню.
     *
     * @throws IOException при ошибке ввода/вывода
     */
    private void runMainLoop() throws IOException {
        while (running) {
            if (currentUser == null) {
                showLoginScreen();
            } else if (currentUser.isAdmin()) {
                showAdminMenu();
            } else {
                showUserMenu();
            }
        }
    }

    /**
     * Показывает экран входа в систему.
     * Пользователь вводит логин и пароль.
     *
     * @throws IOException при ошибке ввода
     */
    private void showLoginScreen() throws IOException {
        TuiScreen loginScreen = new TuiScreen(screen);
        loginScreen.drawHeader("🏦  БАНКОВСКОЕ ПРИЛОЖЕНИЕ  —  ВХОД В СИСТЕМУ");
        loginScreen.drawBox(5, 3, 110, 12);

        loginScreen.drawText(7, 5, "Добро пожаловать! Введите учётные данные для входа.", TextColor.ANSI.CYAN, null);
        loginScreen.drawText(7, 7, "Логин:    ", TextColor.ANSI.WHITE, null);
        loginScreen.drawText(7, 9, "Пароль:   ", TextColor.ANSI.WHITE, null);
        loginScreen.drawText(7, 11, "[Enter] Войти   [Esc] Выйти   [R] Регистрация", TextColor.ANSI.YELLOW, null);
        screen.refresh();

        String username = loginScreen.readInput(17, 7, 40, false);
        if (username == null) { running = false; return; }

        String password = loginScreen.readInput(17, 9, 40, true);
        if (password == null) { running = false; return; }

        try {
            currentUser = userService.authenticate(username, password);
            showMessage("✓ Успешный вход: " + currentUser.getFullName(), TextColor.ANSI.GREEN);
            logger.info("Вход через TUI: {}", currentUser.getUsername());
        } catch (Exception e) {
            showMessage("✗ Ошибка: " + e.getMessage(), TextColor.ANSI.RED);
            logger.warn("Неудачная попытка входа через TUI для '{}'", username);
        }
    }

    /**
     * Показывает главное меню пользователя с навигацией стрелками.
     *
     * @throws IOException при ошибке ввода
     */
    private void showUserMenu() throws IOException {
        String[] menuItems = {
            "💳  Мои счета",
            "💰  Пополнить счёт",
            "📤  Снять средства",
            "📨  Перевод между счетами",
            "📜  История транзакций",
            "👤  Профиль",
            "🚪  Выйти из аккаунта"
        };

        int selected = 0;
        while (true) {
            drawUserMenuScreen(menuItems, selected);
            com.googlecode.lanterna.input.KeyStroke key = screen.readInput();
            if (key == null) continue;

            switch (key.getKeyType()) {
                case ArrowUp -> selected = (selected - 1 + menuItems.length) % menuItems.length;
                case ArrowDown -> selected = (selected + 1) % menuItems.length;
                case Enter -> {
                    boolean exit = handleUserMenuChoice(selected);
                    if (exit) return;
                }
                case Escape -> { currentUser = null; return; }
                default -> {}
            }
        }
    }

    /**
     * Отрисовывает экран главного меню пользователя.
     *
     * @param items    пункты меню
     * @param selected индекс выбранного пункта (0-based)
     * @throws IOException при ошибке отрисовки
     */
    private void drawUserMenuScreen(String[] items, int selected) throws IOException {
        TuiScreen ts = new TuiScreen(screen);
        TerminalSize size = screen.getTerminalSize();
        ts.drawHeader("🏦  БАНКОВСКОЕ ПРИЛОЖЕНИЕ  —  " + currentUser.getFullName().toUpperCase());
        ts.drawBox(3, 2, size.getColumns() - 6, items.length * 2 + 4);
        ts.drawText(5, 3, "Главное меню", TextColor.ANSI.CYAN, SGR.BOLD);

        for (int i = 0; i < items.length; i++) {
            int row = 5 + i * 2;
            if (i == selected) {
                ts.drawText(7, row, " ► " + items[i] + " ", TextColor.ANSI.BLACK, TextColor.ANSI.CYAN, SGR.BOLD);
            } else {
                ts.drawText(7, row, "   " + items[i], TextColor.ANSI.WHITE, null);
            }
        }

        ts.drawText(5, items.length * 2 + 7, "↑↓ навигация   Enter — выбрать   Esc — выход",
                TextColor.ANSI.YELLOW, null);
        screen.refresh();
    }

    /**
     * Обрабатывает выбор пункта меню пользователя.
     *
     * @param choice индекс выбранного пункта
     * @return true если нужно вернуться к экрану входа
     * @throws IOException при ошибке ввода
     */
    private boolean handleUserMenuChoice(int choice) throws IOException {
        switch (choice) {
            case 0 -> showAccounts();
            case 1 -> showDepositScreen();
            case 2 -> showWithdrawScreen();
            case 3 -> showTransferScreen();
            case 4 -> showTransactionHistory();
            case 5 -> showUserProfile();
            case 6 -> { currentUser = null; return true; }
        }
        return false;
    }

    /**
     * Отображает список счетов текущего пользователя.
     *
     * @throws IOException при ошибке ввода
     */
    private void showAccounts() throws IOException {
        List<Account> accounts = accountService.findByUserId(currentUser.getId());
        TuiScreen ts = new TuiScreen(screen);
        ts.drawHeader("💳  МОИ СЧЕТА");

        if (accounts.isEmpty()) {
            ts.drawText(5, 3, "У вас нет счетов. Обратитесь к администратору.", TextColor.ANSI.YELLOW, null);
        } else {
            ts.drawText(5, 3, String.format("%-15s %-12s %-15s %-10s %-8s",
                    "Номер счёта", "Тип", "Баланс", "Валюта", "Статус"),
                    TextColor.ANSI.CYAN, SGR.BOLD);
            ts.drawText(5, 4, "─".repeat(70), TextColor.ANSI.CYAN, null);

            int row = 5;
            for (Account acc : accounts) {
                String status = acc.isActive() ? "Активен" : "Заморожен";
                TextColor color = acc.isActive() ? TextColor.ANSI.WHITE : TextColor.ANSI.RED;
                ts.drawText(5, row, String.format("%-15s %-12s %-15s %-10s %-8s",
                        acc.getAccountNumber(),
                        acc.getAccountType().name(),
                        acc.getBalance().setScale(2),
                        acc.getCurrency(),
                        status), color, null);
                row++;
            }
        }

        ts.drawText(5, 20, "[Enter или Esc] Вернуться", TextColor.ANSI.YELLOW, null);
        screen.refresh();
        waitForEnter();
    }

    /**
     * Экран пополнения счёта.
     *
     * @throws IOException при ошибке ввода
     */
    private void showDepositScreen() throws IOException {
        List<Account> accounts = accountService.findByUserId(currentUser.getId());
        if (accounts.isEmpty()) {
            showMessage("У вас нет счетов для пополнения.", TextColor.ANSI.YELLOW);
            return;
        }

        TuiScreen ts = new TuiScreen(screen);
        ts.drawHeader("💰  ПОПОЛНЕНИЕ СЧЁТА");
        ts.drawBox(5, 2, 90, 14);

        // Показываем список счетов
        ts.drawText(7, 4, "Выберите счёт:", TextColor.ANSI.CYAN, null);
        int row = 5;
        for (int i = 0; i < accounts.size(); i++) {
            Account acc = accounts.get(i);
            ts.drawText(7, row++, String.format("  [%d] %s  Баланс: %.2f %s",
                    i + 1, acc.getAccountNumber(), acc.getBalance().doubleValue(), acc.getCurrency()),
                    TextColor.ANSI.WHITE, null);
        }

        ts.drawText(7, row + 1, "Номер счёта (введите ACC-XXXXXX): ", TextColor.ANSI.WHITE, null);
        screen.refresh();
        String accNum = ts.readInput(42, row + 1, 20, false);
        if (accNum == null || accNum.isBlank()) return;

        ts.drawText(7, row + 2, "Сумма пополнения: ", TextColor.ANSI.WHITE, null);
        screen.refresh();
        String amountStr = ts.readInput(26, row + 2, 15, false);
        if (amountStr == null || amountStr.isBlank()) return;

        try {
            Account acc = accountService.findByAccountNumber(accNum.trim().toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("Счёт не найден"));
            if (acc.getUserId() != currentUser.getId()) {
                throw new IllegalArgumentException("Это не ваш счёт");
            }
            BigDecimal amount = new BigDecimal(amountStr.trim());
            transactionService.deposit(acc.getId(), amount, null);
            showMessage(String.format("✓ Счёт %s пополнен на %.2f %s", acc.getAccountNumber(),
                    amount.doubleValue(), acc.getCurrency()), TextColor.ANSI.GREEN);
        } catch (Exception e) {
            showMessage("✗ Ошибка: " + e.getMessage(), TextColor.ANSI.RED);
            logger.error("Ошибка пополнения счёта через TUI", e);
        }
    }

    /**
     * Экран снятия средств.
     *
     * @throws IOException при ошибке ввода
     */
    private void showWithdrawScreen() throws IOException {
        TuiScreen ts = new TuiScreen(screen);
        ts.drawHeader("📤  СНЯТИЕ СРЕДСТВ");
        ts.drawBox(5, 2, 90, 10);

        ts.drawText(7, 4, "Номер счёта (введите ACC-XXXXXX): ", TextColor.ANSI.WHITE, null);
        screen.refresh();
        String accNum = ts.readInput(42, 4, 20, false);
        if (accNum == null || accNum.isBlank()) return;

        ts.drawText(7, 6, "Сумма снятия: ", TextColor.ANSI.WHITE, null);
        screen.refresh();
        String amountStr = ts.readInput(22, 6, 15, false);
        if (amountStr == null || amountStr.isBlank()) return;

        try {
            Account acc = accountService.findByAccountNumber(accNum.trim().toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("Счёт не найден"));
            if (acc.getUserId() != currentUser.getId()) {
                throw new IllegalArgumentException("Это не ваш счёт");
            }
            BigDecimal amount = new BigDecimal(amountStr.trim());
            transactionService.withdraw(acc.getId(), amount, null);
            showMessage(String.format("✓ Снято %.2f %s со счёта %s", amount.doubleValue(),
                    acc.getCurrency(), acc.getAccountNumber()), TextColor.ANSI.GREEN);
        } catch (Exception e) {
            showMessage("✗ Ошибка: " + e.getMessage(), TextColor.ANSI.RED);
            logger.error("Ошибка снятия средств через TUI", e);
        }
    }

    /**
     * Экран перевода между счетами.
     *
     * @throws IOException при ошибке ввода
     */
    private void showTransferScreen() throws IOException {
        TuiScreen ts = new TuiScreen(screen);
        ts.drawHeader("📨  ПЕРЕВОД СРЕДСТВ");
        ts.drawBox(5, 2, 90, 12);

        ts.drawText(7, 4, "Счёт отправителя (ACC-XXXXXX): ", TextColor.ANSI.WHITE, null);
        screen.refresh();
        String fromNum = ts.readInput(39, 4, 20, false);
        if (fromNum == null || fromNum.isBlank()) return;

        ts.drawText(7, 6, "Счёт получателя  (ACC-XXXXXX): ", TextColor.ANSI.WHITE, null);
        screen.refresh();
        String toNum = ts.readInput(39, 6, 20, false);
        if (toNum == null || toNum.isBlank()) return;

        ts.drawText(7, 8, "Сумма перевода: ", TextColor.ANSI.WHITE, null);
        screen.refresh();
        String amountStr = ts.readInput(24, 8, 15, false);
        if (amountStr == null || amountStr.isBlank()) return;

        ts.drawText(7, 10, "Описание (необязательно): ", TextColor.ANSI.WHITE, null);
        screen.refresh();
        String desc = ts.readInput(34, 10, 40, false);

        try {
            Account from = accountService.findByAccountNumber(fromNum.trim().toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("Счёт отправителя не найден"));
            Account to = accountService.findByAccountNumber(toNum.trim().toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("Счёт получателя не найден"));
            if (from.getUserId() != currentUser.getId()) {
                throw new IllegalArgumentException("Счёт отправителя не принадлежит вам");
            }
            BigDecimal amount = new BigDecimal(amountStr.trim());
            transactionService.transfer(from.getId(), to.getId(), amount,
                    desc != null && !desc.isBlank() ? desc : null);
            showMessage(String.format("✓ Перевод %.2f выполнен: %s → %s",
                    amount.doubleValue(), from.getAccountNumber(), to.getAccountNumber()),
                    TextColor.ANSI.GREEN);
        } catch (Exception e) {
            showMessage("✗ Ошибка: " + e.getMessage(), TextColor.ANSI.RED);
            logger.error("Ошибка перевода через TUI", e);
        }
    }

    /**
     * Отображает историю транзакций для счетов пользователя.
     *
     * @throws IOException при ошибке ввода
     */
    private void showTransactionHistory() throws IOException {
        List<Account> accounts = accountService.findByUserId(currentUser.getId());
        if (accounts.isEmpty()) {
            showMessage("У вас нет счетов.", TextColor.ANSI.YELLOW);
            return;
        }

        TuiScreen ts = new TuiScreen(screen);
        ts.drawHeader("📜  ИСТОРИЯ ТРАНЗАКЦИЙ");

        int row = 3;
        for (Account acc : accounts) {
            ts.drawText(3, row++, "Счёт: " + acc.getAccountNumber() + " (" + acc.getCurrency() + ")",
                    TextColor.ANSI.CYAN, SGR.BOLD);
            ts.drawText(3, row++, String.format("  %-20s %-12s %-12s %s",
                    "Дата", "Тип", "Сумма", "Описание"), TextColor.ANSI.YELLOW, null);

            List<Transaction> history = transactionService.getHistory(acc.getId(), 5);
            if (history.isEmpty()) {
                ts.drawText(5, row++, "  Нет транзакций", TextColor.ANSI.WHITE, null);
            } else {
                for (Transaction tx : history) {
                    String date = tx.getCreatedAt() != null ? tx.getCreatedAt().substring(0, 16) : "—";
                    TextColor color = tx.getType() == Transaction.TransactionType.DEPOSIT
                            ? TextColor.ANSI.GREEN : TextColor.ANSI.RED;
                    ts.drawText(3, row++, String.format("  %-20s %-12s %-12.2f %s",
                            date, tx.getType().name(), tx.getAmount().doubleValue(),
                            tx.getDescription() != null ? tx.getDescription() : ""), color, null);
                }
            }
            row++;
            if (row > 28) break; // Не выходим за пределы экрана
        }

        ts.drawText(3, 31, "[Enter или Esc] Вернуться", TextColor.ANSI.YELLOW, null);
        screen.refresh();
        waitForEnter();
    }

    /**
     * Показывает профиль текущего пользователя.
     *
     * @throws IOException при ошибке ввода
     */
    private void showUserProfile() throws IOException {
        TuiScreen ts = new TuiScreen(screen);
        ts.drawHeader("👤  МОЙ ПРОФИЛЬ");
        ts.drawBox(5, 2, 80, 12);

        ts.drawText(7, 4, "Имя пользователя: " + currentUser.getUsername(), TextColor.ANSI.WHITE, null);
        ts.drawText(7, 5, "Полное имя:       " + currentUser.getFullName(), TextColor.ANSI.WHITE, null);
        ts.drawText(7, 6, "Email:            " + currentUser.getEmail(), TextColor.ANSI.WHITE, null);
        ts.drawText(7, 7, "Роль:             " + (currentUser.isAdmin() ? "Администратор" : "Пользователь"),
                TextColor.ANSI.CYAN, null);
        ts.drawText(7, 8, "Дата регистрации: " + currentUser.getCreatedAt(), TextColor.ANSI.WHITE, null);
        ts.drawText(7, 10, "Количество счетов: " +
                accountService.findByUserId(currentUser.getId()).size(), TextColor.ANSI.WHITE, null);

        ts.drawText(7, 12, "[Enter или Esc] Вернуться", TextColor.ANSI.YELLOW, null);
        screen.refresh();
        waitForEnter();
    }

    /**
     * Показывает меню администратора.
     *
     * @throws IOException при ошибке ввода
     */
    private void showAdminMenu() throws IOException {
        String[] menuItems = {
            "👥  Управление пользователями",
            "💳  Управление счетами",
            "📊  Все транзакции системы",
            "➕  Создать нового пользователя",
            "💳  Создать счёт для пользователя",
            "📋  Статистика системы",
            "👤  Личный кабинет",
            "🚪  Выйти из аккаунта"
        };

        int selected = 0;
        while (true) {
            drawAdminMenuScreen(menuItems, selected);
            com.googlecode.lanterna.input.KeyStroke key = screen.readInput();
            if (key == null) continue;

            switch (key.getKeyType()) {
                case ArrowUp -> selected = (selected - 1 + menuItems.length) % menuItems.length;
                case ArrowDown -> selected = (selected + 1) % menuItems.length;
                case Enter -> {
                    boolean exit = handleAdminMenuChoice(selected);
                    if (exit) return;
                }
                case Escape -> { currentUser = null; return; }
                default -> {}
            }
        }
    }

    /**
     * Отрисовывает меню администратора.
     */
    private void drawAdminMenuScreen(String[] items, int selected) throws IOException {
        TuiScreen ts = new TuiScreen(screen);
        TerminalSize size = screen.getTerminalSize();
        ts.drawHeader("🏦  БАНКОВСКОЕ ПРИЛОЖЕНИЕ  —  ПАНЕЛЬ АДМИНИСТРАТОРА  ⚙️");
        ts.drawBox(3, 2, size.getColumns() - 6, items.length * 2 + 4);
        ts.drawText(5, 3, "Панель администратора: " + currentUser.getUsername(),
                TextColor.ANSI.MAGENTA, SGR.BOLD);

        for (int i = 0; i < items.length; i++) {
            int row = 5 + i * 2;
            if (i == selected) {
                ts.drawText(7, row, " ► " + items[i] + " ", TextColor.ANSI.BLACK, TextColor.ANSI.MAGENTA, SGR.BOLD);
            } else {
                ts.drawText(7, row, "   " + items[i], TextColor.ANSI.WHITE, null);
            }
        }

        ts.drawText(5, items.length * 2 + 7, "↑↓ навигация   Enter — выбрать   Esc — выход",
                TextColor.ANSI.YELLOW, null);
        screen.refresh();
    }

    /**
     * Обрабатывает выбор пункта меню администратора.
     *
     * @param choice индекс выбранного пункта
     * @return true если нужно вернуться к экрану входа
     * @throws IOException при ошибке ввода
     */
    private boolean handleAdminMenuChoice(int choice) throws IOException {
        switch (choice) {
            case 0 -> showUserManagement();
            case 1 -> showAccountManagement();
            case 2 -> showAllTransactions();
            case 3 -> showCreateUserScreen();
            case 4 -> showCreateAccountScreen();
            case 5 -> showSystemStats();
            case 6 -> showUserProfile();
            case 7 -> { currentUser = null; return true; }
        }
        return false;
    }

    /**
     * Показывает список всех пользователей с возможностью управления.
     *
     * @throws IOException при ошибке ввода
     */
    private void showUserManagement() throws IOException {
        List<User> users = userService.findAll();
        TuiScreen ts = new TuiScreen(screen);
        ts.drawHeader("👥  УПРАВЛЕНИЕ ПОЛЬЗОВАТЕЛЯМИ");

        ts.drawText(3, 2, String.format("%-5s %-15s %-25s %-20s %-10s %-10s",
                "ID", "Логин", "Полное имя", "Email", "Роль", "Статус"),
                TextColor.ANSI.CYAN, SGR.BOLD);
        ts.drawText(3, 3, "─".repeat(95), TextColor.ANSI.CYAN, null);

        int row = 4;
        for (User u : users) {
            String role = u.isAdmin() ? "Админ" : "Польз.";
            String status = u.isBlocked() ? "Заблокирован" : "Активен";
            TextColor color = u.isBlocked() ? TextColor.ANSI.RED : TextColor.ANSI.WHITE;
            ts.drawText(3, row++, String.format("%-5d %-15s %-25s %-20s %-10s %-10s",
                    u.getId(), u.getUsername(),
                    truncate(u.getFullName(), 24),
                    truncate(u.getEmail() != null ? u.getEmail() : "", 19),
                    role, status), color, null);
            if (row > 25) { ts.drawText(3, row++, "... и другие", TextColor.ANSI.YELLOW, null); break; }
        }

        row = Math.max(row + 1, 27);
        ts.drawText(3, row, "Действия: [B] Заблокировать/разблокировать  [D] Удалить  [Enter/Esc] Назад",
                TextColor.ANSI.YELLOW, null);
        screen.refresh();

        com.googlecode.lanterna.input.KeyStroke key = screen.readInput();
        if (key == null) return;
        switch (key.getCharacter() != null ? key.getCharacter().toString().toUpperCase() : "") {
            case "B" -> {
                String idStr = promptInput("Введите ID пользователя для блокировки/разблокировки: ");
                if (idStr == null) return;
                try {
                    int uid = Integer.parseInt(idStr.trim());
                    User u = userService.findById(uid).orElseThrow();
                    userService.setBlocked(uid, !u.isBlocked());
                    showMessage("✓ Статус пользователя изменён", TextColor.ANSI.GREEN);
                } catch (Exception e) { showMessage("✗ " + e.getMessage(), TextColor.ANSI.RED); }
            }
            case "D" -> {
                String idStr = promptInput("Введите ID пользователя для удаления: ");
                if (idStr == null) return;
                try {
                    int uid = Integer.parseInt(idStr.trim());
                    if (uid == currentUser.getId()) throw new IllegalArgumentException("Нельзя удалить себя");
                    userService.delete(uid);
                    showMessage("✓ Пользователь удалён", TextColor.ANSI.GREEN);
                } catch (Exception e) { showMessage("✗ " + e.getMessage(), TextColor.ANSI.RED); }
            }
        }
    }

    /**
     * Показывает список всех счетов для управления администратором.
     *
     * @throws IOException при ошибке ввода
     */
    private void showAccountManagement() throws IOException {
        List<Account> accounts = accountService.findAll();
        TuiScreen ts = new TuiScreen(screen);
        ts.drawHeader("💳  УПРАВЛЕНИЕ СЧЕТАМИ");

        ts.drawText(3, 2, String.format("%-5s %-8s %-15s %-12s %-10s %-8s %-10s",
                "ID", "UserID", "Номер счёта", "Тип", "Баланс", "Валюта", "Статус"),
                TextColor.ANSI.CYAN, SGR.BOLD);
        ts.drawText(3, 3, "─".repeat(80), TextColor.ANSI.CYAN, null);

        int row = 4;
        for (Account acc : accounts) {
            String status = acc.isActive() ? "Активен" : "Заморожен";
            TextColor color = acc.isActive() ? TextColor.ANSI.WHITE : TextColor.ANSI.RED;
            ts.drawText(3, row++, String.format("%-5d %-8d %-15s %-12s %-10.2f %-8s %-10s",
                    acc.getId(), acc.getUserId(), acc.getAccountNumber(),
                    acc.getAccountType().name(), acc.getBalance().doubleValue(),
                    acc.getCurrency(), status), color, null);
            if (row > 25) { ts.drawText(3, row++, "... и другие", TextColor.ANSI.YELLOW, null); break; }
        }

        row = Math.max(row + 1, 27);
        ts.drawText(3, row, "Действия: [F] Заморозить/разморозить  [D] Удалить  [Enter/Esc] Назад",
                TextColor.ANSI.YELLOW, null);
        screen.refresh();

        com.googlecode.lanterna.input.KeyStroke key = screen.readInput();
        if (key == null) return;
        switch (key.getCharacter() != null ? key.getCharacter().toString().toUpperCase() : "") {
            case "F" -> {
                String idStr = promptInput("Введите ID счёта: ");
                if (idStr == null) return;
                try {
                    int aid = Integer.parseInt(idStr.trim());
                    Account acc = accountService.findById(aid).orElseThrow();
                    accountService.setActive(aid, !acc.isActive());
                    showMessage("✓ Статус счёта изменён", TextColor.ANSI.GREEN);
                } catch (Exception e) { showMessage("✗ " + e.getMessage(), TextColor.ANSI.RED); }
            }
            case "D" -> {
                String idStr = promptInput("Введите ID счёта для удаления: ");
                if (idStr == null) return;
                try {
                    accountService.delete(Integer.parseInt(idStr.trim()));
                    showMessage("✓ Счёт удалён", TextColor.ANSI.GREEN);
                } catch (Exception e) { showMessage("✗ " + e.getMessage(), TextColor.ANSI.RED); }
            }
        }
    }

    /**
     * Отображает последние транзакции всей системы.
     *
     * @throws IOException при ошибке ввода
     */
    private void showAllTransactions() throws IOException {
        List<Transaction> txs = transactionService.getAllTransactions(30);
        TuiScreen ts = new TuiScreen(screen);
        ts.drawHeader("📊  ТРАНЗАКЦИИ СИСТЕМЫ (последние 30)");

        ts.drawText(3, 2, String.format("%-5s %-8s %-12s %-10s %-18s %s",
                "ID", "Счёт", "Тип", "Сумма", "Дата", "Описание"),
                TextColor.ANSI.CYAN, SGR.BOLD);
        ts.drawText(3, 3, "─".repeat(90), TextColor.ANSI.CYAN, null);

        int row = 4;
        for (Transaction tx : txs) {
            String date = tx.getCreatedAt() != null ? tx.getCreatedAt().substring(0, 16) : "—";
            TextColor color = tx.getType() == Transaction.TransactionType.DEPOSIT
                    ? TextColor.ANSI.GREEN : TextColor.ANSI.RED;
            ts.drawText(3, row++, String.format("%-5d %-8d %-12s %-10.2f %-18s %s",
                    tx.getId(), tx.getAccountId(), tx.getType().name(),
                    tx.getAmount().doubleValue(), date,
                    truncate(tx.getDescription() != null ? tx.getDescription() : "", 25)),
                    color, null);
            if (row > 28) break;
        }

        ts.drawText(3, 31, "[Enter или Esc] Вернуться", TextColor.ANSI.YELLOW, null);
        screen.refresh();
        waitForEnter();
    }

    /**
     * Экран создания нового пользователя (администратором).
     *
     * @throws IOException при ошибке ввода
     */
    private void showCreateUserScreen() throws IOException {
        TuiScreen ts = new TuiScreen(screen);
        ts.drawHeader("➕  СОЗДАНИЕ ПОЛЬЗОВАТЕЛЯ");
        ts.drawBox(5, 2, 90, 16);

        ts.drawText(7, 4, "Логин:        ", TextColor.ANSI.WHITE, null);
        screen.refresh();
        String username = ts.readInput(22, 4, 30, false);
        if (username == null || username.isBlank()) return;

        ts.drawText(7, 6, "Пароль:       ", TextColor.ANSI.WHITE, null);
        screen.refresh();
        String password = ts.readInput(22, 6, 30, true);
        if (password == null || password.isBlank()) return;

        ts.drawText(7, 8, "Полное имя:   ", TextColor.ANSI.WHITE, null);
        screen.refresh();
        String fullName = ts.readInput(22, 8, 40, false);
        if (fullName == null || fullName.isBlank()) return;

        ts.drawText(7, 10, "Email:        ", TextColor.ANSI.WHITE, null);
        screen.refresh();
        String email = ts.readInput(22, 10, 40, false);

        ts.drawText(7, 12, "Администратор? [y/n]: ", TextColor.ANSI.WHITE, null);
        screen.refresh();
        String isAdminStr = ts.readInput(30, 12, 5, false);
        boolean isAdmin = "y".equalsIgnoreCase(isAdminStr != null ? isAdminStr.trim() : "");

        try {
            User newUser = userService.register(username, password, fullName, email);
            if (isAdmin) userService.setAdmin(newUser.getId(), true);
            showMessage("✓ Пользователь '" + newUser.getUsername() + "' создан (ID=" + newUser.getId() + ")",
                    TextColor.ANSI.GREEN);
            logger.info("Администратор {} создал пользователя {}", currentUser.getUsername(), newUser.getUsername());
        } catch (Exception e) {
            showMessage("✗ Ошибка: " + e.getMessage(), TextColor.ANSI.RED);
            logger.error("Ошибка создания пользователя администратором", e);
        }
    }

    /**
     * Экран создания счёта для пользователя (администратором).
     *
     * @throws IOException при ошибке ввода
     */
    private void showCreateAccountScreen() throws IOException {
        TuiScreen ts = new TuiScreen(screen);
        ts.drawHeader("💳  СОЗДАНИЕ СЧЁТА");
        ts.drawBox(5, 2, 80, 12);

        ts.drawText(7, 4, "ID пользователя: ", TextColor.ANSI.WHITE, null);
        screen.refresh();
        String userIdStr = ts.readInput(25, 4, 10, false);
        if (userIdStr == null || userIdStr.isBlank()) return;

        ts.drawText(7, 6, "Валюта [RUB/USD/EUR]: ", TextColor.ANSI.WHITE, null);
        screen.refresh();
        String currency = ts.readInput(30, 6, 5, false);

        ts.drawText(7, 8, "Тип [CHECKING/SAVINGS]: ", TextColor.ANSI.WHITE, null);
        screen.refresh();
        String typeStr = ts.readInput(32, 8, 12, false);

        try {
            int userId = Integer.parseInt(userIdStr.trim());
            userService.findById(userId).orElseThrow(() ->
                    new IllegalArgumentException("Пользователь не найден"));
            String cur = (currency != null && !currency.isBlank()) ? currency.trim().toUpperCase() : "RUB";
            Account.AccountType accType;
            try {
                accType = Account.AccountType.valueOf(typeStr != null ? typeStr.trim().toUpperCase() : "CHECKING");
            } catch (IllegalArgumentException e) {
                accType = Account.AccountType.CHECKING;
            }
            Account acc = accountService.createAccount(userId, cur, accType);
            showMessage("✓ Счёт создан: " + acc.getAccountNumber(), TextColor.ANSI.GREEN);
        } catch (Exception e) {
            showMessage("✗ Ошибка: " + e.getMessage(), TextColor.ANSI.RED);
            logger.error("Ошибка создания счёта администратором", e);
        }
    }

    /**
     * Показывает статистику системы.
     *
     * @throws IOException при ошибке ввода
     */
    private void showSystemStats() throws IOException {
        List<User> users = userService.findAll();
        List<Account> accounts = accountService.findAll();
        List<Transaction> txs = transactionService.getAllTransactions(0);

        TuiScreen ts = new TuiScreen(screen);
        ts.drawHeader("📋  СТАТИСТИКА СИСТЕМЫ");
        ts.drawBox(5, 2, 70, 16);

        ts.drawText(7, 4, "Пользователей всего:     " + users.size(), TextColor.ANSI.WHITE, null);
        ts.drawText(7, 5, "Администраторов:          " + users.stream().filter(User::isAdmin).count(), TextColor.ANSI.WHITE, null);
        ts.drawText(7, 6, "Заблокированных:          " + users.stream().filter(User::isBlocked).count(), TextColor.ANSI.RED, null);
        ts.drawText(7, 8, "Счетов всего:             " + accounts.size(), TextColor.ANSI.WHITE, null);
        ts.drawText(7, 9, "Активных счетов:          " + accounts.stream().filter(Account::isActive).count(), TextColor.ANSI.GREEN, null);
        ts.drawText(7, 10, "Замороженных счетов:      " + accounts.stream().filter(a -> !a.isActive()).count(), TextColor.ANSI.RED, null);
        ts.drawText(7, 12, "Транзакций всего:         " + txs.size(), TextColor.ANSI.WHITE, null);

        double totalBalance = accounts.stream().mapToDouble(a -> a.getBalance().doubleValue()).sum();
        ts.drawText(7, 13, String.format("Суммарный баланс:         %.2f", totalBalance), TextColor.ANSI.CYAN, SGR.BOLD);

        ts.drawText(7, 15, "[Enter или Esc] Вернуться", TextColor.ANSI.YELLOW, null);
        screen.refresh();
        waitForEnter();
    }

    // ─── Вспомогательные методы ─────────────────────────────────────────────

    /**
     * Показывает временное сообщение внизу экрана и ждёт нажатия Enter.
     *
     * @param message сообщение для отображения
     * @param color   цвет текста
     * @throws IOException при ошибке ввода
     */
    private void showMessage(String message, TextColor color) throws IOException {
        TuiScreen ts = new TuiScreen(screen);
        ts.drawText(3, 30, " ".repeat(100), TextColor.ANSI.WHITE, null);
        ts.drawText(3, 30, message + "   [Enter]", color, null);
        screen.refresh();
        waitForEnter();
    }

    /**
     * Показывает строку ввода с подсказкой и возвращает введённую строку.
     *
     * @param prompt подсказка для ввода
     * @return введённая строка или null если нажат Esc
     * @throws IOException при ошибке ввода
     */
    private String promptInput(String prompt) throws IOException {
        TuiScreen ts = new TuiScreen(screen);
        ts.drawText(3, 32, prompt, TextColor.ANSI.YELLOW, null);
        screen.refresh();
        return ts.readInput(3 + prompt.length(), 32, 30, false);
    }

    /**
     * Ожидает нажатия Enter или Esc от пользователя.
     *
     * @throws IOException при ошибке ввода
     */
    private void waitForEnter() throws IOException {
        while (true) {
            com.googlecode.lanterna.input.KeyStroke key = screen.readInput();
            if (key == null) continue;
            if (key.getKeyType() == com.googlecode.lanterna.input.KeyType.Enter ||
                key.getKeyType() == com.googlecode.lanterna.input.KeyType.Escape) break;
        }
    }

    /**
     * Обрезает строку до указанной длины, добавляя "..." если нужно.
     *
     * @param s     исходная строка
     * @param maxLen максимальная длина
     * @return обрезанная строка
     */
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}
