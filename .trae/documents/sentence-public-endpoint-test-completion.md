# SentencePublicEndpoint 测试补齐与最终验证

## 摘要

本计划承接上一轮「SentenceReconciler 重构 - 深度优化」方案（`.trae/documents/sentence-reconciler-refactor-deep-optimization.md`），上下文丢失后剩余的最后 3 个任务：

- **Task 6**：新建 `SentencePublicEndpointTest.java`（6 个测试用例）
- **Task 7**：运行 `./gradlew test` 验证全部测试通过
- **Task 8**：运行 `./gradlew build` 验证构建成功

**前情确认**（Tasks 1-5 已完成）：
- `Sentence.java` — 已添加 Jakarta Validation 注解（`@NotBlank` / `@Size`）
- `SentenceConsoleEndpoint.java` — 已实现 `sanitizeSentenceInput` + `concatMap` 串行删除 + `listSentences` 共用方法 + 4 个方法 package-private
- `CategoryConsoleEndpoint.java` — 已使用 `NumberUtils.toInt` 兜底 + `listCategoriesWithCounts` package-private
- `SentenceConsoleEndpointTest.java` — 已创建（12 个测试用例）
- `CategoryConsoleEndpointTest.java` — 已创建（6 个测试用例）
- `SentencePublicEndpoint.getRandomSentences` / `toggleLike` — 已改为 package-private

---

## 当前状态分析

### 被测对象：`SentencePublicEndpoint`

位于 [SentencePublicEndpoint.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/SentencePublicEndpoint.java)，对外暴露 2 个路由方法：

