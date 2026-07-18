# 轻言（hitokoto-hub）重构收尾执行计划

## 摘要

本计划承接已批准的 [hitokoto-hub-refactor-continuation.md](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/.trae/documents/hitokoto-hub-refactor-continuation.md)，从中断点恢复，完成剩余的步骤 5-9。**技术方案与三项关键决策不变**：全栈全面重构、hitokoto.html 拆分为 Thymeleaf fragment、建测试基础设施+核心路径测试。

本计划聚焦"完成"而非"重新决策"——所有架构选择已在前期批准，此处仅落地实现。

---

## 当前状态核实（2026-07-18，Phase 1 探索确认）

### ✅ 已完成（无需重做）

| 步骤 | 内容 | 核实结果 |
|------|------|----------|
| 0-3 | 编译修复/工具类/EchartsDataBuilder | ✅ 全部就位 |
| 4 | 算法层 9 文件 + 6 单测 | ✅ `service/similarity/` 9 文件 + `test/service/similarity/` 6 测试 + `test/support/` MockExtensionClient + TestFixtures |

**算法层 9 文件**（`src/main/java/top/puresky/hitokotohub/service/similarity/`）：
- 纯算法（零依赖）：SentenceProfile、SentencePair、TextSimilarityCalculator、UnionFind、SentenceScorer、SimilarityPairFinder、SimilarityMappers
- 边界层（@Component）：SimilarityGroupBuilder、SentencePairJsonCodec

**测试支持**（`src/test/java/top/puresky/hitokotohub/support/`）：
- MockExtensionClient（7100 字节）：`builder().with(ext).build()`，内存 fake，覆盖 8 个 ReactiveExtensionClient 方法，Mono/Flux 同步完成支持 `.block()`
- TestFixtures（5944 字节）：`sentence()`/`category()`/`successLog()`/`pair()`/`submission()`/`viewRecord()` 工厂方法

### ❌ 待完成（本计划聚焦）

| 步骤 | 内容 | 现状（已核实） |
|------|------|----------|
| 5 | SimilarityCheckServiceImpl 重构 | 898 行，UnionFind(L905-986)+SimilarityResult(L255-260) 内嵌，未引用 similarity 包 |
| 6 | 前端共享抽象 | `ui/src/` 无 styles/、composables/ 目录 |
| 7 | 6 个前端组件重构 | SentenceList 1420 / SimilarityCheck 1370 / Overview 1209 / SubmissionList 749 / AiGenerateLogList 605 / CategoryList 486 行 |
| 8 | hitokoto.html 拆分 | 2533 行：`<style>` L20-1446（1426 行）、body L1448-1615（168 行）、`<script>` L1616-2819（1203 行） |
| 9 | 端到端验证 | 待执行 |

### 关键文件结构确认

**SimilarityCheckServiceImpl.java**（898 行）：
- 3 个 public API（签名绝对不变）：`performCheck`(L84-105)、`getGroups`(L116-123)、`deleteNonOptimalSentences`(L138-157)
- 构造函数（L59-63）：注入 `ReactiveExtensionClient client` + `ObjectMapper objectMapper`
- 需删除（~620 行）：`SimilarityResult` record(L255-260)、嵌套 `UnionFind` 类(L905-986)、`calculateSimilarPairs`/`buildPair`/`tokenizeToSet`/`computeTfVector`/`computeIdf`/`computeTfidfVector`/`cosineSimilarity`/`vectorNorm`/`jaccardSimilarity`/`buildGroupsResult`/`buildGroup`/`buildSimilarityMap`/`similarityKey`/`getSimilarity`/`buildSentenceInfo`/`paginateGroups`/`emptyGroupsResult`/`collectNonOptimalNames`(旧)/`serializePairs`/`parseSimilarPairs`
- 需保留（~280 行）：`createInitialLog`/`executeCheck`(改写)/`deleteOldLogs`/`populateEmptyResult`/`deleteSentencesSerially`/`getLatestSuccessLog`/`fetchAllSentences`/`fetchSentencesByName`
- `MAX_STORED_PAIRS = 500`(L54) 保留

