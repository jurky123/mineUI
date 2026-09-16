package com.mineui.paper.command;

import com.mineui.paper.MineUiPlugin;
import com.mineui.paper.SessionManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /mineui &lt;status|test&gt;
 * <p>
 * 通过 Paper Brigadier Commands API 注册：子命令 tab 补全由命令树自动生成，
 * 无权限的子命令（test）对发送者不可见。
 */
public final class MineUiCommand {

    private final MineUiPlugin plugin;

    public MineUiCommand(MineUiPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands) {
        LiteralCommandNode<CommandSourceStack> node = Commands.literal("mineui")
                .executes(context -> {
                    status(context.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("status")
                        .executes(context -> {
                            status(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("test")
                        .requires(source -> source.getSender().hasPermission("mineui.admin"))
                        .executes(context -> {
                            test(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();

        commands.register(node, "MineUI 主命令");
    }

    private void status(CommandSender sender) {
        if (sender instanceof Player player) {
            SessionManager.ClientSession session = plugin.sessions().get(player.getUniqueId());
            if (session == null) {
                player.sendMessage(Component.text("你当前是原版客户端（未收到 MineUI 握手）", NamedTextColor.GRAY));
            } else {
                player.sendMessage(Component.text("你是 MineUI 客户端：mod " + session.modVersion()
                        + " · protocol " + session.protocol()
                        + " · mc " + session.minecraft()
                        + " · caps " + session.capabilities(), NamedTextColor.GREEN));
            }
        }

        List<String> modNames = plugin.sessions().count() == 0
                ? List.of()
                : Bukkit.getOnlinePlayers().stream()
                        .filter(p -> plugin.sessions().isModClient(p.getUniqueId()))
                        .map(Player::getName)
                        .sorted()
                        .toList();
        sender.sendMessage(Component.text("在线 MineUI 客户端: " + modNames.size()
                + (modNames.isEmpty() ? "" : " (" + String.join(", ", modNames) + ")"), NamedTextColor.AQUA));
    }

    private void test(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
            return;
        }
        if (!plugin.sessions().isModClient(player.getUniqueId())) {
            sender.sendMessage(Component.text("你需要安装 MineUI 客户端 mod 才能使用测试界面", NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text("MineUI 测试界面将在 Phase 1 实现", NamedTextColor.YELLOW));
    }
}