1. **`getRandomSentences(ServerRequest)`**（[第 87 行](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/SentencePublicEndpoint.java#L87)）
   - 流程：`settingConfig.getBasicConfig()` → 解析 `categoryName`/`limit`/`encode` → `client.countBy` → `client.listBy`（必要时二次补足）→ `Collections.shuffle` → 可选 `incrementAndRecordViews`（受 `enableViewCount` 控制）→ 按 `encode` 返回 JSON 或 text
   - 关键依赖：`SettingConfig.BasicConfig`（`randomLimit`/`maxRandomLimit`/`encode`/`defaultCategory`/`enableViewCount`）

2. **`toggleLike(ServerRequest)`**（[第 214 行](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/SentencePublicEndpoint.java#L214)）
   - 流程：从 `request.exchange().getRequest()` 提取 IP（`HttpUtils.getClientIp`）→ 检查 `likeCache` 冷却 → `client.get(Sentence.class, name)` → 更新 `likeCount` → `client.update` → 创建/删除 `CategoryViewRecord`（LIKE 事件）→ 返回 `LikeResponse`
   - 关键依赖：`SettingConfig.BasicConfig.likeCooldown`、内部 `IpCooldownCache<SimpleCooldownState> likeCache`（实例字段，非注入）

### 测试基础设施（已存在，可复用）

- [TestFixtures.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/test/java/top/puresky/hitokotohub/support/TestFixtures.java) — `sentence(...)` / `category(...)` / `viewRecord(...)` 工厂方法
- [MockExtensionClient.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/test/java/top/puresky/hitokotohub/support/MockExtensionClient.java) — 内存存储 mock，覆盖 `listAll` / `listBy` / `fetch` / `get` / `create` / `update` / `delete` / `countBy`
- [SentenceConsoleEndpointTest.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/test/java/top/puresky/hitokotohub/endpoint/SentenceConsoleEndpointTest.java) — 已建立的测试模式参考（纯 Mockito + `doReturn` + `StepVerifier`）

### 测试挑战与策略

| 挑战 | 应对策略 |
|------|---------|
| `toggleLike` 内部用 `request.exchange().getRequest()` 取 IP | mock `ServerRequest.exchange()` 返回 `ServerWebExchange`，其 `getRequest()` 返回 `MockServerHttpRequest`（带 `X-Forwarded-For` 头）|
| `likeCache` 是实例字段，无法注入 | 不注入，利用其行为：在同一 `SentencePublicEndpoint` 实例上连续两次调用 `toggleLike` 即可触发 `rate_limited` 路径 |
| `getRandomSentences` 路径长（含 `countBy` → `listBy` → shuffle） | `MockExtensionClient.builder()` 内置 `countBy` 与 `listBy` 内存实现，预置 Sentence 即可；`enableViewCount=false` 跳过 `incrementAndRecordViews` 避免 `update` 复杂断言 |
| `getRandomSentences` 用 `PageRequestImpl.of` 取分页 | `MockExtensionClient.build()` 已通过反射调用 `getPage`/`getSize` 适配 `PageRequestImpl`，无需特殊处理 |
| `RandomSentenceResponse` body 提取复杂 | 用 `StepVerifier` 验证 `statusCode()` 为 2xx + `verify(client).listBy(...)` 间接验证；`encode=text` 用 `BodyExtractors` 提取纯文本断言 |
| `BasicConfig` 是 `SettingConfig` 内部 `@Data` 类 | `mock(SettingConfig.class)` + `when(config.getBasicConfig()).thenReturn(Mono.just(basicConfig))`；`basicConfig` 用 `new SettingConfig.BasicConfig()` + setter 构造 |

---

## 提议的改动

### Task 6：新建 `src/test/java/top/puresky/hitokotohub/endpoint/SentencePublicEndpointTest.java`

**文件位置**：`c:\Users\19002\Documents\Trae\plugin-hitokoto-hub\src\test\java\top\puresky\hitokotohub\endpoint\SentencePublicEndpointTest.java`

**测试用例（6 个）**：

#### A. `getRandomSentences` 测试组（3 个）

**A1. `getRandomSentences_defaultParams_returnJsonResponse`**
- **场景**：默认参数（不传 `categoryName`/`limit`/`encode`）
- **预置**：`MockExtensionClient.builder().with(sentence1).with(sentence2)` + `BasicConfig` 默认值（`randomLimit=1`, `maxRandomLimit=10`, `encode="json"`, `enableViewCount=false`）
- **断言**：响应 2xx；`verify(client).listBy(...)` 被调用至少一次；`verify(client, never()).update(any())`（因 `enableViewCount=false`）

**A2. `getRandomSentences_encodeText_returnPlainText`**
- **场景**：`encode=text`
- **预置**：同 A1，但 `BasicConfig.encode="text"` 或 request 传 `encode=text`
- **断言**：响应 `Content-Type` 为 `text/plain`；body 包含预置句子的 content（用 `BodyExtractors.toDataBuffers` 或 `toEntity(String.class)` 提取）

**A3. `getRandomSentences_withCategoryName_appliesFilter`**
- **场景**：`categoryName=cat-a`
- **预置**：两个 Sentence（`cat-a` 和 `cat-b`）+ 一个 `Category("cat-a", "分类A")`
- **断言**：响应 2xx；`verify(client).fetch(Category.class, "cat-a")` 被调用（用于 `getDisplayName`）；返回的 `categoryName` 字段为 "分类A"
- **注**：`MockExtensionClient` 不实现 ListOptions 过滤，listBy 返回全部 Sentence；此测试主要验证 `client.fetch(Category.class, ...)` 调用路径正确

#### B. `toggleLike` 测试组（3 个）

**B1. `toggleLike_normalLike_likeCountIncremented`**
- **场景**：首次点赞，缓存为空
- **预置**：`MockExtensionClient.builder().with(sentence("s1", "内容", true, 5, 10))`；`BasicConfig.likeCooldown=1`
- **断言**：响应 2xx；`LikeResponse.code="ok"`；`LikeResponse.success=true`；`LikeResponse.sentence.likeCount=6`（原 5 + 1）
- **关键**：通过 `ArgumentCaptor<Sentence>` 捕获 `client.update()` 入参，验证 `likeCount=6`；或通过 body 提取

**B2. `toggleLike_duplicateLike_returnsRateLimited`**
- **场景**：同一 IP + 同一 sentence name，连续两次点赞
- **预置**：同 B1；使用**同一个** `SentencePublicEndpoint` 实例（保证 `likeCache` 共享）
- **执行**：
  1. 第一次 `endpoint.toggleLike(request).block()` → 成功，`likeCache` 填入
  2. 第二次 `endpoint.toggleLike(request).block()` → 进入 `rate_limited` 分支
- **断言**：第二次响应 `LikeResponse.code="rate_limited"`；`LikeResponse.success=false`
- **注**：`MockExtensionClient` 的 `update` 会原地替换存储，第二次 `client.get` 取回的 `likeCount` 已是 6，不影响 `rate_limited` 分支（该分支在更新前 return）

**B3. `toggleLike_sentenceNotFound_returnsNotFound`**
- **场景**：`name=nonexistent`
- **预置**：`MockExtensionClient.builder().build()`（空存储）；`BasicConfig.likeCooldown=1`
- **断言**：响应 2xx；`LikeResponse.code="not_found"`；`LikeResponse.sentence=null`
- **关键**：`MockExtensionClient.get` 对不存在 name 返回 `Mono.error(RuntimeException("Not found"))`；endpoint 的 `.defaultIfEmpty(buildErrorResponse())` 兜底——但 `Mono.error` 不会触发 `defaultIfEmpty`，而是走 `onError` 路径
- **处理方案**：在测试中改用 `mock(ReactiveExtensionClient.class)` + `when(client.get(any(), eq("nonexistent"))).thenReturn(Mono.empty())`，确保触发 `defaultIfEmpty`

#### 辅助方法

```java
/** 构造 BasicConfig 测试夹具。 */
private static SettingConfig.BasicConfig basicConfig() {
    SettingConfig.BasicConfig c = new SettingConfig.BasicConfig();
    c.setMaxRandomLimit(10);
    c.setRandomLimit(1);
    c.setEncode("json");
    c.setDefaultCategory(List.of());
    c.setLikeCooldown(1);
    c.setEnableViewCount(false);
    return c;
}

/** mock SettingConfig，getBasicConfig 返回固定 BasicConfig。 */
private static SettingConfig mockSettingConfig(SettingConfig.BasicConfig basicConfig) {
    SettingConfig config = mock(SettingConfig.class);
    when(config.getBasicConfig()).thenReturn(Mono.just(basicConfig));
    return config;
}

/** 构造带 X-Forwarded-For 的 ServerRequest mock（用于 toggleLike 取 IP）。 */
private static ServerRequest mockRequestWithIp(String ip, String name, String action) {
    ServerRequest request = mock(ServerRequest.class);
    when(request.queryParam("name")).thenReturn(Optional.of(name));
    when(request.queryParam("action")).thenReturn(Optional.ofNullable(action));

    ServerWebExchange exchange = mock(ServerWebExchange.class);
    ServerHttpRequest httpRequest = MockServerHttpRequest.get("/")
        .header("X-Forwarded-For", ip)
        .build();
    when(exchange.getRequest()).thenReturn(httpRequest);
    when(request.exchange()).thenReturn(exchange);
    return request;
}

/** 构造 getRandomSentences 的 ServerRequest mock。 */
private static ServerRequest mockRandomRequest(String categoryName, String limit, String encode) {
    ServerRequest request = mock(ServerRequest.class);
    when(request.queryParam("categoryName")).thenReturn(Optional.ofNullable(categoryName));
    when(request.queryParam("limit")).thenReturn(Optional.ofNullable(limit));
    when(request.queryParam("encode")).thenReturn(Optional.ofNullable(encode));
    return request;
}
```

#### 依赖 import

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.test.web.server.MockServerHttpRequest;  // 若 classpath 不可用，回退到手工 mock
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.ReactiveExtensionClient;
import top.puresky.hitokotohub.config.SettingConfig;
import top.puresky.hitokotohub.endpoint.SentencePublicEndpoint.LikeResponse;
import top.puresky.hitokotohub.endpoint.SentencePublicEndpoint.RandomSentenceResponse;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.support.MockExtensionClient;
import top.puresky.hitokotohub.support.TestFixtures;
```

**`MockServerHttpRequest` 风险预案**：若 `spring-test` 中的 `MockServerHttpRequest` 在 classpath 不可用（Halo 项目未必引入 `spring-test`），回退到 `Mockito.mock(ServerHttpRequest.class)` + `when(req.getHeaders()).thenReturn(new HttpHeaders())` + `headers.add("X-Forwarded-For", ip)`。编译时若报错，立即切换方案。

---

### Task 7：运行 `./gradlew test`

**命令**：
```powershell
./gradlew test
```

**预期**：134（前一轮已有）+ 12（SentenceConsoleEndpointTest）+ 6（CategoryConsoleEndpointTest）+ 6（SentencePublicEndpointTest）= **约 158 个测试通过**，无回归。

**失败处理流程**：
1. 若 `SentencePublicEndpointTest` 编译失败 → 根据错误调整 import / mock 策略，重新跑 `./gradlew test --tests "top.puresky.hitokotohub.endpoint.SentencePublicEndpointTest"`
2. 若已有测试失败 → 排查是否由 Tasks 1-3 主代码改动引起的回归
3. 若 `MockServerHttpRequest` 不可用 → 切换到 `Mockito.mock(ServerHttpRequest.class)` + 手工构造 headers

---

### Task 8：运行 `./gradlew build`

**命令**：
```powershell
./gradlew build
```

**预期**：
- `compileJava` ✅
- `compileTestJava` ✅
- `test` ✅
- `ui:pnpmBuild` ✅（前端不受影响）
- 无 lint / spotless 失败

**失败处理**：
- 若 `spotless` 或 `lint` 失败 → 检查代码格式（缩进、import 顺序），按报错修复
- 若 `ui:pnpmBuild` 失败 → 与本次改动无关，应单独排查（前一轮已通过）

---

## 假设与决策

| # | 决策 | 理由 |
|---|------|------|
| 1 | `SentencePublicEndpointTest` 使用 `MockExtensionClient.builder()` 为主，对 `get` 方法在 B3 用纯 `mock()` | `MockExtensionClient.get` 返回 `Mono.error` 而非 `Mono.empty`，不触发 `defaultIfEmpty`；B3 需要精确控制 |
| 2 | B2（重复点赞）使用同一 `SentencePublicEndpoint` 实例 | `likeCache` 是实例字段，必须共享实例才能复现缓存命中 |
| 3 | `getRandomSentences` 测试不验证 shuffle 随机性 | 随机性难以断言；只验证返回结构与调用次数 |
| 4 | `enableViewCount=false` 跳过 `incrementAndRecordViews` | 该分支涉及 `client.update` 与 `client.create(CategoryViewRecord)`，引入额外断言复杂度；已由现有 `CategoryViewRecordFactoryTest`（如存在）或后续测试覆盖 |
| 5 | 不测试 `cleanExpiredLikeCache` 公开方法 | 该方法 `.subscribe()` 异步执行且无返回值，测试价值低 |
| 6 | `MockServerHttpRequest` 风险预案：若不可用则回退手工 mock | 项目未必引入 `spring-test`，需灵活应对 |

---

## 涉及文件清单

### 新建
- `c:\Users\19002\Documents\Trae\plugin-hitokoto-hub\src\test\java\top\puresky\hitokotohub\endpoint\SentencePublicEndpointTest.java`

### 不改动
- 主代码（Tasks 1-3 已完成，不重复修改）
- `SentenceConsoleEndpointTest.java` / `CategoryConsoleEndpointTest.java`（Tasks 4-5 已完成）
- 其他 endpoint / reconciler / service

---

## 验证步骤

### 1. 编译验证
```powershell
./gradlew compileTestJava
```
**预期**：BUILD SUCCESSFUL，无编译错误。

### 2. 单元测试
```powershell
./gradlew test
```
**预期**：全部测试通过，约 158 个用例，无回归。

### 3. 构建验证
```powershell
./gradlew build
```
**预期**：BUILD SUCCESSFUL，包含 `compileJava` / `compileTestJava` / `test` / `ui:pnpmBuild`。

---

## 实施顺序（TodoWrite 任务）

1. ✅ Task 1: 修改 `Sentence.java`（已完成）
2. ✅ Task 2: 修改 `SentenceConsoleEndpoint.java`（已完成）
3. ✅ Task 3: 修改 `CategoryConsoleEndpoint.java`（已完成）
4. ✅ Task 4: 新建 `SentenceConsoleEndpointTest.java`（已完成）
5. ✅ Task 5: 新建 `CategoryConsoleEndpointTest.java`（已完成）
6. 🔄 **Task 6: 新建 `SentencePublicEndpointTest.java`**（本次重点）
   - 6.1 编写测试代码（6 个用例 + 辅助方法）
   - 6.2 编译验证 `./gradlew compileTestJava`
   - 6.3 若 `MockServerHttpRequest` 不可用 → 切换回退方案
7. ⏳ **Task 7: 运行 `./gradlew test`**
8. ⏳ **Task 8: 运行 `./gradlew build`**

---

## 风险评估

| 风险 | 概率 | 缓解措施 |
|------|------|---------|
| `MockServerHttpRequest` 不在 classpath | 中 | 回退到 `Mockito.mock(ServerHttpRequest.class)` + 手工 headers |
| `MockExtensionClient.get` 返回 `Mono.error` 不触发 `defaultIfEmpty` | 高 | B3 改用纯 `mock(ReactiveExtensionClient.class)` + `thenReturn(Mono.empty())` |
| `toggleLike` 的 `client.update` 链路复杂，断言失败 | 中 | 优先用 `ArgumentCaptor` 捕获 update 入参；必要时只验证响应状态 |
| `getRandomSentences` 中 `Collections.shuffle` 导致断言不稳定 | 低 | 不验证具体顺序，只验证集合大小与内容包含关系 |
| 既有测试因上下文丢失回归 | 低 | Tasks 1-5 已编译通过；本次不改动主代码 |