**SimilarityGroup.java**：`scoreSentence(Sentence)` 静态方法在 L53-96，需标 `@Deprecated` 并委托 `SentenceScorer.score(SentenceProfile.from(sentence))`

**hitokoto.html 关键结构**：
- L28-29 已有 `:root { --bg: #0a0606; --bg-glass: rgba(...); }` CSS 变量定义，可在此扩展 rose/z-index 变量
- 5 个 Thymeleaf 注入点：`th:attr` data-theme/sakura/hint(L1459)、`th:each` randomSentences(L1511)、`th:text` content/author(L1515,1518)
- body 主体 L1448-1615 含：SVG 噪点滤镜、#templateConfig、背景层、header(主题切换/提交/点赞)、main(句子展示)、footer(Next)、toast、提交弹窗、自定义下拉

---

## 执行计划

### 步骤 5：SimilarityCheckServiceImpl 重构 + shadow-compare + 集成测试

#### 5.1 创建 shadow-compare 等价性验证测试（先做，安全网）

**新建** `src/test/java/top/puresky/hitokotohub/service/similarity/SimilarityAlgorithmParityTest.java`：

**目的**：在重构 ServiceImpl 前，验证旧逻辑（ServiceImpl 内嵌算法）与新逻辑（similarity 包）输出完全一致。

**实现要点**：
- 构造 15 个 Sentence（TestFixtures.sentence）：3 组重复内容 + 2 组相似内容 + 5 个独立内容
- 旧逻辑：`new SimilarityCheckServiceImpl(mockClient, objectMapper)`，反射调用 private `calculateSimilarPairs`、`buildGroupsResult`、`collectNonOptimalNames`
- 新逻辑：`SimilarityPairFinder.find(profiles, algo, threshold)` + `SimilarityGroupBuilder.buildGroups(...)` + `collectNonOptimalNames(...)`
- 断言（COSINE 和 JACCARD 各跑一遍）：
  1. 相似对数量一致
  2. 每对相似度值一致（含 `Math.round(sim*10000)/10000.0` 截断）
  3. 分组数量一致
  4. 每组 bestSentence name 一致
  5. 每组 similarCount 一致
  6. 待删除 name 集合一致

**关键风险点**：`Math.round(similarity * 10000) / 10000.0` 截断在两路径中顺序一致（旧版在 `buildPair` L349 截断；新版在 `SimilarityPairFinder.find` 内部截断）

#### 5.2 重构 SimilarityCheckServiceImpl（898 → ~280 行）

**修改** [SimilarityCheckServiceImpl.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/impl/SimilarityCheckServiceImpl.java)：

**新增构造注入**：
```java
private final SimilarityGroupBuilder groupBuilder;  // @Component
private final SentencePairJsonCodec codec;          // @Component
// SimilarityPairFinder/TextSimilarityCalculator/SentenceScorer/UnionFind/SimilarityMappers 是静态/工具类，无需注入
```

**新增边界转换方法**（~30 行）：
```java
private List<SentenceProfile> toProfiles(List<Sentence> sentences) {
    return sentences.stream().map(SentenceProfile::from).toList();
}
private Map<String, SentenceProfile> toProfileMap(List<Sentence> sentences) {
    return sentences.stream().collect(Collectors.toMap(
        s -> s.getMetadata().getName(),
        SentenceProfile::from, (a, b) -> a, HashMap::new));
}
```

**executeCheck 改写**（核心）：
```java
List<SentenceProfile> profiles = toProfiles(sentences);
List<SentencePair> pairs = SimilarityPairFinder.find(profiles, algorithm, threshold);
List<SentencePair> storedPairs = pairs.size() > MAX_STORED_PAIRS
    ? pairs.subList(0, MAX_STORED_PAIRS) : pairs;
String pairsJson = codec.serialize(storedPairs);
// 填充 logEntry spec（totalSentences/totalPairs/similarPairCount/similarPairs/status/durationMs）
```

