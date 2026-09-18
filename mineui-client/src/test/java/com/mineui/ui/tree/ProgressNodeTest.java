package com.mineui.ui.tree;

import com.google.gson.JsonPrimitive;
import com.mineui.ui.spec.BooleanSpec;
import com.mineui.ui.spec.DoubleSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressNodeTest {

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

    private static ProgressNode node(String text, String timeFormat, boolean interpolate,
                                     double rate, BooleanSpec playing) {
        return new ProgressNode(NodeStyle.defaults(), DoubleSpec.of(0), 0, 180,
                ProgressNode.Direction.LEFT_RIGHT, 0xFF3FA9F5, null, playing, interpolate, rate, 3.0,
                text, timeFormat, 0xFFFFFFFF);
    }

    private static ProgressNode boundNode(boolean interpolate, double rate,
                                          double[] position, boolean[] playing) {
        DoubleSpec spec = DoubleSpec.parse(new JsonPrimitive("{state.pos}"), 0);
        return new ProgressNode(NodeStyle.defaults(), spec, 0, 180,
                ProgressNode.Direction.LEFT_RIGHT, 0xFF3FA9F5, null,
                BooleanSpec.parse(new JsonPrimitive("{state.playing}")), interpolate, rate, 3.0,
                "", null, 0xFFFFFFFF);
    }

    private static StateAccess state(double[] position, boolean[] playing) {
        return (path, fallback) -> switch (path) {
            case "pos" -> Double.toString(position[0]);
            case "playing" -> Boolean.toString(playing[0]);
            default -> fallback;
        };
    }

    @Test
    void formatsTime() {
        assertEquals("0:00", ProgressNode.formatTime(0));
        assertEquals("0:09", ProgressNode.formatTime(9.4));
        assertEquals("1:05", ProgressNode.formatTime(65));
        assertEquals("3:00", ProgressNode.formatTime(180));
        assertEquals("1:02:05", ProgressNode.formatTime(3725));
        assertEquals("0:00", ProgressNode.formatTime(-5));
    }

    @Test
    void withoutInterpolationDisplayEqualsServerValue() {
        double[] position = {42};
        boolean[] playing = {true};
        ProgressNode node = boundNode(false, 0, position, playing);
        assertEquals(42, node.displayValue(state(position, playing), 0), 0.001);
        assertEquals(42, node.displayValue(state(position, playing), 5000), 0.001);
    }

    @Test
    void explicitRateExtrapolatesAndCaps() {
        double[] position = {10};
        boolean[] playing = {true};
        ProgressNode node = boundNode(true, 1.0, position, playing);
        StateAccess state = state(position, playing);

        assertEquals(10, node.displayValue(state, 0), 0.001);
        assertEquals(10.5, node.displayValue(state, 500), 0.001);
        // 超过外推上限（5s）后停止外推，回落到最后样本值
        assertEquals(10, node.displayValue(state, 6000), 0.001);
    }

    @Test
    void derivesRateFromConsecutiveSamples() {
        double[] position = {10};
        boolean[] playing = {true};
        ProgressNode node = boundNode(true, 0, position, playing);
        StateAccess state = state(position, playing);

        node.displayValue(state, 0);
        position[0] = 11;
        node.displayValue(state, 1000);
        assertEquals(11.5, node.displayValue(state, 1500), 0.001);
    }

    @Test
    void seekSnapsAndKeepsRate() {
        double[] position = {10};
        boolean[] playing = {true};
        ProgressNode node = boundNode(true, 0, position, playing);
        StateAccess state = state(position, playing);

        node.displayValue(state, 0);
        position[0] = 11;
        node.displayValue(state, 1000);

        position[0] = 100;
        assertEquals(100, node.displayValue(state, 1100), 0.001, "seek 立即对齐");
        assertEquals(100.5, node.displayValue(state, 1600), 0.001, "对齐后按原速率继续");
    }

    @Test
    void pausedDoesNotExtrapolate() {
        double[] position = {50};
        boolean[] playing = {false};
        ProgressNode node = boundNode(true, 1.0, position, playing);
        StateAccess state = state(position, playing);

        node.displayValue(state, 0);
        assertEquals(50, node.displayValue(state, 2000), 0.001);
    }

    @Test
    void clampsServerValue() {
        ProgressNode low = new ProgressNode(NodeStyle.defaults(), DoubleSpec.of(-10), 0, 180,
                ProgressNode.Direction.LEFT_RIGHT, 0, null, BooleanSpec.TRUE, false, 0, 3.0,
                "", null, 0xFFFFFFFF);
        ProgressNode high = new ProgressNode(NodeStyle.defaults(), DoubleSpec.of(999), 0, 180,
                ProgressNode.Direction.LEFT_RIGHT, 0, null, BooleanSpec.TRUE, false, 0, 3.0,
                "", null, 0xFFFFFFFF);
        assertEquals(0, low.serverValue(StateAccess.EMPTY), 0.001);
        assertEquals(180, high.serverValue(StateAccess.EMPTY), 0.001);
    }

    @Test
    void labelSupportsTimeValueMaxAndPercent() {
        ProgressNode time = node("{value} / {max}", "mm:ss", false, 0, BooleanSpec.TRUE);
        assertEquals("1:30 / 3:00", time.label((path, fallback) -> "90", 90));

        ProgressNode percent = node("{percent}%", null, false, 0, BooleanSpec.TRUE);
        assertEquals("50%", percent.label((path, fallback) -> "90", 90));

        ProgressNode binding = node("剩余 {state.left}", null, false, 0, BooleanSpec.TRUE);
        assertEquals("剩余 1:30", binding.label((path, fallback) -> path.equals("left") ? "1:30" : fallback, 90));
    }

    @Test
    void ratioClampsToUnitRange() {
        ProgressNode node = node("", null, false, 0, BooleanSpec.TRUE);
        assertEquals(0.25, node.ratioOf(45), 0.001);
        assertEquals(0, node.ratioOf(-10), 0.001);
        assertEquals(1, node.ratioOf(500), 0.001);
    }
}
