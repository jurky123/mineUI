package com.mineui.ui.tree;

import com.mineui.ui.spec.Insets;
import com.mineui.ui.spec.SizeSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputNodeTest {

    private static final TextMeasurer TEXT = new TextMeasurer() {
        @Override
        public int width(String text) {
            return text.length() * 6;
        }

        @Override
        public int lineHeight() {
            return 9;
        }
    };

    private static InputNode input(int maxLength) {
        return new InputNode(NodeStyle.defaults(), "搜索", maxLength, "search", 0xFFFFFFFF, 0xFF808080);
    }

    private static MeasureContext ctx() {
        return new MeasureContext(200, 200, 200, 200, TEXT, StateAccess.EMPTY);
    }

    @Test
    void defaultSizeAndPlaceholder() {
        InputNode node = input(32);
        node.measure(ctx());
        assertEquals(160, node.width(), 0.01f);
        assertEquals(9 + 10, node.height(), 0.01f);
        assertEquals("搜索", node.placeholder());
        assertEquals("search", node.action());
        assertFalse(node.focused());
    }

    @Test
    void focusPutsCursorAtEnd() {
        InputNode node = input(32);
        node.insert("abc");
        node.moveCursorTo(0);
        node.focus();
        assertTrue(node.focused());
        assertEquals(3, node.cursor());
    }

    @Test
    void insertRespectsMaxLength() {
        InputNode node = input(5);
        assertTrue(node.insert("abcdefg"));
        assertEquals("abcde", node.text());
        assertFalse(node.insert("x"));
    }

    @Test
    void insertsAtCursor() {
        InputNode node = input(32);
        node.insert("ac");
        node.moveCursor(-1);
        node.insert("b");
        assertEquals("abc", node.text());
        assertEquals(2, node.cursor());
    }

    @Test
    void preeditIsDisplayOnly() {
        InputNode node = input(10);
        assertEquals("", node.preedit());
        node.setPreedit("nihao");
        assertEquals("nihao", node.preedit());
        // 组词文本不计入 text()，合成提交后由 charTyped 写入
        assertEquals("", node.text());
        node.clearPreedit();
        assertEquals("", node.preedit());
        node.setPreedit(null);
        assertEquals("", node.preedit());
    }

    @Test
    void backspaceAndDeleteForward() {
        InputNode node = input(32);
        node.insert("abc");
        assertTrue(node.backspace());
        assertEquals("ab", node.text());
        node.moveCursorTo(0);
        assertFalse(node.backspace());
        assertTrue(node.deleteForward());
        assertEquals("b", node.text());
        node.moveCursorTo(1);
        assertFalse(node.deleteForward());
    }

    @Test
    void cursorMovementClamps() {
        InputNode node = input(32);
        node.insert("ab");
        node.moveCursorTo(0);
        assertFalse(node.moveCursor(-1));
        assertTrue(node.moveCursor(1));
        assertEquals(1, node.cursor());
        node.moveCursorTo(99);
        assertEquals(2, node.cursor());
        node.moveCursorTo(-5);
        assertEquals(0, node.cursor());
    }

    @Test
    void blurStopsFocusButKeepsText() {
        InputNode node = input(32);
        node.focus();
        node.insert("hi");
        node.blur();
        assertFalse(node.focused());
        assertEquals("hi", node.text());
    }

    @Test
    void clearResetsTextAndCursorKeepsFocus() {
        InputNode node = input(32);
        node.focus();
        node.insert("hello");
        node.clear();
        assertEquals("", node.text());
        assertEquals(0, node.cursor());
        assertTrue(node.focused());
        assertTrue(node.insert("x"));
        assertEquals("x", node.text());
    }

    @Test
    void mouseClickHitsOnlyInside() {
        InputNode node = input(32);
        node.measure(ctx());
        node.layout(10, 10);
        assertEquals(node, node.mouseClicked(20, 15, 0));
        assertEquals(null, node.mouseClicked(200, 200, 0));
    }
}
