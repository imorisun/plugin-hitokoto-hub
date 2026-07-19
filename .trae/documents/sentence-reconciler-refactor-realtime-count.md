# SentenceReconciler 重构 + 分类计数实时化方案

## Context

### 背景与问题
当前 `SentenceReconciler` 把分类下句子数量缓存在 `Category.Status.sentenceCount`，存在以下已确认问题（参考上一轮代码审查）：

1. **跨分类迁移漏更新**：sentence 改 categoryName 时只更新新分类，旧分类计数永久偏高
2. **并发乐观锁冲突**：多 worker 并发 `fetch + countBy + update` 同一 Category，触发 `OptimisticLockingFailureException`，批量删除时形成重试雪崩
3. **SimilarityCheckLog 脏数据**：删除 sentence 时未清理 `SimilarityCheckLog.spec.similarPairs` 中引用的 `sentence1Name/sentence2Name`，前端出现"鬼魂句子"
4. **fetch empty 永久丢失计数**：sentence 已被永久删除时 reconcile 直接返回，计数无法修正
5. **null/uncategorized 边界错误**：异常数据导致 `uncategorized` 计数被错误扣减
6. **冗余/低效代码**：`setFinalizers(emptySet())` 实际是 no-op、`Status null` 检查冗余、完全无日志、每次 reconcile 全量 countBy 性能差

### 内存记录的事实更正
项目内存中记录"已通过 142 单元测试完成 P0 修复"——经核查 `git log` 与代码，**该修复从未提交到代码库**。`cleanupSimilarityCheckLogs`、`CategoryUpdateLockManager`、`retryUpdateCategoryCount`、`findWithInvertedIndex` 均不存在。本次重构需从头实现。

### 目标
- **彻底消除缓存一致性问题**：移除 `Category.Status.sentenceCount`，所有读路径改为实时 `countBy`
- **修复 SentenceReconciler 数据一致性问题**：删除时清理 SimilarityCheckLog 脏数据
- **强化数据验证**：sentence 创建/更新时严格校验 categoryName
- **性能优化**：批量查询避免 N+1，单次 `listAll` + 内存分组
- **测试覆盖**：为新增组件编写单元测试，运行全部已有测试确保无回归

## 架构概览

```
读路径（实时计数）：
  CategoryConsoleEndpoint  ──┐
  CategoryPublicEndpoint   ──┼──> CategoryCountService.getCounts() ──> listAll(Sentence) + 内存分组
  HitokotoFinderImpl       ──┤                                        (单次查询，O(N))
  OverviewConsoleEndpoint  ──┘

写路径（不再维护缓存）：
  SentenceReconciler
    ├─ 创建/更新：仅做 categoryName 校验与归一化（→ uncategorized）
    └─ 删除：调用 SimilarityCheckService.cleanupReferencesForDeletedSentence(name)
              清理 SimilarityCheckLog.spec.similarPairs 中引用被删句子的 pair

启动：
  UncategorizedCategoryInitializer（@PostConstruct）
    确保 uncategorized Category 实体存在（兜底）
```

## 实施步骤

### Step 1：移除 `Category.Status.sentenceCount` 字段

**文件**：[`src/main/java/top/puresky/hitokotohub/extension/Category.java`](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/extension/Category.java)

- 删除 `Status` 类中的 `sentenceCount` 字段
- 保留空的 `Status` 类（Halo 扩展 spec/status 约定，向前兼容）
- 添加注释说明设计决策：`// sentenceCount 已移除，改为通过 CategoryCountService 实时查询`

### Step 2：新增 `CategoryCountService`

**新文件**：`src/main/java/top/puresky/hitokotohub/service/CategoryCountService.java`

```java
public interface CategoryCountService {
    /** 返回所有分类的 sentenceCount 映射（含 0）。单次 listAll + 内存分组，O(N)。 */
    Mono<Map<String, Long>> getAllCounts();

    /** 返回指定分类的 sentenceCount。 */
    Mono<Long> getCount(String categoryName);
}
```

