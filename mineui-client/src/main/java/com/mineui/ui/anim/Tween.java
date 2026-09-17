package com.mineui.ui.anim;

/** 单值补间：from → to，支持延迟、缓动与完成回调。 */
public final class Tween {

    private final float from;
    private final float to;
    private final float duration;
    private final float delay;
    private final Easing easing;

    private float elapsed;
    private boolean completed;
    private Runnable onComplete;

    public Tween(float from, float to, float duration, Easing easing) {
        this(from, to, duration, 0f, easing);
    }

    public Tween(float from, float to, float duration, float delay, Easing easing) {
        this.from = from;
        this.to = to;
        this.duration = Math.max(0f, duration);
        this.delay = Math.max(0f, delay);
        this.easing = easing == null ? Easing.LINEAR : easing;
    }

    public Tween onComplete(Runnable callback) {
        this.onComplete = callback;
        return this;
    }

    public void update(float delta) {
        if (completed) {
            return;
        }
        elapsed += Math.max(0f, delta);
        if (elapsed >= delay + duration) {
            completed = true;
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    public boolean done() {
        return completed;
    }

    public float value() {
        if (elapsed <= delay) {
            return from;
        }
        if (duration <= 0f) {
            return to;
        }
        float t = (elapsed - delay) / duration;
        if (t >= 1f) {
            return to;
        }
        return from + (to - from) * easing.apply(t);
    }
}
