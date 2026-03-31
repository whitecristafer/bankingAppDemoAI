package ru.whitecristafer.banking.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.whitecristafer.banking.db.DatabaseManager;
import ru.whitecristafer.banking.model.VirtualCard;
import ru.whitecristafer.banking.util.PasswordUtil;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Сервис управления виртуальными банковскими картами.
 * Поддерживает создание, поиск и деактивацию карт.
 * Номер карты генерируется по алгоритму Луна.
 * CVV хранится в хэшированном виде (BCrypt).
 *
 * @author whitecristafer
 */
public class CardService {

    private static final Logger logger = LogManager.getLogger(CardService.class);

    /** Максимальное количество карт на одного пользователя */
    private static final int MAX_CARDS_PER_USER = 10;

    /** Срок действия карты с момента выпуска (месяцев) */
    private static final int CARD_EXPIRY_MONTHS = 36;

    /** Менеджер базы данных */
    private final DatabaseManager dbManager;

    /** Генератор случайных чисел для CVV и номера карты */
    private final Random random = new Random();

    /**
     * Конструктор сервиса карт.
     *
     * @param dbManager менеджер базы данных
     */
    public CardService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Создаёт новую виртуальную карту для пользователя.
     * Автоматически генерирует номер карты (алгоритм Луна), CVV (хэшируется),
     * срок действия (текущий месяц + 36 месяцев).
     * Максимум 10 карт на одного пользователя.
     *
     * @param userId     идентификатор пользователя
     * @param accountId  идентификатор счёта
     * @param holderName имя держателя карты (латиницей)
     * @return созданный объект VirtualCard
     * @throws IllegalStateException если достигнут лимит карт
     */
    public VirtualCard createCard(int userId, int accountId, String holderName) {
        // Проверяем лимит карт
        if (getCardCount(userId) >= MAX_CARDS_PER_USER) {
            throw new IllegalStateException("Достигнут максимальный лимит карт: " + MAX_CARDS_PER_USER);
        }

        // Генерируем данные карты
        String cardNumber = generateLuhnCardNumber();
        String rawCvv = String.format("%03d", random.nextInt(1000));
        String cvvHash = PasswordUtil.hash(rawCvv);

        // Срок действия: текущий месяц + 36 месяцев
        LocalDate expiryDate = LocalDate.now().plusMonths(CARD_EXPIRY_MONTHS);
        int expiryMonth = expiryDate.getMonthValue();
        int expiryYear = expiryDate.getYear();

        String sql = """
            INSERT INTO virtual_cards (user_id, account_id, card_number, card_holder_name,
                expiry_month, expiry_year, cvv_hash, card_type, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'DEBIT', 1)
            """;
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, userId);
            pstmt.setInt(2, accountId);
            pstmt.setString(3, cardNumber);
            pstmt.setString(4, holderName.toUpperCase());
            pstmt.setInt(5, expiryMonth);
            pstmt.setInt(6, expiryYear);
            pstmt.setString(7, cvvHash);
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    VirtualCard card = findById(keys.getInt(1)).orElseThrow();
                    logger.info("Создана карта {} для пользователя ID={}", cardNumber, userId);
                    return card;
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка создания карты для пользователя ID={}", userId, e);
            throw new RuntimeException("Ошибка создания карты: " + e.getMessage(), e);
        }
        throw new RuntimeException("Не удалось создать карту");
    }

    /**
     * Возвращает все карты пользователя.
     *
     * @param userId идентификатор пользователя
     * @return список карт
     */
    public List<VirtualCard> findByUserId(int userId) {
        List<VirtualCard> cards = new ArrayList<>();
        String sql = "SELECT * FROM virtual_cards WHERE user_id = ? ORDER BY created_at DESC";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) cards.add(mapRowToCard(rs));
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения карт пользователя ID={}", userId, e);
        }
        return cards;
    }

    /**
     * Возвращает карты, привязанные к счёту.
     *
     * @param accountId идентификатор счёта
     * @return список карт счёта
     */
    public List<VirtualCard> findByAccountId(int accountId) {
        List<VirtualCard> cards = new ArrayList<>();
        String sql = "SELECT * FROM virtual_cards WHERE account_id = ? ORDER BY created_at DESC";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, accountId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) cards.add(mapRowToCard(rs));
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения карт счёта ID={}", accountId, e);
        }
        return cards;
    }

    /**
     * Деактивирует карту (устанавливает is_active = 0).
     *
     * @param cardId идентификатор карты
     */
    public void deactivateCard(int cardId) {
        String sql = "UPDATE virtual_cards SET is_active = 0 WHERE id = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, cardId);
            pstmt.executeUpdate();
            logger.info("Карта ID={} деактивирована", cardId);
        } catch (SQLException e) {
            logger.error("Ошибка деактивации карты ID={}", cardId, e);
            throw new RuntimeException("Ошибка деактивации карты: " + e.getMessage(), e);
        }
    }

    /**
     * Возвращает общее количество карт пользователя (активных и неактивных).
     *
     * @param userId идентификатор пользователя
     * @return количество карт
     */
    public int getCardCount(int userId) {
        String sql = "SELECT COUNT(*) FROM virtual_cards WHERE user_id = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Ошибка подсчёта карт пользователя ID={}", userId, e);
        }
        return 0;
    }

    /**
     * Ищет карту по идентификатору.
     *
     * @param id идентификатор карты
     * @return Optional с объектом VirtualCard
     */
    public Optional<VirtualCard> findById(int id) {
        String sql = "SELECT * FROM virtual_cards WHERE id = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRowToCard(rs));
            }
        } catch (SQLException e) {
            logger.error("Ошибка поиска карты по ID={}", id, e);
        }
        return Optional.empty();
    }

    /**
     * Генерирует 16-значный номер карты, прошедший алгоритм Луна.
     * Формат: "XXXX XXXX XXXX XXXX"
     *
     * @return строка номера карты с пробелами
     */
    private String generateLuhnCardNumber() {
        String cardNumber;
        do {
            // Генерируем 15 случайных цифр (без ведущего нуля)
            StringBuilder sb = new StringBuilder();
            sb.append((char)('1' + random.nextInt(9))); // первая цифра не 0
            for (int i = 1; i < 15; i++) {
                sb.append(random.nextInt(10));
            }
            // Вычисляем контрольную цифру Луна
            int checkDigit = calculateLuhnCheckDigit(sb.toString());
            sb.append(checkDigit);
            // Форматируем как "XXXX XXXX XXXX XXXX"
            String digits = sb.toString();
            cardNumber = digits.substring(0, 4) + " " + digits.substring(4, 8) + " " +
                         digits.substring(8, 12) + " " + digits.substring(12, 16);
        } while (isCardNumberExists(cardNumber));
        return cardNumber;
    }

    /**
     * Вычисляет контрольную цифру по алгоритму Луна.
     *
     * @param digits первые 15 цифр номера карты
     * @return контрольная цифра (0-9)
     */
    private int calculateLuhnCheckDigit(String digits) {
        int sum = 0;
        boolean alternate = true; // для 15 цифр, начиная с последней: alternate=true
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return (10 - (sum % 10)) % 10;
    }

    /**
     * Проверяет номер карты по алгоритму Луна.
     *
     * @param number номер карты (16 цифр без пробелов)
     * @return true если номер корректен
     */
    private boolean luhnCheck(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = number.charAt(i) - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }

    /**
     * Проверяет, существует ли номер карты в базе данных.
     *
     * @param cardNumber номер карты в формате "XXXX XXXX XXXX XXXX"
     * @return true если номер уже занят
     */
    private boolean isCardNumberExists(String cardNumber) {
        String sql = "SELECT COUNT(*) FROM virtual_cards WHERE card_number = ?";
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, cardNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.error("Ошибка проверки уникальности номера карты", e);
        }
        return false;
    }

    /**
     * Преобразует строку ResultSet в объект VirtualCard.
     *
     * @param rs ResultSet с данными
     * @return объект VirtualCard
     * @throws SQLException при ошибке чтения
     */
    private VirtualCard mapRowToCard(ResultSet rs) throws SQLException {
        VirtualCard card = new VirtualCard();
        card.setId(rs.getInt("id"));
        card.setUserId(rs.getInt("user_id"));
        card.setAccountId(rs.getInt("account_id"));
        card.setCardNumber(rs.getString("card_number"));
        card.setCardHolderName(rs.getString("card_holder_name"));
        card.setExpiryMonth(rs.getInt("expiry_month"));
        card.setExpiryYear(rs.getInt("expiry_year"));
        card.setCvvHash(rs.getString("cvv_hash"));
        card.setCardType(VirtualCard.CardType.valueOf(rs.getString("card_type")));
        card.setActive(rs.getInt("is_active") == 1);
        card.setCreatedAt(rs.getString("created_at"));
        return card;
    }
}
