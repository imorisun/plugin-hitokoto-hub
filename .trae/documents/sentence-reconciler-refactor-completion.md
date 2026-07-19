# SentenceReconciler 重构收尾方案（剩余工作）

## Context

### 背景

主重构方案 [`sentence-reconciler-refactor-realtime-count.md`](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/.trae/documents/sentence-reconciler-refactor-realtime-count.md) 已被用户批准并执行至 Step 9（共 12 步）。本方案聚焦主方案中**尚未完成**的剩余工作，不再重复已确认的设计决策。

### 已完成工作核查（基于当前 git status + 文件实际状态）

| 步骤 | 内容 | 状态 |
|---|---|---|
| Step 1 | 移除 `Category.Status.sentenceCount` | ✅ 已完成 |
| Step 2 | 新增 `CategoryCountService` + `CategoryCountServiceImpl` | ✅ 已完成 |
| Step 3 | 新增 `CategoryConsoleEndpoint` | ✅ 已完成 |
| Step 4 | 重构 `SentenceReconciler` | ✅ 已完成 |
| Step 5 | `SimilarityCheckService` 扩展清理方法 | ✅ 已完成 |
| Step 6 | `CategoryPublicEndpoint` 改用实时计数 | ✅ 已完成 |
| Step 7 | `HitokotoFinderImpl` 改用实时计数 | ✅ 已完成 |
| Step 8 | `OverviewConsoleEndpoint` 改用实时计数 | ✅ 已完成 |
| Step 9 | 新增 `UncategorizedCategoryInitializer` | ✅ 已完成 |
| Step 10 | 前端 `CategoryList.vue` 适配新端点 | ❌ 未完成 |
| Step 11 | 修复 `TestFixtures` 编译错误 | ❌ 未完成（**编译阻断**） |
| Step 12 | `MockExtensionClient` 增强（可选） | ✅ 无需修改（采用 listAll + 内存分组方案） |
| 测试计划 | 新增/扩展单元测试 | ❌ 未完成 |
| 验证 | `./gradlew build` 编译 + 全测试 | ❌ 未运行 |

### 当前阻断问题

`src/test/java/top/puresky/hitokotohub/support/TestFixtures.java:72-77` 仍存在以下方法：

```java
public static Category category(String name, String displayName, long sentenceCount) {
    Category c = category(name, displayName);
    c.getStatus().setSentenceCount(sentenceCount);  // ← Category.Status 已是空类，无此 setter
    return c;
}
```

由于 `Category.Status` 已重构为空类（移除 `sentenceCount` 字段），Lombok `@Data` 不再生成 `setSentenceCount` 方法，**此行编译失败**，导致整个测试模块无法编译，进而阻断所有测试运行。

经全代码库搜索确认，**没有任何测试代码实际调用此三参重载**（`rg "TestFixtures\.category\("` 返回空，且没有静态导入使用此方法的代码）。删除安全。

### 目标

1. 解除测试编译阻断（修复 `TestFixtures`）
2. 前端切换至新端点取数
3. 补齐新增组件的单元测试
4. 运行全量构建验证编译 + 测试通过

---

## 剩余实施步骤

### Task 1：修复 TestFixtures 编译错误（Step 11）

**文件**：[`src/test/java/top/puresky/hitokotohub/support/TestFixtures.java`](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/test/java/top/puresky/hitokotohub/support/TestFixtures.java)

**操作**：删除三参重载方法（第 72-77 行）：

```java
/** 构造带句子数的 Category。 */
public static Category category(String name, String displayName, long sentenceCount) {
    Category c = category(name, displayName);
    c.getStatus().setSentenceCount(sentenceCount);
    return c;
}
```

**保留**：两参版本 `category(String name, String displayName)` 不变（其中 `c.setStatus(new Category.Status())` 仍然合法，因为 `Status` 是空类，构造空对象仍有效）。

**理由**：
- 三参方法使用已删除的 `setSentenceCount`，编译失败
- 全代码库无引用，删除安全
- 保留两参方法不动，避免连锁修改其他测试

