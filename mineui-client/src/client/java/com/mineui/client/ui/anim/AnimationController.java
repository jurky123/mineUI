package com.mineui.client.ui.anim;

import com.mineui.ui.anim.Easing;
import com.mineui.ui.anim.Tween;
import com.mineui.ui.tree.UiNode;

import java.util.ArrayList;
import java.util.List;

/** 节点属性动画控制器（渲染线程每帧更新）。 */
public final class AnimationController {

    public enum Property {
        OFFSET_X,
        OFFSET_Y,
        SCALE,
        OPACITY,
        ROTATION
    }

    private static final class Entry {
        final UiNode node;
        final Property property;
        final Tween tween;

        Entry(UiNode node, Property property, Tween tween) {
            this.node = node;
            this.property = property;
            this.tween = tween;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public void animate(UiNode node, Property property, float from, float to, float duration, Easing easing) {
        entries.add(new Entry(node, property, new Tween(from, to, duration, easing)));
    }

    public void animate(UiNode node, Property property, float from, float to, float duration,
                        Easing easing, Runnable onComplete) {
        entries.add(new Entry(node, property, new Tween(from, to, duration, easing).onComplete(onComplete)));
    }

    /** 每帧推进；完成回调可能追加新动画，因此对快照迭代。 */
    public void update(float delta) {
        for (Entry entry : new ArrayList<>(entries)) {
            entry.tween.update(delta);
            apply(entry.node, entry.property, entry.tween.value());
            if (entry.tween.done()) {
                entries.remove(entry);
            }
        }
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private static void apply(UiNode node, Property property, float value) {
        switch (property) {
            case OFFSET_X -> node.setAnimOffsetX(value);
            case OFFSET_Y -> node.setAnimOffsetY(value);
            case SCALE -> node.setAnimScale(value);
            case OPACITY -> node.setAnimOpacity(value);
            case ROTATION -> node.setAnimRotation(value);
        }
    }
}
