package com.mineui.protocol.session;

import java.util.function.LongSupplier;

/**
 * 固定窗口限流器（线程安全）。
 * <p>
 * 用于网络入口的包数限流与业务动作限流；时间源可注入，便于单测。
 */
public final class RateWindow {

    private final int limit;
    private final long windowMillis;
    private final LongSupplier clock;

    private long windowStart;
    private int count;

    public RateWindow(int limit, long windowMillis) {
        this(limit, windowMillis, System::currentTimeMillis);
    }

    public RateWindow(int limit, long windowMillis, LongSupplier clock) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须为正数");
        }
        if (windowMillis <= 0) {
            throw new IllegalArgumentException("windowMillis 必须为正数");
        }
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.clock = clock;
        this.windowStart = clock.getAsLong();
    }

    /** 尝试获取一个配额；超过窗口限额返回 false。 */
    public synchronized boolean tryAcquire() {
        long now = clock.getAsLong();
        if (now - windowStart >= windowMillis) {
            windowStart = now;
            count = 0;
        }
        return ++count <= limit;
    }

    public int limit() {
        return limit;
    }

    public long windowMillis() {
        return windowMillis;
    }
}