---

### Task 2：前端 CategoryList.vue 适配新端点（Step 10）

#### 2.1 新增 API 客户端方法

**文件**：[`ui/src/api/index.ts`](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/api/index.ts)

在 `categoryCoreApiClient` 对象中新增 `listCategoriesWithCounts` 方法，调用新端点 `GET /apis/console.api.hitokotohub.puresky.top/v1alpha1/categories`：

```typescript
interface CategoryWithCount {
  metadata: { name: string; [k: string]: any }
  spec: { name: string; description?: string }
  status: Record<string, never>
  sentenceCount: number
}
interface ListResultCategoryWithCount {
  page: number
  size: number
  total: number
  items: CategoryWithCount[]
}

const categoryCoreApiClient = {
  category: new CategoryV1alpha1Api(undefined, '', axiosInstance),
  // 新增：带实时句子数量的分类列表查询
  listCategoriesWithCounts: (params: { page?: number; size?: number }) =>
    axiosInstance.get<ListResultCategoryWithCount>(
      '/apis/console.api.hitokotohub.puresky.top/v1alpha1/categories',
      { params }
    ),
}
```

**理由**：
- 直接复用 `axiosInstance`（已统一鉴权 + baseURL），不引入新依赖
- 不修改 `generated/` 目录（由 OpenAPI Generator 生成，避免被覆盖）
- 类型 `CategoryWithCount` 与后端 `CategoryConsoleEndpoint.CategoryWithCount` 一一对应

#### 2.2 改造 CategoryList.vue 取数与展示

**文件**：[`ui/src/components/CategoryList.vue`](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/components/CategoryList.vue)

**改动点**：

1. **替换取数调用**（`fetchCategories` 与 `fetchCategoriesSilently` 中）：
   - 旧：`categoryCoreApiClient.category.listCategory({ page, size })`，返回 `data.items` 为 `Category[]`
   - 新：`categoryCoreApiClient.listCategoriesWithCounts({ page, size })`，返回 `data.items` 为 `CategoryWithCount[]`

2. **替换 `sentenceCount` 读取路径**：
   - 旧（两处：常规分类 + 未分类）：`category.status?.sentenceCount ?? 0`
   - 新：`category.sentenceCount ?? 0`（字段扁平化，从顶层直接读）

3. **类型适配**：
   - `categories` ref 类型改为 `CategoryWithCount[]`（兼容 Category 的字段子集）
   - `formCategory` 保留为 `CategoryWithCount | null`
   - `isDeleting` 判断逻辑不变（仍读 `metadata.deletionTimestamp`）

4. **保持不变的部分**：
   - 分页、轮询、删除、清空未分类、新建/编辑表单等所有交互逻辑
   - `categoryCoreApiClient.category.createCategory/updateCategory/deleteCategory/getCategory`（这些是 CRUD 操作，仍走原生 Category 资源端点，不涉及 `sentenceCount`）
   - `sentenceCoreApiClient.clearUncategorizedSentences` 不变

**理由**：
- 仅替换"读路径"，不动"写路径"，最小化改动面
- 保留 `CategoryV1alpha1Api` 的 CRUD 调用，避免改写 generated API client

---

### Task 3：新增 `CategoryCountServiceImplTest` 单元测试

**新文件**：`src/test/java/top/puresky/hitokotohub/service/impl/CategoryCountServiceImplTest.java`

**测试用例**（基于 `MockExtensionClient.builder()`）：

1. `getAllCounts_单分类多句子_计数正确`
   - 准备：1 个 Category `cat-a`，3 个 Sentence（categoryName=`cat-a`）
   - 断言：返回 map 为 `{cat-a: 3}`

2. `getAllCounts_多分类多句子_每个分类计数正确`
   - 准备：2 个 Category `cat-a`、`cat-b`，4 个 Sentence（3 个 `cat-a` + 1 个 `cat-b`）
   - 断言：`{cat-a: 3, cat-b: 1}`