**getGroups 改写**：
```java
List<SentencePair> pairs = codec.deserialize(latestLog.getSpec().getSimilarPairs());
List<Sentence> sentences = fetchAllSentences().block();
Map<String, SentenceProfile> profileMap = toProfileMap(sentences);
// 过滤已删除句子：profileMap 不含的 name 跳过
List<SimilarityGroup> groups = groupBuilder.buildGroups(pairs, profileMap);
return groupBuilder.paginate(groups, page, size);
```

**deleteNonOptimalSentences 改写**：
```java
List<SentenceProfile> profiles = toProfiles(sentences);
List<SentencePair> pairs = SimilarityPairFinder.find(profiles, algorithm, threshold);
Map<String, SentenceProfile> profileMap = profiles.stream().collect(...);
Set<String> toDelete = groupBuilder.collectNonOptimalNames(pairs, profileMap);
return deleteSentencesSerially(toDelete);
```

**删除**（~620 行）：
- 嵌套 `UnionFind` 类（L905-986）
- `SimilarityResult` record（L255-260）
- 所有已迁出的 private 方法（见上文"需删除"清单）
- 清理未使用 imports（JsonProcessingException/TypeReference/ArrayList/Collections/Comparator/HashMap/HashSet/Stream 等）

**修改** [SimilarityGroup.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/extension/SimilarityGroup.java)：
- `scoreSentence(Sentence)` L53 标 `@Deprecated`，方法体改为委托：
  ```java
  @Deprecated
  public static double scoreSentence(Sentence sentence) {
      return SentenceScorer.score(SentenceProfile.from(sentence));
  }
  ```
- 添加 `import top.puresky.hitokotohub.service.similarity.SentenceScorer;` 和 `SentenceProfile;`
- 保留原 Javadoc 说明评分规则（供外部调用方参考）

#### 5.3 创建 Service 集成测试

**新建** `src/test/java/top/puresky/hitokotohub/service/impl/SimilarityCheckServiceImplTest.java`：

**测试基础设施**：
- `MockExtensionClient.builder().with(s1).with(s2)...build()` 构造内存 fake client
- `new SimilarityGroupBuilder()` + `new SentencePairJsonCodec(new ObjectMapper())` 直接构造
- `new SimilarityCheckServiceImpl(client, objectMapper, groupBuilder, codec)` 构造被测对象
- `StepVerifier` 断言 Mono

**测试场景**（6 个）：
1. `performCheck` MANUAL/COSINE：3 句子（2 相似 + 1 不相似）→ log status=SUCCESS，similarPairCount=1，totalPairs=3
2. `performCheck` 空句子列表 → status=SUCCESS，totalSentences=0
3. `performCheck` 异常路径 → status=FAILED，errorMessage 非空
4. `getGroups` page=1 size=10：返回 total、groups 列表，bestSentence 正确
5. `getGroups` 无日志 → emptyResult（total=0，groups 空列表）
6. `deleteNonOptimalSentences`：2 组相似，删除非最优后 client 中剩余句子数 = 总数 - 非最优数

**验证**：
- `.\gradlew compileJava` 通过
- `.\gradlew test --tests "top.puresky.hitokotohub.service.similarity.*"` 全绿（含 shadow-compare）
- `.\gradlew test --tests "top.puresky.hitokotohub.service.impl.SimilarityCheckServiceImplTest"` 全绿

---

### 步骤 6：前端共享抽象

#### 6.1 新建样式变量文件

