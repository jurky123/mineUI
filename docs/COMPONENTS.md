# MineUI 组件与素材指南

> 面向业务插件开发者：如何用声明式 JSON 做装饰、交互与动效。
> 在线预览：管理员可用 `/mineui gallery`（组件画廊）与 `/mineui style`（原版风格模板）打开实机示例。

---

## 0. 画廊速览（`/mineui gallery`）

```text
┌──────────────────────── MineUI 组件画廊 ────────────────────────┐
│  物品与头颅装饰                                                  │
│  [钻石][绿宝石][下界之星][金苹果][骷髅][僵尸][苦力怕][龙首][猪灵] │
│                                                                  │
│  装饰按钮：悬浮放大 / 缩小 / 换图案                               │
│  [骷髅→(悬浮)凋灵骷髅头] [钻石→(悬浮)绿宝石] [海之心] [像素爱心]  │
│  点击次数: 0                                                     │
│                                                                  │
│  随时间轮换（默认每秒）                                          │
│  [色块每秒变色]  [羊毛每秒换色]  [轮换文字]                       │
│                                                                  │
│  原版精灵与自绘像素素材                                          │
│  [原版槽位精灵] [自绘星星] [自绘爱心] [内置 Logo]                 │
│  [槽位网格 4x2]                                                  │
│                                                                  │
│  按钮与弹窗                                                      │
│  [悬浮放大按钮] [打开弹窗]                                        │
└──────────────────────────────────────────────────────────────────┘
```

对应 JSON：`mineui-client/src/main/resources/assets/mineui/ui/mineui/gallery.json`。

---

## 1. 节点类型一览

| type | 用途 | 关键字段 |
|---|---|---|
| `column` / `row` / `stack` | 布局容器（纵/横/叠加） | `gap`、`padding`、`align`、`justify` |
| `grid` | 网格 | `columns`、`gap`、`rowGap` |
| `scroll` | 滚动视口 | `height`、子节点自然高度 |
| `text` | 文本（支持绑定/缩放/轮换色） | `text`、`color`、`scale`、`cycle` |
| `button` | 按钮 | `text`、`action`、`hoverScale` |
| `box` | 色块/圆角/渐变/轮换背景 | `background`、`radius`、`cycle` |
| `image` | 图片或原版精灵 | `texture`（`sprite:` 前缀 = 九宫格精灵） |
| `item` | 物品/头颅装饰，可作按钮 | `item`、`count`、`model`、`scale`、`hoverItem`、`cycle.items` |
| `entity` | 生物 3D 预览 | `entity`、`scale`、`followMouse` |
| `player` | 玩家 3D 预览（皮肤/缩放） | `player`、`skin`、`zoomable`、`zoomMin/zoomMax` |
| `input` | 文本输入 | `placeholder`、`maxLength`、`action` |
| `slider` | 滑块 | `min`、`max`、`value`、`text`、`action` |

通用字段（所有节点）：`width/height`（px / `%` / `vw` / `vh` / auto）、`padding`、`background`、`radius`、`border`、`shadow`、`gradient`、`sprite/spriteHover/spriteFocus`、`z`、`clip`、`visible`（状态绑定）、`tooltip`、`modal`、`action`（任意节点可点击）、`hoverAction`（悬浮动作）、`hoverScale`、`cycle`。

样式皮肤：`"skin": "vanilla:button | input | slider | panel | dialog | slot"`（写 `"skin": "vanilla"` 按节点类型自动选择）；
HUD/浮层可用 `"skin": "mineui:glass | glass_dense"`（半透明圆角、无边框，无需素材），无前缀简写 `"skin": "glass"`。

---

## 2. 装饰：物品、头颅与精灵

```json
// 物品与生物头颅（直接引用原版物品，自动按原版模型渲染）
{ "type": "item", "item": "minecraft:diamond", "scale": 1.4, "itemTooltip": true }
{ "type": "item", "item": "minecraft:dragon_head", "scale": 1.4 }

// 原版九宫格精灵（slot_frame / button / text_field / scroller …）
{ "type": "image", "texture": "sprite:minecraft:widget/slot_frame", "width": 20, "height": 20 }

// 自绘像素素材（随 mod 发布在 assets/mineui/textures/gui/）
{ "type": "image", "texture": "mineui:textures/gui/icon_star.png", "width": 32, "height": 32 }
```

