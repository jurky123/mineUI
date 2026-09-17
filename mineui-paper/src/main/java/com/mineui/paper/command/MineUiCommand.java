package com.mineui.paper.command;

import com.mineui.paper.MineUiPlugin;
import com.mineui.paper.SessionManager;
import com.mineui.paper.ui.UiSession;
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
 * /mineui &lt;status|test|close&gt;
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
                .then(Commands.literal("close")
                        .requires(source -> source.getSender().hasPermission("mineui.admin"))
                        .executes(context -> {
                            close(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("style")
                        .requires(source -> source.getSender().hasPermission("mineui.admin"))
                        .executes(context -> {
                            style(context.getSource().getSender());
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
            UiSession ui = plugin.uiSessions().get(player);
            if (ui != null) {
                player.sendMessage(Component.text("当前界面会话 #" + ui.id() + " " + ui.app() + "/" + ui.view(),
                        NamedTextColor.YELLOW));
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
        sender.sendMessage(Component.text("活动界面会话: " + plugin.uiSessions().count(), NamedTextColor.AQUA));
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

        UiSession session = plugin.uiSessions().open(player, "mineui", "test");
        session.state("title", "MineUI Phase 1");
        session.state("count", 0);
        session.state("showModal", false);
        session.on("button_click", event -> {
            int count = session.getInt("count", 0) + 1;
            session.state("count", count);
            session.state("title", "点击了 " + count + " 次");
        });
        session.on("toggle_modal", event -> session.state("showModal", !session.getBoolean("showModal", false)));
        session.on("close_modal", event -> session.state("showModal", false));

        // 输入框提交（负载 {"text": ...}）与悬浮预览（hoverAction）演示
        session.state("query", "");
        session.state("hover", "");
        session.on("search", event -> session.state("query", event.string("text", "")));
        for (int i = 0; i < 12; i++) {
            final int slot = i + 1;
            session.on("hover_slot_" + i, event -> session.state("hover", "物品格 " + slot));
        }
        session.snapshot();

        player.sendMessage(Component.text("已打开 MineUI 测试界面（点按钮 +1，Esc 关闭）", NamedTextColor.GREEN));
    }

    /** /mineui style：打开原版风格模板演示页（面板/按钮/输入框/滑块/滚动/弹窗）。 */
    private void style(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
            return;
        }
        if (!plugin.sessions().isModClient(player.getUniqueId())) {
            sender.sendMessage(Component.text("你需要安装 MineUI 客户端 mod 才能查看原版风格模板", NamedTextColor.RED));
            return;
        }
        UiSession session = plugin.uiSessions().open(player, "mineui", "vanilla");
        session.state("query", "");
        session.state("volume", 50);
        session.state("dialog", false);
        session.on("search", event -> session.state("query", event.string("text", "")));
        session.on("clear_search", event -> session.state("query", ""));
        session.on("volume_set", event -> session.state("volume", (int) Math.round(event.number("value", 50))));
        session.on("open_dialog", event -> session.state("dialog", true));
        session.on("close_dialog", event -> session.state("dialog", false));
        session.on("ok", event -> session.close());
        session.snapshot();
        player.sendMessage(Component.text("已打开原版风格模板（/mineui style）", NamedTextColor.GREEN));
    }

    private void close(CommandSender sender) {        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
            return;
        }
        if (plugin.uiSessions().get(player) == null) {
            player.sendMessage(Component.text("当前没有打开的 MineUI 界面", NamedTextColor.GRAY));
            return;
        }
        plugin.uiSessions().close(player);
        player.sendMessage(Component.text("已关闭 MineUI 界面", NamedTextColor.YELLOW));
    }
}
