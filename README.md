# MineUI

> Paper 26.2 + Fabric 26.2 · 服务器自定义界面框架

MineUI 让服务器拥有不受原版限制的自定义界面：菜单、UNO 卡牌、皮肤浏览器、商店、HUD 等，
全部使用 Minecraft 原生渲染，支持动画、滚动、3D 预览与实时状态更新。

界面页面由业务插件随界面会话下发（声明式 JSON，客户端严格校验、不可执行代码），
客户端 mod 是可选增强：没装的玩家由业务插件回退到原版界面。

---

## 功能

### 控件与布局
- **布局**：Row / Column / Stack / Grid / Scroll，尺寸支持 px / % / vw / vh / auto，对齐与间距
- **基础控件**：文本（缩放/绑定）、按钮、色块、图片、滚动列表、网格、输入框
- **3D 预览**：物品（含 CustomModelData 卡面）、生物实体、玩家（在线皮肤 / 任意皮肤 value+signature）
- **窗口**：模态弹窗、Tooltip、z 序与裁剪、任意节点可点击 / 可悬浮

### 视觉与交互
- 圆角、描边、纵向渐变、软阴影、透明度继承、悬停过渡、点击/状态脉冲、6 种缓动
- 任意节点绑定点击 `action` 与悬浮 `hoverAction`（悬浮预览、列表联动等）
- 输入框：聚焦输入、光标与滚动、回车提交 `{"text": ...}`、Esc 失焦
- 数据绑定：`{state.xxx}` 文本插值、`visible` 状态控制显隐、PATCH 增量更新

### 开发体验
- F9 热重载 JSON、F10 把当前页面导出到本地开发目录（含服务端下发的页面）
- `/mineui status | test | close` 管理命令
- 业务 API `com.mineui.api`：会话 open / state / snapshot / on(action) / close，含 owner 生命周期

---

## 业务插件接入（简述）

```java
MineUi mineUi = MineUiProvider.get();
if (mineUi != null && mineUi.supportsServerUi(player)) {          // 旧客户端会回退
    JsonObject ui = loadResource("assets/<plugin>/ui/skin/browser.json");
    MineUiSession session = mineUi.open(this, player, "skin", "browser", ui);
    session.state("title", "皮肤浏览器");
    session.on("search", action -> session.state("query", action.string("text", "")));
    session.snapshot();
}
```

- 客户端支持情况用 `hasClient` / `capabilities` / `supportsServerUi` 判断，不支持时回退原版界面
- 页面 JSON 建议 ≤24 KiB；节点与字段规范见 [`docs/DESIGN.md`](docs/DESIGN.md)

---

## 原版风格模板

界面可直接套用原版控件贴图（按钮 / 输入框 / 滑块 / 滚动条 / 面板），业务插件只写语义与布局：

```json
{ "type": "button", "skin": "vanilla:button", "text": "确定", "action": "ok" }
{ "type": "input",  "skin": "vanilla:input",  "placeholder": "搜索…", "action": "search" }
{ "type": "slider", "skin": "vanilla:slider", "min": 0, "max": 100,
  "value": "{state.volume}", "text": "音量: {value}", "action": "volume_set" }
{ "type": "column", "skin": "vanilla:panel",  "children": [ ] }
```

- 内置皮肤：`vanilla:button` / `vanilla:input` / `vanilla:slider` / `vanilla:panel` / `vanilla:dialog` / `vanilla:slot`；
  写 `"skin": "vanilla"` 时按节点类型自动选择
- 皮肤只补默认值，JSON 里显式写的尺寸/颜色优先
- 滑块拖动结束提交 `{"value": <数值>}`（服务端用 `action.number("value", 0)` 取值）
- 内置完整示例页 `mineui/vanilla`，管理员可用 `/mineui style` 打开预览

---

## 玩家安装

需要 **Minecraft 26.2**（Java 版）+ **Fabric Loader** + **Fabric API**。

1. 下载最新的 MineUI 客户端安装包（含安装说明）
2. 按说明安装 Fabric，把 `mineui-client`、`mineui-client-api` 与 `fabric-api` 放入 `mods/` 目录
3. 使用 Fabric 配置启动游戏，进入服务器

进服后聊天栏出现 `[MineUI] 已连接服务端` 即安装成功。没有安装也能正常游玩，业务插件会回退到原版界面。

