package com.mineui.client.ui.toast;

import com.mineui.client.net.ProtocolClient;
import com.mineui.protocol.msg.Toast;
import com.mineui.ui.paint.UiColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端 Toast 提示：右上角队列、淡入淡出。
 * <p>
 * 无界面时作为 HUD 元素显示；打开界面时由 Screen 事件叠绘，点击可回传全局动作。
 */
public final class MineUiToasts {

    private static final int MAX_VISIBLE = 5;
    private static final long FADE_MILLIS = 250L;
    private static final int WIDTH = 190;
    private static final int HEIGHT = 22;
    private static final int GAP = 3;
    private static final int MARGIN = 4;
    private static final int BACKGROUND = 0xD0181818;
    private static final int BORDER = 0x40FFFFFF;

    /** 各 kind 的描边色：info / success / warn / error。 */
    private static int kindBorder(String kind) {
        return switch (kind == null ? "info" : kind) {
            case "success" -> 0xC03FBF6F;
            case "warn" -> 0xC0F5C542;
            case "error" -> 0xC0FF6666;
            default -> 0x40FFFFFF;
        };
    }

    private static final Map<String, ItemStack> ICONS = new HashMap<>();
    private static final Deque<Active> ACTIVE = new ArrayDeque<>();

    private record Active(Toast toast, long shownAt) {
    }

    private MineUiToasts() {
    }

    public static void show(Toast toast) {
        if (toast == null || (toast.text().isEmpty() && toast.icon().isEmpty())) {
            return;
        }
        if (ACTIVE.size() >= MAX_VISIBLE) {
            ACTIVE.removeFirst();
        }
        ACTIVE.addLast(new Active(toast, System.currentTimeMillis()));
    }

    /** 断线/切服清理。 */
    public static void reset() {
        ACTIVE.clear();
    }

    /** 点击 Toast 上的动作（由界面鼠标事件调用）；返回 true 表示已消费。 */
    public static boolean mouseClicked(double mouseX, double mouseY) {
        long now = System.currentTimeMillis();
        List<Active> list = new ArrayList<>(ACTIVE);
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        for (int i = list.size() - 1; i >= 0; i--) {
            Active active = list.get(i);
            if (active.toast().action().isEmpty() || alpha(active, now) <= 0f) {
                continue;
            }
            float x = screenWidth - MARGIN - WIDTH;
            float y = MARGIN + i * (HEIGHT + GAP);
            if (mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + HEIGHT) {
                ProtocolClient.sendGlobalAction(active.toast().action());
                ACTIVE.remove(active);
                return true;
            }
        }
        return false;
    }

    /** 渲染全部 Toast（HUD 与界面叠绘共用）。 */
    public static void render(GuiGraphicsExtractor graphics, Font font) {
        long now = System.currentTimeMillis();
        prune(now);
        if (ACTIVE.isEmpty()) {
            return;
        }
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        List<Active> list = new ArrayList<>(ACTIVE);
        for (int i = 0; i < list.size(); i++) {
            Active active = list.get(i);
            float alpha = alpha(active, now);
            if (alpha <= 0f) {
                continue;
            }
            int x = screenWidth - MARGIN - WIDTH;
            int y = MARGIN + i * (HEIGHT + GAP);
            graphics.fill(x, y, x + WIDTH, y + HEIGHT, UiColors.withOpacity(BACKGROUND, alpha));
            graphics.fill(x, y, x + WIDTH, y + 1, UiColors.withOpacity(kindBorder(active.toast().kind()), alpha));
            int textX = x + 6;
            ItemStack icon = icon(active.toast().icon());
            if (!icon.isEmpty()) {
                graphics.item(icon, x + 3, y + 3);
                textX = x + 23;
            }
            String text = fit(font, active.toast().text(), x + WIDTH - 4 - textX);
            graphics.text(font, text, textX, y + (HEIGHT - font.lineHeight) / 2,
                    UiColors.withOpacity(active.toast().color(), alpha), true);
        }
    }

    // ---------- 内部 ----------

    private static void prune(long now) {
        while (!ACTIVE.isEmpty()) {
            Active first = ACTIVE.peekFirst();
            if (now - first.shownAt() >= first.toast().durationMillis()) {
                ACTIVE.removeFirst();
            } else {
                break;
            }
        }
    }

    private static float alpha(Active active, long now) {
        long age = now - active.shownAt();
        long duration = active.toast().durationMillis();
        if (age < 0 || age >= duration) {
            return 0f;
        }
        if (age < FADE_MILLIS) {
            return age / (float) FADE_MILLIS;
        }
        if (age > duration - FADE_MILLIS) {
            return (duration - age) / (float) FADE_MILLIS;
        }
        return 1f;
    }

    private static String fit(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "…";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (font.width(builder.toString() + c + ellipsis) > maxWidth) {
                break;
            }
            builder.append(c);
        }
        return builder + ellipsis;
    }

    private static ItemStack icon(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ICONS.computeIfAbsent(itemId, id -> {
            Identifier identifier = Identifier.tryParse(id);
            Item item = identifier == null ? null : BuiltInRegistries.ITEM.getValue(identifier);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        });
    }
}
