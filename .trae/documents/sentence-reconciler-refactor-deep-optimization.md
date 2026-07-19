# SentenceReconciler 重构 - 深度优化阶段

## 摘要

本计划在前一轮已完成的核心重构（134 测试通过，[见总结](./sentence-reconciler-refactor-completion.md)）基础上，针对用户"全面重构"请求中尚未充分覆盖的方面做深度优化：

- **数据校验与清洗**：`Sentence.Spec` 添加 Jakarta Validation 注解；`SentenceConsoleEndpoint` 提取统一的输入清洗逻辑并应用到批量创建/Excel 导入两条路径
- **性能优化**：修复 `SentenceConsoleEndpoint.clearUncategorizedSentences` 的并发删除风险（与项目硬约束"并发删除同分类句子会导致 Category 乐观锁冲突"保持一致）
- **健壮性**：`CategoryConsoleEndpoint` 的 `page`/`size` 参数解析增加异常兜底
- **测试覆盖**：补齐 3 个核心 endpoint（`SentenceConsoleEndpoint` / `CategoryConsoleEndpoint` / `SentencePublicEndpoint`）的单元测试

**范围澄清（用户已确认）**：
- `CategoryCountServiceImpl` 的 `getAllCounts` 保留 `listAll + 内存分组` 实现（已批准的更优方案，避免 N 次 `countBy` 的 N+1 问题）；`getCount` 单分类查询仍用 `countBy`
- 不改动 `SentenceReconciler`（已完善）
- 不创建独立 `SentenceSanitizer` 工具类（保持与现有 `buildSentence` 内联风格一致，提取私有方法即可）

---

## 当前状态分析

### 已完成（前一轮重构）

| 模块 | 状态 |
|------|------|
| `SentenceReconciler` | ✅ 已移除 Category 缓存更新、已添加 SimilarityCheckService 清理调用（fire-and-forget）、已实现 categoryName 归一化 |
| `Category.Status` | ✅ 空类，无 `sentenceCount` 字段 |
| `CategoryCountServiceImpl` | ✅ 实时查询实现（listAll + 内存分组 O(C+S)；getCount 用 countBy） |
| `CategoryConsoleEndpoint.listCategoriesWithWeights` | ✅ 已实现 |
| `SimilarityCheckServiceImpl.cleanupReferencesForDeletedSentence` | ✅ 已实现 |
| `HitokotoHubPlugin` / `UncategorizedCategoryInitializer` | ✅ 均已移除 `setSentenceCount` 调用 |
| 前端 `CategoryList.vue` | ✅ 已切换到 `listCategoriesWithWeights` 端点 |
| 测试 | ✅ 134 个测试通过 |

### 识别出的剩余优化点

| 问题点 | 位置 | 风险 |
|--------|------|------|
| `Sentence.Spec` 字段仅有 `@Schema`，缺少 Jakarta Validation 注解 | [Sentence.java:25-42](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/extension/Sentence.java) | 输入校验缺失，无法在 Bean Validation 框架下被自动校验 |
| `SentenceConsoleEndpoint.createSentences` 未做输入清洗 | [SentenceConsoleEndpoint.java:140-171](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/SentenceConsoleEndpoint.java) | 脏数据风险（content 含前后空白、author 为空字符串等） |
| `SentenceConsoleEndpoint.clearUncategorizedSentences` 用 `flatMap` 并发删除 | [SentenceConsoleEndpoint.java:271-281](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/SentenceConsoleEndpoint.java) | 违反项目硬约束"并发删除同分类句子会导致 Category 乐观锁冲突"；虽然 SentenceReconciler 不再写 Category.Status，但并发触发 SimilarityCheckLog 清理仍有压力 |
| `CategoryConsoleEndpoint` 的 `page`/`size` 解析未做 NumberFormatException 兜底 | [CategoryConsoleEndpoint.java:71-72](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/CategoryConsoleEndpoint.java) | 恶意/异常输入会返回 500 而非兜底默认值 |
| 3 个核心 endpoint 无单元测试 | `SentenceConsoleEndpoint` / `CategoryConsoleEndpoint` / `SentencePublicEndpoint` | 重构后回归风险高 |

---

## 提议的改动

### 主代码改动

#### 1. `src/main/java/top/puresky/hitokotohub/extension/Sentence.java`

**What**：为 `Spec` 内部类字段添加 Jakarta Validation 注解。

