# 轻言（hitokoto-hub）重构收尾 — 续作执行计划

## 摘要

本计划承接已批准的 [hitokoto-hub-refactor-finalize.md](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/.trae/documents/hitokoto-hub-refactor-finalize.md)，从上下文中断点恢复，完成剩余步骤 5.3 → 6 → 7 → 8 → 9。**技术方案与三项关键决策不变**：全栈全面重构、hitokoto.html 拆分为 Thymeleaf fragment、建测试基础设施+核心路径测试。本计划聚焦"完成"而非"重新决策"。

---

## 当前状态核实（2026-07-18，Phase 1 探索确认）

### ✅ 已完成（无需重做）

| 步骤 | 内容 | 核实结果 |
|------|------|----------|
| 0-3 | 编译修复/工具类/EchartsDataBuilder | ✅ 全部就位 |
| 4 | 算法层 9 文件 + 6 单测 | ✅ `service/similarity/` 9 文件 + `test/service/similarity/` 6 测试 + `test/support/` MockExtensionClient(164 行) + TestFixtures(146 行) |
| 5.1 | shadow-compare ParityTest | ✅ 5 测试全绿后已删除（private 方法已不存在） |
| 5.2 | ServiceImpl 重构 | ✅ 338 行（@RequiredArgsConstructor 注入 client/groupBuilder/codec），SimilarityGroup.scoreSentence 标 @Deprecated 委托 SentenceScorer |

**算法层 9 文件**（`src/main/java/top/puresky/hitokotohub/service/similarity/`）：
- 纯算法（零依赖）：SentenceProfile、SentencePair、TextSimilarityCalculator、UnionFind、SentenceScorer、SimilarityPairFinder、SimilarityMappers
- 边界层（@Component）：SimilarityGroupBuilder、SentencePairJsonCodec

**ServiceImpl 当前状态**（338 行，已核实）：
- 构造函数：`@RequiredArgsConstructor` 自动注入 `ReactiveExtensionClient client` + `SimilarityGroupBuilder groupBuilder` + `SentencePairJsonCodec codec`（字段顺序 L54-56）
- 3 个 public API 签名不变：`performCheck`(L72-92)、`getGroups`(L100-106)、`deleteNonOptimalSentences`(L120-138)
- 编排方法保留：createInitialLog/executeCheck/deleteOldLogs/buildGroupsResult/collectNonOptimalNames/deleteSentencesSerially/getLatestSuccessLog/fetchAllSentences/populateEmptyResult
- 边界转换方法：toProfiles/toProfileMap（L310-323）
- `deleteSentencesSerially` 含 `.delayElement(Duration.ofSeconds(1))`（L277，测试需容忍 1 秒延迟）

**ServiceImpl 测试文件已创建**（159 行，6 场景，尚未运行验证）：
- 构造方式：`new SimilarityCheckServiceImpl(client, groupBuilder, codec)`（3 参数，与 ServiceImpl 字段顺序匹配）
- 场景 1-3 测 performCheck（COSINE 正常/空列表/异常），场景 4-5 测 getGroups（有日志/无日志），场景 6 测 deleteNonOptimalSentences
- 异常测试用 `Mockito.mock(ReactiveExtensionClient.class)` + `Flux.error`
- MockExtensionClient.delete 返回 `Mono.just(ext)` 并从 store 移除，支持 deleteNonOptimalSentences 测试

### ❌ 待完成（本计划聚焦）

| 步骤 | 内容 | 现状（已核实） |
|------|------|----------|
| 5.3 | 运行 ServiceImpl 集成测试 | 测试文件已创建（159 行），未运行 |
| 6 | 前端共享抽象 | `ui/src/` 无 styles/、composables/ 目录 |
| 7 | 6 个前端组件重构 | SentenceList 1420 / SimilarityCheck 1370 / Overview 1209 / SubmissionList 749 / AiGenerateLogList 605 / CategoryList 486 行；12 处 #fb7185 硬编码 |
| 8 | hitokoto.html 拆分 | 2533 行：`<style>` L20-1446（1426 行）、body L1448-1615（168 行）、`<script>` L1616-2819（1203 行）；templates/ 无 fragments/ |
| 9 | 端到端验证 | 待执行 |

### 前端构建配置（已核实）

- `ui/package.json` scripts：`build`（run-p type-check + build-only）、`type-check`（vue-tsc --build）、`test:unit`（vitest --passWithNoTests）、`lint`（run-s lint:oxlint lint:eslint）
- 无独立 `vitest.config.ts`，使用 `tsconfig.vitest.json`
- 依赖：Vue 3.5 + Element Plus 2.13 + @halo-dev/components 2.23 + ECharts 6.0 + sass 1.89 + @vue/test-utils 2.4 + vitest 4.1
- pnpm 10.12.4

---

## 执行计划

### 步骤 5.3：运行 ServiceImpl 集成测试

