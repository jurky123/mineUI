package com.mineui.ui.tree;

import com.google.gson.JsonPrimitive;
import com.mineui.ui.spec.BooleanSpec;
import com.mineui.ui.spec.Insets;
import com.mineui.ui.spec.SizeSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpinAngleTest {

    /** spinPlaying 绑定到 {state.playing}，由每次调用传入的状态决定冻结与否。 */
    private static final BooleanSpec PLAYING_PATH = BooleanSpec.parse(new JsonPrimitive("{state.playing}"));

    private static final StateAccess PLAYING = (path, fallback) -> "true";
    private static final StateAccess PAUSED = (path, fallback) -> "false";

    private static NodeStyle spinStyle(float spinSeconds) {
        // 全量构造：spin / spinPlaying 在末尾两位
        return new NodeStyle(null, SizeSpec.px(50), SizeSpec.px(50), Insets.ZERO, null,
                CrossAlign.START, MainAlign.START, 0f, 0f, null, 0f, null, 0f, 0f,
                null, 0, false, false, BooleanSpec.TRUE, null, false, null, null,
                null, null, null, 1f, null, spinSeconds, PLAYING_PATH);
    }

    @Test
    void zeroSpinIsAlwaysZero() {
        // 8 参兼容构造：spin 默认 0
        UiNode node = new BoxNode(new NodeStyle(null, SizeSpec.px(50), SizeSpec.px(50), Insets.ZERO,
                null, CrossAlign.START, MainAlign.START, 0f));
        assertEquals(0f, node.spinAngle(1000, PLAYING), 0.001);
        assertEquals(0f, node.spinAngle(5000, PLAYING), 0.001);
    }

    @Test
    void angleAccumulatesByLocalClock() {
        // 8 秒/圈 = 45 度/秒
        UiNode node = new BoxNode(spinStyle(8f));
        assertEquals(0f, node.spinAngle(0, PLAYING), 0.001);
        assertEquals(45f, node.spinAngle(1000, PLAYING), 0.001);
        assertEquals(90f, node.spinAngle(2000, PLAYING), 0.001);
    }

    @Test
    void frozenAngleKeepsTimeAnchor() {
        UiNode node = new BoxNode(spinStyle(8f));
        node.spinAngle(0, PLAYING);
        node.spinAngle(1000, PLAYING); // 45°
        assertEquals(135f, node.spinAngle(3000, PLAYING), 0.001); // 继续播放：1000→3000 +90

        // 暂停期间时间照走：恢复后从冻结角度继续，不跳变
        UiNode frozen = new BoxNode(spinStyle(8f));
        frozen.spinAngle(0, PAUSED);
        frozen.spinAngle(1000, PAUSED); // 冻结，仍 0°（时间锚已更新到 1000）
        assertEquals(90f, frozen.spinAngle(3000, PLAYING), 0.001); // 1000→3000 = +90
    }

    @Test
    void angleWrapsAt360() {
        UiNode node = new BoxNode(spinStyle(2f)); // 180 度/秒
        node.spinAngle(0, PLAYING);
        assertEquals(180f, node.spinAngle(1000, PLAYING), 0.001);
        assertEquals(90f, node.spinAngle(2500, PLAYING), 0.001); // 450° 绕圈为 90°
    }
}