**新文件**：`src/main/java/top/puresky/hitokotohub/service/impl/CategoryCountServiceImpl.java`

实现要点：
- `getAllCounts()`：先 `listAll(Category.class)` 获取所有分类名（确保 0 计数分类也出现），再 `listAll(Sentence.class)`，按 `spec.categoryName` 内存分组计数
- `getCount(name)`：`countBy(Sentence.class, equal("spec.categoryName", name))`
- 过滤掉 `metadata.deletionTimestamp != null` 的 sentence（已删除）
- 使用 `Schedulers.boundedElastic()` 包裹内存分组（避免阻塞 reactor 线程）

### Step 3：新增 `CategoryConsoleEndpoint`（前端取数）

**新文件**：`src/main/java/top/puresky/hitokotohub/endpoint/CategoryConsoleEndpoint.java`

提供 `GET /api/console.api.hitokotohub.puresky.top/v1alpha1/categories`：
- 入参：`page`、`size`（分页）
- 出参：`ListResult<CategoryWithCount>`，其中 `CategoryWithCount` 含完整 Category 字段 + `sentenceCount`
- 实现：
  1. `listBy(Category.class, ..., pageRequest)` 拿分页分类
  2. `categoryCountService.getAllCounts()` 拿计数 map（仅一次）
  3. 合并：为每个分类注入 `sentenceCount`
- 注意：因 `Category.Status.sentenceCount` 已移除，前端无法直接读 `status.sentenceCount`，必须通过此端点

### Step 4：重构 `SentenceReconciler`

**文件**：[`src/main/java/top/puresky/hitokotohub/reconciler/SentenceReconciler.java`](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/reconciler/SentenceReconciler.java)

重构后职责：
- **创建/更新分支**：仅做 categoryName 校验与归一化（→ uncategorized），不再触发 Category 计数更新
- **删除分支**：调用 `SimilarityCheckService.cleanupReferencesForDeletedSentence(sentenceName)`，清理 SimilarityCheckLog 脏数据
- **移除**：`updateCategoryCount` 方法整体删除
- **移除**：`sentence.getMetadata().setFinalizers(Collections.emptySet())`（当前 sentence 无 finalizer，此为 no-op）
- **新增**：`@Slf4j` 日志（关键决策点记录）
- **新增**：数据验证（content 非空、author/source 默认值兜底）
- **保留**：`Result.doNotRetry()` 返回策略（异常时 Halo 自动重试）

伪代码：
```java
@Slf4j
@Component
@RequiredArgsConstructor
public class SentenceReconciler implements Reconciler<Reconciler.Request> {
    private final ExtensionClient client;
    private final SimilarityCheckService similarityCheckService;

    @Override
    public Result reconcile(Request request) {
        client.fetch(Sentence.class, request.name()).ifPresent(sentence -> {
            if (ExtensionOperator.isDeleted(sentence)) {
                handleDeletion(sentence);
                return;
            }
            normalizeCategory(sentence);
        });
        return Result.doNotRetry();
    }

    private void handleDeletion(Sentence sentence) {
        String sentenceName = sentence.getMetadata().getName();
        log.info("清理已删除句子的相似度引用: {}", sentenceName);
        similarityCheckService.cleanupReferencesForDeletedSentence(sentenceName)
            .doOnError(e -> log.error("清理相似度引用失败: {}", sentenceName, e))
            .subscribe();  // 异步清理，不阻塞 reconcile
    }

    private void normalizeCategory(Sentence sentence) {
        String categoryName = sentence.getSpec().getCategoryName();
        if (categoryName == null || categoryName.isBlank()
            || client.fetch(Category.class, categoryName).isEmpty()) {
            if (!UncategorizedConstants.METADATA_NAME.equals(categoryName)) {
                log.debug("句子 {} 的分类 {} 无效，归入未分类", 
                    sentence.getMetadata().getName(), categoryName);
                sentence.getSpec().setCategoryName(UncategorizedConstants.METADATA_NAME);
                client.update(sentence);
            }
        }
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new Sentence()).build();
    }
}
```

