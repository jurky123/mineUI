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

## 玩家安装

需要 **Minecraft 26.2**（Java 版）+ **Fabric Loader** + **Fabric API**。

1. 下载最新的 MineUI 客户端安装包（含安装说明）
2. 按说明安装 Fabric，把 `mineui-client` 与 `fabric-api` 放入 `mods/` 目录
3. 使用 Fabric 配置启动游戏，进入服务器

进服后聊天栏出现 `[MineUI] 已连接服务端` 即安装成功。没有安装也能正常游玩，业务插件会回退到原版界面。

---

## 服务器管理

| 命令 | 说明 |
|---|---|
| `/mineui status` | 查看自己的客户端状态与在线 MineUI 玩家 |
| `/mineui test` | 打开测试界面（验证客户端是否生效） |
| `/mineui close` | 关闭当前 MineUI 界面 |

---

## 当前进度

- ✅ 通信与版本协商、会话（session/revision）、限流与安全校验
- ✅ 界面定义：mod 内置 / 开发目录覆盖 / 业务插件随 OPEN 下发（客户端严格校验）
- ✅ 布局引擎、基础与 3D 控件、视觉、动画、滚动/网格/弹窗/Tooltip、显隐与数据绑定
- ✅ 点击与悬浮动作、输入框、F9 热重载 / F10 导出
- ✅ 业务 API `com.mineui.api`（含 `server_ui` 能力协商与 owner 生命周期）
- ⏳ HUD Overlay、标签页/下拉框/滑块等扩展控件
- ⏳ UNO 等业务界面（由业务插件实现）

---

## 开发

技术设计文档见 [`docs/DESIGN.md`](docs/DESIGN.md)。
