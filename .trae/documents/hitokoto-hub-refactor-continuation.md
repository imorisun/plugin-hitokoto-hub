# 轻言（hitokoto-hub）重构续作计划：完成剩余步骤

## 背景与当前状态（已通过文件系统核实）

本计划承接已批准的 [hitokoto-hub-refactor-resume.md](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/.trae/documents/hitokoto-hub-refactor-resume.md)，从中断点恢复。**用户已确认的三项关键决策不变**：全栈全面重构、hitokoto.html 拆分为独立 CSS/JS、建测试基础设施+核心路径测试。

### 已完成（核实通过，无需重做）

| 步骤 | 内容 | 核实结果 |
|------|------|----------|
| 0 | 修复编译错误 | ✅ SentencePublicEndpoint/SentenceSubmissionPublicEndpoint 已替换为 utils |
| 1 | ExtensionIndexRegistrar + HitokotoHubPlugin 重构 | ✅ utils/ExtensionIndexRegistrar.java 已创建 |
| 2 | 工具类单测 | ✅ utils/ 下 4 个测试文件存在 |
| 3 | EchartsDataBuilder 抽离 | ✅ endpoint/overview/EchartsDataBuilder.java + Test 已创建 |
| 4（部分） | 算法层文件 | ✅ service/similarity/ 下 9 个文件全部创建且编译通过 |

**算法层 9 文件已就位**（已逐一读源码确认签名正确）：
- 纯算法（零 Spring/Extension 依赖）：[SentenceProfile.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/similarity/SentenceProfile.java)、[SentencePair.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/similarity/SentencePair.java)、[TextSimilarityCalculator.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/similarity/TextSimilarityCalculator.java)、[UnionFind.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/similarity/UnionFind.java)、[SentenceScorer.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/similarity/SentenceScorer.java)、[SimilarityPairFinder.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/similarity/SimilarityPairFinder.java)、[SimilarityMappers.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/similarity/SimilarityMappers.java)
- 边界层（@Component）：[SimilarityGroupBuilder.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/similarity/SimilarityGroupBuilder.java)、[SentencePairJsonCodec.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/similarity/SentencePairJsonCodec.java)

### 待完成（本计划聚焦）

| 步骤 | 内容 | 现状 |
|------|------|------|
| 4（剩余） | 6 个算法单测 | ❌ `src/test/.../service/similarity/` 目录不存在 |
| 5 | SimilarityCheckServiceImpl 重构 | ❌ 仍 898 行，未引用 similarity 包，UnionFind(L905-986)+SimilarityResult(L255-260) 仍内嵌 |
| 6 | 前端共享抽象 | ❌ ui/src/ 无 composables/ 与 styles/ 目录 |
| 7 | 6 个前端组件重构 | ❌ 6 个 .vue 文件行数：SentenceList 1420 / SimilarityCheck 1370 / Overview 1209 / SubmissionList 749 / AiGenerateLogList 605 / CategoryList 486 |
| 8 | hitokoto.html 拆分 | ❌ 单文件 2533 行，`<style>` L20-1446（~1426 行）、`<script>` L1616-2819（~1203 行） |
| 9 | 端到端验证 | ❌ 待执行 |

### 关键环境核实结果

- **前端目录**：`ui/`（非 `console-src/`），src 下有 `api/assets/components/views` 四个子目录
- **前端依赖**：vue、element-plus、@vue/test-utils、jsdom、sass、vitest@^4.1.0 均已安装
- **Vitest 配置**：无独立 `vitest.config.ts`，通过 `package.json` 的 `"test:unit": "vitest --passWithNoTests"` 运行；`tsconfig.vitest.json` include 为 `src/**/__tests__/*`，所以测试文件放 `__tests__/` 目录或用 `*.spec.ts` 命名均可
- **访客模板路由**：[HitokotoTemplateRouter.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/HitokotoTemplateRouter.java) 使用 `TemplateNameResolver.resolveTemplateNameOrDefault(...)` + `ServerResponse.ok().render(templateName, model)` 渲染 `hitokoto` 模板。模板经 Halo 主题解析器渲染，**静态资源最稳妥方案是用 Thymeleaf fragment 拆分**（见步骤 8）
- **PowerShell 环境**：所有命令用 `;` 分隔，不用 `&&`

---

## 执行计划

### 步骤 4（剩余）：6 个算法单测

**新建目录** `src/test/java/top/puresky/hitokotohub/service/similarity/`

**新建 6 个测试文件**（纯 JUnit 5 单测，无 Spring）：

