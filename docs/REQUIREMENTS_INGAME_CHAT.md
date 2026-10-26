# MineAgent 游戏内 Chat 界面需求（MineUI）

> 提出方：MineAgent（Paper 插件）
> 状态：需求评审（2026-10-26）
> 目标：MineUI 在现有声明式 JSON / `state` / action 能力上，支持一个类似 DSH 网页聊天的游戏内界面：左侧导航、消息流、底部输入框、工具/审批卡、公开全服聊天。

---

## 1. 背景

MineAgent 的 `/agent` 目前使用 MinUI 的单列 `column + list + input`，看起来像“功能面板”，不像 DSH：

- 没有左侧导航/会话区；
- 消息被压平成单行文本，没有用户/助手/工具/审批的角色区分；
- 没有底部固定 composer 的清晰层级；
- 长回答只能全部展开，不能“摘要 + 展开全文”；
- 流式回复无法只更新最后一条卡片而不重建列表。

MineAgent 已决定：

- 游戏内 Chat **全服公开**（不是私聊），但界面要像 DSH；
- 身体控制直接放宽，聊天/身体状态在同一界面；
- 原版 Dialog 继续作为无 MineUI 客户端的回退。

---

## 2. 必须能力（P0）

### 2.1 两栏布局

- 根节点支持 `row`：左栏固定/百分比宽度，右栏占满剩余空间。
- 左栏包含：标题、状态、导航按钮（全服对话 / AI 玩家 / 审批 / 帮助）、关闭。
- 右栏包含：header、消息流、底部 composer。
- 尺寸建议：左栏 22–26%，右栏 74–78%；在 1280×720、GUI scale 2/3 下不横向溢出。

### 2.2 消息卡片

- `list` 的 `itemTemplate` 支持**每个条目不同高度**（当前实现已按 item 自然高度布局，请确认并文档化）。
- 每个 item 可以是：
  - `column` 卡片：作者 + 多行正文 + 可选操作；
  - 用户/助手/系统/工具/审批用 `visible` 绑定不同变体；
  - 卡片 `background`/`radius`/`padding` 由 item 字段或固定变体决定。
- 单条消息需要 **至少 8 行**正文（服务端按 50 列预换行），超过时提供“展开全文”动作或详情页。
- 列表滚动到底部/高亮最后一条时，能保持合理位置；不要每次 state 更新都强制跳回顶部。

### 2.3 多行文本（关键缺口）

当前 `text` 是单行，MineAgent 只能把回答切成多个 `text` 节点。需要以下任一方案：

**方案 A（推荐，最小）**：`text` 增加：

```json
{
  "type": "text",
  "text": "{state.reply}",
  "wrap": true,
  "maxLines": 8,
  "lineHeight": 10
}
```

- `wrap:true` 时按节点宽度自动换行，节点高度按行数增加；
- `maxLines` 超出时截断并显示省略号/由业务提供“展开”；
- 保留现有单行行为（默认 `wrap:false`），不破坏旧页面；
- state 更新时高度变化要触发布局，不能闪烁/跳位。

**方案 B**：新增 `chatMessage` 组合节点：`author`、`role`、`text`、`wrap`、`maxLines`、`background`、`radius`、`actions`。

**方案 C**：`list` 的 itemTemplate 支持 `{item.lines}` 数组迭代，自动生成多行（语义清晰，但实现可能更大）。

> 如果 MineUI 不做多行，MineAgent v1 仍可用 8 个固定 line 槽 + `visible` 实现，但长回答需要“展开”页，体验差一档。

### 2.4 流式更新

- 需要能在不完全重建页面的情况下更新最后一条消息：
  - 现有 `session.state()` / `batch()` 足够；
  - `text` 节点绑定 `{state.streamText}`，高度/宽度按内容变化；
  - 列表不要因某个 item 文本变化而重建所有 item（或至少保持滚动位置）。
- MineAgent 会以 100–200ms 合并 delta；MineUI 不需要逐 token 支持。

### 2.5 底部 composer

- `input` + 提交动作（现有 `send`）已满足；
- 发送按钮最好能“取当前输入并提交”，若不能，回车发送即可；
- 运行中需要“停止”按钮（独立 action）；
- 输入框固定在底部，不随消息流滚动；窗口高度变化时 composer 保持可见。

### 2.6 角色/状态样式

- 用户/助手/系统/工具/审批/错误可用不同 `background`/`border`/文字颜色；
- 工具卡建议小字：`工具名 · 状态 · 耗时`，可展开；
- 审批卡需要按钮（批准/拒绝）与剩余时间；
- 状态（思考/工具/流式/错误）在 header 或消息流底部可见。

---

## 3. 建议能力（P1）

- `list` 支持 `stickToBottom` / `followLatest` 状态：新消息时保持底部，用户主动上滚后暂停跟随；
- `scroll` 支持“回到底部”按钮或程序化滚动动作；
- `input` 支持 `onSubmit` 状态/禁用态；
- 卡片支持 `border`/`shadow`/`gradient` 的状态绑定（当前主要是静态样式）；
- 小屏/大 GUI scale 的紧凑模式（隐藏左栏、从顶部按钮打开导航）；
- 键位：`keybind` 打开/关闭 Chat（MineAgent 侧会声明，MineUI 已有能力）。

---

## 4. 兼容与验收

- 不破坏现有页面：新增字段默认关闭/保持旧行为；
- 旧客户端/旧 server_ui：MineAgent 回退到当前单列/原版 Dialog；
- 页面建议 ≤24 KiB；MineAgent 会限制最近消息数；
- 验收：
  1. 两栏页面在 1280×720、1920×1080、GUI scale 2/3 下无横向溢出；
  2. 8 行消息卡片正常布局，长文本可展开/滚动；
  3. state 更新最后一条文本时，列表不整体重建、滚动位置基本稳定；
  4. composer 始终在底部可见，输入/回车/停止动作可用；
  5. 无 MineUI 客户端时 MineAgent 使用原版 Dialog，不报错。

---

## 5. 非目标

- 不要求 MineUI 解析 Markdown/HTML/图片消息；
- 不要求客户端 mod 新增聊天网络协议；
- 不要求 MineUI 负责会话存储/权限/审批；这些仍在 MineAgent 后端。

---

## 6. 交付边界

- 本文件是 MineAgent 对 MineUI 的需求，不修改 MineUI 代码；
- MineAgent 的 v1 实现可在当前 `text` 单行能力下用固定 8 行槽 + 展开详情完成；
- 若 MineUI 实现多行 `text.wrap`，MineAgent 会优先切换到该能力并保留回退。
