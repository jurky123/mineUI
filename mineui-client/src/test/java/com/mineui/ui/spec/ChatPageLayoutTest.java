package com.mineui.ui.spec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mineui.ui.tree.ListViewNode;
import com.mineui.ui.tree.MeasureContext;
import com.mineui.ui.tree.ScrollViewNode;
import com.mineui.ui.tree.StateAccess;
import com.mineui.ui.tree.TextMeasurer;
import com.mineui.ui.tree.UiNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 内置 chat 演示页在 1280×720 / GUI scale 3（426×240 逻辑像素）下不能横向溢出。 */
class ChatPageLayoutTest {

    @TempDir
    Path tempDir;

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

    @AfterEach
    void cleanup() {
        UiDefinitionLoader.clearCache();
    }

    @Test
    void headerAndComposerStayInsideSmallViewport() throws Exception {
        UiNode root = UiDefinitionLoader.load("mineui", "chat", tempDir).root();
        StateAccess state = state(buildState());

        root.measure(new MeasureContext(426, 240, 426, 240, TEXT, state));
        root.layout(0, 0);

        UiNode header = root.findById("header");
        UiNode composer = root.findById("composer");
        assertNotNull(header, "chat 页应有 header");
        assertNotNull(composer, "chat 页应有 composer");
        assertChildrenInside(header);
        assertChildrenInside(composer);
        // 列表/滚动视口内部允许超出（自身裁剪），其余节点必须落在屏幕内
        assertInside(root, 0, 0, 426, 240);
    }

    private static JsonObject buildState() {
        JsonObject state = new JsonObject();
        state.addProperty("title", "全服对话" + "很长的标题".repeat(20));
        state.addProperty("status", "在线" + "状态文本".repeat(20));
        state.addProperty("running", true);
        String longText = "这是一条很长的消息，用来验证换行与角色卡片不会把界面撑出屏幕。".repeat(12);
        JsonArray messages = new JsonArray();
        messages.add(message("system", "系统", longText, false, false, false, false, true));
        messages.add(message("user", "你", longText, true, false, false, false, false));
        messages.add(message("assistant", "MineAgent", longText, false, true, false, false, false));
        JsonObject tool = message("tool", "工具调用", longText, false, false, true, false, false);
        tool.addProperty("tool", "server_status");
        tool.addProperty("toolStatus", "完成");
        tool.addProperty("duration", "0.6s");
        messages.add(tool);
        JsonObject approval = message("approval", "审批", longText, false, false, false, true, false);
        approval.addProperty("tool", "command_runner");
        approval.addProperty("time", "30s");
        messages.add(approval);
        state.add("messages", messages);
        return state;
    }

    private static JsonObject message(String role, String author, String text,
                                      boolean user, boolean assistant, boolean tool,
                                      boolean approval, boolean system) {
        JsonObject message = new JsonObject();
        message.addProperty("isUser", user);
        message.addProperty("isAssistant", assistant);
        message.addProperty("isTool", tool);
        message.addProperty("isApproval", approval);
        message.addProperty("isSystem", system);
        message.addProperty("author", author);
        message.addProperty("text", text);
        message.addProperty("time", "刚刚");
        return message;
    }

    private static StateAccess state(JsonObject root) {
        return new StateAccess() {
            @Override
            public String get(String path, String defaultValue) {
                JsonElement element = getElement(path);
                return element != null && element.isJsonPrimitive() ? element.getAsString() : defaultValue;
            }

            @Override
            public JsonElement getElement(String path) {
                JsonElement current = root;
                for (String part : path.split("\\.")) {
                    if (current instanceof JsonObject object && object.has(part)) {
                        current = object.get(part);
                    } else {
                        return null;
                    }
                }
                return current;
            }
        };
    }

    private static void assertChildrenInside(UiNode parent) {
        for (UiNode child : parent.children()) {
            if (!child.visibleNow()) {
                continue;
            }
            assertInside(child, parent.x(), parent.y(),
                    parent.x() + parent.width(), parent.y() + parent.height());
        }
    }

    /** 非滚动容器的可见节点必须落在给定矩形内；列表/滚动容器只检查自身，内部由裁剪负责。 */
    private static void assertInside(UiNode node, float left, float top, float right, float bottom) {
        if (!node.visibleNow()) {
            return;
        }
        float tolerance = 0.5f;
        assertTrue(node.x() >= left - tolerance && node.y() >= top - tolerance,
                node.getClass().getSimpleName() + " 左上越界: " + node.x() + "," + node.y());
        assertTrue(node.x() + node.width() <= right + tolerance
                        && node.y() + node.height() <= bottom + tolerance,
                node.getClass().getSimpleName() + " 右下越界: "
                        + (node.x() + node.width()) + "," + (node.y() + node.height()));
        if (node instanceof ListViewNode || node instanceof ScrollViewNode) {
            return;
        }
        for (UiNode child : node.children()) {
            assertInside(child, node.x(), node.y(), node.x() + node.width(), node.y() + node.height());
        }
    }
}
