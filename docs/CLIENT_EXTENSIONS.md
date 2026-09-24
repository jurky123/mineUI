# 客户端本地扩展点（状态绑定 + 动作直连）

> 需求来源：MineAudio（2026-09-19），要求做成**通用能力**。
> 状态：已实现（MineUI 0.12.0）。配套客户端 mod：`mineui-client-api`。

## 1. 背景

MineUI 页面是服务端下发的声明式 JSON，状态默认来自服务端（`{state.x}`）与 1Hz 更新。
但播放位置、拖动预览、本地暂停等必须由客户端本地主线程逐帧处理，经 Paper 中转会有延迟与
网络开销。本能力提供两条通路，让页面**直接读本地状态、直接派发本地动作**，且 MineUI 内
不出现任何业务概念。

## 2. 组成

### 2.1 `mineui-client-api`（独立 artifact/mod）

- 纯 Java 接口 + 注册表，**无 MC 依赖、无入口点**（`fabric.mod.json` 仅 id/version）
- 随客户端包分发；MineUI 客户端与业务客户端都 `compileOnly` 依赖它，
  运行时不 `include`（避免双份同 id 冲突）
- MineUI 不在时注册表只是没人消费，业务客户端正常加载（惰性、无副作用）
- Maven 坐标：`com.mineui:mineui-client-api:<版本>`（`publishToMavenLocal` 可发布）

```java
package com.mineui.client.api;

public interface ClientStateProvider {
    /** 客户端渲染/逻辑线程调用；无该键返回 null。实现必须无阻塞、无网络。 */
    Object get(String key);
    /** 结构性变化（列表项增删等）时自增以触发重排；纯数值变化无需自增。默认 0。 */
    default long generation() { return 0L; }
}

public interface ClientActionHandler {
    /** 只接收同命名空间的 local:<ns>.<action>；返回 true 表示已处理。 */
    boolean handle(String action, java.util.Map<String, Object> payload);
}

public interface MineUiClientBridge {
    static MineUiClientBridge get();
    AutoCloseable register(String namespace, ClientStateProvider state, ClientActionHandler actions);
    void declareCapability(String capability);
}
```

### 2.2 页面语法

| 用途 | 写法 |
|---|---|
| 读取本地状态 | `{local.<namespace>.<key>}` |
| 本地动作 | `"action": "local:<namespace>.<action>"` |

- 绑定可用于文本、`progress.value`、`slider.value`、`visible` 等一切现有绑定位置，**每帧直接读取**
- 动作 payload 与现有动作一致（滑块提交 `{"value": n}`、输入框 `{"text": s}`）
- 文本/`{state}` 与 `{local}` 可混用：`"标题 {state.title} · {local.mineaudio.position}"`

## 3. 能力位与降级

- MineUI 客户端按注册表自动上报：`local_state`（有状态提供者）、`local_action`（有动作处理器）；
  `declareCapability(...)` 追加业务能力位（如 `mineaudio_local`）
- 服务端用 `MineUi.capabilities(player)` 判断，决定下发“本地版”还是“服务端推送版”页面
- 降级：旧客户端 / 未安装 MineUI 客户端 / 未注册命名空间 → `{local.*}` 渲染为空串，
  `local:` 动作被忽略（`handled=false`，不弹错、不回传服务端）

## 4. 生命周期与线程

- 注册发生在客户端初始化（业务 `onInitializeClient`），读取/派发在渲染线程
- 注销句柄关闭后对应绑定为空、动作忽略；业务断开/停用时自行关闭句柄
- `generation()` 自增时 MineUI 触发一次重排；屏幕与 HUD 都会响应

## 4.1 标量绑定与结构绑定的支持范围（重要）

| 绑定用途 | 更新时机 | 是否需要 `generation()` |
|---|---|---|
| 文本内容（`text` 等） | 每帧读取 | ❌ 不需要 |
| `progress.value` / `slider.value` | 每帧读取 | ❌ 不需要 |
| `list.items` / `itemTemplate` 内条目绑定 | 仅重排时读取 | ✅ 结构变化（列表项增删）时自增 |
| `visible` / slider `enabled` | 仅重排时求值 | ✅ 可见性/可用性切换时自增 |
| 播放位置等高频数值 | 每帧读取 | ❌ 不要自增（否则每帧重排） |

- 注册表版本在**注册/注销**时自增（即使 provider 的 `generation()` 恒为 0，注册生命周期变化也会触发一次重排）
- 纯数值变化**不会**触发重排——不要把位置变化转成重排事件
- 方法引用注册（如 `key -> clock.position()`）默认 `generation()` 恒为 0：若该命名空间的
  `visible`/`enabled`/列表需要跟随本地状态变化，请让 provider 自行维护并自增 generation

## 5. 安全边界（写死）

- `local.*` 是客户端本地数据（可信本地 mod 提供），**只在本机解析，协议里不出现，服务端无法读取**
- `local:` 动作只派发给**同命名空间**的注册者，禁止跨命名空间派发
- 本地数据可被本机玩家篡改，但只影响该玩家自己；任何权威判定（全服同步、计分、权限）
  必须走服务端，不得依赖 `local.*`
- provider/handler 抛异常会被隔离，不影响页面其他部分

## 6. 接入示例（业务客户端）

```java
public class ExampleClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MineUiClientBridge.get().register("example",
            key -> switch (key) {
                case "position" -> PlayerClock.position();
                case "playing"  -> PlayerClock.playing();
                default -> null;
            },
            (action, payload) -> switch (action) {
                case "seek"   -> { PlayerClock.seek(((Number) payload.get("value")).doubleValue()); yield true; }
                case "toggle" -> { PlayerClock.toggle(); yield true; }
                default -> false;
            });
    }
}
```

构建依赖（运行时不打包）：

```gradle
compileOnly "com.mineui:mineui-client-api:0.12.0"
```

## 7. 验收

- 本地绑定逐帧平滑（无需 1Hz 插值），拖动/暂停即时生效
- 未装 api mod / 未注册命名空间时页面安全降级
- 能力位正确上报（`/mineui status` 可见 caps）
- 命名空间隔离：A 的 `local:` 动作不会派发到 B

## 8. 与服务端 PATCH 的配合（节流）

- `UiSession.state()` 对**相等值直接跳过**（Gson 数值语义），业务每秒重复 push 相同字段不再产生网络包
- `session.batch(() -> { ... })` 把多次 `state()` 合并成一个 PATCH（按写入顺序保留，不跨路径去重）；
  动作处理器内的状态修改默认已按批合并
- 业务侧仍应避免推送客户端本地已拥有的字段（position/time/volume 等），详见 R9 建议