---

## 服务器管理

| 命令 | 说明 |
|---|---|
| `/mineui status` | 查看自己的客户端状态与在线 MineUI 玩家 |
| `/mineui test` | 打开测试界面（验证客户端是否生效） |
| `/mineui style` | 打开原版风格模板预览（面板/按钮/输入框/滑块/滚动/弹窗） |
| `/mineui gallery` | 打开组件画廊（物品/头颅装饰、悬浮动效、时间轮换、像素素材） |
| `/mineui hud` | 打开 HUD 演示（服务端声明布局；F6 本地开关） |
| `/mineui lyrics` | 打开列表演示（歌词高亮 + 自动居中 + 滚轮） |
| `/mineui toast` | 发送 Toast 短提示（打开界面后点击可触发全局动作） |
| `/mineui keybind` | 声明通用键位（槽位 1，默认 F7）→ 按下打开歌词页 |
| `/mineui close` | 关闭当前 MineUI 界面 |

---

## 当前进度

- ✅ 通信与版本协商、会话（session/revision）、限流与安全校验
- ✅ 界面定义：mod 内置 / 开发目录覆盖 / 业务插件随 OPEN 下发（客户端严格校验）
- ✅ 布局引擎、基础与 3D 控件、视觉、动画、滚动/网格/弹窗/Tooltip、显隐与数据绑定
- ✅ 点击与悬浮动作、输入框、滑块、F9 热重载 / F10 导出
- ✅ 原版风格模板（按钮/输入框/滑块/滚动条/面板/弹窗，像素级对齐原版贴图）
- ✅ 装饰与动效（物品/头颅/精灵/自绘像素素材、悬浮缩放与换图案、时间轮换）
- ✅ 动态贴图：`image.texture` / `item.item` 支持状态绑定，换图不重开界面（棋盘/棋子等）
- ✅ 业务 API `com.mineui.api`（含 `server_ui` / `hud_v2` 能力协商与 owner 生命周期）
- ✅ HUD：服务端声明布局（锚点/偏移/缩放）、与屏幕并存、客户端本地偏好（F6 开关，`config/mineui/client.json`）
- ✅ 进度条：`progress` 组件（方向/渐变/时间文本）与低频更新客户端插值；滑块步进/禁用态
- ✅ 远程图片：`image.url` 支持状态绑定；HTTPS 直连 + 服务端域名白名单 + 私网拦截 + 内存/磁盘缓存 + 占位降级；
  网易云封面（JPEG）实测通过，失败有原因提示与超时保护
- ✅ 本地图片：`image.texture` 支持 `{local.<ns>.<key>}`（客户端本地来源，不走网络/白名单）；
  业务 mod 经 `mineui-client-api` 提供字节，能力位 `local_image`
- ✅ 列表：`list` 节点（items 绑定 + 任意 itemTemplate + 高亮行自动居中 + 滚轮）；`/mineui lyrics` 演示
- ✅ Toast：服务端短提示（图标/时长）+ 界面内点击回传全局动作；`MineUi.onAction`；`/mineui toast` 演示
- ✅ 键位：客户端通用键位池（`key.mineui.slot1..8`，改键走原版）+ 服务端 `keybind` 声明 + `{key.<action>}` 页面提示
- ✅ 本地状态/动作扩展点：`mineui-client-api`（业务 mod 注册 `{local.<ns>.<key>}` 与 `local:<ns>.<action>`，
  逐帧读取 + 本地直连，能力位 `local_state` / `local_action`）
- ⏳ 标签页/下拉框等扩展控件
- ⏳ UNO 等业务界面（由业务插件实现）

---

## 开发

- 组件与素材指南（装饰/交互/动效，含示例）：[`docs/COMPONENTS.md`](docs/COMPONENTS.md)
- 音乐 UI 能力需求与阶段状态：[`docs/REQUIREMENTS_AUDIO_UI.md`](docs/REQUIREMENTS_AUDIO_UI.md)
- 客户端本地扩展点（业务 mod 接入）：[`docs/CLIENT_EXTENSIONS.md`](docs/CLIENT_EXTENSIONS.md)
- 技术设计文档：[`docs/DESIGN.md`](docs/DESIGN.md)
