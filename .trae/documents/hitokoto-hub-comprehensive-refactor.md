# 轻言（hitokoto-hub）全栈全面优化重构方案

## Context（背景与动机）

本项目是 Halo CMS 插件"轻言"（一句话/一言管理），技术栈为 Java 21 + Spring WebFlux + ReactiveExtensionClient（后端 32 个类、约 5000 行）+ Vue 3 + TS + Element Plus/Halo Components（前端 6 个组件 + 1 个 2533 行访客模板，约 8300 行）。

经精读代码识别出以下结构性问题，已影响可维护性：

1. **重复代码**：
   - `getClientIp`、`formatRemainingTime` 在 [SentencePublicEndpoint.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/SentencePublicEndpoint.java#L343-L350) 与 [SentenceSubmissionPublicEndpoint.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/SentenceSubmissionPublicEndpoint.java#L223-L240) **逐字重复**
   - `CategoryViewRecord` 创建逻辑在 SentencePublicEndpoint 两处（L204-213、L283-290）重复
   - IP 冷却缓存模式（likeCache / submitCache）两套相似实现
   - 前端 6 个组件中 `VPagination`(8处)、`VModal`(9处)、`Toast`(87处)、`FormKit` 表单模式大量重复
   - rose 配色(#fb7185)硬编码 26 处，z-index 硬编码 12 处（访客模板）

2. **超大体量单体**：
   - [SimilarityCheckServiceImpl.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/impl/SimilarityCheckServiceImpl.java) 898 行混合 6 个职责（算法/分组/并查集/删除/数据访问/编排），算法方法为 private 无法单测
   - [OverviewConsoleEndpoint.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/OverviewConsoleEndpoint.java) 514 行，ECharts 数据构建可抽离
   - 前端 `SentenceList.vue`(1420)、`SimilarityCheck.vue`(1370)、`Overview.vue`(1209) 三个超大组件
   - [hitokoto.html](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/resources/templates/hitokoto.html) 2533 行单体（CSS 1426 行 + HTML + JS 1200 行）

3. **规范问题**：[HitokotoHubPlugin.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/HitokotoHubPlugin.java#L159) 用 `System.out.println`（L159/218/238）而非 logger；100+ 行索引注册内联在 start()

4. **零测试覆盖**：项目无任何 `src/test`，与"确保不影响现有功能"要求存在严重张力

**预期成果**：消除重复、拆分超大类与模板、提取可复用工具/composable/样式变量、搭建测试基础设施并覆盖核心路径，在不改变任何对外行为的前提下显著提升可维护性与可测试性。

## 范围与非目标

**范围**：后端工具提取 + SimilarityCheckServiceImpl 拆分 + EchartsDataBuilder 抽离 + 前端共享抽象（变量/composable）+ 6 组件重构 + hitokoto.html 拆分 + 测试基础设施与核心路径测试。

**非目标**（避免范围蔓延）：不动 Extension GVK 与持久化 JSON 格式；不动 `SimilarityCheckService` 接口签名；不重构 reconciler/finder/AiGenerateServiceImpl/StatsCleanupScheduler 内部逻辑；不重写 generated API 客户端；不改动访客模板的视觉与交互表现。

## 总体执行策略

按风险从低到高分 7 阶段，每阶段独立 commit、独立验证。"先建安全网（测试）→ 低风险去重 → 中风险抽离 → 高风险结构拆分"。最高风险的算法拆分采用 **shadow-compare**（新旧逻辑并行跑、断言结果一致后再切换）。

---

## 阶段 0：测试基础设施搭建（无生产代码改动）

**目标**：建立测试骨架，为后续重构提供回归安全网。

1. 创建目录 `src/test/java/top/puresky/hitokotohub/{service/similarity,endpoint,utils,support}`
2. 创建 [support/TestFixtures.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/test/java/top/puresky/hitokotohub/support/TestFixtures.java)：构造 Sentence/Category/SimilarityCheckLog 等 extension 对象的工厂方法
3. 创建 [support/MockExtensionClient.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/test/java/top/puresky/hitokotohub/support/MockExtensionClient.java)：基于内存 Map 的 `ReactiveExtensionClient` fake（实现 listAll/fetch/create/update/delete/countBy/listBy），保证 `.block()` 安全
4. 创建 [support/FakeExtensionClient.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/test/java/top/puresky/hitokotohub/support/FakeExtensionClient.java)：实现接口的内存版（优先于 Mockito mock，对 reactor 调度更友好）
5. 确认 `build.gradle` 已含 `spring-boot-starter-test` + `junit-platform-launcher`（已就绪，无需改）
6. 前端：确认 `ui/package.json` 已含 vitest + @vue/test-utils（已就绪），创建 `ui/src/utils/__tests__/` 目录

**验证**：`./gradlew test`（空测试通过）+ 写一个 smoke 测试（如 `SentenceScorerTest` 调用现有 `SimilarityGroup.scoreSentence`）通过。

**回归基线**：阶段 0 开始前，手动跑核心场景（随机获取/点赞/提交/相似度检查/概览图表）并保存响应 JSON 作为后续对照基线。

---

## 阶段 1：后端工具类提取（低风险纯抽取）

新建包 `top.puresky.hitokotohub.utils`（当前不存在）。

### 1.1 HttpUtils
[utils/HttpUtils.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/utils/HttpUtils.java)：`static String getClientIp(ServerHttpRequest)`。替换 SentencePublicEndpoint L343-350 与 SentenceSubmissionPublicEndpoint L223-230 的私有副本。

### 1.2 TimeFormatUtils
[utils/TimeFormatUtils.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/utils/TimeFormatUtils.java)：`static String formatRemainingTime(long seconds)`（<60→秒，<3600→分钟，否则小时）。替换两处 L332-341/L232-240。

### 1.3 CategoryViewRecordFactory
[utils/CategoryViewRecordFactory.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/utils/CategoryViewRecordFactory.java)：
- `forView(categoryName, sentenceName)` — 仅 categoryName+sentenceName
- `forLike(categoryName, sentenceName, ip)` — 含 ip
- `create(Sentence, EventType, ip)` — 通用

统一 `metadata.generateName="cvr-"`，消除 SentencePublicEndpoint L204-213 与 L283-290 重复。

### 1.4 IpCooldownCache + 状态类
两种冷却模式不同（单时间戳 vs 批量计数），用泛型状态值而非强行合并：
- [utils/IpCooldownCache.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/utils/IpCooldownCache.java)：`IpCooldownCache<S>` 包装 ConcurrentHashMap，提供 get/put/compute/remove/cleanIf/size
- [utils/SimpleCooldownState.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/utils/SimpleCooldownState.java)：`record SimpleCooldownState(long timestampMillis)` + `isCoolingDown`/`remainingMillis`（替代 likeCache 的 `Map<String,Long>`）
- [utils/BatchCooldownState.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/utils/BatchCooldownState.java)：`firstSubmitTime + count` + `isExpired`/`reachedBatchLimit`（替代 SubmissionCooldownState 私有类）

替换：SentencePublicEndpoint.likeCache → `IpCooldownCache<SimpleCooldownState>`；SentenceSubmissionPublicEndpoint.submitCache → `IpCooldownCache<BatchCooldownState>` 并删除内部私有类。

### 1.5 ExtensionIndexRegistrar
[utils/ExtensionIndexRegistrar.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/utils/ExtensionIndexRegistrar.java)：`@Component`，`registerAll()`/`unregisterAll()` 迁移 HitokotoHubPlugin.start() L43-154 的 6 个 scheme 注册与 stop() 的注销。重构后 HitokotoHubPlugin 仅保留 start/stop 编排 + ensureUncategorizedCategory，并将 3 处 `System.out.println`（L159/218/238）改为 `@Slf4j log.info`。

### 1.6 配套单测
每个工具类配纯单测：HttpUtilsTest（X-Forwarded-For 解析、无 remoteAddress）、TimeFormatUtilsTest（边界值）、CategoryViewRecordFactoryTest（VIEW/LIKE 字段正确性）、IpCooldownCacheTest（并发 compute、cleanIf、两种状态判定）。

**验证**：`./gradlew build` 通过；启动插件验证启动日志（logger 输出）、点赞/提交/冷却端点行为与基线一致。

---

## 阶段 2：EchartsDataBuilder 抽离（中风险）

[endpoint/overview/EchartsDataBuilder.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/overview/EchartsDataBuilder.java)：纯逻辑类，迁移 OverviewConsoleEndpoint 的 `aggregateByGranularity`(L335)、`buildTimePoints`(L359)、`buildEchartsData`(L384) 三个 private 方法。**保留 `ViewStatisticsResponse` 嵌套 DTO 不动**（避免影响 OpenAPI 生成），builder 依赖该嵌套类型。

配 [EchartsDataBuilderTest.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/test/java/top/puresky/hitokotohub/endpoint/overview/EchartsDataBuilderTest.java)：构造 List<CategoryViewRecord> 输入，断言 day/week/month 粒度聚合、xAxis/series 正确性。OverviewConsoleEndpoint 注入 builder 后从 514 行降至约 380 行。

**验证**：手动请求 `/apis/console.api.hitokotohub.puresky.top/v1alpha1/overview/view-statistics`，对比重构前后 JSON 完全一致。

---

## 阶段 3：SimilarityCheckServiceImpl 拆分（最高风险，分多步 + shadow-compare）

新建包 `top.puresky.hitokotohub.service.similarity`。核心原则：**算法层零 Spring、零 Extension 依赖**，用纯 record 解耦，便于纯单测。

### 3.1 解耦层 record
- [service/similarity/SentenceProfile.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/similarity/SentenceProfile.java)：`record(name,content,categoryName,author,source,published,likeCount,viewCount)` + `static from(Sentence)`
- [service/similarity/SentencePair.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/similarity/SentencePair.java)：纯 record，与 extension.SimilarityCheckLog.SimilarityPair 通过 mapper 互转
- [service/similarity/SimilarityMappers.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/similarity/SimilarityMappers.java)：extension ↔ record 转换

### 3.2 算法层（纯函数，重点单测对象）
- [service/similarity/TextSimilarityCalculator.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/similarity/TextSimilarityCalculator.java)：迁移 tokenizeToSet/computeTfVector/computeIdf/computeTfidfVector/cosineSimilarity/vectorNorm/jaccardSimilarity 为 static
- [service/similarity/SimilarityPairFinder.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/similarity/SimilarityPairFinder.java)：`find(List<SentenceProfile>, algorithm, threshold)` → `List<SentencePair>`（按 similarity 降序）+ `totalPairs(n)`
- [service/similarity/UnionFind.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/similarity/UnionFind.java)：泛型 `UnionFind<T>`，迁移并泛化原嵌套类（L905-986）
- [service/similarity/SentenceScorer.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/similarity/SentenceScorer.java)：迁移 `SimilarityGroup.scoreSentence` 为 `score(SentenceProfile)`；SimilarityGroup 原静态方法标 `@Deprecated` 委托新类（渐进迁移）
- [service/similarity/SimilarityGroupBuilder.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/similarity/SimilarityGroupBuilder.java)：迁移 buildGroupsResult/buildGroup/buildSimilarityMap/similarityKey/paginate/emptyResult/collectNonOptimalNames；仅依赖 SimilarityGroup DTO
- [service/similarity/SentencePairJsonCodec.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/similarity/SentencePairJsonCodec.java)：`serialize(List<SentencePair>)`/`deserialize(json)`，封装异常为 "[]"/emptyList

### 3.3 重构后的 Service（约 280 行编排层）
SimilarityCheckServiceImpl 仅保留：3 个 public API（签名不变）、createInitialLog/executeCheck/deleteOldLogs/populateEmptyResult/deleteSentencesSerially、数据访问（getLatestSuccessLog/fetchAllSentences/fetchSentencesByName）、边界转换（toProfiles/toProfileMap）。删除已迁出的 private 方法、嵌套 UnionFind、SimilarityResult record。ObjectMapper 已在 PluginConfiguration 注册为 Bean，构造器注入 finder/groupBuilder/codec。

### 3.4 shadow-compare 验证策略
阶段 3.2 算法迁移期间，临时在 executeCheck 中同时调用旧 private 方法与新算法类，断言两者输出（相似对列表、相似度数值）一致后切换。重点核对 `Math.round(similarity*10000)/10000.0` 截断顺序不引入差异。

### 3.5 配套测试
- 纯单测：TextSimilarityCalculatorTest（bigram 分词、cosine 相同文本=1、jaccard 交集/并集、空向量=0）、SimilarityPairFinderTest（阈值过滤、降序）、UnionFindTest（传递合并 A~B~C 同组、路径压缩）、SentenceScorerTest（发布+点赞+浏览+长度+作者+来源加权）、SimilarityGroupBuilderTest（传递分组、过滤已删除、选最优、collectNonOptimalNames）、SentencePairJsonCodecTest（往返序列化、损坏 JSON 容错）
- Mockito+StepVerifier：SimilarityCheckServiceImplTest（performCheck 创建日志并计算、getGroups 无日志返回空、deleteNonOptimalSentences 串行删除），用 FakeExtensionClient 内存存储

**验证**：每子步骤 `./gradlew build` 通过；阶段完成后手动触发相似度检查端点，对比检查日志 JSON 与重构前一致；批量删除非最优句子行为不变（注意遵守项目记忆：串行删除避免 Category 乐观锁冲突、单条删除不触发重检）。

---

## 阶段 4：前端共享抽象（样式变量 + composables）

### 4.1 样式变量统一
创建 [ui/src/styles/variables.scss](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/styles/variables.scss)：
```scss
// rose 配色（替换 26 处硬编码）
$rose-500: #fb7185;  // 主色
$rose-600: #f43f5e;
$rose-700: #e11d48;
// z-index 层级（替换 12 处硬编码，含访客模板）
$z-base: 0; $z-content: 1; $z-floating: 2; $z-dropdown: 10; $z-toolbar: 20;
$z-overlay: 50; $z-modal: 300; $z-dropdown-elevated: 400; $z-toast: 500; $z-top: 100;
:root { --rose-500:#fb7185; --rose-600:#f43f5e; --rose-700:#e11d48; /* z-index vars */ }
```
在 Overview.vue(9处)/SimilarityCheck.vue(15处)/SubmissionList.vue(1处) 替换硬编码为变量。

### 4.2 Composables
- [ui/src/composables/usePagination.ts](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/composables/usePagination.ts)：封装 page/size/total + handlePageChange + 分页 fetch 触发（替换 4 组件 8 处 VPagination 模板逻辑）
- [ui/src/composables/useCrudModal.ts](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/composables/useCrudModal.ts)：封装 showFormModal/isEditing/formData/saving/handleCreate/handleEdit/handleSave/handleDelete 模式（替换 9 处 VModal 重复）
- [ui/src/composables/useAsyncTable.ts](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/composables/useAsyncTable.ts)：封装 loading/list/refresh/handleSearch/keyword 模式（onMounted 加载 + 刷新）
- [ui/src/composables/useToast.ts](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/composables/useToast.ts)：薄包装 Toast.success/error/warning，统一调用方式（87 处）

### 4.3 配套测试
[ui/src/composables/__tests__/usePagination.spec.ts](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/ui/src/composables/__tests__/usePagination.spec.ts)：分页边界、handlePageChange 触发 fetch。`pnpm --filter ui test:unit` 运行。

**验证**：`pnpm --filter ui build` 通过；类型检查 `pnpm --filter ui type-check` 通过。

---

## 阶段 5：前端组件重构

将 6 个组件（SentenceList.vue 1420、SimilarityCheck.vue 1370、Overview.vue 1209、SubmissionList.vue 749、AiGenerateLogList.vue 605、CategoryList.vue 486）逐步改用阶段 4 的 composables 与变量。优先重构最大的 SentenceList.vue 与 SimilarityCheck.vue。对超 1000 行的组件，按职责拆分子组件（如 SentenceList 的"分类侧栏"、"批量导入弹窗"可独立）。遵守项目记忆约束：暗色+rose#fb7185+玻璃拟态、自定义下拉组件置于 body 末尾、Toast z-index:500、el-pagination 中文显示、单条删除乐观 UI 更新不打乱排序。

**验证**：`pnpm --filter ui build` + `type-check` 通过；浏览器手动验证管理端各页面视觉与交互与重构前一致。

---

## 阶段 6：hitokoto.html 拆分为独立 CSS/JS 文件

**关键约束**：严格保持视觉与交互完全一致（暗色+rose#fb7185+玻璃拟态、樱花点击绽放→无缝飘落动画、主题切换、访客提交表单、点赞）。

### 6.1 先确认 Halo 插件静态资源服务机制
检查 [HitokotoTemplateRouter.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/HitokotoTemplateRouter.java) 的路由注册，确认访客模板的访问路径；确认插件 static 资源服务路径（通常 `/plugins/hitokoto-hub/static/...` 来自 `src/main/resources/static/`）。若机制不支持外链，则改为 Thymeleaf fragment 拆分（`th:replace`）。

### 6.2 拆分
- 将 L20-1446 的 `<style>` 内容抽到 `src/main/resources/static/hitokoto.css`（或 `templates/fragments/hitokoto-styles.html`）
- 将 L1616-2819 的 `<script>` 内容抽到 `src/main/resources/static/hitokoto.js`（保留 Thymeleaf data 属性注入的服务端配置读取逻辑）
- hitokoto.html 仅保留 HTML 骨架 + `<link>`/`<script>` 引用 + 服务端 data 注入点
- 12 处 z-index 与 rose 配色改用 CSS 变量（与阶段 4.1 统一）

### 6.3 验证（必须逐项）
- 樱花点击绽放→飘落动画无缝衔接（项目记忆重点）
- 主题切换（暗/亮/auto）+ localStorage 持久化
- 访客提交表单（按项目记忆：访客提交关闭时隐藏组件、IP 冷却限制）
- 点赞交互 + IP 冷却
- 随机获取句子展示
- 移动端响应式

**验证**：浏览器打开访客页逐项对比重构前后；`./gradlew build` 通过。

---

## 阶段 7：端到端验证

1. `./gradlew build`（含 test）全绿
2. `pnpm --filter ui build` + `type-check` + `lint` 全绿
3. `pnpm --filter ui test:unit` 全绿
4. 手动回归核心场景（对照阶段 0 基线）：
   - 后端：随机获取/多分类/点赞+冷却/访客提交+冷却/Excel+JSON 导入/相似度检查/批量删除非最优/概览图表/今日详情
   - 前端管理端：6 个页面 CRUD/分页/搜索/批量/弹窗
   - 访客端：阶段 6.3 全部动画与交互项
5. 确认无 `console.log` 残留、无 `System.out.println` 残留、无硬编码 rose/z-index 残留

## 全程风险控制要点

1. **小步提交**：每子步骤独立 commit，便于二分定位回归
2. **shadow-compare**（阶段 3 专用）：算法拆分时新旧并行跑、断言一致后切换，避免数值截断差异
3. **不动 GVK 与持久化 JSON 格式**：SimilarityCheckLog/Sentence/CategoryViewRecord 的 GVK 与 spec.similarPairs JSON 格式绝对不动，避免持久化数据反序列化失败
4. **不动接口签名**：SimilarityCheckService 3 个 public 方法签名稳定，避免影响 endpoint
5. **FakeExtensionClient 优于 Mockito mock**：service 内部有 `.block()`，fake 内存版对 reactor 调度更安全；`Mono.just(...)` 同步完成保证 `.block()` 不死锁
6. **Spring 上下文最小化**：endpoint 集成测试用 `@SpringJUnitConfig` 显式列 Component 类，避免触发 Halo 完整插件上下文（依赖运行时 PluginContext）
7. **访客模板零行为变更**：阶段 6 仅搬运代码到独立文件 + 变量化，不改任何动画时序/物理参数（damping 0.99、gravity 0.005、scale 15 帧等）
8. **遵守项目记忆全部硬约束**：单条删除乐观 UI 不重检、批量删除串行失败跳过、分页中文、Toast z-index 500、自定义下拉置 body 末尾、AI 日志设置按开关隐藏

## 关键文件清单

### 后端需修改
- [SimilarityCheckServiceImpl.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/impl/SimilarityCheckServiceImpl.java)（898→~280 行）
- [SentencePublicEndpoint.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/SentencePublicEndpoint.java)（去重 getClientIp/formatRemainingTime/CategoryViewRecord/likeCache）
- [SentenceSubmissionPublicEndpoint.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/SentenceSubmissionPublicEndpoint.java)（去重 + 删 SubmissionCooldownState）
- [OverviewConsoleEndpoint.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/endpoint/OverviewConsoleEndpoint.java)（514→~380 行，抽离 EchartsDataBuilder）
- [HitokotoHubPlugin.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/HitokotoHubPlugin.java)（抽离索引注册 + System.out→log）
- [SimilarityGroup.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/extension/SimilarityGroup.java)（scoreSentence 标 @Deprecated 委托）

### 后端需新建
- utils/{HttpUtils,TimeFormatUtils,CategoryViewRecordFactory,IpCooldownCache,SimpleCooldownState,BatchCooldownState,ExtensionIndexRegistrar}.java
- endpoint/overview/EchartsDataBuilder.java
- service/similarity/{SentenceProfile,SentencePair,SimilarityMappers,TextSimilarityCalculator,SimilarityPairFinder,UnionFind,SentenceScorer,SimilarityGroupBuilder,SentencePairJsonCodec}.java
- src/test 下对应测试类 + support/{TestFixtures,FakeExtensionClient,MockExtensionClient}.java

### 前端需修改
- ui/src/components/{SentenceList,SimilarityCheck,Overview,SubmissionList,AiGenerateLogList,CategoryList}.vue
- src/main/resources/templates/hitokoto.html（拆分 + 变量化）

### 前端需新建
- ui/src/styles/variables.scss
- ui/src/composables/{usePagination,useCrudModal,useAsyncTable,useToast}.ts + __tests__
- src/main/resources/static/hitokoto.{css,js}（或 templates/fragments/）

### 不修改（接口/契约稳定）
- [SimilarityCheckService.java](file:///c:/Users/19002/Documents/Trae/plugin-hitokoto-hub/src/main/java/top/puresky/hitokotohub/service/SimilarityCheckService.java)（接口签名）
- 各 extension 的 GVK 与字段
- build.gradle / ui/package.json（依赖已就绪）
