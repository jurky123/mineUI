# MineUI — Paper 26.2 + Fabric 26.2 客户端自定义 GUI 框架

> 方案版本 v0.1 · 2026-09-16 · 状态：设计评审
> 目标组合：**Paper 26.2（服务端） + Fabric 26.2 客户端 mod + 自定义 Payload 协议 + 原生 UI Runtime**

---

## 0. 结论摘要（TL;DR）

- 做一套 **Paper 插件 + Fabric 客户端 mod** 的自定义 UI 基础设施，服务端管状态、客户端管表现，覆盖菜单 / UNO / 皮肤浏览器 / 商店 / HUD 等全部界面需求。
- **Fabric 26.2 路线可行**（已核实：官方示例工程目标 26.2），但注意 **Yarn 映射停更在 1.21.11，26.x 改用 Mojang 官方映射**；Fabric API 已有 `0.160.0+26.2`。
- **不用 WebGUI/Chromium**：SkinsRestorer、UNO、背包、战斗 HUD 这类界面需要 Minecraft 原生渲染（物品/玩家/实体/字体/音效），且 Chromium 150MB 太重。WebGUI 仅作为架构参考。
- 通信走 **Minecraft 连接内的自定义 Payload**（Paper `sendPluginMessage` / Fabric Custom Payload），**不用 HTTP 同步游戏状态**。
- 协议核心：**单通道 + 命名空间**、握手/能力协商、**Session + Revision**、**Snapshot + Patch**、分片+压缩、服务端限流校验。
- **Vanilla fallback 必须保留**：无 mod 玩家走 Paper Dialog API（26.2 原生支持，已在用）。
- V1 范围严格限制（见 §9），达到验收即停止造框架，转去写 UNO/皮肤浏览器等业务。

---

## 1. 项目目标与范围

### 1.1 目标

```text
MineUI
├─ 服务器菜单        ├─ 商店
├─ UNO（替换现有背包 GUI）
├─ 皮肤浏览器（3D 预览）
├─ 玩家 Profile      ├─ 任务/成就
├─ 排行榜            ├─ 设置
└─ HUD（任务追踪、UNO 状态、计时器等）
```

能力目标（类比 MMORPG 界面）：任意布局、图片、自定义字体、圆角、阴影、渐变、动画、ScrollView、Grid、Flex 布局、Tooltip、Modal、Tabs、Dropdown、Text Input、Slider、3D 物品/玩家/实体、HUD Overlay、全屏 Screen。**不受 54 格箱子限制。**

### 1.2 V1 明确不做

```text
完整 CSS parser / HTML / JavaScript / Lua / Chromium
完整 DOM / React clone / 服务器下发可执行代码
复杂 Shader 编辑器 / 远程脚本系统
```

### 1.3 与现有项目的关系

- `customized_plugins/mineUNO`（现有，Maven）：UNO 插件 + PackHost。**短期不动**；MineUI 成熟后 MineUNO 切到 MineUI API（背包手牌 GUI → `UnoScreen`）。
- 服务器现有 Paper 插件栈（EssentialsX/Multiverse/TAB/Skript/SkinsRestorer 等）**不受影响**：MineUI 是增量插件。

---

## 2. 技术前提核查（已于本机实测）

| 项目 | 结论 | 证据/备注 |
|---|---|---|
| Paper 26.2 自定义 Payload | 可用 | `Player#sendPluginMessage("mineui:main", bytes)`；客户端→服务端走 `PluginMessageListener` |
| 客户端 mod 加载器 | **Fabric 26.2 可用** | Loader `0.19.5`；官方示例工程 `minecraft_version=26.2` |
| 映射（Mappings） | **注意：Yarn 已停更于 1.21.11** | 26.x 使用 **Mojang 官方映射**（示例工程已无 `yarn_mappings` 项）；旧教程会误导 |
| Fabric API | `0.160.0+26.2`（26.3 亦有 `0.160.6+26.3`） | 客户端 mod 需同时安装 Fabric Loader + Fabric API |
| Loom | `1.17-SNAPSHOT` | Gradle 多模块构建 |
| 单包大小上限 | 原版硬限制（clientbound ≈1 MiB，serverbound/代理转发常见 32 KiB 档） | 协议层**统一按 ≤32 KiB 分片**，大快照压缩+分片，超限拒绝 |
| 本机开发环境 | 需安装 `openjdk-25-jdk`；Gradle 用 wrapper（无系统 Gradle） | 服务器 2 vCPU 7.5G，能编译；**无 GUI，客户端测试必须在玩家 PC 上做** |
| 产物分发 | `:8123`（PackHost 静态目录）已对外可访问 | 构建出的 jar 放进去，玩家浏览器/启动器下载 |
| Vanilla fallback | Paper **Dialog API** 原生支持（文本/物品/输入框/滑块/按钮） | 已在服务器验证 Dialog/TAB/移动端可用 |

