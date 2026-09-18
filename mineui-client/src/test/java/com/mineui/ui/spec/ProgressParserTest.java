package com.mineui.ui.spec;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineui.ui.tree.ProgressNode;
import com.mineui.ui.tree.SliderNode;
import com.mineui.ui.tree.StateAccess;
import com.mineui.ui.tree.UiNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ProgressParserTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void parsesProgressFields() throws Exception {
        UiNode root = UiSpecParser.parse(json("""
                {
                  "type": "progress", "width": 160, "height": 14,
                  "value": "{state.position}", "min": 0, "max": 180,
                  "playing": "{state.playing}", "interpolate": true,
                  "color": "#3FA9F5", "fillGradient": ["#3FA9F5", "#8A5FD0"],
                  "text": "{value} / {max}", "timeFormat": "mm:ss"
                }
                """));

        ProgressNode progress = assertInstanceOf(ProgressNode.class, root);
        assertEquals(0, progress.min(), 0.001);
        assertEquals(180, progress.max(), 0.001);
        assertEquals(0xFF3FA9F5, progress.fillColor());
        assertEquals(0xFF8A5FD0, progress.fillGradientTo());
        assertEquals(ProgressNode.Direction.LEFT_RIGHT, progress.direction());
        assertEquals("0:00 / 3:00", progress.label((path, fallback) -> "0", 0));
    }

    @Test
    void parsesProgressDirection() throws Exception {
        ProgressNode progress = assertInstanceOf(ProgressNode.class, UiSpecParser.parse(json("""
                { "type": "progress", "direction": "bottom-top", "value": 50 }
                """)));
        assertEquals(ProgressNode.Direction.BOTTOM_TOP, progress.direction());
    }

    @Test
    void rejectsBadFillGradient() {
        try {
            UiSpecParser.parse(json("""
                    { "type": "progress", "fillGradient": ["#FFFFFF"] }
                    """));
            throw new AssertionError("应当抛出 UiSpecException");
        } catch (UiSpecException expected) {
            // ok
        }
    }

    @Test
    void parsesSliderStepAndEnabled() throws Exception {
        SliderNode slider = assertInstanceOf(SliderNode.class, UiSpecParser.parse(json("""
                { "type": "slider", "min": 0, "max": 100, "step": 5, "enabled": false }
                """)));
        assertEquals(5, slider.step(), 0.001);
        slider.measure(new com.mineui.ui.tree.MeasureContext(200, 200, 200, 200,
                new com.mineui.ui.tree.TextMeasurer() {
                    @Override
                    public int width(String text) {
                        return text.length() * 6;
                    }

                    @Override
                    public int lineHeight() {
                        return 9;
                    }
                }, StateAccess.EMPTY));
        assertFalse(slider.enabledNow());
    }
}