**新建** `ui/src/styles/variables.scss`：
```scss
// Rose 配色（项目硬约束：暗色 + rose #fb7185 + 玻璃拟态）
$rose-300: #fda4af;
$rose-500: #fb7185;
$rose-600: #e11d48;
$rose-700: #be123c;

// z-index 层级（项目硬约束：modal 300, dropdown 400, toast 500）
$z-index-modal: 300;
$z-index-dropdown: 400;
$z-index-toast: 500;

// :root CSS 变量（供 JS 与原生 CSS 使用，与 hitokoto.html 步骤 8 共享）
:root {
  --hitokoto-rose-300: #{$rose-300};
  --hitokoto-rose-500: #{$rose-500};
  --hitokoto-rose-600: #{$rose-600};
  --z-modal: #{$z-index-modal};
  --z-dropdown: #{$z-index-dropdown};
  --z-toast: #{$z-index-toast};
}
```

#### 6.2 新建 4 个 composables

**新建** `ui/src/composables/`：

1. **`usePagination.ts`**：分页状态 + 翻页回调
   - 入参：`onPageChange: (page: number, size: number) => Promise<void>`、初始 `page=1, size=10`
   - 返回：`{ page, size, total, setPage, setSize, setTotal, handlePageChange, handleSizeChange, resetPage }`
   - 用 `ref` 管理状态，`handlePageChange` 触发回调

2. **`useCrudModal.ts`**：表单弹窗 CRUD 状态
   - 入参：`saveFn: (data: T, isEdit: boolean) => Promise<void>`、`createForm: () => T`
   - 返回：`{ showFormModal, isEditing, formData, saving, handleCreate, handleEdit, handleSave, closeForm, resetForm }`

3. **`useAsyncTable.ts`**：列表加载 + 刷新 + 搜索
   - 入参：`fetchFn: (keyword?: string) => Promise<T[]>` 或 `Promise<{ items: T[]; total: number }>`
   - 返回：`{ loading, list, total, refresh, handleSearch, keyword }`

4. **`useToast.ts`**：薄包装 Halo `Toast`
   - 返回：`{ success, error, warning }` 委托 `@halo-dev/components` 的 Toast
   - 确保 z-index 500 可见（项目硬约束）

#### 6.3 composables 单测

**新建** `ui/src/composables/__tests__/usePagination.spec.ts`：
- 初始 page=1 size=10 total=0
- `handlePageChange(2, 20)` 更新 page=2 size=20 并触发回调
- `resetPage()` 重置 page=1
- 用 Vitest + `@vue/test-utils` 的 `defineComponent` + `setup`

**验证**：
- `pnpm --filter ui type-check` 通过
- `pnpm --filter ui test:unit` 通过（含新单测）

---

### 步骤 7：6 个前端组件重构

**策略**：从大到小重构，每个组件独立验证。**遵守项目记忆全部硬约束**：
- 暗色 + rose #fb7185 + 玻璃拟态（不改视觉）
- 自定义下拉（非原生 `<select>`）置于 body 末尾避免 transform/backdrop-filter 问题
- Toast z-index:500 可见
- el-pagination 中文显示
- 相似度结果单条删除：乐观 UI 更新（直接改本地 groups.value，不重检、不打乱排序）
- 批量删除非最优：操作所有数据（非仅当前页），串行删除单条失败跳过不中断
- AI 日志设置：AI 生成禁用时隐藏 max keep count 与 retention days

**重构顺序与操作**：

1. **[SentenceList.vue](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/components/SentenceList.vue)**（1420 行）
   - 内联分页 → `usePagination`
   - CRUD 弹窗 → `useCrudModal`
   - 列表加载 → `useAsyncTable`
   - Toast → `useToast`
   - 硬编码 `#fb7185` / z-index → `@use '@/styles/variables.scss' as *;` 变量

2. **[SimilarityCheck.vue](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/components/SimilarityCheck.vue)**（1370 行）
   - composable 替换同上
   - **关键硬约束**：批量删除非最优操作所有数据（非仅当前页），串行失败跳过
   - 相似度结果后端分页
   - 单条删除乐观 UI（直接修改本地 groups.value，不触发重新检测、不打乱排序）

3. **[Overview.vue](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/components/Overview.vue)**（1209 行）
   - ECharts 数据加载 → `useAsyncTable`
   - 配色变量化