> 结论：用户的判断"Fabric 26.2 已有正式开发文档/网络 API"正确；**唯一需要修正的是 Yarn→Mojmap**，以及把包大小上限按 32 KiB 保守设计。

---

## 3. 总体架构

```text
Paper Server                          Fabric Client
┌──────────────────────────┐         ┌──────────────────────────┐
│ MineUI-Core Plugin       │         │ MineUI Client Mod        │
│  ├ UI Session Manager    │         │  ├ Protocol Decoder      │
│  ├ Protocol Encoder      │         │  ├ UI Runtime            │
│  └ API for business      │◄───────►│  │  ├ Widget Tree        │
│      plugins (UNO/皮肤…) │ Custom  │  │  ├ Layout Engine      │
│                          │ Payload │  │  ├ Animation          │
│ Business Plugins         │         │  │  ├ Input/Focus        │
│  ├ MineUNO               │         │  │  └ Asset Manager      │
│  ├ SkinBrowser           │         │  └ Render Backend        │
│  └ Shop / Quest / …      │         │     (GuiGraphics / 原生) │
└──────────────────────────┘         └──────────────────────────┘
```

**第一原则：Server owns state. Client owns presentation.**

- 服务端：游戏状态、权限、数据、业务逻辑、合法性校验。
- 客户端：渲染、布局、动画、输入、视觉。**永远不决定**金币/出牌/购买/权限结果。
- 客户端只发 `ACTION`；服务端验证（是不是本人、是不是他的牌、轮次、规则）后更新状态并下发。

---

## 4. 通信与协议

### 4.1 通道与命名空间

- 底层**只开一个通道**：`mineui:main`（Paper 侧 channel 字符串；Fabric 侧 `CustomPayload` 标识符）。
- 业务通过消息内 `namespace` 区分（`mineuno`、`skin`、`shop`…），**不要每业务开一个 channel**。

### 4.2 握手与能力协商

客户端加入后主动发 `HELLO`（服务端 2~3 秒内未收到 = Vanilla 客户端）：

```json
{ "protocol": 1, "modVersion": "0.1.0", "minecraft": "26.2",
  "capabilities": ["screen","hud","item_render","entity_render","player_render","animation_v1"] }
```

服务端回 `HELLO_ACK`：

```json
{ "protocol": 1, "serverVersion": "0.1.0", "minimumClient": "0.1.0" }
```

能力按功能位协商（`UI_SCHEMA_V1` / `ANIMATION_V1` / `PLAYER_3D` / `CUSTOM_FONT` / `REMOTE_IMAGE`），保证 0.1/0.2/0.3 客户端长期兼容。

### 4.3 会话（Session）与修订（Revision）

- 每次开界面：服务端生成 `sessionId`，`OPEN {session, app, view}`。
- 客户端 action 必须带 `session`；**不匹配直接丢弃**（防延迟旧包、界面已关）。
- session 内维护 `revision` 单调递增；action 带 revision，服务端已更新到更高版本则**拒绝并回发最新 state**（解决双击/延迟/不同步）。

### 4.4 Snapshot + Patch

- 首次打开：`SNAPSHOT`（完整 state）。
- 后续变更：只发 `PATCH`（JSON Patch 风格 `{"op":"replace","path":"/topCard","value":{...}}`）。
- **绝不出牌一次重发整个界面。**

### 4.5 数据包信封与限制

```text
MAGIC(2) | PROTOCOL_VERSION(1) | TYPE(1) | SESSION(4) | REVISION(4) | FLAGS(1) | LENGTH(4) | PAYLOAD
```

