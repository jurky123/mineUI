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
    public MineUiSession open(Plugin owner, Player player, String app, String view, JsonObject definition) {
        return plugin.uiSessions().open(owner, player, app, view, definition);
    }

    @Override
    public void closeAll(Plugin owner) {
        plugin.uiSessions().closeOwned(owner);
    }
}
