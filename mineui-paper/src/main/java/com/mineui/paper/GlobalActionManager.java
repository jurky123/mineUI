package com.mineui.paper;

import com.mineui.api.MineUiAction;
import com.mineui.paper.ui.ActionEvent;
import com.mineui.protocol.Envelope;
import com.mineui.protocol.JsonCodec;
import com.mineui.protocol.ProtocolException;
import com.mineui.protocol.msg.Action;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 全局动作（session=0 的 ACTION）：Toast 点击、键位等没有会话的操作。
 * 处理器按 owner 注册，owner 插件停用时自动清理；同名动作同 owner 只保留最后一次注册。
 */
public final class GlobalActionManager {

    private record Handler(Plugin owner, Consumer<MineUiAction> action) {
    }

    private final MineUiPlugin plugin;
    private final Map<String, List<Handler>> handlers = new ConcurrentHashMap<>();

    GlobalActionManager(MineUiPlugin plugin) {
        this.plugin = plugin;
    }

    public void on(Plugin owner, String actionId, Consumer<MineUiAction> handler) {
        if (owner == null || actionId == null || actionId.isEmpty() || handler == null) {
            return;
        }
        List<Handler> list = handlers.computeIfAbsent(actionId, ignored -> new CopyOnWriteArrayList<>());
        list.removeIf(existing -> existing.owner() == owner);
        boolean collision = list.stream().anyMatch(existing -> existing.owner() != owner);
        if (collision) {
            // 同名动作会被所有 owner 的处理器一起触发：提示改用 "<插件名>:<动作>" 命名，避免串触发
            plugin.getLogger().warning("全局动作 id 冲突: \"" + actionId + "\" 已被其他插件注册，"
                    + owner.getName() + " 仍会追加处理器；建议改用 \"插件名:动作\" 形式（如 \"minea:open_ui\"）");
        }
        list.add(new Handler(owner, handler));
    }

    /** 主线程执行。 */
    public void handle(Player player, Envelope envelope) {
        Action action;
        try {
            action = JsonCodec.decode(envelope.payload(), Action.class);
        } catch (ProtocolException e) {
            return;
        }
        List<Handler> list = handlers.get(action.id());
        if (list == null || list.isEmpty()) {
            plugin.getLogger().fine(() -> "未注册的 MineUI 全局动作: " + action.id() + "（" + player.getName() + "）");
            return;
        }
        MineUiAction event = new ActionEvent(player, action.id(), action.payload());
        for (Handler handler : list) {
            try {
                handler.action().accept(event);
            } catch (Exception e) {
                plugin.getLogger().warning("MineUI 全局动作处理器异常 #" + action.id()
                        + "（" + player.getName() + "）: " + e);
            }
        }
    }

    public void clearOwned(Plugin owner) {
        handlers.values().forEach(list -> list.removeIf(handler -> handler.owner() == owner));
        handlers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public void clear() {
        handlers.clear();
    }
}