- 单包 **≤32 KiB**；超出分片（`FLAGS` 标记首/续/末片）并重组。
- 大快照先 **Deflate 压缩**（`FLAGS` 标记），仍超限则分片。
- 服务端硬限制：单包/单 session 速率/总字节数，防恶意客户端。

### 4.6 安全

- 服务端校验一切（action 合法性、限速、权限）；客户端输入不可信。
- 远程资源只允许服务端显式下发 `https://` URL + `sha256` + 尺寸白名单；**禁止 file://、jar:// 等协议**。

---

## 5. 服务端（Paper）设计

### 5.1 模块

```text
mineui-protocol   纯 Java，无 Paper/Minecraft 依赖（package 模型/常量/编解码接口）
mineui-paper      Paper 插件：会话管理、编解码、API、fallback 路由
```

### 5.2 业务插件 API（目标形态）

```java
UiSession session = mineUI.open(player, NamespacedKey.fromString("mineuno:game"));
session.state("hand", cards);          // Snapshot 字段
session.patch("activeColor", "RED");   // Patch
session.on("play_card", action -> {    // 事件
    int cardId = action.getInt("cardId");
    unoGame.playCard(player, cardId);
});
session.close();
```

业务插件**完全不需要知道** Fabric/Packet/ByteBuf 的存在。

### 5.3 会话生命周期

`open → snapshot → (action/patch)* → close`；玩家退出/切服/死亡等场景自动清理；会话超时回收。

### 5.4 Vanilla / Dialog Fallback

- 无 mod 玩家：同一业务 API 走 **Dialog API / 箱子菜单** 降级实现（能力协商决定路由）。
- 三档能力：`VANILLA`（Dialog/Inventory）、`MINEUI`（原生 UI）、`MINEUI_FULL`（原生 UI + 远程资源）。

---

## 6. 客户端（Fabric）设计

### 6.1 模块

```text
net/protocol     解码、会话、action 派发
ui/runtime       Node/Container/Style/Layout/Input/Focus/Animation/Binding
ui/render        RenderContext、SDF 圆角、裁剪栈、Z 序、字体
ui/widgets       基础与高级控件
assets           内置 UI JSON、贴图、字体、shader
cache            远程资源缓存（.minecraft/mineui/cache/）
debug            F8 调试面板、F9 热重载、Inspector（后期）
```

### 6.2 Widget 与布局

- 基础：`Box / Text / Image / Button / IconButton / ItemView / Row / Column / Stack / ScrollView / Grid / ProgressBar / Tooltip / Modal`。
- 二期：`TextField / Checkbox / Slider / Dropdown / Tabs / ListView / VirtualList / PlayerView / EntityView / ItemGrid / ColorPicker`。
- 布局：Flex 风格（`Row/Column/Stack/Grid/Absolute`），尺寸支持 `px / % / vw / vh / auto`，锚点 9 宫格（HUD 必需）。
- **所有布局用 Minecraft GUI 逻辑像素**，不做 framebuffer 物理像素，保证 GUI Scale 1/2/3 与 4K/1080p 一致。

### 6.3 渲染

```text
Pre → Measure → Layout → Background → Content → Children → Overlay → Tooltip
```

- `UiRenderContext` 封装 `GuiGraphics / mouseX,Y / partialTick / screen 尺寸`；控件禁止直接摸 `Minecraft.getInstance()`。
- 圆角/描边/渐变/阴影：**SDF shader 方案**（26.2 社区已有基于原版 GUI 管线的抗锯齿圆角示例），不堆 PNG。
- **裁剪栈必须一期实现**（`pushClip/popClip`，用于 ScrollView/Modal/圆角容器），控件不得各自 setScissor。
- `zIndex` 控制 Modal/Dropdown/Tooltip 层级。

### 6.4 输入与焦点

- 统一事件：`PointerEvent / KeyEvent / ScrollEvent`；**Capture → Target → Bubble**（类似 DOM），子控件可 `consume()`（如卡牌按钮阻止 ScrollView 滚动）。
- `FocusManager`：Tab/Shift+Tab/Enter/Esc/方向键（TextInput、Dropdown 必需）。

### 6.5 动画

