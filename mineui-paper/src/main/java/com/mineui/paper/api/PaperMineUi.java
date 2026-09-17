package com.mineui.paper.api;

import com.mineui.api.MineUi;
import com.mineui.api.MineUiSession;
import com.mineui.paper.MineUiPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

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
    public MineUiSession open(Plugin owner, Player player, String app, String view) {
        return plugin.uiSessions().open(owner, player, app, view);
    }

    @Override
    public void closeAll(Plugin owner) {
        plugin.uiSessions().closeOwned(owner);
    }
}