### Step 5：扩展 `SimilarityCheckService` 增加清理方法

**文件**：[`src/main/java/top/puresky/hitokotohub/service/SimilarityCheckService.java`](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/SimilarityCheckService.java)

新增接口方法：
```java
/**
 * 清理所有 SimilarityCheckLog 中引用了已删除句子的相似对。
 * 仅处理最新一条 SUCCESS 日志（旧日志已被 deleteOldLogs 清理）。
 *
 * @param deletedSentenceName 被删除句子的 metadata.name
 * @return 完成信号
 */
Mono<Void> cleanupReferencesForDeletedSentence(String deletedSentenceName);
```

**文件**：[`src/main/java/top/puresky/hitokotohub/service/impl/SimilarityCheckServiceImpl.java`](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/impl/SimilarityCheckServiceImpl.java)

实现要点：
- 复用 `getLatestSuccessLog()` 拿最新日志
- 反序列化 `spec.similarPairs` 为 `List<SentencePair>`
- 过滤掉 `sentence1Name.equals(deletedSentenceName) || sentence2Name.equals(deletedSentenceName)` 的 pair
- 重新序列化、更新 `similarPairCount`、`client.update(log)`
- 若过滤后为空，将 `similarPairs` 设为 `"[]"`，`similarPairCount` 设为 0
- 异常时记 log，不抛出（避免影响 reconcile）

### Step 6：更新 `CategoryPublicEndpoint` 使用实时计数

**文件**：[`src/main/java/top/puresky/hitokotohub/endpoint/CategoryPublicEndpoint.java`](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/CategoryPublicEndpoint.java)

- 注入 `CategoryCountService`
- `listCategories()`：`listAll(Category)` + `categoryCountService.getAllCounts()` 并行，合并后返回 `CategoryItem` 列表
- 移除对 `category.getStatus().getSentenceCount()` 的依赖

### Step 7：更新 `HitokotoFinderImpl.listCategories()` 使用实时计数

**文件**：[`src/main/java/top/puresky/hitokotohub/finder/impl/HitokotoFinderImpl.java`](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/finder/impl/HitokotoFinderImpl.java)

- 注入 `CategoryCountService`
- `listCategories()`：先 `listAll(Category)`，再 `getAllCounts()` 合并
- 保留原有过滤逻辑：`sentenceCount > 0` 才展示
- `toCategoryVo()` 改为接收 count 参数

### Step 8：更新 `OverviewConsoleEndpoint.getOverview()` 使用实时计数

**文件**：[`src/main/java/top/puresky/hitokotohub/endpoint/OverviewConsoleEndpoint.java`](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/OverviewConsoleEndpoint.java)

- 注入 `CategoryCountService`
- `getOverview()` 第 87 行 `long totalCount = category.getStatus().getSentenceCount()` 改为从 `getAllCounts()` map 取
- 复用已有的 `client.countBy(Sentence.class, ...)` 获取已发布数（这部分已经是实时查询，保留）

### Step 9：新增 `UncategorizedCategoryInitializer`

**新文件**：`src/main/java/top/puresky/hitokotohub/init/UncategorizedCategoryInitializer.java`

确保系统启动时 `uncategorized` Category 实体存在：
- 实现 `ApplicationRunner` 或 `@PostConstruct`
- 启动时 `client.fetch(Category.class, "uncategorized")`，若不存在则创建
- 字段：`metadata.name = "uncategorized"`、`spec.name = "未分类"`、`spec.description = "系统内置分类..."`
- 失败时仅 log.error，不阻止插件启动

### Step 10：更新前端 `CategoryList.vue`

**文件**：[`ui/src/components/CategoryList.vue`](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/components/CategoryList.vue)