1. **`TextSimilarityCalculatorTest.java`**
   - `tokenizeToSet`：`"你好世界"` → `{你好, 好世, 世界}`；空串/null/单字 → 空集
   - `computeTfVector`：归一化词频和为 1
   - `computeIdf`：N=3，df=1 → `log(3/2)+1`
   - `cosineSimilarity`：相同向量 = 1.0；不相交 = 0.0；空向量 = 0.0
   - `jaccardSimilarity`：交集/并集；两空集 = 0.0
   - `vectorNorm`：`{a:3,b:4}` → 5.0

2. **`SimilarityPairFinderTest.java`**
   - 3 个 profile，两两相似度都 ≥ 阈值 → 返回 3 对，降序
   - 阈值 = 1.0 → 仅完全相同内容返回对
   - JACCARD 算法路径
   - `totalPairs(5)` = 10

3. **`UnionFindTest.java`**
   - 传递合并：add A,B,C；union(A,B)；union(B,C) → groupByRoot 返回 1 组含 3 元素
   - 路径压缩：find 后 parent 直接指向根
   - 未 add 的节点 find 返回自身
   - groupByRoot 多组分离

4. **`SentenceScorerTest.java`**
   - 全满分：published + likeCount=5 + viewCount=100 + len=30 + author + source = 40+10+10+15+10+5=90
   - 全空：未发布 + likeCount=0 + viewCount=0 + 空 content + 匿名 + 未知 = 0
   - 内容长度 >80 → +8
   - 内容长度 <15 → 不加分

5. **`SimilarityGroupBuilderTest.java`**
   - `buildGroups`：3 对传递相似（A~B, B~C, A~C）→ 1 组，bestSentence 为评分最高，similarCount=2
   - `buildGroups`：空 pairs → 空列表
   - `collectNonOptimalNames`：3 句子组保留评分最高，返回其余 2 个 name
   - `paginate`：5 组，page=1 size=2 → total=5，groups.size=2
   - `emptyResult`：total=0，groups 为空列表

6. **`SentencePairJsonCodecTest.java`**
   - 往返：构造 `List<SentencePair>` → serialize → deserialize → 与原列表相等
   - 损坏 JSON `"{invalid"` → deserialize 返回空列表（不抛异常）
   - null/空白 → 空列表
   - serialize 失败容错（传 null ObjectMapper 路径用 Mockito mock 抛异常验证返回 "[]"）

**Codec 测试需 Spring**：用 `new SentencePairJsonCodec(new ObjectMapper())` 直接构造，无需 @SpringBootTest。

**验证**：`.\gradlew test --tests "top.puresky.hitokotohub.service.similarity.*"` 全绿

---

### 步骤 5：SimilarityCheckServiceImpl 重构 + shadow-compare + Service 集成测试

#### 5.1 shadow-compare 验证算法等价性（先做，保证零行为变更）

**临时方案**：在重构 ServiceImpl 前，先写一个 `SimilarityAlgorithmParityTest.java`（放 `src/test/.../service/similarity/`）：
- 构造 10-20 个 Sentence 列表（含重复内容、相似内容、不相似内容）
- 调用**旧逻辑**：直接 `new SimilarityCheckServiceImpl(mockClient, objectMapper)` 反射调用 private `calculateSimilarPairs` / `buildGroupsResult` / `collectNonOptimalNames`
- 调用**新逻辑**：`SimilarityPairFinder.find(profiles, algo, threshold)` + `SimilarityGroupBuilder.buildGroups(...)` + `collectNonOptimalNames(...)`
- 断言：相似对列表（按 similarity 降序）、分组数量、每组 bestSentence name、每组 similarCount、待删除 name 集合**完全一致**
- 重点核对 `Math.round(similarity * 10000) / 10000.0` 截断在两路径中顺序一致

**通过后删除该临时测试**（或保留为回归测试）。

#### 5.2 重构 SimilarityCheckServiceImpl（898 → ~280 行）

**修改** [SimilarityCheckServiceImpl.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/impl/SimilarityCheckServiceImpl.java)：

**新增构造注入**：
```java
private final SimilarityPairFinder finder;          // 静态调用，实际可不注入
private final SimilarityGroupBuilder groupBuilder;  // @Component 注入
private final SentencePairJsonCodec codec;          // @Component 注入
```
（SimilarityPairFinder/TextSimilarityCalculator/SentenceScorer/UnionFind/SimilarityMappers 是纯静态/工具类，无需注入）

