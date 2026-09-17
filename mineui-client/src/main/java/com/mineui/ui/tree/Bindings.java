package com.mineui.ui.tree;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 文本状态绑定：{@code {state.key}} / {@code {state.a.b}}。 */
public final class Bindings {

    private static final Pattern PATTERN = Pattern.compile("\\{state\\.([a-zA-Z0-9_.\\-]+)}");

    private Bindings() {
    }

    public static String resolve(String template, StateAccess state) {
        if (template == null || template.indexOf('{') < 0) {
            return template == null ? "" : template;
        }
        Matcher matcher = PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = state.get(matcher.group(1), "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
