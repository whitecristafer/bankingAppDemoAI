package ru.whitecristafer.banking.tui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;

import java.io.IOException;

/**
 * Вспомогательный класс для отрисовки элементов TUI-экрана.
 *
 * Предоставляет методы для:
 * - рисования заголовка с цветным фоном
 * - рисования рамок (box)
 * - отображения цветного текста
 * - ввода строк с маскировкой (для паролей)
 * - очистки экрана
 *
 * Использует Lanterna Screen API для управления терминальным экраном.
 * Все координаты указываются как (column, row), где (0,0) — верхний левый угол.
 *
 * @author whitecristafer
 */
public class TuiScreen {

    /** Основной экран Lanterna */
    private final Screen screen;

    /** Объект для рисования текста на экране */
    private final TextGraphics graphics;

    /**
     * Конструктор — привязывается к переданному Screen.
     * Выполняет очистку экрана при создании.
     *
     * @param screen активный Lanterna Screen
     */
    public TuiScreen(Screen screen) {
        this.screen = screen;
        this.graphics = screen.newTextGraphics();
        // Очищаем экран перед каждым новым отрисовыванием
        screen.clear();
    }

    /**
     * Рисует заголовок приложения на верхней строке экрана.
     * Заголовок занимает всю ширину терминала с синим фоном и жёлтым текстом.
     *
     * @param title текст заголовка
     */
    public void drawHeader(String title) {
        TerminalSize size = screen.getTerminalSize();
        int width = size.getColumns();

        // Заполняем фон заголовка
        graphics.setForegroundColor(TextColor.ANSI.YELLOW);
        graphics.setBackgroundColor(TextColor.ANSI.BLUE);
        String padding = " ".repeat(width);
        graphics.putString(0, 0, padding);

        // Центрируем текст заголовка
        int textStart = Math.max(0, (width - title.length()) / 2);
        graphics.enableModifiers(SGR.BOLD);
        graphics.putString(textStart, 0, title.substring(0, Math.min(title.length(), width)));
        graphics.disableModifiers(SGR.BOLD);
        graphics.setForegroundColor(TextColor.ANSI.DEFAULT);
        graphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
    }

    /**
     * Рисует прямоугольную рамку (box) из символов псевдографики.
     *
     * @param col    колонка левого верхнего угла
     * @param row    строка левого верхнего угла
     * @param width  ширина рамки (включая бордер)
     * @param height высота рамки (включая бордер)
     */
    public void drawBox(int col, int row, int width, int height) {
        graphics.setForegroundColor(TextColor.ANSI.CYAN);

        // Верхняя граница
        graphics.putString(col, row, "╔" + "═".repeat(width - 2) + "╗");
        // Нижняя граница
        graphics.putString(col, row + height - 1, "╚" + "═".repeat(width - 2) + "╝");
        // Боковые линии
        for (int r = row + 1; r < row + height - 1; r++) {
            graphics.putString(col, r, "║");
            graphics.putString(col + width - 1, r, "║");
        }
        graphics.setForegroundColor(TextColor.ANSI.DEFAULT);
    }

    /**
     * Выводит цветной текст в указанной позиции без фонового цвета.
     *
     * @param col  колонка начала текста
     * @param row  строка текста
     * @param text выводимый текст
     * @param fg   цвет текста (TextColor.ANSI.*)
     * @param sgr  атрибут текста (SGR.BOLD, SGR.ITALIC и т.д.) или null
     */
    public void drawText(int col, int row, String text, TextColor fg, SGR sgr) {
        TerminalSize size = screen.getTerminalSize();
        // Не выходим за пределы терминала
        if (row >= size.getRows() || col >= size.getColumns()) return;
        int maxLen = size.getColumns() - col;
        String safeText = text.length() > maxLen ? text.substring(0, maxLen) : text;

        graphics.setForegroundColor(fg != null ? fg : TextColor.ANSI.DEFAULT);
        graphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
        if (sgr != null) graphics.enableModifiers(sgr);
        graphics.putString(col, row, safeText);
        if (sgr != null) graphics.disableModifiers(sgr);
        graphics.setForegroundColor(TextColor.ANSI.DEFAULT);
    }

    /**
     * Выводит цветной текст с фоновым цветом в указанной позиции.
     *
     * @param col  колонка начала текста
     * @param row  строка текста
     * @param text выводимый текст
     * @param fg   цвет текста (TextColor.ANSI.*)
     * @param bg   фоновый цвет (TextColor.ANSI.*) или null
     * @param sgr  атрибут текста или null
     */
    public void drawText(int col, int row, String text, TextColor fg, TextColor bg, SGR sgr) {
        TerminalSize size = screen.getTerminalSize();
        if (row >= size.getRows() || col >= size.getColumns()) return;
        int maxLen = size.getColumns() - col;
        String safeText = text.length() > maxLen ? text.substring(0, maxLen) : text;

        graphics.setForegroundColor(fg != null ? fg : TextColor.ANSI.DEFAULT);
        graphics.setBackgroundColor(bg != null ? bg : TextColor.ANSI.DEFAULT);
        if (sgr != null) graphics.enableModifiers(sgr);
        graphics.putString(col, row, safeText);
        if (sgr != null) graphics.disableModifiers(sgr);
        graphics.setForegroundColor(TextColor.ANSI.DEFAULT);
        graphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
    }

    /**
     * Читает строку ввода от пользователя в интерактивном режиме.
     * Поддерживает BackSpace для удаления символов, Enter для подтверждения,
     * Esc для отмены.
     *
     * @param col      колонка начала поля ввода
     * @param row      строка поля ввода
     * @param maxLen   максимальная длина вводимой строки
     * @param password если true — вместо символов отображаются '*' (маскировка пароля)
     * @return введённая строка или null если нажат Esc
     * @throws IOException при ошибке ввода
     */
    public String readInput(int col, int row, int maxLen, boolean password) throws IOException {
        StringBuilder input = new StringBuilder();
        screen.setCursorPosition(new TerminalPosition(col, row));

        while (true) {
            // Отображаем текущий ввод
            String display = password ? "*".repeat(input.length()) : input.toString();
            String field = display + " ".repeat(Math.max(0, maxLen - display.length()));
            graphics.setForegroundColor(TextColor.ANSI.WHITE);
            graphics.setBackgroundColor(TextColor.ANSI.BLUE);
            graphics.putString(col, row, field.substring(0, Math.min(field.length(), maxLen)));
            graphics.setBackgroundColor(TextColor.ANSI.DEFAULT);
            screen.setCursorPosition(new TerminalPosition(col + display.length(), row));
            screen.refresh();

            KeyStroke key = screen.readInput();
            if (key == null) continue;

            if (key.getKeyType() == KeyType.Escape) {
                screen.setCursorPosition(null);
                return null;
            } else if (key.getKeyType() == KeyType.Enter) {
                screen.setCursorPosition(null);
                return input.toString();
            } else if (key.getKeyType() == KeyType.Backspace) {
                if (input.length() > 0) input.deleteCharAt(input.length() - 1);
            } else if (key.getKeyType() == KeyType.Character && key.getCharacter() != null) {
                if (input.length() < maxLen) {
                    input.append(key.getCharacter());
                }
            }
        }
    }
}