**保留**（约 280 行）：
- 3 个 public API（`performCheck` / `getGroups` / `deleteNonOptimalSentences`，签名绝对不变）
- `createInitialLog` / `executeCheck` / `deleteOldLogs` / `populateEmptyResult` / `deleteSentencesSerially`
- 数据访问：`fetchAllSentences` / `getLatestSuccessLog`
- 边界转换：`toProfiles(List<Sentence>)` → `List<SentenceProfile>`、`toProfileMap(...)` → `Map<String, SentenceProfile>`

**executeCheck 改写**（核心）：
```java
List<SentenceProfile> profiles = sentences.stream().map(SentenceProfile::from).toList();
List<SentencePair> pairs = SimilarityPairFinder.find(profiles, algorithm, threshold);
List<SentencePair> storedPairs = pairs.size() > MAX_STORED_PAIRS
    ? pairs.subList(0, MAX_STORED_PAIRS) : pairs;
String pairsJson = codec.serialize(storedPairs);
// 填充 logEntry...
```

**getGroups 改写**：
```java
List<SentencePair> pairs = codec.deserialize(latestLog.getSpec().getSimilarPairs());
Map<String, SentenceProfile> profileMap = fetchAllSentences().block().stream()
    .collect(Collectors.toMap(p -> p.name(), Function.identity(),
        (a,b)->a, HashMap::new));  // 转 profile
List<SimilarityGroup> groups = groupBuilder.buildGroups(pairs, profileMap);
return groupBuilder.paginate(groups, page, size);
```

**deleteNonOptimalSentences 改写**：
```java
List<SentencePair> pairs = SimilarityPairFinder.find(profiles, algorithm, threshold);
Set<String> toDelete = groupBuilder.collectNonOptimalNames(pairs, profileMap);
return deleteSentencesSerially(toDelete);
```

**删除**（约 620 行）：
- 嵌套 `UnionFind` 类（L905-986）
- `SimilarityResult` record（L255-260）
- `calculateSimilarPairs` / `buildPair` / `tokenizeToSet` / `computeTfVector` / `computeIdf` / `computeTfidfVector` / `cosineSimilarity` / `vectorNorm` / `jaccardSimilarity`
- `buildGroupsResult` / `buildGroup` / `buildSimilarityMap` / `similarityKey` / `getSimilarity` / `buildSentenceInfo` / `collectNonOptimalNames`（旧版） / `paginateGroups` / `emptyGroupsResult` / `serializePairs` / `deserializePairs`
- 清理未使用 imports

**修改** [SimilarityGroup.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/extension/SimilarityGroup.java)：
- `scoreSentence(Sentence)` 标 `@Deprecated`（保留以兼容外部调用），方法体改为委托：
  ```java
  @Deprecated
  public static double scoreSentence(Sentence sentence) {
      return SentenceScorer.score(SentenceProfile.from(sentence));
  }
  ```
- 添加 `import top.puresky.hitokotohub.service.similarity.SentenceScorer;` 和 `SentenceProfile;`

#### 5.3 Service 集成测试

**新建** `src/test/java/top/puresky/hitokotohub/service/impl/SimilarityCheckServiceImplTest.java`：
- 用 [MockExtensionClient](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/test/java/top/puresky/hitokotohub/support/MockExtensionClient.java)（内存 fake，对 reactor 调度更安全）+ [TestFixtures](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/test/java/top/puresky/hitokotohub/support/TestFixtures.java)
- 注入真实 `SimilarityGroupBuilder` 和 `SentencePairJsonCodec`（new ObjectMapper()）
- 测试场景：
  1. `performCheck` MANAUL/COSINE：3 句子（2 相似 1 不相似）→ log status=SUCCESS，similarPairCount=1，totalPairs=3
  2. `performCheck` 空句子列表 → status=SUCCESS，totalSentences=0
  3. `performCheck` 异常 → status=FAILED，errorMessage 非空
  4. `getGroups` page=1 size=10：返回 total、groups 列表，bestSentence 正确
  5. `getGroups` 无日志 → emptyResult（total=0）
  6. `deleteNonOptimalSentences`：2 组相似，删除非最优后 client 中剩余句子数 = 总数 - 非最优数
- 用 `StepVerifier` 断言 Mono

**验证**：
- `.\gradlew compileJava` 通过
- `.\gradlew test` 全绿
- 手动核对：旧版 JSON 输出与新版输出格式字段完全一致（sentence1Name/sentence1Content/.../similarity）

---

### 步骤 6：前端共享抽象

#### 6.1 新建样式变量

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