4. **[SubmissionList.vue](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/components/SubmissionList.vue)**（749 行）
   - composable 替换
   - 审核状态（PENDING/APPROVED/REJECTED）逻辑保留

5. **[AiGenerateLogList.vue](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/components/AiGenerateLogList.vue)**（605 行）
   - composable 替换
   - **硬约束**：AI 生成禁用时隐藏 max keep count 与 retention days 设置项

6. **[CategoryList.vue](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/components/CategoryList.vue)**（486 行）
   - composable 替换
   - **硬约束**：自定义下拉（非原生 `<select>`）置于 body 末尾

**验证**（每组件完成后）：
- `pnpm --filter ui type-check` 通过
- 全部完成后 `pnpm --filter ui build` 通过
- `pnpm --filter ui lint` 通过

---

### 步骤 8：hitokoto.html 拆分为 Thymeleaf fragment

#### 8.1 拆分方案（基于 HitokotoTemplateRouter 实际机制）

**核实结论**：[HitokotoTemplateRouter.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/HitokotoTemplateRouter.java) 通过 `TemplateNameResolver.resolveTemplateNameOrDefault(...)` + `ServerResponse.ok().render(templateName, model)` 渲染 `hitokoto` 模板。**用 Thymeleaf fragment 拆分最稳妥**（无需配置静态资源路径，零行为变更风险）。

#### 8.2 新建 fragment 文件

**新建** `src/main/resources/templates/fragments/hitokoto-styles.html`：
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:fragment="styles">
  <style>
    /* 从 hitokoto.html L20-1446 的 <style> 内容整体搬运 */
    /* L28 :root 已有 --bg/--bg-glass 变量，在此扩展 --hitokoto-rose-500/--z-toast 等 */
    /* 12 处硬编码 z-index 与 rose 配色替换为 var(--hitokoto-rose-500)/var(--z-toast) 等 */
  </style>
</head>
</html>
```

**新建** `src/main/resources/templates/fragments/hitokoto-scripts.html`：
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
<script th:fragment="scripts">
  /* 从 hitokoto.html L1616-2819 的 <script> 内容整体搬运 */
  /* 保留所有 Thymeleaf data 属性读取（document.getElementById('templateConfig').dataset 等） */
  /* 物理参数绝对不改：damping 0.99, gravity 0.005, scale 15 帧, 樱花点击绽放→无缝飘落 */
</script>
</body>
</html>
```

#### 8.3 重写 hitokoto.html（2533 → ~180 行）

**重写** [hitokoto.html](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/resources/templates/hitokoto.html)：
- L1-19：保留（DOCTYPE + head 头部 + meta + 字体链接）
- L20-1446：替换为 `<th:block th:replace="~{fragments/hitokoto-styles :: styles}"></th:block>`
- L1448-1615：**原样保留 body 主体**（5 个 Thymeleaf 注入点不动）
- L1616-2819：替换为 `<th:block th:replace="~{fragments/hitokoto-scripts :: scripts}"></th:block>`

#### 8.4 严守零行为变更

**绝对不改**：
- 樱花点击绽放→无缝飘落动画的物理参数（damping 0.99、gravity 0.005、scale 15 帧、vx/vy 连续性）
- 主题切换逻辑（auto/dark/light）
- 访客提交表单逻辑（含 IP 冷却）
- 点赞冷却逻辑
- 5 个 Thymeleaf 数据注入点（templateTheme、templateShowSakura、templateShowHint、randomSentences、s.content/s.author/s.source）
- body 主体 HTML 结构

**仅做**：
- CSS 代码搬运到 `fragments/hitokoto-styles.html`
- JS 代码搬运到 `fragments/hitokoto-scripts.html`
- 12 处硬编码 z-index（如 `z-index: 500`）与 rose 配色（如 `#fb7185`）替换为 CSS 变量
- 确保 fragment 引用语法正确

