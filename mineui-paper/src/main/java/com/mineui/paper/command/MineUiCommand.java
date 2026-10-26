package com.mineui.paper.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mineui.paper.MineUiPlugin;
import com.mineui.paper.SessionManager;
import com.mineui.paper.ui.UiSession;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * /mineui &lt;status|test|style|image|hud|lyrics|chat|toast|keybind|gallery|close&gt;
 * <p>
 * 通过 Paper Brigadier Commands API 注册：子命令 tab 补全由命令树自动生成，
 * 无权限的管理子命令对发送者不可见。
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
                .then(Commands.literal("image")
                        .requires(source -> source.getSender().hasPermission("mineui.admin"))
                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                .executes(context -> {
                                    image(context.getSource().getSender(), StringArgumentType.getString(context, "url"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("hud")
                        .requires(source -> source.getSender().hasPermission("mineui.admin"))
                        .executes(context -> {
                            hud(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("lyrics")
                        .requires(source -> source.getSender().hasPermission("mineui.admin"))
                        .executes(context -> {
                            lyrics(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("chat")
                        .requires(source -> source.getSender().hasPermission("mineui.admin"))
                        .executes(context -> {
                            chat(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("toast")
                        .requires(source -> source.getSender().hasPermission("mineui.admin"))
                        .executes(context -> {
                            toast(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("keybind")
                        .requires(source -> source.getSender().hasPermission("mineui.admin"))
                        .executes(context -> {
                            keybind(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("gallery")
                        .requires(source -> source.getSender().hasPermission("mineui.admin"))
                        .executes(context -> {
                            gallery(context.getSource().getSender());
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
            UiSession ui = plugin.uiSessions().getScreen(player);
            if (ui != null) {
                player.sendMessage(Component.text("当前屏幕会话 #" + ui.id() + " " + ui.app() + "/" + ui.view(),
                        NamedTextColor.YELLOW));
            }
            int huds = plugin.uiSessions().getHuds(player).size();
            if (huds > 0) {
                player.sendMessage(Component.text("当前 HUD 会话: " + huds, NamedTextColor.YELLOW));
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
        sender.sendMessage(Component.text("活动会话: " + plugin.uiSessions().count()
                + "（HUD " + plugin.uiSessions().countHuds() + "）", NamedTextColor.AQUA));
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

    /** /mineui image <url>：远程图片端到端测试（白名单由 config.yml 控制）。 */
    private void image(CommandSender sender, String url) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
            return;
        }
        if (!plugin.sessions().isModClient(player.getUniqueId())) {
            sender.sendMessage(Component.text("你需要安装 MineUI 客户端 mod", NamedTextColor.RED));
            return;
        }
        if (!plugin.remoteImagePolicy().enabled()) {
            sender.sendMessage(Component.text("远程图片未开启：请在 config.yml 的 remote-images.allowed-domains 中添加域名",
                    NamedTextColor.YELLOW));
            return;
        }
        String normalized = url.trim();
        if (normalized.isEmpty() || normalized.chars().anyMatch(Character::isWhitespace)) {
            sender.sendMessage(Component.text("URL 不能包含空格（多条命令请分开发送）", NamedTextColor.RED));
            return;
        }
        if (!normalized.contains("://")) {
            normalized = "https://" + normalized;
        }
        String host;
        try {
            host = java.net.URI.create(normalized).getHost();
        } catch (Exception e) {
            host = null;
        }
        if (host == null) {
            sender.sendMessage(Component.text("URL 格式不正确: " + url, NamedTextColor.RED));
            return;
        }
        if (!plugin.remoteImagePolicy().allowsHost(host)) {
            sender.sendMessage(Component.text("域名不在白名单: " + host, NamedTextColor.RED));
            return;
        }
        UiSession session = plugin.uiSessions().open(player, "mineui", "image");
        session.state("url", normalized);
        session.on("close", event -> session.close());
        session.snapshot();
        player.sendMessage(Component.text("已打开远程图片测试页", NamedTextColor.GREEN));
    }

    /** /mineui hud：打开 HUD 演示（服务端声明布局，F6 本地开关）。 */
    private void hud(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
            return;
        }
        if (!plugin.sessions().isModClient(player.getUniqueId())) {
            sender.sendMessage(Component.text("你需要安装 MineUI 客户端 mod 才能查看 HUD", NamedTextColor.RED));
            return;
        }
        UiSession session = plugin.uiSessions().openHud(plugin, player, "mineui", "hud", null,
                new com.mineui.protocol.msg.HudLayout("top_right", 6f, 6f, 1f));
        session.state("title", "正在播放");
        session.state("subtitle", "MineUI HUD 演示 · F6 开关");
        session.state("duration", 180.0);
        session.state("position", 0.0);
        session.state("playing", true);
        session.snapshot();
        // 服务端 1Hz 更新；客户端按本地时钟插值，进度平滑
        double[] position = {0.0};
        var task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (session.closed()) {
                return;
            }
            position[0] = (position[0] + 1) % 180.0;
            session.state("position", position[0]);
        }, 20L, 20L);
        session.onClose(task::cancel);
        player.sendMessage(Component.text("已打开 HUD 演示（1Hz 更新，客户端插值；F6 开关，/mineui close 关闭）", NamedTextColor.GREEN));
    }

    /** /mineui lyrics：列表（歌词）演示，1Hz 推进高亮行验证自动滚动居中。 */
    private void lyrics(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
            return;
        }
        if (!plugin.sessions().isModClient(player.getUniqueId())) {
            sender.sendMessage(Component.text("你需要安装 MineUI 客户端 mod 才能查看列表演示", NamedTextColor.RED));
            return;
        }
        var lyrics = new com.google.gson.JsonArray();
        for (String line : LYRICS_DEMO) {
            lyrics.add(line);
        }
        UiSession session = plugin.uiSessions().open(player, "mineui", "lyrics");
        session.state("lyrics", lyrics);
        session.state("current", 0);
        session.state("spin", true);
        session.on("close", event -> session.close());
        session.on("toggle_spin", event -> session.state("spin", !session.getBoolean("spin", true)));
        session.snapshot();

        int[] current = {0};
        var task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (session.closed()) {
                return;
            }
            current[0] = (current[0] + 1) % LYRICS_DEMO.length;
            session.state("current", current[0]);
        }, 40L, 20L);
        session.onClose(task::cancel);
        player.sendMessage(Component.text("已打开列表演示（歌词高亮自动居中，/mineui close 关闭）", NamedTextColor.GREEN));
    }

    /**
     * /mineui chat：两栏聊天界面演示（grow 布局 / 换行文本 / stickToBottom 列表 / submit 发送 /
     * 伪造流式回复 / 审批卡按钮），页面见 {@code assets/mineui/ui/mineui/chat.json}。
     */
    private void chat(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
            return;
        }
        if (!plugin.sessions().isModClient(player.getUniqueId())) {
            sender.sendMessage(Component.text("你需要安装 MineUI 客户端 mod 才能查看聊天界面", NamedTextColor.RED));
            return;
        }

        UiSession session = plugin.uiSessions().open(player, "mineui", "chat");
        JsonArray messages = new JsonArray();
        messages.add(chatMessage("system", "系统", "全服对话已开启 · 所有玩家可见，请遵守服务器规则"));
        messages.add(chatMessage("assistant", "MineAgent", "你好，我是 MineAgent。可以提问、闲聊，也可以让我调用工具或提交审批。"));
        JsonObject tool = chatMessage("tool", "工具调用", "已读取主世界在线人数：12 人。");
        tool.addProperty("tool", "server_status");
        tool.addProperty("toolStatus", "完成");
        tool.addProperty("duration", "0.6s");
        messages.add(tool);
        JsonObject approval = chatMessage("approval", "审批", "请求执行 /time set day（会改变全服时间）");
        approval.addProperty("tool", "command_runner");
        approval.addProperty("time", "30s");
        messages.add(approval);

        session.state("title", "全服对话");
        session.state("status", "在线");
        session.state("running", false);
        session.state("messages", messages);
        session.state("nav", "chat");

        // 当前流式任务（每次发送/停止都可能替换，关闭会话时统一取消）
        BukkitTask[] stream = {null};
        session.on("send", event -> {
            String text = event.string("text", "").trim();
            if (text.isEmpty()) {
                return;
            }
            cancelTask(stream);
            messages.add(chatMessage("user", "你", text));
            while (messages.size() > MAX_CHAT_MESSAGES) {
                messages.remove(0);
            }
            session.state("messages", messages);
            streamChatReply(session, messages, stream);
        });
        session.on("stop", event -> {
            cancelTask(stream);
            session.batch(() -> {
                session.state("running", false);
                session.state("status", "已停止");
            });
        });
        session.on("approve", event -> resolveApproval(session, messages, event.number("index", -1), true));
        session.on("reject", event -> resolveApproval(session, messages, event.number("index", -1), false));
        session.on("nav_chat", event -> nav(session, "chat"));
        session.on("nav_ai", event -> nav(session, "ai"));
        session.on("nav_approval", event -> nav(session, "approval"));
        session.on("nav_help", event -> nav(session, "help"));
        session.on("close", event -> session.close());
        session.onClose(() -> cancelTask(stream));
        session.snapshot();
        player.sendMessage(Component.text("已打开聊天界面（发送消息查看流式回复，/mineui close 关闭）", NamedTextColor.GREEN));
    }

    /** 追加一条助手占位消息，并按 250ms 一段把示例文本写到该消息上。 */
    private void streamChatReply(UiSession session, JsonArray messages, BukkitTask[] stream) {
        JsonObject reply = chatMessage("assistant", "MineAgent", "…");
        messages.add(reply);
        int[] cursor = {0};
        session.state("messages", messages);
        session.state("running", true);
        session.state("status", "正在回复…");
        stream[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (session.closed()) {
                cancelTask(stream);
                return;
            }
            if (cursor[0] >= CHAT_REPLY_DEMO.length()) {
                finishChatReply(session, messages, stream);
                return;
            }
            int end = Math.min(CHAT_REPLY_DEMO.length(), cursor[0] + CHAT_STREAM_CHARS);
            reply.addProperty("text", CHAT_REPLY_DEMO.substring(0, end));
            cursor[0] = end;
            session.state("messages", messages);
            if (cursor[0] >= CHAT_REPLY_DEMO.length()) {
                finishChatReply(session, messages, stream);
            }
        }, 250L, 250L);
    }

    /** 流式结束：取消任务并恢复在线状态。 */
    private static void finishChatReply(UiSession session, JsonArray messages, BukkitTask[] stream) {
        cancelTask(stream);
        if (session.closed()) {
            return;
        }
        session.batch(() -> {
            session.state("messages", messages);
            session.state("running", false);
            session.state("status", "在线");
        });
    }

    /** 审批/拒绝：按条目下标把审批卡替换成系统提示（列表条目按钮自动携带 index）。 */
    private static void resolveApproval(UiSession session, JsonArray messages, double rawIndex, boolean approved) {
        int index = (int) Math.round(rawIndex);
        if (index < 0 || index >= messages.size()) {
            return;
        }
        if (!(messages.get(index) instanceof JsonObject message)
                || !message.has("isApproval") || !message.get("isApproval").getAsBoolean()) {
            return;
        }
        messages.set(index, chatMessage("system", "系统", approved ? "✓ 已批准工具调用" : "✗ 已拒绝工具调用"));
        session.state("messages", messages);
    }

    /** 左侧导航演示：切换标题与状态文案。 */
    private static void nav(UiSession session, String nav) {
        session.state("nav", nav);
        switch (nav) {
            case "ai" -> {
                session.state("title", "AI 玩家");
                session.state("status", "演示 · AI 玩家列表");
            }
            case "approval" -> {
                session.state("title", "审批中心");
                session.state("status", "演示 · 1 项待审批");
            }
            case "help" -> {
                session.state("title", "帮助");
                session.state("status", "演示 · 命令与快捷键");
            }
            default -> {
                session.state("title", "全服对话");
                session.state("status", "在线");
            }
        }
    }

    /** 构造一条聊天消息；角色布尔字段与 chat.json 的 itemTemplate 对应。 */
    private static JsonObject chatMessage(String role, String author, String text) {
        JsonObject message = new JsonObject();
        message.addProperty("isUser", "user".equals(role));
        message.addProperty("isAssistant", "assistant".equals(role));
        message.addProperty("isTool", "tool".equals(role));
        message.addProperty("isApproval", "approval".equals(role));
        message.addProperty("isSystem", "system".equals(role));
        message.addProperty("author", author);
        message.addProperty("text", text);
        message.addProperty("time", "刚刚");
        return message;
    }

    private static void cancelTask(BukkitTask[] task) {
        if (task[0] != null) {
            task[0].cancel();
            task[0] = null;
        }
    }

    /** 聊天页保留的最近消息条数。 */
    private static final int MAX_CHAT_MESSAGES = 20;
    /** 流式回复每段追加的字符数（约 250ms 一段）。 */
    private static final int CHAT_STREAM_CHARS = 3;
    private static final String CHAT_REPLY_DEMO =
            "收到！我先看一下主世界的天气和在线玩家，再把结果整理给你。"
                    + "左侧导航、右侧消息卡片和底部输入框都由声明式 JSON 描述，"
                    + "新消息会自动跟随到底部；你可以继续发送消息，或点击「停止」中断这次回复。";

    /** /mineui keybind：声明通用键位（槽位 1，默认 F7）→ 按下打开歌词页。 */
    private void keybind(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
            return;
        }
        if (!plugin.sessions().isModClient(player.getUniqueId())) {
            sender.sendMessage(Component.text("你需要安装 MineUI 客户端 mod 才能使用声明键位", NamedTextColor.RED));
            return;
        }
        plugin.globalActions().on(plugin, "mineui_demo_keybind", action -> lyrics(action.player()));
        plugin.keybinds().set(plugin, player, "1", "mineui_demo_keybind", "打开歌词");
        player.sendMessage(Component.text(
                "已声明键位 1（默认 F7，可在 设置→按键 中改）；按下将打开歌词页，歌词页会显示当前按键",
                NamedTextColor.GREEN));
    }

    /** /mineui toast：短提示 + 全局动作（点击回调）演示。 */
    private void toast(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
            return;
        }
        if (!plugin.sessions().isModClient(player.getUniqueId())) {
            sender.sendMessage(Component.text("你需要安装 MineUI 客户端 mod 才能看到 Toast", NamedTextColor.RED));
            return;
        }
        plugin.globalActions().on(plugin, "mineui_demo_toast", action ->
                action.player().sendMessage(Component.text(
                        "[MineUI] Toast 点击动作已回传: " + action.id(), NamedTextColor.GREEN)));
        plugin.sendToast(player, new com.mineui.protocol.msg.Toast(
                "切歌：示例歌曲 - 示例歌手（打开界面后可点击）",
                "minecraft:music_disc_cat", "mineui_demo_toast", 6000, 0xFFFFFFFF, "success"));
        player.sendMessage(Component.text("已发送 Toast；打开任意界面后点击可触发全局动作", NamedTextColor.GREEN));
    }

    private static final String[] LYRICS_DEMO = {
            "（列表演示）",
            "我曾经跨过山和大海",
            "也穿过人山人海",
            "我曾经拥有着的一切",
            "转眼都飘散如烟",
            "我曾经失落失望失掉所有方向",
            "直到看见平凡才是唯一的答案",
            "当你仍然 还在幻想",
            "你的明天",
            "她会好吗 还是更烂",
            "对我而言是另一天",
            "我曾经毁了我的一切",
            "只想永远地离开",
            "我曾经堕入无边黑暗",
            "想挣扎无法自拔",
    };

    /** /mineui gallery：打开组件画廊（物品/头颅装饰、悬浮动效、时间轮换、精灵素材）。 */
    private void gallery(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
            return;
        }
        if (!plugin.sessions().isModClient(player.getUniqueId())) {
            sender.sendMessage(Component.text("你需要安装 MineUI 客户端 mod 才能查看组件画廊", NamedTextColor.RED));
            return;
        }
        UiSession session = plugin.uiSessions().open(player, "mineui", "gallery");
        session.state("clicks", 0);
        session.state("dialog", false);
        session.state("check", false);
        session.state("check_label", "关闭");
        session.state("tab", 0);
        session.state("tab_name", "曲库");
        session.on("decor_click", event -> session.state("clicks", session.getInt("clicks", 0) + 1));
        session.on("open_dialog", event -> session.state("dialog", true));
        session.on("close_dialog", event -> session.state("dialog", false));
        session.on("toggle_check", event -> {
            boolean now = !session.getBoolean("check", false);
            session.state("check", now);
            session.state("check_label", now ? "开启" : "关闭");
        });
        session.on("toggle_switch", event -> {
            boolean now = !session.getBoolean("check", false);
            session.state("check", now);
            session.state("check_label", now ? "开启" : "关闭");
        });
        session.on("tab_select", event -> {
            // Tabs 控件值走独立 "tab" 字段；极旧客户端只发 "index" 时回退读取
            com.google.gson.JsonElement payload = event.payload();
            boolean hasTab = payload != null && payload.isJsonObject()
                    && payload.getAsJsonObject().has("tab");
            int index = (int) Math.round(hasTab ? event.number("tab", 0) : event.number("index", 0));
            session.state("tab", index);
            session.state("tab_name", switch (index) {
                case 1 -> "搜索";
                case 2 -> "队列";
                default -> "曲库";
            });
        });
        session.on("accent_demo", event -> session.state("clicks", session.getInt("clicks", 0) + 1));
        session.snapshot();
        player.sendMessage(Component.text("已打开组件画廊（/mineui gallery）", NamedTextColor.GREEN));
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
        if (plugin.uiSessions().getSessions(player).isEmpty()) {
            player.sendMessage(Component.text("当前没有打开的 MineUI 界面", NamedTextColor.GRAY));
            return;
        }
        plugin.uiSessions().close(player);
        player.sendMessage(Component.text("已关闭 MineUI 界面", NamedTextColor.YELLOW));
    }
}