- 替换 `categoryCoreApiClient.category.listCategory` 调用为新端点
- 在 `ui/src/api/` 下新增对应的 API 客户端方法（手写 fetch 或基于现有模式）
- 数据访问从 `category.status?.sentenceCount` 改为 `category.sentenceCount`（新端点返回扁平字段）
- 保留分页、轮询、删除等所有现有交互逻辑不变

### Step 11：更新 `TestFixtures`

**文件**：[`src/test/java/top/puresky/hitokotohub/support/TestFixtures.java`](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/test/java/top/puresky/hitokotohub/support/TestFixtures.java)

- 移除 `category(name, displayName, sentenceCount)` 重载（或保留但内部不再设置 status）
- `category(name, displayName)` 不再调用 `c.setStatus(new Category.Status())`（Status 现在是空类，无需初始化）

### Step 12：增强 `MockExtensionClient` 支持字段过滤（可选）

**文件**：[`src/test/java/top/puresky/hitokotohub/support/MockExtensionClient.java`](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/test/java/top/puresky/hitokotohub/support/MockExtensionClient.java)

当前 mock 的 `countBy` 返回该类型全部记录数，不支持 fieldSelector 过滤。`CategoryCountServiceImpl` 内部使用 `listAll(Sentence.class)` + 内存分组，**不依赖 countBy 过滤**，因此 mock 现状可用，无需增强。

若 `CategoryCountServiceImpl` 最终选择 `countBy` + fieldSelector 实现，则需要增强 mock。**推荐采用 listAll + 内存分组方案以避免改 mock**。

## 测试计划

### 新增单元测试

**新文件**：`src/test/java/top/puresky/hitokotohub/service/impl/CategoryCountServiceImplTest.java`

测试用例：
- 单分类多句子 → count 正确
- 多分类多句子 → 每个分类 count 正确
- 空分类（无句子）→ count = 0 仍出现在 map 中
- 包含已删除句子（deletionTimestamp 非 null）→ 不计入 count
- 异常数据（categoryName 为 null）→ 不计入任何分类 count

**新文件**：`src/test/java/top/puresky/hitokotohub/reconciler/SentenceReconcilerTest.java`

测试用例（需要 mock `ExtensionClient` 与 `SimilarityCheckService`）：
- 创建 sentence 时 categoryName 有效 → 不做变更
- 创建 sentence 时 categoryName 为 null → 归入 uncategorized
- 创建 sentence 时 categoryName 不存在 → 归入 uncategorized
- 创建 sentence 时 categoryName = "uncategorized" 但分类不存在 → 不触发无限循环
- 删除 sentence → 调用 `cleanupReferencesForDeletedSentence`
- `cleanupReferencesForDeletedSentence` 抛异常 → reconcile 不抛出（异步 subscribe）

**扩展**：`SimilarityCheckServiceImplTest.java` 新增测试用例：
- `cleanupReferencesForDeletedSentence`：被删句子在 pair 中 → pair 被过滤
- `cleanupReferencesForDeletedSentence`：被删句子不在任何 pair 中 → 日志不变
- `cleanupReferencesForDeletedSentence`：所有 pair 都含被删句子 → similarPairs 变为 `[]`，count 变为 0
- `cleanupReferencesForDeletedSentence`：无 SUCCESS 日志 → 返回 Mono.empty，无副作用

### 回归测试

运行全部已有测试：
```bash
./gradlew test
```

预期：
- `SimilarityCheckServiceImplTest` 现有 5 个用例需要因 `TestFixtures.category(name, displayName, sentenceCount)` 移除而调整
- `EchartsDataBuilderTest`、`SimilarityGroupScorerTest`、`SentencePairTest` 等不涉及 Category.Status，应无影响
- `SentenceSimilarityTest`、`TextSimilarityTest`、`UnionFindTest` 等纯算法测试无影响

## 验证步骤（端到端）

