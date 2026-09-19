package com.mineui.ui.tree;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本绑定：{@code {state.key}} / {@code {state.a.b}}，以及列表条目内的
 * {@code {item}} / {@code {item.a.b}} / {@code {itemIndex}} / {@code {itemHighlight}}。
 */
public final class Bindings {

    private static final String STATE_PREFIX = "state.";

    /** 可解析的绑定路径（限定命名空间，避免误吃文本里的普通花括号）；key/local 允许 "插件名:动作" 形式的 id。 */
    private static final Pattern PATTERN =
            Pattern.compile("\\{((?:state|item|itemIndex|itemHighlight|key|local)(?:\\.[a-zA-Z0-9_:\\-]+)*)}");
    /** list 的 items / highlightIndex 只接受整体 state 绑定。 */
    private static final Pattern STATE_PATH = Pattern.compile("\\{state\\.([a-zA-Z0-9_.\\-]+)}");

    private Bindings() {
    }

    public static String resolve(String template, StateAccess state) {
        if (template == null || template.indexOf('{') < 0) {
            return template == null ? "" : template;
        }
        Matcher matcher = PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String path = matcher.group(1);
            if (path.startsWith(STATE_PREFIX)) {
                path = path.substring(STATE_PREFIX.length());
            }
            String value = state.get(path, "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 若整串就是单个 state 绑定（如 {@code "{state.lyrics}"}），返回内部路径（{@code "lyrics"}）；否则 null。
     * 用于 list 的 items / highlightIndex 结构绑定。
     */
    public static String statePath(String template) {
        if (template == null) {
            return null;
        }
        Matcher matcher = STATE_PATH.matcher(template.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }
}
