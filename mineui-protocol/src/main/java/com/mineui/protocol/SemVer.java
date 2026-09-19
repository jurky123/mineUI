package com.mineui.protocol;

/**
 * 宽松语义版本比较（纯函数，便于单测）。
 * <p>
 * 只比较点分数字段（{@code "0.14.0"}）；后缀（如 {@code "-SNAPSHOT"}）忽略，
 * 无法解析的段按 0 处理，短版本补 0。
 */
public final class SemVer {

    private SemVer() {
    }

    /** 负数表示 a &lt; b，0 相等，正数 a &gt; b；null 均视为 "0"。 */
    public static int compare(String a, String b) {
        int[] x = parse(a);
        int[] y = parse(b);
        int length = Math.max(x.length, y.length);
        for (int i = 0; i < length; i++) {
            int left = i < x.length ? x[i] : 0;
            int right = i < y.length ? y[i] : 0;
            if (left != right) {
                return Integer.compare(left, right);
            }
        }
        return 0;
    }

    /** a 是否低于 b。 */
    public static boolean isOlder(String version, String minimum) {
        return compare(version, minimum) < 0;
    }

    static int[] parse(String version) {
        if (version == null) {
            return new int[0];
        }
        String core = version.trim();
        int dash = core.indexOf('-');
        if (dash >= 0) {
            core = core.substring(0, dash);
        }
        String[] parts = core.split("\\.", -1);
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                numbers[i] = 0;
            }
        }
        return numbers;
    }
}