**Why**：
- `content` 是必填字段（业务核心），但仅有 `@Schema(requiredMode = REQUIRED)`，缺少运行时校验
- 字段长度限制（`@Schema(maxLength = 500)`）仅是文档，无强制力
- 添加 `@NotBlank`/`@Size` 后，若未来引入 Spring Validation 或 Halo 框架支持，可自动触发；当前作为强制文档约束

**How**：
```java
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Schema(name = "SentenceSpec")
public static class Spec {
    @NotBlank
    @Size(max = 100)
    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoryName;

    @NotBlank
    @Size(max = 500)
    @Schema(description = "句子内容", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 500)
    private String content;

    @Size(max = 50)
    @Schema(description = "作者", maxLength = 50, defaultValue = "匿名")
    private String author = "匿名";

    @Size(max = 100)
    @Schema(description = "来源", maxLength = 100, defaultValue = "未知")
    private String source = "未知";

    @Schema(description = "创建用户")
    private String createdBy;
}
```

**Decision**：不添加到 `Status`（运行时数值字段，无外部输入）；不添加到 `metadata`（Halo 框架管理）。

---

#### 2. `src/main/java/top/puresky/hitokotohub/endpoint/SentenceConsoleEndpoint.java`

##### 2.1 新增 `sanitizeSentenceInput(Sentence)` 私有方法

**What**：提取统一的输入清洗逻辑。

**Why**：
- `createSentences` 当前完全未做清洗（content 含空白、author 为空等脏数据直接写入）
- `buildSentence`（Excel 导入路径）已有部分清洗逻辑（trim + 默认值），但与 `createSentences` 路径不一致
- 提取共用方法可消除重复，保证两条路径行为一致

**How**：
```java
/**
 * 清洗 sentence 输入：trim、null 处理、默认值填充、长度截断。
 *
 * <p>注意：不在此处做 categoryName 归一化（null/不存在 → uncategorized），
 * 那是 SentenceReconciler 的职责。此处仅做"输入净化"。
 */
private @NonNull Sentence sanitizeSentenceInput(Sentence sentence) {
    Sentence.Spec spec = sentence.getSpec();
    if (spec == null) {
        return sentence; // 异常输入，交由 reconciler 兜底
    }
    // content: trim + 长度截断（500）
    String content = trimToEmpty(spec.getContent());
    if (content.length() > 500) {
        content = content.substring(0, 500);
    }
    spec.setContent(content);
    // categoryName: trim（不归一化，由 reconciler 处理）
    spec.setCategoryName(trimToEmpty(spec.getCategoryName()));
    // author: trim + 空则填默认值
    String author = trimToNull(spec.getAuthor());
    spec.setAuthor(author != null ? author : "匿名");
    // source: trim + 空则填默认值
    String source = trimToNull(spec.getSource());
    spec.setSource(source != null ? source : "未知");
    return sentence;
}

private static String trimToEmpty(String s) {
    return s == null ? "" : s.trim();
}

private static String trimToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
}
```

**注**：使用 `StringUtils.trimToEmpty`/`trimToNull`（commons-lang3，已被 `SentencePublicEndpoint` 引入，确认可用）替代手写。

##### 2.2 `createSentences` 调用 sanitize

**What**：在 `client.create(sentence)` 前调用 `sanitizeSentenceInput(sentence)`。

**How**（修改 [SentenceConsoleEndpoint.java:149-163](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/SentenceConsoleEndpoint.java)）：
```java
return sentenceFlux.flatMap(sentence -> {
    sentence.getSpec().setCreatedBy(username);
    sanitizeSentenceInput(sentence);  // 新增
    if (sentence.getStatus() == null) {
        sentence.setStatus(new Sentence.Status());
    }
    sentence.getStatus().setPublished(hasSuperRole);
    return client.create(sentence)...
});
```

##### 2.3 `buildSentence` 复用 sanitize

**What**：让 Excel 导入路径也使用统一清洗。

**How**（修改 [SentenceConsoleEndpoint.java:239-252](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/SentenceConsoleEndpoint.java)）：
```java
private @NonNull Sentence buildSentence(String categoryName, String content, String author,
                                        String source) {
    var sentence = new Sentence();
    sentence.setMetadata(new Metadata());
    sentence.getMetadata().setGenerateName("sentence-");
    var spec = new Sentence.Spec();
    spec.setCategoryName(categoryName);
    spec.setContent(content);
    spec.setAuthor(author);   // 不再此处填默认值，交给 sanitize 统一处理
    spec.setSource(source);
    sentence.setSpec(spec);
    sentence.setStatus(new Sentence.Status());
    return sanitizeSentenceInput(sentence);  // 统一清洗
}
```

