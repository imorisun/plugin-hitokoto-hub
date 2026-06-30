# 轻言（Hitokoto Hub）

> 轻拾人间辞藻，言说万千心绪

轻言是一款 [Halo 2.x](https://halo.run) 插件，为你的网站注入"一句话"的灵动与温度。它支持创建、管理海量句子，按分类归档，并提供随机获取、关键词搜索、点赞互动等丰富的开放接口。无论是诗词名言、影视台词还是生活感悟，轻言让你的网站成为一个会说话的角落。
- **QQ 交流群**：

<a href="https://www.lik.cc/upload/iShot_2025-03-03_16.03.00.png">
  <img src="https://www.lik.cc/upload/iShot_2025-03-03_16.03.00.png" width="180" alt="QQ群">
</a>

## 目录

- [功能特性](#功能特性)
- [演示与交流](#演示与交流)
- [插件截图](#插件截图)
- [环境要求](#环境要求)
- [安装](#安装)
- [快速上手](#快速上手)
- [主题集成](#主题集成)
- [公开 API 文档](#公开-api-文档)
- [后台管理 API](#后台管理-api)
- [Finder API](#finder-api)
- [数据模型](#数据模型)
- [插件设置](#插件设置)
- [AI 自动生成](#ai-自动生成)
- [权限体系](#权限体系)
- [项目结构](#项目结构)
- [开发指南](#开发指南)
- [可用数据源](#可用数据源)
- [常见问题](#常见问题)
- [许可](#许可)
- [致谢](#致谢)

## 功能特性

- **句子管理**：创建、编辑、删除句子，支持 JSON 批量导入与 Excel（.xlsx）导入，自动映射字段
- **分类归档**：自定义分类，侧边栏导航，Reconciler 自动统计各分类下的句子数量
- **随机获取**：基于索引分页的随机算法，6000 条数据依然秒级响应；支持多分类筛选、返回数量限制、JSON / 纯文本两种响应格式
- **模糊搜索**：基于索引的 `spec.content` 关键词搜索，支持按分类过滤
- **点赞互动**：开放点赞 / 取消点赞接口，基于 IP 的冷却机制防止刷赞，自动记录点赞事件
- **浏览统计**：随机获取句子时可自动累计浏览量，并生成 `CategoryViewRecord` 事件用于趋势分析
- **数据看板**：后台概览页面展示句子总数、分类总数、发布状态分布；支持按天 / 周 / 月粒度的分类浏览趋势折线图（ECharts）
- **主题集成**：提供 `hitokotoFinder` Finder API，可在 Halo 主题模板中直接调用；内置默认模板 `/hitokoto`，带樱花飘落动画
- **AI 生成**：可选依赖 [AI Foundation](https://www.halo.run/store/apps/app-acslk9nu)，支持定时按主题自动生成句子并可配置自动发布
- **权限控制**：基于 Halo RBAC 的三层角色模板（公共接口 / 查看 / 管理），公共接口自动授权给匿名用户
- **数据自清理**：定时清理过期的点赞缓存与统计记录，支持按条数和天数双重保留策略

## 演示与交流

- **演示站点**：<https://www.puresky.top/hitokoto>
- **文档**：<https://www.puresky.top/docs/>
- **API 文档**：<https://plugin-hitokoto-hub.apifox.cn/>
- **QQ 交流群**：

  <a href="https://www.lik.cc/upload/iShot_2025-03-03_16.03.00.png">
    <img src="https://www.lik.cc/upload/iShot_2025-03-03_16.03.00.png" width="180" alt="QQ群">
  </a>

## 插件截图

|                                                                |                                                                |
|----------------------------------------------------------------|----------------------------------------------------------------|
| ![轻言数据概览](https://www.puresky.top/upload/1777469591419.webp)   | ![轻言数据管理](https://www.puresky.top/upload/1777469648699.webp)   |
| 轻言数据概览                                                         | 轻言数据管理                                                         |
| ![轻言数据批量导入](https://www.puresky.top/upload/1777469675652.webp) | ![轻言单条数据创建](https://www.puresky.top/upload/1777469694151.webp) |
| 轻言数据批量导入                                                       | 轻言单条数据创建                                                       |
| ![轻言默认模板](https://www.puresky.top/upload/1777735550856.webp)   | |
| 轻言默认模板                                                         | |

## 环境要求

| 依赖项          | 版本要求          | 说明                                  |
|--------------|---------------|---------------------------------------|
| Halo         | `>= 2.25.0`   | 必需，插件基于 Halo 2.25 平台构建           |
| Java         | `21`          | 构建时需要 JDK 21                        |
| Node.js      | `>= 18`       | 构建前端时需要，推荐使用 pnpm 作为包管理器 |
| AI Foundation| 任意版本（可选）     | 若需使用 AI 自动生成功能，需在 Halo 应用市场安装并启用 [AI Foundation](https://www.halo.run/store/apps/app-acslk9nu) |

## 安装

### 方式一：应用市场安装（推荐）

在 Halo 后台「应用市场」中搜索「轻言」并一键安装。

### 方式二：手动上传安装

1. 前往 [Releases](https://github.com/imorisun/plugin-hitokoto-hub/releases) 下载最新版 `plugin-hitokoto-hub-x.x.x.jar`
2. 在 Halo 后台的「插件」管理中点击「安装」，上传 jar 文件
3. 启用插件后，后台左侧菜单会出现「轻言」入口

### 方式三：从源码构建

参考下方 [开发指南](#开发指南)。

## 快速上手

### 1. 创建分类

进入「轻言 → 数据管理」，在左侧分类面板点击 `+` 号，填写分类名称与描述。

### 2. 新建句子

点击右上角「新建句子」，填写句子内容、作者、来源并选择分类。

- 若当前用户具有 `super-role`，句子将自动发布；否则默认未发布，需管理员审核发布。

### 3. 批量导入

支持两种格式：

- **JSON 批量导入**：直接粘贴或上传符合 `Sentence` 结构的 JSON 数组
- **Excel 导入**：上传 `.xlsx` 文件，插件会自动识别表头（支持 `hitokoto / content / sentence / 句子内容 / 内容 / 一言` 等别名映射到句子内容字段，`from_who / author / 作者` 映射到作者，`from / source / 来源 / 出处` 映射到来源），也可在导入时手动指定列名

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

# 取消点赞
curl 'https://your-domain.com/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/like?name=sentence-xxxx&action=unlike'
```

## 主题集成

插件内置一个默认的展示模板，访问路径为 `/hitokoto`，带有逐字淡入动画与樱花飘落效果。你也可以通过 Finder API 或 REST API 在自己的主题中自定义展示方式。

### 方式一：Finder API（推荐）

在主题 Thymeleaf 模板中直接调用 `hitokotoFinder`：

```html
<!-- 随机获取 1 条句子 -->
<div th:each="s : ${hitokotoFinder.randomSentences(1, null)}">
    <p th:text="${s.content}"></p>
    <span th:text="${s.author}"></span>
    <span th:text="${s.source}"></span>
    <span th:text="${s.likeCount}"></span>
    <span th:text="${s.viewCount}"></span>
</div>

<!-- 获取分类列表（仅返回有句子的分类） -->
<div th:each="c : ${hitokotoFinder.listCategories()}">
    <a th:href="@{/hitokoto(category=${c.name})}" th:text="${c.displayName}"></a>
    <span th:text="${c.sentenceCount}"></span>
</div>

<!-- 按分类随机获取 8 条 -->
<div th:each="s : ${hitokotoFinder.randomSentences(8, 'category-xxx')}">
    <p th:text="${s.content}"></p>
</div>
```

`SentenceVo` 字段：`name`、`content`、`author`、`source`、`categoryName`、`likeCount`、`viewCount`
`CategoryVo` 字段：`name`、`displayName`、`description`、`sentenceCount`

### 方式二：REST API

在前端脚本中直接调用公开 API：

```javascript
// 随机获取句子
fetch('/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/random?limit=8')
  .then(res => res.json())

// 按分类随机获取
fetch('/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/random?categoryName=category-xxx&limit=8')
  .then(res => res.json())

// 获取分类
fetch('/apis/public.api.hitokotohub.puresky.top/v1alpha1/category/list')
  .then(res => res.json())

// 点赞句子
fetch('/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/like?name=sentence-xxxx&action=like')
  .then(res => res.json())

// 取消点赞
fetch('/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/like?name=sentence-xxxx&action=unlike')
  .then(res => res.json())
```

## 公开 API 文档

所有公开 API 前缀为 `/apis/public.api.hitokotohub.puresky.top/v1alpha1`，无需鉴权（自动授权给匿名用户）。

### 随机获取句子

`GET /sentence/random`

| 参数             | 类型     | 必填  | 说明                                                                  |
|----------------|--------|-----|-----------------------------------------------------------------------|
| `categoryName` | String | 否   | 分类 `metadata.name`，不传则使用插件设置中的默认分类，均未配置则返回全部分类的句子            |
| `limit`        | Int    | 否   | 返回数量，默认使用插件设置值，最大不超过插件设置中的「最大随机条数」                         |
| `encode`       | String | 否   | 返回格式：`json`（默认，返回 `RandomSentenceResponse`）或 `text`（纯文本，每行一句） |

**响应示例（JSON）**：

```json
{
  "categoryName": "温柔治愈",
  "maxRandomLimit": 20,
  "returned": 1,
  "sentences": [
    {
      "metaName": "sentence-xxxx",
      "author": "佚名",
      "content": "愿你被这个世界温柔以待。",
      "source": "未知",
      "createdBy": "admin",
      "likeCount": 12,
      "viewCount": 348
    }
  ]
}
```

### 点赞 / 取消点赞

`GET /sentence/like`

| 参数       | 类型     | 必填  | 说明                          |
|----------|--------|-----|-----------------------------|
| `name`   | String | 是   | 句子的 `metadata.name`        |
| `action` | String | 否   | `like`（默认）或 `unlike`      |

**响应示例**：

```json
{
  "success": true,
  "code": "ok",
  "message": "点赞成功",
  "sentence": { "metaName": "sentence-xxxx", "content": "...", "likeCount": 13 }
}
```

> 同一 IP 对同一句子的点赞 / 取消点赞操作受冷却时间限制（默认 12 小时，可在设置中调整），冷却期内再次操作返回 `code: "rate_limited"`。

### 获取分类列表

`GET /category/list`

返回所有分类数组，每个元素包含 `name`、`displayName`、`description`、`sentenceCount`。

## 后台管理 API

后台管理 API 前缀为 `/apis/console.api.hitokotohub.puresky.top/v1alpha1`，需具备 `plugin:hitokoto-hub:manage` 权限。

| 接口                         | 方法   | 说明                                  |
|----------------------------|------|---------------------------------------|
| `/sentence`                | GET  | 分页查询句子，支持 `keyword`、`categoryName`、`sort`、`page`、`size` 参数 |
| `/sentence/search`         | GET  | 按关键词搜索句子，返回匹配列表                        |
| `/sentence/-/batch`        | POST | 批量创建句子，请求体为 `Sentence` JSON 数组         |
| `/sentence/-/import-excel` | POST | 从 Excel 导入句子，`multipart/form-data` 上传   |
| `/overview`                | GET  | 获取概览：句子总数、分类总数、发布状态、各分类分布            |
| `/overview/view-statistics`| GET  | 获取分类浏览量时序数据，支持 `days`、`granularity`、`eventType` 参数，返回 ECharts 可直接使用的数据结构 |

此外，插件通过 Halo 扩展机制暴露了标准的 CRUD 接口：

- `/apis/hitokotohub.puresky.top/v1alpha1/sentences`
- `/apis/hitokotohub.puresky.top/v1alpha1/categories`

完整的 API 定义可在 `api-docs/openapi/v3_0/extensionApis.json` 中查看，或参考 [Apifox 文档](https://plugin-hitokoto-hub.apifox.cn/)。

## Finder API

| 方法                                                                  | 说明                              |
|---------------------------------------------------------------------|-----------------------------------|
| `hitokotoFinder.randomSentences(int limit, String categoryName)`   | 随机获取句子，`categoryName` 可为 `null` |
| `hitokotoFinder.listCategories()`                                   | 获取所有有句子的分类列表                       |

> Finder 的随机算法与公开 API 一致：先按分类统计总数，随机选中一页，取回后再洗牌，确保返回结果随机且性能稳定。

## 数据模型

插件注册了三个自定义扩展模型（GVK group: `hitokotohub.puresky.top`，version: `v1alpha1`）：

### Sentence

句子资源。

| 字段路径                        | 类型        | 说明                |
|-----------------------------|-----------|-------------------|
| `spec.categoryName`         | String    | 所属分类 `metadata.name`（必填，已建立索引） |
| `spec.content`              | String    | 句子内容，最长 500（必填，已建立索引）   |
| `spec.author`               | String    | 作者，默认「匿名」，最长 50  |
| `spec.source`               | String    | 来源，默认「未知」，最长 100 |
| `spec.createdBy`            | String    | 创建用户              |
| `status.isPublished`        | Boolean   | 是否已发布（已建立索引）      |
| `status.likeCount`          | Long      | 点赞数（已建立索引）        |
| `status.viewCount`          | Long      | 浏览量（已建立索引）        |

### Category

分类资源。

| 字段路径                  | 类型     | 说明              |
|-----------------------|--------|-----------------|
| `spec.name`           | String | 分类显示名称，最长 50    |
| `spec.description`    | String | 分类描述，最长 200     |
| `status.sentenceCount`| Long   | 该分类下句子数量（由 Reconciler 自动维护） |

### CategoryViewRecord

分类事件记录，用于浏览 / 点赞趋势统计。

| 字段路径                 | 类型                                           | 说明                          |
|----------------------|----------------------------------------------|-----------------------------|
| `spec.categoryName`  | String                                       | 关联的分类 `metadata.name`（已建立索引） |
| `spec.eventType`     | Enum: `VIEW` / `LIKE` / `UNLIKE`             | 事件类型（已建立索引）                  |

> Reconciler 会在 `Sentence` 增删改时自动更新对应 `Category.status.sentenceCount`；若分类被删除，其下所有句子也会被级联删除。

## 插件设置

插件设置位于「轻言 → 设置」，分为「基本设置」与「AI 设置」两组。

### 基本设置

| 设置项             | 默认值   | 说明                                       |
|-----------------|-------|------------------------------------------|
| 最大随机条数          | 20    | 随机接口允许响应的最大句子数量（1-100）                    |
| 默认随机条数          | 1     | 随机接口未指定 `limit` 时的默认返回数量（1-100）          |
| 默认分类            | 空（全部） | 随机接口未指定分类时使用的默认分类，可多选                      |
| 默认返回格式          | json  | 随机接口默认返回格式：`json` 或 `text`                |
| 点赞冷却时间（小时）      | 12    | 同一 IP 对同一句子两次操作的间隔时间（1-72）                |
| 启用浏览量统计         | true  | 随机获取句子时是否自动增加浏览量并记录事件                     |
| 统计数据最大保留条数      | 1000  | 超过此数量将自动删除最旧的 `CategoryViewRecord`（100-10000） |
| 统计数据保留天数        | 90    | 超过此天数的统计数据将被清理（7-365）                     |

### 定时清理任务

- **每 6 小时**清理一次过期的点赞缓存
- **每天 03:00** 按天数与条数策略清理过期的 `CategoryViewRecord`

## AI 自动生成

插件可选集成 [AI Foundation](https://www.halo.run/store/apps/app-acslk9nu)，实现按主题定时自动生成句子。

### 启用步骤

1. 在 Halo 应用市场安装并启用 AI Foundation
2. 在 AI Foundation 中配置至少一个语言模型
3. 进入「轻言 → 设置 → AI 设置」，开启「启用 AI 生成」
4. 选择语言模型、设置生成主题、数量、目标分类、定时任务 Cron 表达式

### AI 设置项

| 设置项           | 默认值                    | 说明                                       |
|---------------|------------------------|------------------------------------------|
| 启用 AI 生成      | false                  | 开启后定时任务将自动生成句子                            |
| 定时生成时间        | `0 0 2 * * *`（每天 02:00）| 6 位 Cron 表达式（秒 分 时 日 月 周），支持预设或自定义       |
| 语言模型          | 无                      | 从 AI Foundation 已配置的语言模型中选择              |
| 角色设定          | 内置默认提示词                | AI 的系统提示词，留空使用默认的「文字匠人」角色设定              |
| 生成主题          | 温柔治愈                   | 句子围绕的主题                                  |
| 生成数量          | 5                      | 每次生成的句子数量（1-50）                          |
| 目标分类          | 自动选择                   | 生成句子保存到哪个分类                              |
| 是否自动发布        | false                  | 开启后生成的句子自动发布，否则需管理员手动发布                   |

> 修改 AI 设置后，定时任务会自动重新注册，无需重启 Halo。AI 生成的句子 `createdBy` 字段标记为 `AI`。

## 权限体系

插件部署后会自动创建以下角色模板：

| 角色模板                                | 说明                          | 授权对象         |
|-------------------------------------|-----------------------------|--------------|
| `hitokoto-hub-role-template-public` | 公共接口权限（随机、分类、点赞）            | 匿名用户（自动聚合）   |
| `hitokoto-hub-role-template-view`   | 后台查看权限                      | 需手动分配        |
| `hitokoto-hub-role-template-manage` | 后台管理权限（CRUD、批量导入、概览），依赖查看权限 | 需手动分配        |

对应 UI 权限标识：

- `plugin:hitokoto-hub:manage` — 管理权限
- `plugin:hitokoto-hub:view` — 查看权限

## 项目结构

```
plugin-hitokoto-hub/
├── src/main/java/top/puresky/hitokotohub/
│   ├── HitokotoHubPlugin.java          # 插件入口，注册扩展模型与索引
│   ├── HitokotoTemplateRouter.java     # 默认模板路由 /hitokoto
│   ├── PluginConfiguration.java        # 插件配置
│   ├── config/                         # 设置配置读取
│   ├── endpoint/                       # 自定义 API 端点（公开 + 后台）
│   │   ├── CategoryPublicEndpoint.java
│   │   ├── SentencePublicEndpoint.java
│   │   ├── SentenceConsoleEndpoint.java
│   │   ├── OverviewConsoleEndpoint.java
│   │   └── SentenceQuery.java
│   ├── extension/                      # 自定义扩展模型（GVK）
│   │   ├── Sentence.java
│   │   ├── Category.java
│   │   └── CategoryViewRecord.java
│   ├── finder/                         # 主题 Finder API
│   │   └── impl/HitokotoFinderImpl.java
│   ├── reconciler/                     # 资源 Reconciler
│   │   ├── SentenceReconciler.java
│   │   └── CategoryReconciler.java
│   ├── scheduled/                      # 定时任务
│   │   └── StatsCleanupScheduler.java
│   └── service/                        # AI 生成服务
│       └── impl/AiGenerateServiceImpl.java
├── src/main/resources/
│   ├── extensions/                     # 角色模板与设置定义
│   │   ├── role-template-manage-hitokoto-hub.yaml
│   │   ├── role-template-public-hitokoto-hub.yaml
│   │   ├── role-template-view-hitokoto-hub.yaml
│   │   └── settings.yaml
│   ├── templates/hitokoto.html         # 默认展示模板
│   ├── plugin.yaml                     # 插件清单
│   └── logo.png
├── ui/                                 # 后台前端（Vue 3 + Element Plus）
│   └── src/
│       ├── api/generated/              # OpenAPI 自动生成的 API 客户端
│       ├── components/                 # Overview / CategoryList / SentenceList
│       └── views/HomeView.vue
├── api-docs/openapi/v3_0/              # 生成的 OpenAPI 文档
├── build.gradle                        # 后端构建脚本
├── gradle.properties
└── ui/build.gradle                     # 前端构建脚本
```

## 开发指南

### 技术栈

- **后端**：Java 21、Spring WebFlux、Reactor、Halo Extension API
- **前端**：Vue 3、Element Plus、ECharts、TailwindCSS、Rsbuild、TypeScript
- **构建**：Gradle（后端）、pnpm + Rsbuild（前端）

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

前端开发模式下会监听文件变化并实时构建到 `ui/dist`：

```bash
cd ui
pnpm dev
```

后端可配合 [Halo Plugin Devtools](https://docs.halo.run/developer-guide/plugin/dev-tools) 进行调试，`build.gradle` 中已配置 `haloPlugin` 扩展。

### 重新生成 API 客户端

当后端 API 发生变化时，可重新生成前端使用的 API 客户端代码：

```bash
./gradlew generateOpenApiClient
```

生成的代码位于 `ui/src/api/generated/`。

## 可用数据源

可从以下数据源导入句子到轻言：

- [sentences-bundle](https://github.com/hitokoto-osc/sentences-bundle) — 一言社区官方句子库
- [sentences-bundle-JSDelivr](https://cdn.jsdelivr.net/gh/hitokoto-osc/sentences-bundle@1.0.647/) — JSDelivr CDN 镜像

## 常见问题

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

### 句子未在前台显示

前台随机接口仅返回 `status.isPublished = true` 的句子。非 `super-role` 用户创建的句子默认未发布，需管理员在后台手动发布。

## 许可

[GPL-3.0](./LICENSE) © [imorisun](https://github.com/imorisun)

## 致谢

- [Halo](https://github.com/halo-dev/halo) — 强大易用的开源建站工具
- [一言](https://hitokoto.cn) — 一言项目，灵感来源
- [sentences-bundle](https://github.com/hitokoto-osc/sentences-bundle) — 句子数据源
- [AI Foundation](https://www.halo.run/store/apps/app-acslk9nu) — Halo 官方 AI 基座
