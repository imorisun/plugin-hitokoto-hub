# 轻言（Hitokoto Hub）

> 轻拾人间辞藻，言说万千心绪

[![Halo](https://img.shields.io/badge/Halo-%3E%3D2.25.0-blue.svg)](https://halo.run)
[![License](https://img.shields.io/badge/License-GPL--3.0-green.svg)](./LICENSE)
[![Version](https://img.shields.io/badge/Version-1.0.0-orange.svg)](https://github.com/imorisun/plugin-hitokoto-hub/releases)

轻言是一款 [Halo 2.x](https://halo.run) 生态的开源插件，为你的网站注入「一句话」的灵动与温度。支持创建、管理海量句子，按分类归档，提供随机获取、关键词搜索、点赞互动、AI 生成、相似度检测、句子分享、访客投递等丰富功能。无论是诗词名言、影视台词还是生活感悟，轻言让你的网站成为一个会说话的角落。

## ✨ 功能特性

- **句子管理**：创建、编辑、删除句子，支持 JSON / Excel 批量导入与导出，自动映射字段别名
- **分类归档**：自定义分类体系，侧边栏导航，Reconciler 自动统计各分类句子数量
- **随机获取**：基于索引分页的高效随机算法，海量数据秒级响应；支持多分类筛选、返回数量限制、JSON / 纯文本响应
- **模糊搜索**：基于索引的关键词搜索，支持按分类过滤
- **点赞互动**：开放点赞 / 取消点赞接口，基于 IP 冷却机制防止刷赞
- **浏览统计**：自动累计浏览量，支持按天 / 周 / 月粒度的趋势分析与 ECharts 可视化
- **数据看板**：后台概览展示句子总数、分类总数、发布状态分布、各分类浏览 / 点赞量
- **句子分享**：内置 SVG 分享卡片生成（暗色 / 亮色双主题），支持复制链接、保存图片、复制内容；分享链接可直达指定句子
- **主题集成**：提供 `hitokotoFinder` Finder API，可在主题模板中直接调用；内置默认展示页 `/hitokoto`，含花瓣飘落动画、主题切换、自动轮播
- **AI 生成**：可选集成 [AI Foundation](https://www.halo.run/store/apps/app-acslk9nu)，支持定时按主题自动生成句子，可配置自动发布
- **相似度检查**：基于余弦相似度（TF-IDF）与 Jaccard 算法检测重复句子，通过并查集归组并按综合评分选出最优句子，支持批量清理
- **访客投递**：内置访客提交入口，支持 PENDING / APPROVED / REJECTED 三态审核工作流，可配置 IP 冷却与提交限制
- **权限控制**：基于 Halo RBAC 的三层角色模板（公共接口 / 查看 / 管理）
- **数据自清理**：定时清理过期缓存、统计记录、日志数据，支持条数与天数双重保留策略
- **数据一致性**：Reconciler 自动维护分类计数，删除句子时自动清理关联数据

## 🚀 在线演示

- **演示站点**：<https://www.puresky.top/hitokoto>
- **API 文档**：<https://plugin-hitokoto-hub.apifox.cn/>
- **GitHub 仓库**：<https://github.com/imorisun/plugin-hitokoto-hub>
- **问题反馈**：<https://github.com/imorisun/plugin-hitokoto-hub/issues>

## 📋 环境要求

| 依赖项 | 版本要求 | 说明 |
|--------|----------|------|
| Halo | `>= 2.25.0` | 必需，插件基于 Halo 2.25 平台构建 |
| Java | `21` | 构建时需要 JDK 21 |
| Node.js | `>= 18` | 构建前端时需要，推荐使用 pnpm |
| AI Foundation | 任意版本（可选） | 若需使用 AI 生成与相似度检查功能，需安装 [AI Foundation](https://www.halo.run/store/apps/app-acslk9nu) |

> AI Foundation 为可选依赖，未安装时 AI 生成与相似度检查的定时任务会自动跳过，不影响其他功能。

## 📦 安装

### 方式一：应用市场安装（推荐）

在 Halo 后台「应用市场」中搜索「轻言」并一键安装。

### 方式二：手动上传安装

1. 前往 [Releases](https://github.com/imorisun/plugin-hitokoto-hub/releases) 下载最新版 `plugin-hitokoto-hub-x.x.x.jar`
2. 在 Halo 后台「插件」管理中点击「安装」，上传 jar 文件
3. 启用插件后，后台左侧菜单会出现「轻言」入口

### 方式三：从源码构建

参考下方 [开发指南](#-开发指南)。

## 🎯 快速开始

### 1. 创建分类

进入「轻言 → 数据管理」，在左侧分类面板点击 `+` 号，填写分类名称与描述。

> 插件启动时会自动创建名为「未分类」的内置分类，并将分类为空或已失效的句子自动归入其中。

### 2. 新建句子

点击右上角「新建句子」，填写句子内容、作者、来源并选择分类。还可设置自定义跳转链接（`linkUrl`）与关联文章（`postName`），在前台展示时点击句子可跳转至对应页面。

### 3. 批量导入 / 导出

- **JSON / Excel 导入**：直接粘贴 JSON 数组或上传 `.xlsx` 文件，插件自动识别表头并映射字段
- **导出**：支持按分类导出为 JSON 或 Excel 格式

### 4. 调用公开接口

```bash
# 随机获取 1 条句子（默认返回 JSON）
curl 'https://your-domain.com/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/random'

# 按分类随机获取 8 条，返回纯文本
curl 'https://your-domain.com/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/random?categoryName=category-xxx&limit=8&encode=text'

# 获取所有分类
curl 'https://your-domain.com/apis/public.api.hitokotohub.puresky.top/v1alpha1/category/list'

# 点赞
curl 'https://your-domain.com/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/like?name=sentence-xxxx&action=like'

# 获取句子分享数据
curl 'https://your-domain.com/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/sentence-xxxx/share'

# 获取句子分享卡片 SVG
curl 'https://your-domain.com/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/sentence-xxxx/share/card?theme=dark'
```

## 🎨 主题集成

插件内置默认展示模板，访问路径为 `/hitokoto`，支持暗色 / 亮色 / 跟随系统三种主题、花瓣飘落动画、双击点赞、句子分享、定时自动切换等特性。你也可以通过 Finder API 或 REST API 在自己的主题中自定义展示。

### Finder API（推荐）

在主题 Thymeleaf 模板中直接调用 `hitokotoFinder`：

```html
<!-- 随机获取 1 条句子 -->
<div th:each="s : ${hitokotoFinder.randomSentences(1, null)}">
    <p th:text="${s.content}"></p>
    <span th:text="${s.author}"></span>
    <span th:text="${s.source}"></span>
    <span th:text="${s.likeCount}"></span>
    <span th:text="${s.viewCount}"></span>
    <!-- jumpUrl 由 linkUrl 或关联文章解析而来，非空时句子可点击跳转 -->
    <a th:if="${s.jumpUrl}" th:href="${s.jumpUrl}">阅读原文</a>
</div>

<!-- 按名称获取单条已发布句子（分享链接直达用） -->
<div th:with="s = ${hitokotoFinder.sentenceByName('sentence-xxxx')}">
    <p th:text="${s.content}"></p>
</div>

<!-- 获取分类列表（仅返回有句子的分类） -->
<div th:each="c : ${hitokotoFinder.listCategories()}">
    <a th:href="@{/hitokoto(category=${c.name})}" th:text="${c.displayName}"></a>
    <span th:text="${c.sentenceCount}"></span>
</div>
```

`SentenceVo` 字段：`name`、`content`、`author`、`source`、`categoryName`、`likeCount`、`viewCount`、`jumpUrl`

`CategoryVo` 字段：`name`、`displayName`、`description`、`sentenceCount`

### REST API

在前端脚本中直接调用公开 API：

```javascript
// 随机获取句子
fetch('/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/random?limit=8')
  .then(res => res.json())

// 点赞句子
fetch('/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/like?name=sentence-xxxx&action=like')
  .then(res => res.json())
```

### 分享链接

访问 `/hitokoto?sentence=sentence-xxxx` 可直达指定句子的展示页面，适合在社交媒体或文章中引用某条句子。分享视图会自动暂停句子轮播，聚焦展示被分享的句子。

## 📖 API 文档

### 公开 API（无需鉴权）

所有公开 API 前缀为 `/apis/public.api.hitokotohub.puresky.top/v1alpha1`：

| 接口 | 方法 | 说明 |
|------|------|------|
| `/sentence/random` | GET | 随机获取句子，支持分类、数量、格式参数 |
| `/sentence/like` | GET | 点赞 / 取消点赞 |
| `/category/list` | GET | 获取所有分类列表 |
| `/sentence-submission/submit` | POST | 访客投递句子 |
| `/sentence/{name}/share` | GET | 获取句子分享数据 |
| `/sentence/{name}/share/card` | GET | 获取句子分享卡片 SVG（支持 `theme=dark/light`） |

### 后台管理 API

后台管理 API 前缀为 `/apis/console.api.hitokotohub.puresky.top/v1alpha1`，需具备管理权限：

| 接口 | 方法 | 说明 |
|------|------|------|
| `/sentence` | GET | 分页查询句子 |
| `/sentence/-/batch` | POST | 批量创建句子 |
| `/sentence/-/import-excel` | POST | Excel 导入句子 |
| `/sentence/-/export` | GET | 导出句子（支持 `format=json/excel`、`categoryName` 筛选） |
| `/sentence/-/clear-uncategorized` | POST | 清空未分类句子 |
| `/overview` | GET | 获取概览统计 |
| `/overview/view-statistics` | GET | 获取分类浏览量时序数据 |
| `/sentence-submissions` | GET | 分页查询访客提交记录 |
| `/sentence-submissions/{name}/approve` | POST | 审核通过 |
| `/sentence-submissions/{name}/reject` | POST | 审核拒绝 |
| `/ai-generate-logs/-/trigger` | POST | 手动触发 AI 生成 |
| `/similarity-check-logs/-/trigger` | POST | 手动触发相似度检查 |
| `/similarity-check-groups/-/delete-nonoptimal` | POST | 批量删除非最优句子 |
| `/sentence/{name}/share` | GET | 获取指定句子分享数据（含未发布） |
| `/sentence/{name}/share/card` | GET | 获取指定句子分享卡片 SVG |

此外，插件通过 Halo 扩展机制暴露了标准的 CRUD 接口：

- `/apis/hitokotohub.puresky.top/v1alpha1/sentences`
- `/apis/hitokotohub.puresky.top/v1alpha1/categories`

完整的 API 定义可在 [api-docs/openapi/v3_0/extensionApis.json](./api-docs/openapi/v3_0/extensionApis.json) 中查看，或参考 [Apifox 文档](https://plugin-hitokoto-hub.apifox.cn/)。

## ⚙️ 配置说明

插件设置位于「轻言 → 设置」，共分为六组：

### 基本设置

| 设置项 | 默认值 | 说明 |
|--------|--------|------|
| 最大随机条数 | 20 | 随机接口允许响应的最大句子数量（1-100） |
| 默认随机条数 | 1 | 随机接口未指定 `limit` 时的默认返回数量 |
| 默认分类 | 空（全部） | 随机接口未指定分类时使用的默认分类，可多选 |
| 默认返回格式 | JSON | 随机接口默认返回格式：`json` 或 `text` |
| 点赞冷却时间 | 12 小时 | 同一 IP 对同一句子两次操作的间隔时间 |
| 启用浏览量统计 | true | 随机获取句子时是否自动增加浏览量 |
| 统计数据最大保留条数 | 1000 | 超过此数量将自动删除最旧的统计记录 |
| 统计数据保留天数 | 90 | 超过此天数的统计数据将被清理 |

### AI 设置

| 设置项 | 默认值 | 说明 |
|--------|--------|------|
| 启用 AI 生成 | false | 开启后定时任务将自动生成句子 |
| 自动生成时间 | `0 0 2 * * *` | 6 位 Cron 表达式，支持预设或自定义 |
| 语言模型 | 无 | 从 AI Foundation 已配置的语言模型中选择 |
| 角色设定 | 内置默认提示词 | AI 的系统提示词，留空使用默认的「文字匠人」角色 |
| 生成主题 | 温柔治愈 | 句子围绕的主题 |
| 生成数量 | 25 | 每次生成的句子数量（1-100） |
| 目标分类 | 自动选择 | 生成句子保存到哪个分类 |
| 是否自动发布 | false | 开启后生成的句子自动发布 |
| AI 日志保留策略 | 500 条 / 30 天 | 超过限制自动清理最旧日志 |

### 访客提交设置

| 设置项 | 默认值 | 说明 |
|--------|--------|------|
| 启用访客提交 | true | 开启后访客可在 `/hitokoto` 投递句子 |
| 默认提交分类 | 空 | 访客未选择分类时使用的默认分类 |
| 审核通过后自动发布 | false | 开启后审核通过的句子自动发布 |
| 连续提交上限 | 3 | 同一 IP 在冷却周期内可连续提交的数量 |
| 提交冷却时间 | 10 分钟 | 达到上限后的冷却时间，`0` 表示不限制 |
| 提交记录最大保留条数 | 1000 | 超过此数量自动删除最旧的已处理记录 |

### 相似度检查设置

| 设置项 | 默认值 | 说明 |
|--------|--------|------|
| 启用定时检查 | false | 开启后系统将按设定周期自动比对所有句子 |
| 定时检查时间 | `0 0 2 * * *` | 6 位 Cron 表达式 |
| 相似度算法 | COSINE | `COSINE`（余弦相似度，基于 TF-IDF）或 `JACCARD` |
| 相似度阈值 | 0.8 | 超过此阈值的句子对将被标记为相似 |

### 模板展示设置

| 设置项 | 默认值 | 说明 |
|--------|--------|------|
| 默认主题 | 跟随系统 | 访客首次访问时的主题配色：`auto` / `dark` / `light` |
| 显示花瓣飘落动画 | true | 关闭后模板页面不展示花瓣飘落背景 |
| 显示首次操作提示 | true | 开启后页面加载时显示「双击屏幕任意位置点赞」提示 |
| 定时自动切换句子 | false | 开启后页面按设定间隔自动切换展示新句子 |
| 自动切换间隔 | 10 秒 | 句子自动切换的时间间隔（3-3600 秒） |

### 分享设置

| 设置项 | 默认值 | 说明 |
|--------|--------|------|
| 站点名称 | 轻言 | 展示在分享卡片上的站点名称，留空则使用 Halo 站点标题 |

## 🔄 定时任务

插件内置以下定时任务，均无需手动配置：

| 任务 | 触发时间 | 说明 |
|------|----------|------|
| 清理点赞缓存 | 每 6 小时 | 清理内存中过期的点赞冷却记录 |
| 清理分类统计记录 | 每天 03:00 | 按天数与条数双重策略清理过期的 `CategoryViewRecord` |
| 清理 AI 生成日志 | 每天 03:30 | 按天数与条数双重策略清理过期的 `AiGenerateLog` |
| AI 自动生成句子 | 由 AI 设置中的 Cron 驱动 | 配置变更后自动重新注册 |
| 定时相似度检查 | 由相似度设置中的 Cron 驱动 | 配置变更后自动重新注册 |

## 📊 数据模型

插件注册了六个自定义扩展模型（GVK group: `hitokotohub.puresky.top`，version: `v1alpha1`）：

### Sentence（句子）

| 字段路径 | 类型 | 说明 |
|----------|------|------|
| `spec.categoryName` | String | 所属分类 `metadata.name`（必填，已建立索引） |
| `spec.content` | String | 句子内容，最长 500（必填，已建立索引） |
| `spec.author` | String | 作者，默认「匿名」，最长 50 |
| `spec.source` | String | 来源，默认「未知」，最长 100 |
| `spec.createdBy` | String | 创建用户 |
| `spec.linkUrl` | String | 自定义跳转链接，最长 500 |
| `spec.postName` | String | 关联文章名称（`metadata.name`），最长 100 |
| `status.isPublished` | Boolean | 是否已发布（已建立索引） |
| `status.likeCount` | Long | 点赞数（已建立索引） |
| `status.viewCount` | Long | 浏览量（已建立索引） |

### Category（分类）

| 字段路径 | 类型 | 说明 |
|----------|------|------|
| `spec.name` | String | 分类显示名称，最长 50 |
| `spec.description` | String | 分类描述，最长 200 |
| `status.sentenceCount` | Long | 该分类下句子数量（由 Reconciler 自动维护） |

### CategoryViewRecord（分类事件记录）

用于浏览 / 点赞趋势统计，包含 `categoryName`、`eventType`（VIEW / LIKE）、`sentenceName`、`ip` 等字段。

### SentenceSubmission（访客投递记录）

包含 `content`、`author`、`source`、`categoryName`、`submitterName`、`submitterIp`、`status`（PENDING / APPROVED / REJECTED）、`reviewedBy`、`reviewNote`、`reviewedAt`、`sentenceName` 等字段。

### AiGenerateLog（AI 生成日志）

包含 `modelName`、`topic`、`requestCount`、`successCount`、`failedCount`、`categoryName`、`autoPublish`、`status`（RUNNING / SUCCESS / PARTIAL_SUCCESS / FAILED）、`errorMessage`、`durationMs`、`generatedData` 等字段。

### SimilarityCheckLog（相似度检查日志）

包含 `triggerType`（MANUAL / SCHEDULED）、`triggeredBy`、`algorithm`（COSINE / JACCARD）、`threshold`、`totalSentences`、`totalPairs`、`similarPairCount`、`similarPairs`、`status`、`errorMessage`、`durationMs` 等字段。

## 🏗️ 项目结构

```
plugin-hitokoto-hub/
├── src/main/java/top/puresky/hitokotohub/
│   ├── HitokotoHubPlugin.java              # 插件入口，注册索引，迁移孤儿句子
│   ├── HitokotoTemplateRouter.java         # 默认模板路由 /hitokoto（含分享直达）
│   ├── PluginConfiguration.java            # 插件配置
│   ├── UncategorizedConstants.java         # 「未分类」内置分类常量
│   ├── config/                             # 设置配置读取
│   │   ├── SettingConfig.java              # 六组配置接口
│   │   └── impl/SettingConfigImpl.java
│   ├── endpoint/                           # 自定义 API 端点（公开 + 后台）
│   │   ├── SentencePublicEndpoint.java     # 句子公开接口
│   │   ├── SentenceConsoleEndpoint.java    # 句子管理 + Excel 导入导出
│   │   ├── SentenceSharePublicEndpoint.java    # 分享公开接口
│   │   ├── SentenceShareConsoleEndpoint.java   # 分享管理接口
│   │   ├── SentenceSubmissionPublicEndpoint.java
│   │   ├── SentenceSubmissionConsoleEndpoint.java
│   │   ├── CategoryPublicEndpoint.java
│   │   ├── CategoryConsoleEndpoint.java
│   │   ├── OverviewConsoleEndpoint.java
│   │   ├── AiGenerateLogConsoleEndpoint.java
│   │   ├── SimilarityCheckConsoleEndpoint.java
│   │   └── overview/EchartsDataBuilder.java
│   ├── extension/                          # 自定义扩展模型（GVK）
│   │   ├── Sentence.java
│   │   ├── Category.java
│   │   ├── CategoryViewRecord.java
│   │   ├── SentenceSubmission.java
│   │   ├── AiGenerateLog.java
│   │   ├── SimilarityCheckLog.java
│   │   └── SimilarityGroup.java
│   ├── finder/                             # 主题 Finder API
│   │   ├── HitokotoFinder.java
│   │   └── impl/HitokotoFinderImpl.java
│   ├── reconciler/                         # 资源 Reconciler
│   │   ├── SentenceReconciler.java
│   │   └── CategoryReconciler.java
│   ├── scheduled/                          # 定时任务
│   │   └── StatsCleanupScheduler.java
│   ├── service/                            # 业务服务层
│   │   ├── AiGenerateService.java
│   │   ├── CategoryCountService.java
│   │   ├── SentenceShareService.java       # 句子分享服务
│   │   ├── SimilarityCheckService.java
│   │   ├── dto/SharePayload.java
│   │   ├── impl/
│   │   ├── share/ShareCardSvgBuilder.java  # SVG 分享卡片生成器
│   │   └── similarity/                     # 相似度算法组件
│   └── utils/                              # 工具类
├── src/main/resources/
│   ├── extensions/                         # 角色模板与设置定义
│   │   ├── settings.yaml                   # basic/ai/submission/similarity/template/share 六组
│   │   └── role-template-*.yaml
│   ├── templates/
│   │   ├── hitokoto.html                   # 默认展示模板（含分享弹窗、提交表单）
│   │   ├── hitokoto-styles.html
│   │   └── hitokoto-scripts.html
│   ├── plugin.yaml                         # 插件清单
│   └── logo.png
├── ui/                                     # 后台前端（Vue 3 + Element Plus + ECharts）
│   └── src/
│       ├── api/generated/                  # OpenAPI 自动生成的 API 客户端
│       ├── components/
│       │   ├── Overview.vue
│       │   ├── CategoryList.vue
│       │   ├── SentenceList.vue
│       │   ├── SentenceShareModal.vue      # 句子分享弹窗
│       │   ├── SubmissionList.vue
│       │   ├── AiGenerateLogList.vue
│       │   └── SimilarityCheck.vue
│       └── views/HomeView.vue
└── api-docs/openapi/v3_0/                  # OpenAPI 文档
```

## 🔧 开发指南

### 技术栈

- **后端**：Java 21、Spring WebFlux、Reactor、Halo Extension API、FastExcel
- **前端**：Vue 3、Element Plus、ECharts、TypeScript、Rsbuild、TailwindCSS
- **构建**：Gradle、pnpm

### 本地开发

```bash
# 1. 克隆项目
git clone https://github.com/imorisun/plugin-hitokoto-hub.git
cd plugin-hitokoto-hub

# 2. 构建前端
cd ui
pnpm install
pnpm build

# 3. 构建插件
cd ..
./gradlew build
# 构建产物位于 build/libs/plugin-hitokoto-hub-1.0.0-SNAPSHOT.jar
```

### 开发模式

```bash
# 前端开发模式（监听文件变化并实时构建）
cd ui
pnpm dev
```

后端可配合 [Halo Plugin Devtools](https://docs.halo.run/developer-guide/plugin/dev-tools) 进行调试，`build.gradle` 中已配置 `haloPlugin` 扩展。

### 重新生成 API 客户端

当后端 API 发生变化时，可重新生成前端 API 客户端：

```bash
./gradlew generateOpenApiClient
```

生成的代码位于 `ui/src/api/generated/`。

## 📚 可用数据源

可从以下数据源导入句子到轻言：

- [sentences-bundle](https://github.com/hitokoto-osc/sentences-bundle) — 一言社区官方句子库

## ❓ 常见问题

### 更新插件后出现问题

由于部分版本可能修改了设置项结构，更新后请：
1. 进入「插件 → 轻言 → 设置」，点击「重置」恢复默认设置
2. 重新配置所需设置项
3. 若仍有问题，重启 Halo

### AI 生成功能不可用

AI 自动生成需要同时满足：
1. 已安装并启用 [AI Foundation](https://www.halo.run/store/apps/app-acslk9nu)
2. 在 AI Foundation 中已配置可用的语言模型
3. 在轻言设置中选择了对应的语言模型

### 相似度检查功能不可用

相似度检查同样依赖 AI Foundation 插件，请确保已安装并启用。检查任务需要通过定时任务或手动触发来执行。

### 句子未在前台显示

前台随机接口仅返回已发布（`status.isPublished = true`）的句子。非超级管理员创建的句子默认未发布，需管理员在后台手动发布。

### 访客投递接口返回 403

`code: "submitted_disabled"` 表示后台未开启访客提交。进入「轻言 → 设置 → 访客提交设置」开启即可。

### 访客投递接口返回 429

`code: "rate_limited"` 表示同一 IP 在冷却期内重复提交或达到连续提交上限。可在「访客提交设置」中调整相关参数。注意：冷却基于内存缓存，插件重启后会重置。

## 📄 许可证

[GPL-3.0](./LICENSE) © [晨阳](https://github.com/imorisun)

## 🙏 致谢

- [Halo](https://github.com/halo-dev/halo) — 强大易用的开源建站工具
- [一言](https://hitokoto.cn) — 一言项目，灵感来源
- [sentences-bundle](https://github.com/hitokoto-osc/sentences-bundle) — 句子数据源
- [AI Foundation](https://www.halo.run/store/apps/app-acslk9nu) — Halo 官方 AI 基座
- 所有为项目做出贡献的开发者