说明：
- 直连 PNG 默认**显示整张图**（框架按贴图真实尺寸采样，无需写 `textureSize`）；
  图集/雪碧图才需要 `"textureSize": [宽, 高]` 配合 `u`/`v`/`regionWidth`/`regionHeight` 选子区域。
- 物品包含 `minecraft:*_head` 生物头颅（骷髅/僵尸/苦力怕/龙首/猪灵等），无需额外素材。
- `itemTooltip: true` 悬停显示原版物品名；也可用 `tooltip` 写自定义提示。
- 自定义贴图放在**业务插件自己的资源包命名空间**里（如 `mineskin:textures/gui/xxx.png`），用 `"texture": "mineskin:textures/gui/xxx.png"` 引用；随服务器资源包下发即可。

---

## 2.5 动态贴图（状态绑定，不清屏不重开）

`image.texture` 与 `item.item` 支持状态绑定，服务端改 state 即换图（棋子、高亮、选中框等），
**不需要重开界面、鼠标位置不变**；解析结果按状态代数缓存，只在 state 变化时解析一次。

```json
// 棋盘格：每格一个棋子图 + 一个高亮图，data 里直接放资源路径
{ "type": "image", "texture": "{state.sq_e4}", "width": 64, "height": 64 }
{ "type": "image", "texture": "{state.hl_e4}", "width": 64, "height": 64 }

// 原版精灵也支持绑定
{ "type": "image", "texture": "sprite:{state.icon}" }

// 用物品模型当棋子（item 与 modelStrings 均可绑定）
{ "type": "item", "item": "minecraft:paper", "modelStrings": ["{state.piece_e4}"] }
```

- 值为空或非法 Identifier 时**安全跳过渲染**（不报错、不崩溃），适合"空格子"
- 绑定只在状态变化（generation）时解析一次并缓存，稳定帧零额外开销
- 旧客户端遇到模板字符串（含 `{`）会因非法 Identifier 而跳过，不会崩

---

## 3. 交互：装饰即按钮

任意节点写 `action` 即为按钮；`hoverScale` 控制悬浮缩放（>1 放大，<1 缩小），`hoverItem` 让物品节点悬浮时换成另一个图案：

```json
{ "type": "item", "item": "minecraft:skeleton_skull", "scale": 1.6,
  "action": "decor_click",
  "hoverScale": 1.3,
  "hoverItem": "minecraft:wither_skeleton_skull",
  "tooltip": "悬浮变大并换成凋灵骷髅头" }
```

服务端：

```java
session.on("decor_click", action -> session.state("clicks", session.getInt("clicks", 0) + 1));
```

---

## 4. 动效：随时间轮换（纯客户端）

`cycle` 由本地时间驱动，不占网络、不受服务端限速：

```json
// 背景色每秒轮换
{ "type": "box", "width": 28, "height": 28, "radius": 4,
  "cycle": { "interval": 1.0, "colors": ["#E04040", "#E0A000", "#40C040", "#40A0E0"] } }

// 羊毛每秒换色（物品轮换）
{ "type": "item", "scale": 1.7,
  "cycle": { "interval": 1.0, "items": ["minecraft:red_wool", "minecraft:lime_wool", "minecraft:blue_wool"] } }

// 文字颜色轮换
{ "type": "text", "text": "轮换文字", "scale": 2.0,
  "cycle": { "interval": 0.5, "colors": ["#FF5555", "#55FF55", "#55FFFF"] } }
```

- `interval` 单位秒（最小 0.05）
- `colors` 用于背景/文字色，`items` 用于物品节点；两者可同时写（各自生效）
- 界面每次打开都从 0 开始计时/状态复位

---

## 4.2 进度条与客户端插值

```json
{
  "type": "progress", "width": 160, "height": 14, "radius": 4,
  "background": "#40000000",
  "color": "#3FA9F5", "fillGradient": ["#3FA9F5", "#8A5FD0"],
  "value": "{state.position}", "min": 0, "max": 180,
  "playing": "{state.playing}", "interpolate": true,
  "text": "{value} / {max}", "timeFormat": "mm:ss", "textColor": "#D0E0F0"
}
```

