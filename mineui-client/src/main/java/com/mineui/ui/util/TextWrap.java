package com.mineui.ui.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * 文本自动换行（纯逻辑，字体宽度由调用方注入，便于单测）。
 * <p>
 * 规则：
 * <ul>
 *   <li>先按显式 {@code \n} 分段；段内按宽度贪心换行；</li>
 *   <li>拉丁文本优先在最后一个空格处断行（不把单词拦腰截断），CJK 等无空格文本按字符断行；</li>
 *   <li>{@code maxLines > 0} 时只保留前 maxLines 行，最后一行以省略号收尾。</li>
 * </ul>
 */
public final class TextWrap {

    public static final String ELLIPSIS = "…";

    private TextWrap() {
    }

    /**
     * @param text     原始文本（可含换行）
     * @param maxWidth 单行最大像素宽（≤0 表示只按显式换行切分）
     * @param measure  文本宽度测量（如 {@code font::width}）
     * @param maxLines 最大行数（≤0 表示不限）
     * @return 逐行文本（至少一行；显式换行产生的空行会保留）
     */
    public static List<String> wrap(String text, float maxWidth,
                                    ToIntFunction<String> measure, int maxLines) {
        List<String> lines = new ArrayList<>();
        String value = text == null ? "" : text;
        String[] paragraphs = value.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int i = 0; i < paragraphs.length; i++) {
            // 末尾换行不额外产生空行（服务端预换行时常带结尾 \n）
            if (i == paragraphs.length - 1 && paragraphs[i].isEmpty() && paragraphs.length > 1) {
                continue;
            }
            if (maxWidth <= 0f) {
                lines.add(paragraphs[i]);
            } else {
                wrapParagraph(paragraphs[i], maxWidth, measure, lines);
            }
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        if (maxLines > 0 && lines.size() > maxLines) {
            List<String> limited = new ArrayList<>(lines.subList(0, maxLines));
            limited.set(maxLines - 1, ellipsize(limited.get(maxLines - 1), maxWidth, measure));
            return limited;
        }
        return lines;
    }

    private static void wrapParagraph(String paragraph, float maxWidth,
                                      ToIntFunction<String> measure, List<String> out) {
        if (paragraph.isEmpty()) {
            out.add("");
            return;
        }
        StringBuilder line = new StringBuilder();
        int index = 0;
        while (index < paragraph.length()) {
            int codePoint = paragraph.codePointAt(index);
            int step = Character.charCount(codePoint);
            String character = new String(Character.toChars(codePoint));
            boolean isSpace = Character.isWhitespace(codePoint);

            if (line.length() > 0 && measure.applyAsInt(line + character) > maxWidth) {
                int space = lastSpace(line);
                if (space > 0 && space < line.length() - 1 && !isSpace) {
                    // 词中溢出：把最后一个空格后的片段挪到下一行，保持单词完整
                    out.add(trimTrailing(line.substring(0, space)));
                    String tail = line.substring(space + 1);
                    line.setLength(0);
                    line.append(tail);
                    if (line.length() > 0 && measure.applyAsInt(line + character) > maxWidth) {
                        out.add(trimTrailing(line.toString()));
                        line.setLength(0);
                    }
                } else {
                    out.add(trimTrailing(line.toString()));
                    line.setLength(0);
                }
                if (!isSpace) {
                    line.append(character);
                }
            } else {
                line.append(character);
            }
            index += step;
        }
        out.add(trimTrailing(line.toString()));
    }

    private static int lastSpace(CharSequence line) {
        for (int i = line.length() - 1; i >= 0; i--) {
            char c = line.charAt(i);
            if (c == ' ' || c == '\t') {
                return i;
            }
        }
        return -1;
    }

    private static String trimTrailing(String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return end == value.length() ? value : value.substring(0, end);
    }

    /** 末尾加省略号，必要时从尾部删字符直到放得下。 */
    private static String ellipsize(String line, float maxWidth, ToIntFunction<String> measure) {
        String trimmed = trimTrailing(line);
        if (maxWidth <= 0f || measure.applyAsInt(trimmed + ELLIPSIS) <= maxWidth) {
            return trimmed + ELLIPSIS;
        }
        int end = trimmed.length();
        while (end > 0) {
            end--;
            if (end > 0 && Character.isLowSurrogate(trimmed.charAt(end))
                    && Character.isHighSurrogate(trimmed.charAt(end - 1))) {
                end--;
            }
            String candidate = trimTrailing(trimmed.substring(0, end));
            if (measure.applyAsInt(candidate + ELLIPSIS) <= maxWidth) {
                return candidate + ELLIPSIS;
            }
        }
        return ELLIPSIS;
    }
}
