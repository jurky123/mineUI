package com.mineui.ui.util;

import java.util.function.LongSupplier;

/**
 * 动作节流器：窗口内只立即发送一次，其余只保留最后一次，待窗口到期后补发（trailing）。
 * <p>
 * 用于 hoverAction 这类高频动作：鼠标扫过列表时不会刷爆服务端限速，且最终状态一定送达。
 */
public final class ActionThrottle {

    private final long intervalMillis;
    private final LongSupplier clock;

    private long lastSentMillis;
    private boolean hasSent;
    private String pending;

    public ActionThrottle(long intervalMillis) {
        this(intervalMillis, System::currentTimeMillis);
    }

    public ActionThrottle(long intervalMillis, LongSupplier clock) {
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("intervalMillis 必须为正数");
        }
        this.intervalMillis = intervalMillis;
        this.clock = clock;
    }

    /** 返回应立即发送的动作；未到窗口则只记录最后一次并返回 null。 */
    public String submit(String action) {
        if (action == null || action.isEmpty()) {
            return null;
        }
        long now = clock.getAsLong();
        if (!hasSent || now - lastSentMillis >= intervalMillis) {
            hasSent = true;
            lastSentMillis = now;
            pending = null;
            return action;
        }
        pending = action;
        return null;
    }

    /** 每帧调用：窗口到期后返回被保留的最后动作（无则 null）。 */
    public String poll() {
        if (pending == null) {
            return null;
        }
        long now = clock.getAsLong();
        if (!hasSent || now - lastSentMillis >= intervalMillis) {
            hasSent = true;
            lastSentMillis = now;
            String action = pending;
            pending = null;
            return action;
        }
        return null;
    }
}