- `direction`：`left_right`(默认) / `right_left` / `top_bottom` / `bottom_top`
- `interpolate`：服务端 1Hz 更新即可，客户端按本地时钟外推；`playing` 为 false 时暂停外推
- `rate`：显式速率（单位/秒）；缺省时由相邻两次样本自动推算
- `snap`：跳变超过该值视为 seek，立即对齐（默认 3）；外推超过 5s 无新样本自动停止
- 文本占位：`{value}` / `{max}` / `{percent}`（`timeFormat: mm:ss | hh:mm:ss` 时格式化为时间），另支持 `{state.x}`
- 滑块增强：`step` 步进吸附；`enabled`（支持 `{state.x}` 绑定）禁用后不响应拖动、灰度显示

---

## 4.5 HUD（常驻叠加层）

HUD 与屏幕使用同一套 JSON 节点，不占屏幕、可与屏幕并存；服务端声明布局，客户端本地可覆盖。

```java
// 服务端（业务插件）
UiSession hud = mineUi.openHud(this, player, "example", "nowplaying", definition,
        new HudLayout("top_right", 6f, 6f, 1f));   // anchor / offsetX / offsetY / scale
hud.state("title", "正在播放");
hud.snapshot();
```

- 锚点：`top_left / top_center / top_right / center_left / center / center_right / bottom_left / bottom_center / bottom_right`
- 客户端偏好：`config/mineui/client.json`（`hudEnabled` 总开关、`hudHideOnScreen` 开界面自动隐藏、逐 `app/view` 覆盖 anchor/offset/scale/开关）
- 无边框半透明：HUD 推荐 `"skin": "mineui:glass"`（半透明圆角、无边框，程序化绘制**无需新素材**）；
  更实的底可用 `mineui:glass_dense`，也可自行写 `"background": "#8A0E141B", "radius": 6`
- 游戏内：**F6** 开关 HUD（可在原版按键设置改键）；未安装 mod 的玩家不受影响
- 能力位：`hud_v2`（业务用 `MineUi.supportsHud(player)` 判断，旧客户端自动降级）
- 生命周期：owner 停用 / 玩家退出 / 断线自动清理；HUD 与屏幕互不干扰
- 安全区建议：默认 `top_right`/`top_left` + 小偏移避开 hotbar；底部锚点请留足 `offsetY`
- 本阶段 HUD 为显示型（不响应点击），交互型 HUD 在后续阶段

---

## 5. 3D 预览

```json
// 物品（可换图案、可轮换、可点击）
{ "type": "item", "item": "minecraft:paper", "model": 12345, "scale": 2.0 }

// 生物
{ "type": "entity", "entity": "minecraft:zombie", "width": 56, "height": 72, "scale": 26 }

// 玩家：在线玩家 / 自己 / 任意皮肤，本地滚轮缩放
{ "type": "player", "player": "@self", "zoomable": true, "zoomMin": 0.5, "zoomMax": 2.0 }
{ "type": "player", "skin": { "value": "{state.preview.value}", "signature": "{state.preview.signature}" },
  "zoomable": true }
```

---

## 6. 素材与授权

- **原版素材**：通过标识符引用（`minecraft:...`），不随插件分发，安全。
- **自绘素材**：`assets/mineui/textures/gui/` 下的 `logo.png` / `icon_star.png` / `icon_heart.png` 为本项目自绘示例，可自由参考。
- **第三方素材**（网上二次创作的 MC 风格 UI/图标）：
  - 使用前确认许可证（CC0/CC-BY/MIT 等）并保留署名；
  - 不要直接打包来源不明的素材；
  - 建议只放进**服务器资源包**下发，而不是随插件 jar 分发，便于替换与追责。

---

## 7. 本地调试

- `F9`：热重载当前页面 JSON（无需重启游戏）
- `F10`：把当前页面（含服务端下发的）导出到 `config/mineui/ui/<app>/<view>.json` 作为本地副本
- 开发目录覆盖优先级：`config/mineui/ui/...` > 服务端下发 > mod 内置