3. `getAllCounts_空分类_计数为0仍出现在Map中`
   - 准备：1 个 Category `cat-a`，无 Sentence
   - 断言：`{cat-a: 0}`（确保 0 计数分类也出现）

4. `getAllCounts_包含已删除句子_不计入`
   - 准备：1 个 Category `cat-a`，2 个 Sentence + 1 个 Sentence（`metadata.deletionTimestamp` 非空）
   - 断言：`{cat-a: 2}`
   - **注意**：`MockExtensionClient` 的 `listAll` 不实现 fieldSelector 过滤（返回全部）。需要在 `buildCountsMap` 内部已经做了 `deletionTimestamp != null` 的 double-check 防御（见 [CategoryCountServiceImpl.java:97](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/impl/CategoryCountServiceImpl.java#L97)）。此测试验证该防御逻辑。

5. `getAllCounts_异常数据_categoryName为null_不计入任何分类`
   - 准备：1 个 Category `cat-a`，1 个 Sentence（`spec.categoryName=null`）+ 1 个 Sentence（`spec.categoryName=""`）+ 1 个 Sentence（`spec.categoryName=cat-a`）
   - 断言：`{cat-a: 1}`（异常数据被跳过）

6. `getCount_指定分类存在_返回正确数量`
   - 准备：1 个 Category `cat-a`，3 个 Sentence（`cat-a`）
   - 调用 `getCount("cat-a")`
   - 断言：返回 3
   - **注意**：`MockExtensionClient.countBy` 不实现 fieldSelector 过滤，会返回所有 Sentence 数量。本测试需要单独 mock `countBy`，或在 `getAllCounts` 测试中已覆盖计数逻辑后，此用例改为验证 null/blank 入参返回 0 的边界行为。

7. `getCount_入参为null_返回0`、`getCount_入参为空_返回0`
   - 断言：返回 0（边界处理）

**理由**：覆盖 5 个核心场景（正常/边界/异常/防御），与主方案测试计划一致。

---

### Task 4：新增 `SentenceReconcilerTest` 单元测试

**新文件**：`src/test/java/top/puresky/hitokotohub/reconciler/SentenceReconcilerTest.java`

**测试策略**：`SentenceReconciler` 注入 `ExtensionClient`（注意是同步版 `ExtensionClient`，非 `ReactiveExtensionClient`）+ mock `SimilarityCheckService`。

**测试用例**：

1. `reconcile_创建sentence_categoryName有效_不修改`
   - 准备：Sentence categoryName=`cat-a`，Category `cat-a` 存在
   - 调用 `reconcile(Request.of("s1"))`
   - 断言：`client.update()` 未被调用；Sentence.categoryName 仍为 `cat-a`

2. `reconcile_创建sentence_categoryName为null_归入uncategorized`
   - 准备：Sentence categoryName=null
   - 调用 `reconcile`
   - 断言：`client.update()` 被调用一次；sentence.spec.categoryName 被设为 `uncategorized`

3. `reconcile_创建sentence_categoryName不存在_归入uncategorized`
   - 准备：Sentence categoryName=`ghost-cat`，无对应 Category
   - 调用 `reconcile`
   - 断言：sentence.spec.categoryName 被改为 `uncategorized`；`client.update()` 被调用一次

4. `reconcile_创建sentence_categoryName为uncategorized但分类不存在_不触发无限循环`
   - 准备：Sentence categoryName=`uncategorized`，无 uncategorized Category
   - 调用 `reconcile`
   - 断言：`client.update()` **未被调用**（防止无限 reconcile 循环）

5. `reconcile_删除sentence_触发SimilarityCheck清理`
   - 准备：Sentence 含 `metadata.deletionTimestamp`（已标记删除）
   - 调用 `reconcile`
   - 断言：`similarityCheckService.cleanupReferencesForDeletedSentence("s1")` 被调用一次
   - **注意**：reconciler 内部使用 `subscribe()` 异步触发，测试需用 `StepVerifier` 或 `await` 等待，或改为 mock 验证 `cleanupReferencesForDeletedSentence` 返回 `Mono.empty()` 并被订阅。

6. `reconcile_清理失败_不抛出异常`
   - 准备：mock `cleanupReferencesForDeletedSentence` 返回 `Mono.error(new RuntimeException("清理失败"))`
   - 调用 `reconcile`（删除分支）
   - 断言：`reconcile` 正常返回 `Result.doNotRetry()`，不抛出（subscribe 的错误由 `doOnError` 记录）

**Mock 模式**：
- `ExtensionClient`：用 Mockito mock（注意 `SentenceReconciler` 用的是同步 `ExtensionClient`，方法签名是 `Optional<T> fetch(...)`、`void update(...)`）
- `SimilarityCheckService`：用 Mockito mock，`cleanupReferencesForDeletedSentence` 返回 `Mono.empty()` 或 `Mono.error(...)`

**参考**：`MockExtensionClient` 当前是为 `ReactiveExtensionClient` 设计的，**不能直接用于 `SentenceReconciler`**（它依赖 `ExtensionClient` 同步 API）。需要使用 Mockito 手写 mock。

---

### Task 5：扩展 `SimilarityCheckServiceImplTest` 清理方法测试

**文件**：[`src/test/java/top/puresky/hitokotohub/service/impl/SimilarityCheckServiceImplTest.java`](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/test/java/top/puresky/hitokotohub/service/impl/SimilarityCheckServiceImplTest.java)

**新增测试用例**（追加到现有 5 个用例后）：

1. `cleanupReferencesForDeletedSentence_被删句子在pair中_pair被过滤`
   - 准备：构造 successLog，pairsJson 含 (s1,s2) 和 (s2,s3) 两个 pair
   - 调用 `cleanupReferencesForDeletedSentence("s2")`
   - 断言：日志被更新（`client.update` 被调用）；`similarPairCount` 变为 0；反序列化后 pairs 为空

2. `cleanupReferencesForDeletedSentence_被删句子不在任何pair中_日志不变`
   - 准备：successLog 含 (s1,s2)
   - 调用 `cleanupReferencesForDeletedSentence("s3")`
   - 断言：`client.update` **未被调用**（filterPairsExcludingSentence 返回 null，跳过写入）

3. `cleanupReferencesForDeletedSentence_所有pair都含被删句子_变为空数组`
   - 准备：successLog 含 (s1,s2) 和 (s2,s3)
   - 调用 `cleanupReferencesForDeletedSentence("s2")`
   - 断言：日志 `similarPairs` 设为 `"[]"`，`similarPairCount` 设为 0

4. `cleanupReferencesForDeletedSentence_无SUCCESS日志_返回empty无副作用`
   - 准备：无 SimilarityCheckLog 或只有 RUNNING 状态日志
   - 调用 `cleanupReferencesForDeletedSentence("s1")`
   - 断言：`client.update` 未被调用；返回 Mono 完成

5. `cleanupReferencesForDeletedSentence_入参为null_直接返回empty`
   - 调用 `cleanupReferencesForDeletedSentence(null)` 和 `cleanupReferencesForDeletedSentence("")`
   - 断言：直接返回 Mono.empty（防御性边界）

6. `cleanupReferencesForDeletedSentence_清理异常_不抛出`
   - 准备：mock `client.update` 返回 `Mono.error(...)`
   - 调用清理方法
   - 断言：返回的 Mono 正常 complete（`onErrorResume` 兜底）

**复用工具**：直接使用 `MockExtensionClient.builder()` + `TestFixtures.successLog()` + 真实 `SentencePairJsonCodec`。

---

### Task 6：运行全量构建与测试

**命令**：
```bash
./gradlew clean build
```

**验证项**：
1. **编译通过**：所有 main + test 模块编译成功（特别是 TestFixtures 修复后）
2. **测试全绿**：所有现有测试 + 新增测试通过
3. **无 lint 警告**（warnings 不视为失败）

**预期影响**：
- `SimilarityCheckServiceImplTest`：5 个旧用例 + 6 个新用例 = 11 个
- `CategoryCountServiceImplTest`：6 个新用例
- `SentenceReconcilerTest`：6 个新用例
- 其他测试（EchartsDataBuilderTest、SimilarityGroupScorerSmokeTest、SimilarityPairFinderTest、UnionFindTest、SentencePairJsonCodecTest、SentenceScorerTest、SimilarityGroupBuilderTest、TextSimilarityCalculatorTest、CategoryViewRecordFactoryTest、HttpUtilsTest、IpCooldownCacheTest、TimeFormatUtilsTest）：应无回归

**前端验证**：
```bash
cd ui && npm run build
```
验证 TypeScript 编译通过（CategoryList.vue 改造后类型正确）。

---

## Assumptions & Decisions

1. **不重写已有方案**：本计划只补齐主方案的剩余工作，不重新评估已批准的设计决策（如改用新端点而非扩展现有 generated API、采用 listAll + 内存分组而非 countBy 等）。

2. **不改写 generated API client**：前端在 `ui/src/api/index.ts` 中手写 axios 调用，不修改 `ui/src/api/generated/`（OpenAPI Generator 输出，避免下次生成时被覆盖）。后续若用户重新生成 client，可考虑把 `CategoryWithCount` 类型补入生成配置。

3. **不改 `MockExtensionClient`**：当前 mock 的 `listAll` 不实现 fieldSelector 过滤（返回全部），`CategoryCountServiceImpl.buildCountsMap` 内部已做 `deletionTimestamp != null` 的 double-check 防御，测试通过。若未来有其他测试需要精确过滤，再增强。

4. **SentenceReconciler 测试用纯 Mockito mock**：因 `SentenceReconciler` 依赖同步 `ExtensionClient`（非 ReactiveExtensionClient），`MockExtensionClient` 不适用。新测试使用 Mockito 手写 stub。

5. **前端 `CategoryWithCount` 类型与 `Category` 兼容**：CRUD 表单仍用旧 `Category` 类型；只有列表展示用 `CategoryWithCount`。`formCategory` 在编辑时取自列表项，可直接作为 `Category` 使用（结构子集兼容）。

6. **不补充 `SentenceReconciler` 的 content/author/source 数据验证**：主方案 Step 4 提到"新增数据验证（content 非空、author/source 默认值兜底）"，但当前 `SentenceReconciler` 实现已聚焦核心问题（categoryName 归一化 + 删除清理），未实现数据验证。本次收尾**不扩展**该范围，避免引入未经用户确认的新功能。如需补强数据验证，应作为独立任务提交。

---

## 涉及文件清单

**新增**：
- `src/test/java/top/puresky/hitokotohub/service/impl/CategoryCountServiceImplTest.java`
- `src/test/java/top/puresky/hitokotohub/reconciler/SentenceReconcilerTest.java`

**修改**：
- `src/test/java/top/puresky/hitokotohub/support/TestFixtures.java`（删除三参重载）
- `ui/src/api/index.ts`（新增 `listCategoriesWithCounts` 方法 + 类型）
- `ui/src/components/CategoryList.vue`（替换取数调用 + `sentenceCount` 读取路径）
- `src/test/java/top/puresky/hitokotohub/service/impl/SimilarityCheckServiceImplTest.java`（追加 6 个清理方法测试用例）

**不修改**：
- 所有 main 代码（已完成重构）
- `MockExtensionClient.java`（无需增强）
- `generated/` 目录（OpenAPI Generator 输出）
- 其他测试文件（无回归影响）

---

## 验证步骤

1. **Step 11 修复后**：`./gradlew compileTestJava` 通过
2. **前端改造后**：`cd ui && npm run build` 通过
3. **新增测试编写后**：`./gradlew test` 全绿
4. **最终验证**：`./gradlew clean build` 通过
