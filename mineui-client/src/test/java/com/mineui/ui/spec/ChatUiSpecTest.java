package com.mineui.ui.spec;

import com.google.gson.JsonParser;
import com.mineui.ui.tree.ButtonNode;
import com.mineui.ui.tree.ColumnNode;
import com.mineui.ui.tree.InputNode;
import com.mineui.ui.tree.ListViewNode;
import com.mineui.ui.tree.RowNode;
import com.mineui.ui.tree.StateAccess;
import com.mineui.ui.tree.TextNode;
import com.mineui.ui.tree.UiNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatUiSpecTest {

    private static UiNode parse(String json) throws Exception {
        return UiSpecParser.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void parsesChatPageFeatures() throws Exception {
        ColumnNode root = assertInstanceOf(ColumnNode.class, parse("""
                {"type":"column","gap":4,"children":[
                  {"type":"text","text":"long chat message","wrap":true,"maxLines":2,
                   "lineHeight":11,"width":"100%"},
                  {"type":"row","gap":5,"children":[
                    {"type":"box","width":30,"height":20},
                    {"type":"column","grow":1,"children":[
                      {"type":"text","text":"{state.title}"}
                    ]}
                  ]},
                  {"type":"list","width":"100%","height":160,"items":"{state.messages}",
                   "stickToBottom":true,
                   "itemTemplate":{"type":"text","text":"{item.text}","width":"100%"}},
                  {"type":"input","height":20,"placeholder":"say","action":"send",
                   "enabled":"{state.running}"},
                  {"type":"button","text":"Send","action":"send","submit":true}
                ]}
                """));

        assertEquals(5, root.children().size());

        TextNode message = assertInstanceOf(TextNode.class, root.children().get(0));
        assertTrue(message.wrap());
        assertEquals(2, message.maxLines());
        assertEquals(11f, message.lineHeight(), 0.001f);

        RowNode row = assertInstanceOf(RowNode.class, root.children().get(1));
        assertEquals(2, row.children().size());
        assertEquals(0f, row.children().get(0).style().grow(), 0.001f);
        assertEquals(1f, row.children().get(1).style().grow(), 0.001f);

        ListViewNode list = assertInstanceOf(ListViewNode.class, root.children().get(2));
        assertTrue(list.stickToBottom());

        InputNode input = assertInstanceOf(InputNode.class, root.children().get(3));
        StateAccess running = (path, fallback) -> path.equals("running") ? "true" : fallback;
        StateAccess idle = (path, fallback) -> path.equals("running") ? "false" : fallback;
        assertTrue(input.enabledNow(running));
        assertFalse(input.enabledNow(idle));

        ButtonNode button = assertInstanceOf(ButtonNode.class, root.children().get(4));
        assertTrue(button.submitInput());
        assertEquals("send", button.action());
    }

    @Test
    void acceptsFollowLatestAndFlexGrowAliases() throws Exception {
        RowNode row = assertInstanceOf(RowNode.class, parse("""
                {"type":"row","children":[
                  {"type":"box","height":10,"flexGrow":2},
                  {"type":"list","height":40,"items":"{state.rows}","followLatest":true,
                   "itemTemplate":{"type":"text","text":"{item}"}}
                ]}
                """));

        assertEquals(2f, row.children().get(0).style().grow(), 0.001f);
        assertTrue(assertInstanceOf(ListViewNode.class, row.children().get(1)).stickToBottom());
    }


    @Test
    void submitTargetAndDisabledBinding() throws Exception {
        ColumnNode root = assertInstanceOf(ColumnNode.class, parse("""
                {"type":"column","children":[
                  {"type":"input","id":"chat_input","placeholder":"say","action":"send",
                   "disabled":"{state.running}"},
                  {"type":"button","text":"Send","action":"send","submit":"chat_input"}
                ]}
                """));

        InputNode input = assertInstanceOf(InputNode.class, root.children().get(0));
        StateAccess running = (path, fallback) -> path.equals("running") ? "true" : fallback;
        StateAccess idle = (path, fallback) -> path.equals("running") ? "false" : fallback;
        assertFalse(input.enabledNow(running), "disabled 绑定应取反");
        assertTrue(input.enabledNow(idle));

        ButtonNode button = assertInstanceOf(ButtonNode.class, root.children().get(1));
        assertTrue(button.submitInput());
        assertEquals("chat_input", button.submitTarget());
    }
}