**验证**：
- `.\gradlew build` 通过
- 浏览器访问 `/hitokoto` 页面，逐项核对：樱花飘落、点击绽放无缝过渡、主题切换、一言展示、点赞、访客提交
- 视觉与重构前完全一致

---

### 步骤 9：端到端验证

1. `.\gradlew clean build`（含 test）全绿
2. `pnpm --filter ui type-check` 全绿
3. `pnpm --filter ui test:unit` 全绿
4. `pnpm --filter ui lint` 全绿
5. `pnpm --filter ui build` 全绿
6. 全局残留检查：
   - `Select-String -Path "src\main\**\*.java" -Pattern "System\.out\.println"` 无结果
   - `Select-String -Path "ui\src\**\*.vue" -Pattern "console\.log"` 仅保留必要调试
   - `Select-String -Path "ui\src\**\*.vue" -Pattern "#fb7185"` 已变量化
   - `Select-String -Path "ui\src\**\*.vue" -Pattern "z-index:\s*500"` 已变量化
7. 手动回归核心场景（核对清单）：
   - 句子 CRUD + 发布
   - 相似度检查 + 分组查看 + 单条删除（乐观 UI，不重检，不打乱排序）+ 批量删除非最优（串行失败跳过）
   - 分类管理
   - 访客提交 + 审核
   - AI 生成日志（开关隐藏设置项）
   - 概览统计图表
   - 访客模板 `/hitokoto`（樱花动画 + 一言 + 提交 + 主题切换）

---

## 假设与决策

1. **继续执行已批准方案**：技术方案不变，仅从中断点恢复。不重新决策已确认事项（全栈重构、模板拆分、测试深度）
2. **shadow-compare 优先**：步骤 5 重构 ServiceImpl 前先验证算法等价性，保证零行为变更
3. **MockExtensionClient 优于纯 Mockito mock**：service 内部有 `.block()`，内存版 fake 对 reactor 调度更安全
4. **hitokoto.html 用 Thymeleaf fragment 拆分**：基于 HitokotoTemplateRouter 实际机制，比 static/ 静态资源方案更稳妥（零路径配置）
5. **不动契约**：GVK 注解、持久化 JSON 格式、SimilarityCheckService 接口签名、HitokotoTemplateRouter 路由、Thymeleaf 数据模型 key 绝对不动
6. **PowerShell 兼容**：所有 shell 命令用 `;` 分隔，不用 `&&`
7. **小步验证**：每个步骤完成后立即运行对应验证命令，不累积错误
8. **算法层零依赖原则**：纯算法类（TextSimilarityCalculator/Finder/UnionFind/Scorer）保持零 Spring/Extension 依赖，边界层（Builder/Codec/Mappers）可依赖 Extension DTO

## 风险控制

1. **步骤 5 最高风险**：ServiceImpl 重构涉及核心业务逻辑，shadow-compare 测试是安全网，必须先通过；JSON 持久化格式字段不变（sentence1Name/sentence1Content/.../similarity）
2. **步骤 7 遵守项目记忆硬约束**：
   - 单条删除乐观 UI 不重检、不打乱排序
   - 批量删除串行失败跳过、操作所有数据
   - 分页中文、Toast z-index 500、自定义下拉置 body 末尾
   - AI 日志设置按开关隐藏
   - 暗色+rose#fb7185+玻璃拟态视觉不变
3. **步骤 8 零行为变更**：仅搬运代码 + 变量化，不改任何动画时序/物理参数/数据注入点/body 结构
4. **每步独立验证**：每完成一步立即运行对应验证命令，失败则修复后再进入下一步
5. **Write 工具不稳定应对**：若 Write 创建 0 字节文件，改用 PowerShell `[IO.File]::WriteAllText` + UTF-8 NoBOM；超长命令用 `WriteAllText` + `AppendAllText` 分段

## 执行顺序

步骤 5 → 步骤 6 → 步骤 7（按 1→6 顺序）→ 步骤 8 → 步骤 9

每步完成后用 TodoWrite 标记进度，确保可追踪。