1. **编译验证**：`./gradlew build` 通过
2. **单元测试**：`./gradlew test` 全绿
3. **手动验证场景**：
   - 启动 Halo，安装插件
   - 创建分类 A，添加 3 条句子 → 前端显示 "3 条句子"
   - 创建分类 B，把 1 条句子从 A 移到 B → A 显示 "2 条"，B 显示 "1 条"（验证跨分类迁移）
   - 删除分类 A 下 1 条句子 → A 显示 "1 条"
   - 触发相似度检查 → 删除相似组中的非最优句子 → 再次查看相似度分组，无"鬼魂句子"
   - 重启插件 → uncategorized 分类存在
4. **并发测试**：批量删除同一分类下 20 条句子 → 无乐观锁异常日志（因为不再更新 Category）
5. **性能验证**：1000 条句子场景下，分类列表接口响应时间 < 200ms（单次 listAll 内存分组）

## 风险评估与缓解

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| 前端 API 客户端需手写 | 改动量较大 | 参考现有 `categoryCoreApiClient` 模式，使用 `axios` 直接调用 |
| `Category.Status` 字段移除后已有数据库记录残留 | 旧数据反序列化时多出 `sentenceCount` 字段 | Jackson 默认忽略未知字段，无影响 |
| `cleanupReferencesForDeletedSentence` 异步执行失败 | 脏数据残留 | log.error 记录，下次相似度检查会重算覆盖 |
| `UncategorizedCategoryInitializer` 启动失败 | uncategorized 分类不存在 | log.error 不阻断启动；后续创建 sentence 时归一化逻辑会兜底 |
| 删除 sentence 时不再更新 Category → Category 无写操作 | 性能提升（无锁冲突） | 正是设计目标 |
| `listAll(Sentence.class)` 全量加载 | 句子量极大时内存压力 | 项目场景下句子通常 < 10k，可接受；后续可加分页或缓存 |

## 涉及文件清单

**新增**：
- `src/main/java/top/puresky/hitokotohub/service/CategoryCountService.java`
- `src/main/java/top/puresky/hitokotohub/service/impl/CategoryCountServiceImpl.java`
- `src/main/java/top/puresky/hitokotohub/endpoint/CategoryConsoleEndpoint.java`
- `src/main/java/top/puresky/hitokotohub/init/UncategorizedCategoryInitializer.java`
- `src/test/java/top/puresky/hitokotohub/service/impl/CategoryCountServiceImplTest.java`
- `src/test/java/top/puresky/hitokotohub/reconciler/SentenceReconcilerTest.java`

**修改**：
- `src/main/java/top/puresky/hitokotohub/extension/Category.java`
- `src/main/java/top/puresky/hitokotohub/reconciler/SentenceReconciler.java`
- `src/main/java/top/puresky/hitokotohub/service/SimilarityCheckService.java`
- `src/main/java/top/puresky/hitokotohub/service/impl/SimilarityCheckServiceImpl.java`
- `src/main/java/top/puresky/hitokotohub/endpoint/CategoryPublicEndpoint.java`
- `src/main/java/top/puresky/hitokotohub/endpoint/OverviewConsoleEndpoint.java`
- `src/main/java/top/puresky/hitokotohub/finder/impl/HitokotoFinderImpl.java`
- `ui/src/components/CategoryList.vue`
- `ui/src/api/` 下新增对应 API 客户端
- `src/test/java/top/puresky/hitokotohub/support/TestFixtures.java`
- `src/test/java/top/puresky/hitokotohub/service/impl/SimilarityCheckServiceImplTest.java`（新增测试用例）

**不修改**：
- `SentenceConsoleEndpoint.java`（与本次重构无关）
- `SentencePublicEndpoint.java`、`SentenceSubmissionConsoleEndpoint.java`、`SentenceSubmissionPublicEndpoint.java`
- `AiGenerateServiceImpl.java`、`AiGenerateLogConsoleEndpoint.java`
- `CategoryReconciler.java`（已稳定）
- `StatsCleanupScheduler.java`（已稳定）
- 相似度算法层 `service/similarity/*`（纯算法无 IO）
