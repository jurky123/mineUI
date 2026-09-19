package com.mineui.paper.api;

import com.google.gson.JsonObject;
import com.mineui.api.MineUi;
import com.mineui.api.MineUiSession;
import com.mineui.paper.MineUiPlugin;
import com.mineui.paper.SessionManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Set;

/** {@link MineUi} 的 Paper 实现。 */
public final class PaperMineUi implements MineUi {

    private final MineUiPlugin plugin;

    public PaperMineUi(MineUiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean hasClient(Player player) {
        return plugin.sessions().isModClient(player.getUniqueId());
    }

    @Override
    public Set<String> capabilities(Player player) {
        SessionManager.ClientSession session = plugin.sessions().get(player.getUniqueId());
        return session == null ? Set.of() : Set.copyOf(session.capabilities());
    }

    @Override
    public String modVersion(Player player) {
        SessionManager.ClientSession session = plugin.sessions().get(player.getUniqueId());
        return session == null ? null : session.modVersion();
    }

    @Override
    public MineUiSession open(Plugin owner, Player player, String app, String view, JsonObject definition) {
        return plugin.uiSessions().open(owner, player, app, view, definition);
    }

    @Override
    public MineUiSession openHud(Plugin owner, Player player, String app, String view,
                                 com.google.gson.JsonObject definition,
                                 com.mineui.protocol.msg.HudLayout layout) {
        return plugin.uiSessions().openHud(owner, player, app, view, definition, layout);
    }

    @Override
    public void toast(Player player, com.mineui.protocol.msg.Toast toast) {
        plugin.sendToast(player, toast);
    }

    @Override
    public void onAction(Plugin owner, String actionId, java.util.function.Consumer<com.mineui.api.MineUiAction> handler) {
        plugin.globalActions().on(owner, actionId, handler);
    }

    @Override
    public void keybind(Plugin owner, Player player, String slot, String actionId, String label) {
        plugin.keybinds().set(owner, player, slot, actionId, label);
    }

    @Override
    public void clearKeybinds(Plugin owner, Player player) {
        plugin.keybinds().clear(owner, player);
    }

    @Override
    public void closeAll(Plugin owner) {
        plugin.uiSessions().closeOwned(owner);
    }
}