// :root CSS 变量（供 JS 与原生 CSS 使用）
:root {
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
   - 返回：`page, size, total, handlePageChange, resetPage`
   - 接收回调 `onPageChange: (page, size) => Promise<void>`

2. **`useCrudModal.ts`**：表单弹窗 CRUD 状态
   - 返回：`showFormModal, isEditing, formData, saving, handleCreate, handleEdit, handleSave, closeForm, resetForm`
   - 接收 `saveFn: (data, isEdit) => Promise<void>`

3. **`useAsyncTable.ts`**：列表加载 + 刷新 + 搜索
   - 返回：`loading, list, refresh, handleSearch, keyword`
   - 接收 `fetchFn: (keyword?) => Promise<T[]>`

4. **`useToast.ts`**：薄包装 Halo `Toast`（确保 z-index 500 可见）
   - 返回：`success, error, warning`（委托 `@halo-dev/components` 的 Toast）

#### 6.3 composables 单测

**新建** `ui/src/composables/__tests__/usePagination.spec.ts`：
- 初始 page=1 size=10 total=0
- handlePageChange(2, 20) 更新 page=2 size=20 并触发回调
- resetPage 重置 page=1

**可选** `useCrudModal.spec.ts`：handleCreate 置 isEditing=false+showFormModal=true；handleEdit 置 isEditing=true+填充 formData。

**验证**：
- `pnpm --filter ui type-check` 通过
- `pnpm --filter ui test:unit` 通过

---

### 步骤 7：6 个前端组件重构

**策略**：从大到小重构，每个组件独立 commit。**遵守项目记忆全部硬约束**（暗色+rose#fb7185+玻璃拟态、自定义下拉置 body 末尾、Toast z-index:500、el-pagination 中文、单条删除乐观 UI 不重检不打乱排序、批量删除串行失败跳过）。

**重构顺序**：

1. **[SentenceList.vue](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/components/SentenceList.vue)**（1420 行）
   - 替换内联分页为 `usePagination`
   - 替换 CRUD 弹窗为 `useCrudModal`
   - 替换列表加载为 `useAsyncTable`
   - Toast 替换为 `useToast`
   - 硬编码 `#fb7185` / z-index 替换为 `variables.scss` 变量（`@use '@/styles/variables.scss' as *;`）
   - **关键**：相似度检测结果删除单条句子保持乐观 UI 更新（直接修改本地 groups.value，不触发重新检测、不打乱排序）

2. **[SimilarityCheck.vue](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/components/SimilarityCheck.vue)**（1370 行）
   - 同上 composable 替换
   - 批量删除非最优句子：所有数据操作（非仅当前页），单条失败跳过不中断
   - 相似度结果后端分页

3. **[Overview.vue](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/components/Overview.vue)**（1209 行）
   - ECharts 数据加载用 `useAsyncTable`
   - 配色变量化

4. **[SubmissionList.vue](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/components/SubmissionList.vue)**（749 行）
   - composable 替换
   - 审核状态（PENDING/APPROVED/REJECTED）逻辑保留

5. **[AiGenerateLogList.vue](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/components/AiGenerateLogList.vue)**（605 行）
   - composable 替换
   - **硬约束**：AI 生成禁用时隐藏 max keep count 与 retention days 设置

6. **[CategoryList.vue](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/components/CategoryList.vue)**（486 行）
   - composable 替换
   - 自定义下拉（非原生 `<select>`）置于 body 末尾避免 transform/backdrop-filter 问题

**验证**：
- 每个组件重构后 `pnpm --filter ui type-check` 通过
- 全部完成后 `pnpm --filter ui build` 通过
- `pnpm --filter ui lint` 通过
- 浏览器手动核对暗色主题、rose 配色、玻璃拟态、分页中文、Toast 可见性不变

---

### 步骤 8：hitokoto.html 拆分

#### 8.1 静态资源方案

**核实结论**：[HitokotoTemplateRouter.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/HitokotoTemplateRouter.java) 通过 `TemplateNameResolver` 渲染模板，属于 Halo 主题模板机制。**最稳妥方案是用 Thymeleaf fragment 拆分**（无需配置静态资源路径，零行为变更风险）。

#### 8.2 拆分实施

**新建** `src/main/resources/templates/fragments/hitokoto-styles.html`：
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:fragment="styles">
  <style>
    /* 从 hitokoto.html L20-1446 的 <style> 内容整体搬运 */
    /* 12 处 z-index 与 rose 配色改用 CSS 变量 var(--hitokoto-rose-500) / var(--z-toast) 等 */
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
  /* 保留所有 Thymeleaf data 属性注入（th:inline="javascript" 等） */
  /* 物理参数绝对不改：damping 0.99, gravity 0.005, scale 15 帧, 樱花点击绽放→无缝飘落 */
</script>
</body>
</html>
```

**重写** [hitokoto.html](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/resources/templates/hitokoto.html)（2533 → ~150 行）：
```html
<!DOCTYPE html>
<html lang="zh-CN" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <title>轻言</title>
  <!-- 替换原 <style> 块 -->
  <th:block th:replace="~{fragments/hitokoto-styles :: styles}"></th:block>
</head>
<body>
  <!-- 原始 HTML 主体结构（L1446-1616）保持不变 -->
  ...
  <!-- 替换原 <script> 块 -->
  <th:block th:replace="~{fragments/hitokoto-scripts :: scripts}"></th:block>
</body>
</html>
```

#### 8.3 严守零行为变更

**绝对不改**：
- 樱花点击绽放→无缝飘落动画的物理参数（damping 0.99、gravity 0.005、scale 15 帧、vx/vy 连续性）
- 主题切换逻辑
- 访客提交表单逻辑
- 点赞冷却逻辑
- 所有 Thymeleaf 数据注入点（templateTheme、templateShowSakura、templateShowHint）

**仅做**：
- CSS/JS 代码搬运到 fragment 文件
- 12 处硬编码 z-index（如 `z-index: 500`）与 rose 配色（如 `#fb7185`）替换为 CSS 变量
- 确保 fragment 引用语法正确

**验证**：
- `.\gradlew build` 通过
- 浏览器访问 `/hitokoto` 页面，逐项核对：樱花飘落、点击绽放、主题切换、一言展示、点赞、访客提交
- 截图对比重构前后视觉完全一致

---

### 步骤 9：端到端验证

1. `.\gradlew clean build`（含 test）全绿
2. `pnpm --filter ui type-check` 全绿
3. `pnpm --filter ui test:unit` 全绿
4. `pnpm --filter ui lint` 全绿
5. `pnpm --filter ui build` 全绿
6. 全局检查无残留：
   - `grep -r "System.out.println" src/main/` 无结果
   - `grep -r "console.log" ui/src/` 仅保留必要调试（生产代码无）
   - `grep -rn "#fb7185\|z-index:\s*500" ui/src/` 已变量化
7. 手动回归核心场景：
   - 句子 CRUD + 发布
   - 相似度检查 + 分组查看 + 单条删除（乐观 UI）+ 批量删除非最优（串行失败跳过）
   - 分类管理
   - 访客提交 + 审核
   - AI 生成日志（开关隐藏设置项）
   - 概览统计图表
   - 访客模板 `/hitokoto`（樱花动画 + 一言 + 提交）

---

## 假设与决策

1. **继续执行已批准方案**：技术方案不变，仅从中断点恢复。本计划不重新决策已确认事项（全栈重构、模板拆分、测试深度）
2. **shadow-compare 优先**：步骤 5 重构 ServiceImpl 前先验证算法等价性，保证零行为变更
3. **FakeExtensionClient 优于 Mockito mock**：service 内部有 `.block()`，内存版 fake 对 reactor 调度更安全
4. **hitokoto.html 用 Thymeleaf fragment 拆分**：基于 HitokotoTemplateRouter 实际机制，比 static/ 静态资源方案更稳妥（零路径配置）
5. **不动契约**：GVK 注解、持久化 JSON 格式、SimilarityCheckService 接口签名、HitokotoTemplateRouter 路由、Thymeleaf 数据模型 key 绝对不动
6. **PowerShell 兼容**：所有 shell 命令用 `;` 分隔
7. **小步提交**：每个步骤完成后独立 commit，便于二分定位回归

## 风险控制

1. **步骤 5 最高风险**：ServiceImpl 重构涉及核心业务逻辑，shadow-compare 测试是安全网，必须先通过
2. **步骤 8 零行为变更**：仅搬运代码 + 变量化，不改任何动画时序/物理参数/数据注入点
3. **步骤 7 遵守项目记忆硬约束**：单条删除乐观 UI 不重检、批量删除串行失败跳过、分页中文、Toast z-index 500、自定义下拉置 body 末尾、AI 日志设置按开关隐藏
4. **每步独立验证**：每完成一步立即运行对应验证命令，不累积错误
5. **算法层零依赖原则**：纯算法类（TextSimilarityCalculator/Finder/UnionFind/Scorer）保持零 Spring/Extension 依赖，边界层（Builder/Codec/Mappers）可依赖 Extension DTO
