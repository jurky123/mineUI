package com.mineui.protocol.session;

/**
 * 会话修订守卫（服务端持有）。
 * <p>
 * 规则（见设计文档 §4.3）：
 * <ul>
 *   <li>incoming == current → 接受，current 自增（本次操作生效）；</li>
 *   <li>incoming &lt; current → 过期（双击/延迟旧包），拒绝并回发最新 state；</li>
 *   <li>incoming &gt; current → 非法（客户端乱序/伪造），拒绝。</li>
 * </ul>
 * 线程安全：服务端可能从不同线程访问。
 */
public final class RevisionGuard {

    public enum Decision {
        /** 接受：revision 已自增 */
        ACCEPT,
        /** 过期：客户端落后，应回发最新 state */
        STALE,
        /** 非法：客户端越过当前 revision */
        INVALID
    }

    private int revision;

    public RevisionGuard() {
        this(0);
    }

    public RevisionGuard(int initialRevision) {
        if (initialRevision < 0) {
            throw new IllegalArgumentException("initialRevision 不能为负");
        }
        this.revision = initialRevision;
    }

    public synchronized int revision() {
        return revision;
    }

    public synchronized Decision submit(int incoming) {
        if (incoming == revision) {
            revision++;
            return Decision.ACCEPT;
        }
        return incoming < revision ? Decision.STALE : Decision.INVALID;
    }
}
