package com.mineui.ui.spec;

/** UI 定义解析/加载失败。 */
public class UiSpecException extends Exception {

    public UiSpecException(String message) {
        super(message);
    }

    public UiSpecException(String message, Throwable cause) {
        super(message, cause);
    }
}
