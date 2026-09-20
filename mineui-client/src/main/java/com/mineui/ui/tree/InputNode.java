package com.mineui.ui.tree;

/**
 * 文本输入控件（客户端本地编辑）。
 * <p>
 * 点击聚焦；回车提交 {@code action}，负载约定 {@code {"text": "<内容>"}}；Esc 失焦。
 * 编辑状态属于客户端运行时，随每次打开界面重建。
 */
public final class InputNode extends UiNode {

    private final String placeholder;
    private final int maxLength;
    private final String action;
    private final int textColor;
    private final int placeholderColor;

    private final StringBuilder text = new StringBuilder();
    private boolean focused;
    private int cursor;
    /** IME 组词中文本（仅展示，不计入 text()；合成提交后由 charTyped 写入并清空）。 */
    private String preedit = "";

    public InputNode(NodeStyle style, String placeholder, int maxLength, String action,
                     int textColor, int placeholderColor) {
        super(style);
        this.placeholder = placeholder == null ? "" : placeholder;
        this.maxLength = Math.max(1, maxLength);
        this.action = action == null ? "" : action;
        this.textColor = textColor;
        this.placeholderColor = placeholderColor;
    }

    public String placeholder() {
        return placeholder;
    }

    public int maxLength() {
        return maxLength;
    }

    public int textColor() {
        return textColor;
    }

    public int placeholderColor() {
        return placeholderColor;
    }

    public String text() {
        return text.toString();
    }

    public boolean focused() {
        return focused;
    }

    public int cursor() {
        return cursor;
    }

    public void focus() {
        focused = true;
        cursor = text.length();
    }

    public void blur() {
        focused = false;
    }

    public String preedit() {
        return preedit;
    }

    public void setPreedit(String value) {
        preedit = value == null ? "" : value;
    }

    public void clearPreedit() {
        preedit = "";
    }

    /** 清空内容并把光标移到开头（提交后调用，保留聚焦状态方便连续输入）。 */
    public void clear() {
        text.setLength(0);
        cursor = 0;
    }

    /** 在光标处插入（超长截断）。返回是否发生变化。 */
    public boolean insert(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        int room = maxLength - text.length();
        if (room <= 0) {
            return false;
        }
        String added = value.length() > room ? value.substring(0, room) : value;
        text.insert(cursor, added);
        cursor += added.length();
        return true;
    }

    public boolean backspace() {
        if (cursor <= 0) {
            return false;
        }
        text.deleteCharAt(cursor - 1);
        cursor--;
        return true;
    }

    public boolean deleteForward() {
        if (cursor >= text.length()) {
            return false;
        }
        text.deleteCharAt(cursor);
        return true;
    }

    /** 相对移动光标（-1 左 / +1 右）。 */
    public boolean moveCursor(int delta) {
        int next = Math.max(0, Math.min(text.length(), cursor + delta));
        if (next == cursor) {
            return false;
        }
        cursor = next;
        return true;
    }

    public void moveCursorTo(int position) {
        cursor = Math.max(0, Math.min(text.length(), position));
    }

    public String action() {
        return action;
    }

    @Override
    public void measure(MeasureContext context) {
        if (!evaluateVisible(context.state())) {
            width = 0;
            height = 0;
            return;
        }
        float resolvedW = resolveWidth(context);
        float resolvedH = resolveHeight(context);
        width = resolvedW >= 0 ? resolvedW : 160f;
        height = resolvedH >= 0 ? resolvedH : context.text().lineHeight() + 10f;
    }

    @Override
    public UiNode mouseClicked(double mx, double my, int button) {
        return contains(mx, my) ? this : null;
    }
}