##### 2.4 `clearUncategorizedSentences` 改为串行删除

**What**：`flatMap` → `concatMap`（与 `SimilarityCheckServiceImpl.deleteSentencesSerially` 风格一致）。

**Why**：项目硬约束"并发删除同分类句子会导致 Category 乐观锁冲突"。

**How**（修改 [SentenceConsoleEndpoint.java:271-281](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/SentenceConsoleEndpoint.java)）：
```java
private @NonNull Mono<ServerResponse> clearUncategorizedSentences(ServerRequest request) {
    var listOptions = new ListOptions();
    listOptions.setFieldSelector(
        FieldSelector.of(Queries.equal("spec.categoryName",
            UncategorizedConstants.METADATA_NAME)));

    return client.listAll(Sentence.class, listOptions, Sort.unsorted())
        .concatMap(sentence -> client.delete(sentence)
            .onErrorResume(e -> {
                log.warn("删除未分类句子 [{}] 失败: {}", sentence.getMetadata().getName(),
                    e.getMessage());
                return Mono.empty();
            }))
        .count()
        .flatMap(count -> ServerResponse.ok().bodyValue(count));
}
```

**注**：`concatMap` 保证串行，单条失败跳过不中断（与 `deleteSentencesSerially` 一致）。不加 `delayElement`（无需延迟，删除本身是顺序执行的）。

##### 2.5（可选）合并 `querySentences` / `searchSentence` 重复代码

**What**：提取共用 `listSentences(SentenceQuery)` 私有方法。

**Why**：两个方法逻辑重复，仅返回类型不同。

**How**：
```java
private Mono<ListResult<Sentence>> listSentences(ServerRequest request) {
    var query = new SentenceQuery(request);
    return client.listBy(Sentence.class, query.toListOptions(), query.toPageRequest());
}

private @NonNull Mono<ServerResponse> querySentences(ServerRequest request) {
    return listSentences(request)
        .flatMap(sentences -> ServerResponse.ok().bodyValue(sentences));
}

private @NonNull Mono<ServerResponse> searchSentence(ServerRequest request) {
    return listSentences(request)
        .map(ListResult::getItems)
        .flatMap(sentences -> ServerResponse.ok().bodyValue(sentences));
}
```

---

#### 3. `src/main/java/top/puresky/hitokotohub/endpoint/CategoryConsoleEndpoint.java`

**What**：`page`/`size` 解析加固。

**Why**：`Integer.parseInt` 遇到非数字输入会抛 `NumberFormatException`，导致 500 错误；应兜底为默认值。

**How**（修改 [CategoryConsoleEndpoint.java:70-72](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/CategoryConsoleEndpoint.java)）：
```java
import org.apache.commons.lang3.math.NumberUtils;

private @NonNull Mono<ServerResponse> listCategoriesWithCounts(ServerRequest request) {
    int page = NumberUtils.toInt(request.queryParam("page").orElse("1"), 1);
    int size = NumberUtils.toInt(request.queryParam("size").orElse("20"), 20);
    // ... 后续不变
}
```

**注**：`commons-lang3` 已被 `SentencePublicEndpoint` 使用（`StringUtils`、`RandomUtils`），确认 main 依赖中可用。`NumberUtils.toInt(str, defaultValue)` 对 null/空/非数字均返回默认值。

---

### 测试改动

#### 4. `src/test/java/top/puresky/hitokotohub/endpoint/SentenceConsoleEndpointTest.java`（新建）

**覆盖范围**：
- `batchCreateSentence` 正常流程（含 super-role 判断、createdBy 设置）
- `batchCreateSentence` 输入清洗验证（content 含前后空白被 trim、author 为空填"匿名"、content 超长被截断至 500）
- `clearUncategorizedSentences` 计数正确（含失败跳过）
- `querySentences` 分页返回 ListResult
- `searchSentence` 返回 items 数组
- `importExcelSentences` 文件类型校验（非 .xlsx 报错）

**Mock 策略**：
- `ReactiveExtensionClient`：使用 `MockExtensionClient.builder()`
- `RoleService`：`Mockito.mock(RoleService.class)`，`when(roleService.getRolesByUsername(any())).thenReturn(Flux.just("super-role"))`
- `ServerRequest`：使用 Spring WebFlux 的 `MockServerRequest.builder()`（已传递依赖）
- 断言：直接 `.block()` 获取 `ServerResponse`，检查 `statusCode()` 与 `body()`（通过 `BodyExtractor` 提取）