- 客户端插值，**服务端绝不发坐标**。
- `AnimationController`：FLOAT/COLOR/POSITION/SIZE/OPACITY/ROTATION；缓动：LINEAR/EASE_IN/EASE_OUT/EASE_IN_OUT/BACK_OUT/SPRING。
- 例：卡牌 hover `scale 1→1.08, y 0→-8, 120ms`；出牌 `scale→1.2, 飞向中央, rotation→4°, opacity→0, ~300ms`，结束后按新 state 从树中移除。
- 出牌可用 **optimistic 动画**：点击立即抬起 → 收到 `ACTION_ACCEPTED` 再飞牌；`ACTION_REJECTED` 则抖动回位。

### 6.6 资源

- 内置：`assets/mineui/{textures,fonts,icons,shaders,ui}`，**UI 定义 JSON 随 mod 发布**。
- 远程资源：服务端显式下发（活动 Banner、商品图等），客户端下载 → 校验 sha256 → `DynamicTexture` → 本地缓存。

### 6.7 高级控件

- `ItemView`：原生 ItemStack 渲染（附魔光效/CustomModelData/ItemModel 天然正确）。
- `PlayerView`：离屏/伪实体渲染玩家模型，支持 `rotationX/Y、scale、pose` —— **皮肤浏览器 3D 预览的关键件**。
- `EntityView`：同样用假渲染实体，不真的 spawn。

---

## 7. UI 定义与绑定

- UI 布局 JSON **随 mod/资源包发布**（`assets/mineui/ui/<app>/<view>.json`），服务端只发 `state`；**不允许服务端下发任意 UI/代码**（安全 + 省流量 + 动画不受网络限制）。
- 数据绑定：`"text": "{state.currentPlayer.name}"`、`"visible": "{state.isMyTurn}"`。
- Action 绑定：`"action": {"type":"server","id":"draw_card"}` → UI Runtime → ActionDispatcher → 协议 → Paper → 业务校验。
- V1 不做脚本引擎（JS/Lua/Kotlin），保持声明式 JSON。

---

## 8. 开发环境与工作流（本机）

```text
customized_plugins/customized_GUI_API/
├── settings.gradle.kts / build.gradle.kts      # Gradle 多模块（client 必须 Loom）
├── mineui-protocol/     # 纯 Java
├── mineui-paper/        # Paper 插件（构建后放服务器 plugins/）
├── mineui-client/       # Fabric mod（Loom，Mojmap）
├── mineuno-*/           # 后续业务模块（common/paper/client）
└── run/                 # 本地跑服/调试
```

- 服务器装 `openjdk-25-jdk`；构建用 `./gradlew`（wrapper 自动下载）。
- **客户端测试在玩家 PC**：我们构建 jar → 上传到 `:8123` 静态目录 → 玩家下载安装（Fabric Loader + Fabric API + MineUI）。
- 服务端改动：`stop → 替换 plugins/mineui.jar → ./start.sh`；测试期建议开独立 tmux 会话跑实例，避免影响现有服。
- 抓包/调试：协议 JSON 阶段可直接看日志；客户端 F8 调试面板显示 session/revision/包速率。

---

## 9. 路线图与 V1 验收

| Phase | 内容 | 产出 |
|---|---|---|
| 0 | Gradle 多模块骨架 + Paper 插件 + Fabric mod | 进服握手成功，控制台输出 `Alice MineUI 0.1.0 connected` |
| 1 | OPEN/CLOSE/ACTION/STATE 最小闭环 | 黑底 + 文本 + 按钮的 Screen |
| 2 | UI Core：Node/Container/Row/Column/Stack/Text/Button/Image/Layout/Input | 可排版界面 |
| 3 | 视觉：圆角/阴影/渐变/裁剪、Hover/Transition/Animation | 好看、会动 |
| 4 | ScrollView/Grid/Modal/Tooltip | **可开始写 UNO** |
| 5 | MineUNO GUI（2~8 人真实联机验证） | UNO 可玩 |
| 6 | ItemView/PlayerView/EntityView | 皮肤浏览器 / 商店 / Profile |
| 7 | JSON UI + 数据绑定 + 热重载 + Inspector | 真正的框架 |
| 8 | 远程资源 / CDN / Web API | 动态内容 |