**操作**：运行已创建的 `SimilarityCheckServiceImplTest`（159 行，6 场景）。

**验证命令**：
```powershell
.\gradlew test --tests "top.puresky.hitokotohub.service.impl.SimilarityCheckServiceImplTest" --console=plain
```

**预期**：6 个测试全绿。

**已知风险点与应对**：
1. `deleteNonOptimalSentences` 测试：`deleteSentencesSerially` 含 `.delayElement(Duration.ofSeconds(1))`，StepVerifier 默认超时 30 秒足够。若超时，检查 MockExtensionClient.delete 是否正确返回 `Mono.just(ext)`
2. `performCheckWithError` 测试：用 `Mockito.mock(ReactiveExtensionClient.class)`，需确保 `client.create(any())` 和 `client.update(any())` 都 mock 了返回值（测试已处理 L92-93）
3. `getGroupsWithLog` 测试：successLog 需有 creationTimestamp（TestFixtures.metadata 已设 `Instant.now()`），`getLatestSuccessLog` 按 creationTimestamp 降序取首个 SUCCESS

**如失败**：修复测试代码（不改 ServiceImpl，已验证逻辑正确），重跑直到全绿。

**完成后**：标记步骤 5.3 完成，进入步骤 6。

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
   - el-pagination 中文显示（项目硬约束）

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

**验证命令**：
```powershell
pnpm --filter ui type-check
pnpm --filter ui test:unit
```

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
   - 硬编码 `#fb7185` → `@use '@/styles/variables.scss' as *;` 变量

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

**验证命令**（每组件完成后）：
```powershell
pnpm --filter ui type-check
```
全部完成后：
```powershell
pnpm --filter ui build
pnpm --filter ui lint
```

---

### 步骤 8：hitokoto.html 拆分为 Thymeleaf fragment

#### 8.1 拆分方案

基于 [HitokotoTemplateRouter.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/HitokotoTemplateRouter.java) 通过 `TemplateNameResolver.resolveTemplateNameOrDefault(...)` + `ServerResponse.ok().render(templateName, model)` 渲染 `hitokoto` 模板。**用 Thymeleaf fragment 拆分最稳妥**（无需配置静态资源路径，零行为变更风险）。

#### 8.2 新建 fragment 文件

**新建** `src/main/resources/templates/fragments/hitokoto-styles.html`：
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:fragment="styles">
  <style>
    /* 从 hitokoto.html L20-1446 的 <style> 内容整体搬运 */
    /* L28 :root 已有 --bg/--bg-glass 变量，在此扩展 --hitokoto-rose-500/--z-toast 等 */
    /* 硬编码 z-index 与 rose 配色替换为 var(--hitokoto-rose-500)/var(--z-toast) 等 */
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
- 硬编码 z-index 与 rose 配色替换为 CSS 变量
- 确保 fragment 引用语法正确

**验证命令**：
```powershell
.\gradlew build
```

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
2. **ServiceImpl 测试已就绪**：5.3 仅需运行验证，无需重写测试
3. **MockExtensionClient 优于纯 Mockito mock**：service 内部有 `.block()`，内存版 fake 对 reactor 调度更安全（异常路径测试除外，用 Mockito mock listAll 返回 Flux.error）
4. **hitokoto.html 用 Thymeleaf fragment 拆分**：基于 HitokotoTemplateRouter 实际机制，比 static/ 静态资源方案更稳妥（零路径配置）
5. **不动契约**：GVK 注解、持久化 JSON 格式、SimilarityCheckService 接口签名、HitokotoTemplateRouter 路由、Thymeleaf 数据模型 key 绝对不动
6. **PowerShell 兼容**：所有 shell 命令用 `;` 分隔，不用 `&&`
7. **小步验证**：每个步骤完成后立即运行对应验证命令，不累积错误
8. **算法层零依赖原则**：纯算法类保持零 Spring/Extension 依赖，边界层可依赖 Extension DTO

## 风险控制

1. **步骤 5.3**：deleteNonOptimalSentences 含 1 秒延迟，StepVerifier 超时足够；如失败先排查 MockExtensionClient.delete 行为
2. **步骤 7 遵守项目记忆硬约束**：
   - 单条删除乐观 UI 不重检、不打乱排序
   - 批量删除串行失败跳过、操作所有数据
   - 分页中文、Toast z-index 500、自定义下拉置 body 末尾
   - AI 日志设置按开关隐藏
   - 暗色+rose#fb7185+玻璃拟态视觉不变
3. **步骤 8 零行为变更**：仅搬运代码 + 变量化，不改任何动画时序/物理参数/数据注入点/body 结构
4. **每步独立验证**：每完成一步立即运行对应验证命令，失败则修复后再进入下一步

## 执行顺序

步骤 5.3 → 步骤 6 → 步骤 7（按 1→6 顺序）→ 步骤 8 → 步骤 9

每步完成后用 TodoWrite 标记进度，确保可追踪。