**关键测试用例草图**：
```java
@Test
@DisplayName("batchCreateSentence：content 含前后空白 → 被 trim")
void batchCreateSentence_contentTrimmed() {
    Sentence input = TestFixtures.sentence("s1", "  内容  ", "cat-a", "匿名", "未知", false, 0, 0);
    ReactiveExtensionClient client = MockExtensionClient.builder()
        .with(TestFixtures.category("cat-a", "分类A")).build();
    RoleService roleService = mock(RoleService.class);
    when(roleService.getRolesByUsername(any())).thenReturn(Flux.just("super-role"));

    SentenceConsoleEndpoint endpoint = new SentenceConsoleEndpoint(client, roleService);
    MockServerRequest request = MockServerRequest.builder()
        .principal(() -> "test-user")
        .body(List.of(input));

    StepVerifier.create(endpoint.batchCreateSentence(request).block())
        // ... 验证 client.create 被调用，且 content 已被 trim
}
```

**注**：实际实现时可能需要用 `MockServerRequest`（spring-webflux-test）或手工 mock `ServerRequest`。如果 `MockServerRequest` 不可用，使用 `Mockito.mock(ServerRequest.class)` + `when(...).thenReturn(...)` 手工桩。

---

#### 5. `src/test/java/top/puresky/hitokotohub/endpoint/CategoryConsoleEndpointTest.java`（新建）

**覆盖范围**：
- `listCategoriesWithWeights` 正常返回（多个分类 + 实时计数）
- `listCategoriesWithWeights` `page=abc`/`size=xyz` 非数字 → 兜底为 1/20
- `listCategoriesWithWeights` 空数据 → 返回空 ListResult
- `listCategoriesWithWeights` 含已删除分类 → 被 `Queries.isNull("metadata.deletionTimestamp")` 过滤（虽然 `MockExtensionClient` 不实现 fieldSelector，但通过断言总条数验证）
- `listCategoriesWithWeights` `sentenceCount` 来自 `CategoryCountService.getAllCounts()`（mock 返回）

**Mock 策略**：
- `ReactiveExtensionClient`：`MockExtensionClient.builder()`
- `CategoryCountService`：`Mockito.mock(CategoryCountService.class)`，`when(service.getAllCounts()).thenReturn(Mono.just(Map.of("cat-a", 3L)))`

---

#### 6. `src/test/java/top/puresky/hitokotohub/endpoint/SentencePublicEndpointTest.java`（新建）

**覆盖范围**：
- `getRandomSentences` 默认参数 → 返回 JSON 格式 `RandomSentenceResponse`
- `getRandomSentences` `encode=text` → 返回纯文本（每行一句）
- `getRandomSentences` 指定 `categoryName` → 通过 `Queries.in("spec.categoryName", ...)` 过滤
- `toggleLike` 正常点赞 → `likeCount+1`，返回 `code=ok`
- `toggleLike` 重复点赞 → 返回 `code=rate_limited`
- `toggleLike` 句子不存在 → 返回 `code=not_found`

**Mock 策略**：
- `ReactiveExtensionClient`：`MockExtensionClient.builder()` + 预置 Sentence / CategoryViewRecord
- `SettingConfig`：`Mockito.mock(SettingConfig.class)`，`when(config.getBasicConfig()).thenReturn(Mono.just(basicConfig))`
- `MockServerRequest` 模拟 queryParam 与 Principal

**注**：`SentencePublicEndpoint` 内部维护 `IpCooldownCache<SimpleCooldownState> likeCache`（实例字段）。测试 `rate_limited` 路径需要先发起一次点赞填入缓存，再发起第二次验证限流。

---

## 假设与决策

| # | 决策 | 理由 |
|---|------|------|
| 1 | `CategoryCountServiceImpl` 不改动 | 用户已确认保留 `listAll + 内存分组` 方案（性能优于 N 次 `countBy`） |
| 2 | `SentenceReconciler` 不改动 | 已完善，避免过度工程 |
| 3 | 不创建独立 `SentenceSanitizer` 工具类 | 与现有 `buildSentence` 内联风格一致；提取 endpoint 内私有方法即可 |
| 4 | `Jakarta Validation` 注解即使 Halo 不自动执行也添加 | 作为强制文档约束；未来若启用 Bean Validation 自动生效 |
| 5 | `clearUncategorizedSentences` 改 `concatMap` 不加 `delayElement` | 删除本身是顺序执行的，无需额外延迟（与 `deleteSentencesSerially` 不同，后者需要等待 reconciler） |
| 6 | `querySentences`/`searchSentence` 提取共用方法 | 消除重复，但保持端点签名不变（API 兼容） |
| 7 | Endpoint 测试用 `MockServerRequest` 或手工 mock `ServerRequest` | 项目目前无 WebTestClient 配置，用最小依赖实现 |
| 8 | 不补充 `SentenceSubmissionConsoleEndpoint` / `SimilarityCheckConsoleEndpoint` / `OverviewConsoleEndpoint` 测试 | 本次范围聚焦用户请求的"数据校验+性能优化"，这三个 endpoint 已有 EchartsDataBuilderTest 覆盖核心逻辑，无输入校验需求 |