**V1 Definition of Done**：连接（握手/版本/能力）、协议（OPEN/CLOSE/SNAPSHOT/PATCH/ACTION + session/revision）、UI（§6.2 基础控件 + ScrollView/Grid/Modal/Tooltip）、渲染（圆角/描边/阴影/渐变/贴图/物品/裁剪/Z 序）、动画（透明度/位置/缩放/旋转/颜色）、输入（鼠标/滚轮/键盘 + 焦点）、开发（热重载 + F8 调试）。
**达到即冻结框架**，转做业务。

### 禁止清单（防止过度设计）

```text
完整 CSS parser / JavaScript 引擎 / HTML parser / Chromium / 完整 DOM / Lua
服务器动态下载可执行代码 / 复杂 shader 编辑器 / 完整 React clone
```

---

## 10. 风险与对策

| 风险 | 说明 | 对策 |
|---|---|---|
| 客户端 mod 分发 | 每个玩家要装 Fabric + API + MineUI | Vanilla fallback（Dialog）保底；提供一键包/教程；版本号 + 更新检查 |
| MC 版本跟进 | 大版本升级需跟 Minecraft/Fabric API | protocol 独立模块 + 能力协商；客户端版本宽松兼容 |
| Yarn 停更带来的资料误导 | 网上 26.x 教程可能缺失/错误 | 统一用官方示例工程（Mojmap）+ 官方文档 |
| 包大小限制 | 单包硬上限 | ≤32 KiB 分片 + Deflate + 限流 |
| 作弊/伪造包 | 客户端可伪造 action | 服务端全量校验；rate limit；session/revision |
| 与现有服并存 | 不能让实验代码影响线上 | 独立测试实例；插件级隔离（MineUI 失败不影响其他插件） |
| 自研成本 | 框架容易无限膨胀 | V1 DoD + 禁止清单 + Phase 门禁 |

---

## 11. 参考项目与法律注意

| 项目 | 用途 | 注意 |
|---|---|---|
| **WebGUI**（MIT，支持 26.2） | 参考"客户端↔服务端 bridge / HUD / Screen 生命周期 / token" | 仅参考架构，不依赖 Chromium |
| **owo-ui / owo-lib**（开源） | 参考声明式 UI/布局/动画设计 | 学习设计，不硬依赖 |
| **FancyMenu**（26.2 已移植） | 参考 GUI 生命周期/字体/模糊/PiP | 许可证非宽松，**读思路不抄代码** |
| **YACL** | 将来做 MineUI 自己的"设置界面" | 只用于配置 UI，不做业务界面 |
| **ClientBridge** | Paper↔Fabric 桥接的现成案例 | 参考桥接方式 |
| **VexView** | 闭源、停更（≤1.16） | **不要反编译取源码**；可借鉴"YAML 定义界面+服务端下发"的思路 |
| **Polymer** | 目标相反（服务端兼容原版） | 不采用 |

---

## 附录 A：消息一览（V1）

| 类型 | 方向 | 用途 |
|---|---|---|
| `HELLO` / `HELLO_ACK` | C→S / S→C | 握手、版本、能力 |
| `OPEN` / `CLOSE` | S→C / 双向 | 打开/关闭界面（带 session/app/view） |
| `SNAPSHOT` | S→C | 完整状态 |
| `PATCH` | S→C | 增量状态（revision） |
| `ACTION` | C→S | 用户操作（session/revision/action/payload） |
| `ACTION_ACCEPTED` / `ACTION_REJECTED` | S→C | 操作反馈（用于乐观动画） |
| `ASSET_MANIFEST` | S→C | 远程资源清单（URL+sha256+尺寸） |
| `PING` / `PONG` | 双向 | 延迟/保活（调试面板） |

## 附录 B：与原始设计稿的差异

1. **修正**：Yarn 26.x 不存在 → 用 Mojang 映射；Fabric API 用 `0.160.0+26.2`。
2. **修正**：包大小按 ≤32 KiB 分片设计（原稿写 64 KB）。
3. **补充**：本机开发/分发/测试工作流（服务器无 GUI、产物走 :8123 分发）。
4. **收敛**：V1 范围、DoD、禁止清单集中为验收门槛，防止框架无限扩张。
