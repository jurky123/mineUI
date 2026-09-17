package com.mineui.ui.spec;

import com.mineui.ui.tree.ItemViewNode;
import com.mineui.ui.tree.UiNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 内置演示页（随客户端发布）必须始终能被解析。 */
class DemoPagesTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanup() {
        UiDefinitionLoader.clearCache();
    }

    private static List<UiNode> flatten(UiNode node) {
        List<UiNode> result = new ArrayList<>();
        result.add(node);
        for (UiNode child : node.children()) {
            result.addAll(flatten(child));
        }
        return result;
    }

    @Test
    void allBundledPagesParse() throws Exception {
        for (String view : List.of("test", "vanilla", "gallery")) {
            UiDefinition definition = UiDefinitionLoader.load("mineui", view, tempDir);
            assertNotNull(definition.root(), view);
            assertFalse(flatten(definition.root()).isEmpty(), view);
        }
    }

    @Test
    void galleryCoversDecorations() throws Exception {
        List<UiNode> nodes = flatten(UiDefinitionLoader.load("mineui", "gallery", tempDir).root());

        boolean cycle = nodes.stream().anyMatch(n -> n.style().cycle() != null);
        boolean hoverScale = nodes.stream().anyMatch(n -> n.style().hoverScale() != 1f);
        boolean hoverItem = nodes.stream().anyMatch(n -> n instanceof ItemViewNode item && item.hoverItem() != null);
        boolean mobHead = nodes.stream().anyMatch(n -> n instanceof ItemViewNode item && item.item().contains("_head"));

        assertTrue(cycle, "画廊应包含时间轮换示例");
        assertTrue(hoverScale, "画廊应包含悬浮缩放示例");
        assertTrue(hoverItem, "画廊应包含悬浮换图案示例");
        assertTrue(mobHead, "画廊应包含生物头颅装饰");
    }
}