---

## 涉及文件清单

### 修改
- [src/main/java/top/puresky/hitokotohub/extension/Sentence.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/extension/Sentence.java) — 添加 Validation 注解
- [src/main/java/top/puresky/hitokotohub/endpoint/SentenceConsoleEndpoint.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/SentenceConsoleEndpoint.java) — 输入清洗 + 串行删除 + 提取共用方法
- [src/main/java/top/puresky/hitokotohub/endpoint/CategoryConsoleEndpoint.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/CategoryConsoleEndpoint.java) — page/size 解析加固

### 新建（测试）
- `src/test/java/top/puresky/hitokotohub/endpoint/SentenceConsoleEndpointTest.java`
- `src/test/java/top/puresky/hitokotohub/endpoint/CategoryConsoleEndpointTest.java`
- `src/test/java/top/puresky/hitokotohub/endpoint/SentencePublicEndpointTest.java`

### 不改动（明确决策）
- `SentenceReconciler.java`
- `CategoryCountServiceImpl.java`
- `HitokotoHubPlugin.java`
- `UncategorizedCategoryInitializer.java`
- 前端代码（`ui/`）

---

## 验证步骤

### 1. 单元测试
```powershell
./gradlew test --tests "top.puresky.hitokotohub.endpoint.*"
./gradlew test
```
**预期**：134（已有）+ 新增约 15-20 个 endpoint 测试用例，全部通过，无回归。

### 2. 构建验证
```powershell
./gradlew build
```
**预期**：`compileJava` ✅、`compileTestJava` ✅、`test` ✅、`ui:pnpmBuild` ✅（前端不受影响）。

### 3. 手动验证清单（可选，由用户决定）
- [ ] 通过 Postman 调用 `POST /apis/console.api.hitokotohub.puresky.top/v1alpha1/sentence/-/batch`，传入 `content="  测试  "`、`author=""`，验证数据库中 `content="测试"`、`author="匿名"`
- [ ] 调用 `GET .../categories?page=abc&size=xyz`，验证返回第 1 页 20 条（兜底）
- [ ] 调用 `DELETE .../sentence/-/clear-uncategorized`，验证未分类句子被串行删除（日志显示顺序删除）
- [ ] 调用 `GET /apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/random?encode=text`，验证返回纯文本

---

## 风险评估

| 风险 | 概率 | 缓解措施 |
|------|------|---------|
| Jakarta Validation 注解导致 Halo 反序列化失败 | 低 | Halo 不强制执行 Bean Validation；注解仅作为元数据 |
| `MockServerRequest` 在 classpath 中不可用 | 中 | 退回到手工 `Mockito.mock(ServerRequest.class)` 方案 |
| `concatMap` 串行删除性能下降（大量未分类句子） | 低 | 用户场景下未分类句子数量有限；可接受 |
| Endpoint 测试因响应式链路复杂难写 | 中 | 优先用 `StepVerifier`，必要时用 `.block()` 后断言 |
| 输入清洗改变现有行为（如已发布数据被回写） | 低 | `sanitizeSentenceInput` 仅在 `create` 前调用，不影响已存在数据 |

---

## 实施顺序（建议 TodoWrite 任务）

1. **修改 `Sentence.java`**：添加 Jakarta Validation 注解
2. **修改 `SentenceConsoleEndpoint.java`**：
   - 提取 `sanitizeSentenceInput` 私有方法
   - `createSentences` 调用 sanitize
   - `buildSentence` 复用 sanitize
   - `clearUncategorizedSentences` 改 `concatMap`
   - 提取 `listSentences` 共用方法
3. **修改 `CategoryConsoleEndpoint.java`**：page/size 解析用 `NumberUtils.toInt`
4. **新建 `SentenceConsoleEndpointTest.java`**
5. **新建 `CategoryConsoleEndpointTest.java`**
6. **新建 `SentencePublicEndpointTest.java`**
7. **运行 `./gradlew test`**：验证全部测试通过
8. **运行 `./gradlew build`**：验证构建成功
