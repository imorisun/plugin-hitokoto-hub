# 轻言（Hitokoto Hub）

> 轻拾人间辞藻，言说万千心绪

[![Halo](https://img.shields.io/badge/Halo-%3E%3D2.25.0-blue.svg)](https://halo.run)
[![Release](https://img.shields.io/github/v/release/imorisun/plugin-hitokoto-hub?sort=semver&color=orange)](https://github.com/imorisun/plugin-hitokoto-hub/releases)
[![License](https://img.shields.io/badge/License-GPL--3.0-green.svg)](./LICENSE)

轻言（Hitokoto Hub）是一款面向 [Halo 2.x](https://halo.run) 的开源插件，为你的网站注入「一句话」的灵动与温度。你可以创建并管理海量句子，按分类归档，通过公开 API 随机获取、搜索、点赞与分享；插件还提供内置展示页、主题 Finder API、AI 生成、相似度检测、访客投递审核与数据看板等能力。无论是诗词名言、影视台词还是生活感悟，轻言让你的网站成为一个会说话的角落。

## ✨ 功能特性

**句子管理**

- 创建、编辑、删除与批量操作句子，支持 JSON / Excel / CSV 批量导入，自动识别表头并映射字段别名
- 支持按分类导出为 JSON 或 Excel，配合「未分类」兜底机制，数据永不丢失

**分类归档**

- 自定义分类体系，左侧面板便捷管理；内置受保护的「未分类」分类
- 删除分类时，其下句子自动迁入「未分类」；分类句子数量实时统计，杜绝缓存不一致

**开放接口**

- **随机获取**：基于索引分页的高效随机算法，海量数据秒级响应；支持分类筛选、数量限制、JSON / 纯文本输出
- **搜索与榜单**：基于索引的关键词模糊搜索；按点赞 / 浏览排序的热门句子榜单
- **点赞互动**：点赞 / 取消点赞接口，基于 IP 冷却机制防止刷赞，响应携带 `hasLiked` 标记
- **浏览统计**：随机获取自动计数，同一 IP 30 秒内去重，统计失败自动降级不影响主流程

**数据看板**

- 概览统计：句子总数、分类总数、发布状态分布、各分类浏览 / 点赞量
- 趋势分析：按天 / 周 / 月粒度的 ECharts 可视化，支持今日句子维度的浏览 / 点赞详情

**句子分享**

- 内置 SVG 分享卡片生成（暗色 / 亮色双主题），支持复制链接、保存图片、复制内容
- 分享链接可直达指定句子，适合在社交媒体或文章中引用

**主题集成**

- 提供 `hitokotoFinder` Finder API，可在主题 Thymeleaf 模板中直接调用
- 内置展示页 `/hitokoto`：花瓣飘落动画、暗色 / 亮色 / 跟随系统主题、双击点赞、定时自动切换

**AI 生成（可选）**

- 集成 [AI Foundation](https://www.halo.run/store/apps/app-acslk9nu)，按主题定时生成句子，可配置自动发布，生成日志可查

**相似度检查**

- 基于 TF-IDF 加权的余弦相似度与 Jaccard 双算法检测重复句子，纯本地计算、无需外部依赖
- 并查集归组 + 综合评分自动选出组内最优句子，支持一键清理非最优句子

**访客投递**

- 访客可在线投递句子，PENDING / APPROVED / REJECTED 三态审核工作流
- 基于 IP 的连续提交上限、冷却时间与待审核数量上限三重限流

**权限与运维**

- 基于 Halo RBAC 的三层角色模板：公共接口 / 查看 / 管理
- 数据自清理：过期缓存、统计记录、日志数据定时清理，支持条数与天数双重保留策略
- Reconciler 自动维护数据一致性：分类名归一化、「未分类」删除保护、删除分类时的句子迁移与统计清理

## 🚀 在线演示

| 资源 | 链接 |
|------|------|
| 演示站点 | <https://www.puresky.top/hitokoto> |
| API 文档 | <https://plugin-hitokoto-hub.apifox.cn/> |
| 应用市场 | <https://www.halo.run/store/apps/app-cmisffbv> |
| GitHub 仓库 | <https://github.com/imorisun/plugin-hitokoto-hub> |
| 问题反馈 | <https://github.com/imorisun/plugin-hitokoto-hub/issues> |

## 📋 环境要求

| 依赖项 | 版本要求 | 说明 |
|--------|----------|------|
| Halo | `>= 2.25.0` | 必需，插件基于 Halo 2.25 平台构建 |
| Java | `21` | 构建时需要 JDK 21 |
| Node.js | `>= 18` | 构建前端时需要，推荐使用 pnpm |
| AI Foundation | 任意版本（可选） | 若需使用 AI 生成功能，需安装 [AI Foundation](https://www.halo.run/store/apps/app-acslk9nu) |

> AI Foundation 为可选依赖：未安装时，AI 生成相关组件不会加载，不影响其他功能。相似度检查为纯本地算法实现，不依赖 AI Foundation。

## 📦 安装

### 方式一：应用市场安装（推荐）

在 Halo 后台「应用市场」中搜索「轻言」，或访问[应用市场页面](https://www.halo.run/store/apps/app-cmisffbv)一键安装。

### 方式二：手动上传安装

1. 前往 [Releases](https://github.com/imorisun/plugin-hitokoto-hub/releases) 下载最新版 `plugin-hitokoto-hub-x.x.x.jar`
2. 在 Halo 后台「插件」中点击「安装」，上传 jar 文件
3. 启用插件后，后台「工具」下会出现「轻言管理」入口

### 方式三：从源码构建

参考下方[开发指南](#-开发指南)。

## 🎯 快速开始

### 1. 创建分类

进入「工具 → 轻言管理 → 数据列表」，在左侧分类面板点击 `+` 新建分类，填写名称与描述。

> 插件启动时会自动创建内置的「未分类」分类，分类为空或已失效的历史句子会被自动归入其中；删除分类时，其下句子同样会迁移至「未分类」。

### 2. 新建句子

点击「新建句子」，填写内容、作者、来源并选择分类。还可以设置自定义跳转链接（`linkUrl`）或关联文章（`postName`），前台展示时句子可跳转至对应页面。

> 非超级管理员创建的句子默认未发布，需管理员在后台手动发布后才会出现在前台。

### 3. 批量导入 / 导出

- **JSON 导入**：直接粘贴 JSON 数组
- **Excel / CSV 导入**：上传 `.xlsx` 或 `.csv` 文件，插件自动识别表头并映射字段
- **导出**：支持按分类导出为 JSON 或 Excel

### 4. 调用公开接口

所有公开接口均无需鉴权，前缀为 `/apis/public.api.hitokotohub.puresky.top/v1alpha1`：

```bash
# 随机获取 1 条句子（默认返回 JSON）
curl 'https://your-domain.com/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/random'

# 按分类随机获取 8 条，纯文本返回（每行一句）
curl 'https://your-domain.com/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/random?categoryName=category-xxx&limit=8&encode=text'

# 关键词搜索（默认 10 条，最多 20 条）
curl 'https://your-domain.com/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/search?keyword=%E6%B8%A9%E6%9F%94'

# 热门榜单（按点赞排序；sort=view 按浏览排序，最多 50 条）
curl 'https://your-domain.com/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/hot?sort=like&limit=10'

# 按名称获取单条已发布句子
curl 'https://your-domain.com/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/sentence-xxxx'

# 点赞 / 取消点赞
curl 'https://your-domain.com/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/like?name=sentence-xxxx&action=like'
curl 'https://your-domain.com/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/like?name=sentence-xxxx&action=unlike'

# 获取所有分类（含实时句子数量）
curl 'https://your-domain.com/apis/public.api.hitokotohub.puresky.top/v1alpha1/category/list'

# 获取句子分享数据与分享卡片 SVG
curl 'https://your-domain.com/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/sentence-xxxx/share'
curl 'https://your-domain.com/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/sentence-xxxx/share/card?theme=dark'
```

## 🎨 主题集成

插件内置默认展示页 `/hitokoto`，支持暗色 / 亮色 / 跟随系统三种主题、花瓣飘落动画、双击点赞、句子分享、定时自动切换，样式与行为可在「模板展示设置」中调整。你也可以通过 Finder API 或 REST API 在自己的主题中自定义展示。

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

`SentenceVo` 字段：`name`、`author`、`content`、`source`、`categoryName`、`likeCount`、`viewCount`、`jumpUrl`

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

### 分享直达

访问 `/hitokoto?sentence=sentence-xxxx` 可直达指定句子的展示页面。分享视图会自动暂停句子轮播，聚焦展示被分享的句子。

## 📖 API 文档

### 公开 API（无需鉴权）

前缀：`/apis/public.api.hitokotohub.puresky.top/v1alpha1`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/sentence/random` | GET | 随机获取句子。参数：`categoryName`、`limit`（受最大随机条数限制）、`encode=json\|text` |
| `/sentence/like` | GET | 点赞 / 取消点赞。参数：`name`（必填）、`action=like\|unlike` |
| `/sentence/{name}` | GET | 按名称获取单条已发布句子，未找到返回 404 |
| `/sentence/search` | GET | 搜索已发布句子。参数：`keyword`（必填）、`categoryName`、`limit`（默认 10，最大 20） |
| `/sentence/hot` | GET | 热门榜单。参数：`sort=like\|view`（默认 like）、`categoryName`、`limit`（默认 10，最大 50） |
| `/category/list` | GET | 获取所有分类（含实时句子数量） |
| `/sentence-submission/config` | GET | 获取访客提交配置（是否启用、默认分类、待审核上限） |
| `/sentence-submission/-/submit` | POST | 访客投递句子（进入待审核状态） |
| `/sentence/{name}/share` | GET | 获取句子分享数据 |
| `/sentence/{name}/share/card` | GET | 获取分享卡片 SVG。参数：`theme=dark\|light`（默认 dark） |

### 后台管理 API

前缀：`/apis/console.api.hitokotohub.puresky.top/v1alpha1`，需具备「一言查看 / 一言管理」角色权限：

| 接口 | 方法 | 说明 |
|------|------|------|
| `/sentence` | GET | 分页查询句子。参数：`keyword`、`categoryName`、`page`、`size` 及排序参数 |
| `/sentence/search` | GET | 后台搜索句子 |
| `/sentence/-/batch` | POST | 批量创建句子 |
| `/sentence/-/import-excel` | POST | 上传 `.xlsx` 批量导入 |
| `/sentence/-/import-csv` | POST | 上传 `.csv` 批量导入 |
| `/sentence/-/export` | GET | 导出句子。参数：`format=json\|excel`、`categoryName` |
| `/sentence/-/clear-uncategorized` | POST | 清空未分类句子 |
| `/categories` | GET | 分页获取分类列表（含实时句子数量） |
| `/overview` | GET | 概览统计 |
| `/overview/view-statistics` | GET | 分类浏览量时序数据（ECharts 折线图） |
| `/overview/today-sentence-details` | GET | 今日句子维度的浏览 / 点赞详情 |
| `/sentence-submissions` | GET | 分页查询访客提交记录 |
| `/sentence-submissions/{name}/approve` | POST | 审核通过（可配置自动发布） |
| `/sentence-submissions/{name}/reject` | POST | 审核拒绝 |
| `/sentence-submissions/{name}` | DELETE | 删除提交记录 |
| `/ai-generate-logs` | GET | 分页查询 AI 生成日志 |
| `/ai-generate-logs/-/trigger` | POST | 手动触发 AI 生成 |
| `/ai-generate-logs/{name}` | DELETE | 删除 AI 生成日志 |
| `/similarity-check-logs` | GET | 分页查询相似度检查日志 |
| `/similarity-check-logs/{name}` | GET | 查询单次检查日志详情 |
| `/similarity-check-logs/-/trigger` | POST | 手动触发相似度检查 |
| `/similarity-check-config` | GET | 获取相似度检查配置 |
| `/similarity-check-groups` | GET | 获取相似句子分组结果 |
| `/similarity-check-groups/-/delete-nonoptimal` | POST | 批量删除分组内非最优句子 |
| `/sentence/{name}/share` | GET | 获取指定句子分享数据（含未发布） |
| `/sentence/{name}/share/card` | GET | 获取指定句子分享卡片 SVG |

### 扩展 CRUD API

插件通过 Halo 扩展机制暴露标准 CRUD 接口：

- `/apis/hitokotohub.puresky.top/v1alpha1/sentences`
- `/apis/hitokotohub.puresky.top/v1alpha1/categories`

完整的 OpenAPI 定义见 [api-docs/openapi/v3_0/extensionApis.json](./api-docs/openapi/v3_0/extensionApis.json)，或参考 [Apifox 在线文档](https://plugin-hitokoto-hub.apifox.cn/)。

## ⚙️ 配置说明

插件设置位于「插件 → 轻言 → 设置」，共分为六组：

### 基本设置

| 设置项 | 默认值 | 说明 |
|--------|--------|------|
| 最大随机条数 | 20 | 随机接口允许响应的最大句子数量（1-100） |
| 默认随机条数 | 1 | 未指定 `limit` 时的默认返回数量 |
| 默认分类 | 空（全部） | 随机接口未指定分类时使用的默认分类，可多选 |
| 默认返回格式 | JSON | 随机接口默认返回格式：`json` 或 `text` |
| 点赞冷却时间 | 12 小时 | 同一 IP 对同一句子两次点赞 / 取消点赞的间隔时间（1-72 小时） |
| 信任反向代理头 | true | 开启后优先从 `X-Forwarded-For` 识别访客 IP（适用于 Nginx/CDN 反代部署）；Halo 直连公网时建议关闭，防止伪造该头绕过点赞/提交限流 |
| 启用浏览量统计 | true | 随机获取句子时是否增加浏览量（同一 IP 对同一句子 30 秒内去重） |
| 统计数据最大保留条数 | 1000 | 浏览记录与点赞记录分别计算，超过各自上限自动删除最旧数据（100-10000） |
| 统计数据保留天数 | 90 | 超过此天数的统计数据将被清理（7-365） |
| 统计数据清理时间 | `0 0 3 * * *` | 统计清理任务的 6 位 Cron 表达式，支持预设或自定义 |

### AI 设置

| 设置项 | 默认值 | 说明 |
|--------|--------|------|
| 启用 AI 自动生成 | false | 开启后定时任务按设定时间自动生成句子；关闭后仍可在后台手动触发 |
| 自动生成时间 | `0 0 2 * * *` | 6 位 Cron 表达式，支持预设或自定义 |
| 语言模型 | 无 | 从 AI Foundation 已配置的语言模型中选择 |
| 角色设定 | 内置默认提示词 | AI 的系统提示词，留空使用默认的「文字匠人」角色 |
| 生成主题 | 温柔治愈 | 句子围绕的主题 |
| 生成数量 | 25 | 每次生成的句子数量（1-100） |
| 目标分类 | 自动选择 | 生成句子保存到哪个分类 |
| 是否自动发布 | false | 开启后生成的句子自动发布 |
| AI 日志最大保留条数 | 500 | 超过此数量自动删除最旧日志（10-10000） |
| AI 日志保留天数 | 30 | 超过此天数的日志将被清理（1-365） |
| AI 日志清理时间 | `0 30 3 * * *` | AI 日志清理任务的 6 位 Cron 表达式，支持预设或自定义 |

### 访客提交设置

| 设置项 | 默认值 | 说明 |
|--------|--------|------|
| 启用访客提交 | true | 开启后访客可在 `/hitokoto` 页面投递句子，管理员在后台审核 |
| 默认提交分类 | 空 | 访客未选择分类时使用的默认分类 |
| 审核通过后自动发布 | false | 开启后审核通过的句子自动发布 |
| 连续提交上限 | 3 | 同一 IP 在冷却周期内可连续提交的数量（1-20） |
| 提交冷却时间 | 10 分钟 | 同一 IP 两次提交的间隔；达到连续上限后进入冷却，`0` 表示不限制（0-1440） |
| 待审核句子数量上限 | 50 | 同一 IP 处于 PENDING 状态的提交数量上限；`0` 表示不限制（0-1000） |
| 提交记录最大保留条数 | 1000 | 超过此数量自动删除最旧的已处理记录（100-10000） |
| 提交记录清理时间 | `0 0 4 * * *` | 提交记录清理任务的 6 位 Cron 表达式，支持预设或自定义 |

### 相似度检查设置

| 设置项 | 默认值 | 说明 |
|--------|--------|------|
| 启用定时检查 | false | 开启后系统按设定周期自动比对所有句子 |
| 定时检查时间 | `0 0 2 * * *` | 6 位 Cron 表达式，支持预设或自定义 |
| 相似度算法 | COSINE | `COSINE`（基于 TF-IDF 加权的余弦相似度）或 `JACCARD` |
| 相似度阈值 | 0.8 | 超过此阈值的句子对将被标记为相似（0.1-1.0，越大越严格） |

### 模板展示设置

| 设置项 | 默认值 | 说明 |
|--------|--------|------|
| 默认主题 | 跟随系统 | 访客首次访问时的主题：`auto` / `dark` / `light`；访客手动切换后以其选择为准 |
| 显示花瓣飘落动画 | true | 关闭后模板页面不展示花瓣飘落背景 |
| 显示首次操作提示 | true | 开启后页面加载时显示「双击屏幕任意位置点赞」提示 |
| 定时自动切换句子 | false | 开启后页面按设定间隔自动切换展示新句子 |
| 自动切换间隔 | 10 秒 | 句子自动切换的时间间隔（3-3600 秒） |

### 分享设置

| 设置项 | 默认值 | 说明 |
|--------|--------|------|
| 站点名称 | 轻言 | 展示在分享卡片上的站点名称，留空则使用 Halo 站点标题 |

## 🔄 定时任务

以下任务由插件自动注册，无需手动配置：

| 任务 | 触发时间 | 说明 |
|------|----------|------|
| 清理点赞冷却缓存 | 每 6 小时 | 清理内存中过期的点赞冷却记录 |
| 清理浏览去重缓存 | 每 5 分钟 | 清理 30 秒浏览去重窗口的过期记录 |
| 清理分类统计记录 | 基本设置 → 统计数据清理时间 | 按天数与条数双重策略清理 `CategoryViewRecord`（浏览 / 点赞分别计数） |
| 清理 AI 生成日志 | AI 设置 → AI 日志清理时间 | 按天数与条数双重策略清理 `AiGenerateLog` |
| 清理访客提交记录 | 访客提交设置 → 提交记录清理时间 | 按条数清理最旧的已处理记录（保留 PENDING） |
| AI 自动生成句子 | AI 设置 → 自动生成时间 | 仅开启时注册，配置变更后自动重新注册 |
| 定时相似度检查 | 相似度设置 → 定时检查时间 | 仅开启时注册，配置变更后自动重新注册 |

## 📊 数据模型

插件注册了 6 个自定义扩展模型（GVK group: `hitokotohub.puresky.top`，version: `v1alpha1`）：

### Sentence（句子）

| 字段路径 | 类型 | 说明 |
|----------|------|------|
| `spec.categoryName` | String | 所属分类 `metadata.name`（必填，已建立索引，最长 100） |
| `spec.content` | String | 句子内容（必填，已建立索引，最长 500） |
| `spec.author` | String | 作者，默认「匿名」（最长 50） |
| `spec.source` | String | 来源，默认「未知」（最长 100） |
| `spec.createdBy` | String | 创建用户 |
| `spec.linkUrl` | String | 自定义跳转链接（最长 500） |
| `spec.postName` | String | 关联文章名称（`metadata.name`，最长 100） |
| `status.isPublished` | Boolean | 是否已发布，默认 false（已建立索引） |
| `status.likeCount` | Long | 点赞数（已建立索引） |
| `status.viewCount` | Long | 浏览量（已建立索引） |

### Category（分类）

| 字段路径 | 类型 | 说明 |
|----------|------|------|
| `spec.name` | String | 分类显示名称（最长 50） |
| `spec.description` | String | 分类描述（最长 200） |

分类下的句子数量不再落库缓存，由 `CategoryCountService` 实时统计（单次 `listAll` + 内存分组），从源头消除计数不一致问题。内置的「未分类」分类受 Reconciler 保护，不可删除。

### CategoryViewRecord（分类事件记录）

浏览 / 点赞行为的事件记录，用于统计与趋势分析，包含 `categoryName`、`eventType`（VIEW / LIKE）、`sentenceName`、`ip` 等字段。

### SentenceSubmission（访客投递记录）

包含 `content`、`author`、`source`、`categoryName`、`submitterName`、`submitterIp`、`status`（PENDING / APPROVED / REJECTED）、`reviewedBy`、`reviewNote`、`reviewedAt`、`sentenceName`（审核通过后生成的句子）等字段。

### AiGenerateLog（AI 生成日志）

包含 `modelName`、`topic`、`requestCount`、`successCount`、`failedCount`、`categoryName`、`autoPublish`、`status`（RUNNING / SUCCESS / PARTIAL_SUCCESS / FAILED）、`errorMessage`、`durationMs`、`generatedData` 等字段。

### SimilarityCheckLog（相似度检查日志）

包含 `triggerType`（MANUAL / SCHEDULED）、`triggeredBy`、`algorithm`（COSINE / JACCARD）、`threshold`、`totalSentences`、`totalPairs`、`similarPairCount`、`similarPairs`（含句子名称 / 内容 / 相似度的内嵌结构）、`status`、`errorMessage`、`durationMs` 等字段。

> 相似句子分组结果 `SimilarityGroup` 不是扩展模型，仅作为 API 响应结构：通过并查集归组后按综合评分（发布状态、点赞、浏览、内容长度、作者 / 来源完整度）选出组内最优句子，供一键清理使用。

## 🏗️ 项目结构

```
plugin-hitokoto-hub/
├── src/main/java/top/puresky/hitokotohub/
│   ├── HitokotoHubPlugin.java              # 插件入口：注册扩展索引、迁移孤儿句子
│   ├── HitokotoTemplateRouter.java         # 内置展示页路由 /hitokoto（含分享直达）
│   ├── PluginConfiguration.java            # Spring Bean 配置
│   ├── UncategorizedConstants.java         # 「未分类」内置分类常量
│   ├── config/                             # 设置配置读取
│   │   ├── SettingConfig.java              # 六组配置接口
│   │   └── impl/SettingConfigImpl.java
│   ├── endpoint/                           # 自定义 API 端点（公开 + 后台）
│   │   ├── SentencePublicEndpoint.java     # 随机 / 点赞 / 单条 / 搜索 / 热门
│   │   ├── SentenceConsoleEndpoint.java    # 句子管理 + 批量导入导出
│   │   ├── SentenceQuery.java              # 后台查询参数
│   │   ├── SentenceSharePublicEndpoint.java
│   │   ├── SentenceShareConsoleEndpoint.java
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
│   │   └── SimilarityGroup.java            # 相似分组结果（API 响应 DTO）
│   ├── finder/                             # 主题 Finder API
│   │   ├── HitokotoFinder.java
│   │   └── impl/HitokotoFinderImpl.java
│   ├── init/UncategorizedCategoryInitializer.java   # 启动时初始化「未分类」
│   ├── reconciler/                         # Reconciler
│   │   ├── SentenceReconciler.java         # 句子分类名归一化
│   │   └── CategoryReconciler.java         # 分类删除保护与句子迁移
│   ├── scheduled/StatsCleanupScheduler.java # 定时任务统一调度
│   ├── service/                            # 业务服务层
│   │   ├── AiGenerateService.java
│   │   ├── CategoryCountService.java       # 分类句子数量实时统计
│   │   ├── SentenceShareService.java
│   │   ├── SimilarityCheckService.java
│   │   ├── dto/
│   │   ├── impl/
│   │   ├── share/ShareCardSvgBuilder.java  # SVG 分享卡片生成器
│   │   └── similarity/                     # 相似度算法（TF-IDF 余弦 / Jaccard / 并查集 / 评分）
│   └── utils/                              # 工具类（IP 冷却缓存、HTTP、索引注册等）
├── src/main/resources/
│   ├── extensions/
│   │   ├── settings.yaml                   # basic / ai / submission / similarity / template / share 六组设置
│   │   └── role-template-*.yaml            # 公共接口 / 查看 / 管理三层角色模板
│   ├── templates/
│   │   ├── hitokoto.html                   # 内置展示页（含分享弹窗、提交表单）
│   │   ├── hitokoto-styles.html
│   │   └── hitokoto-scripts.html
│   ├── plugin.yaml                         # 插件清单
│   └── logo.png
├── ui/                                     # 后台前端（Vue 3 + Element Plus + ECharts）
│   └── src/
│       ├── api/generated/                  # OpenAPI 自动生成的 API 客户端
│       ├── components/
│       │   ├── Overview.vue                # 概览看板
│       │   ├── SentenceList.vue            # 句子管理
│       │   ├── SentenceShareModal.vue      # 句子分享弹窗
│       │   ├── SubmissionList.vue          # 访客提交审核
│       │   ├── AiGenerateLogList.vue       # AI 生成日志
│       │   └── SimilarityCheck.vue         # 相似度检查
│       ├── composables/                    # 组合式函数
│       └── views/HomeView.vue
└── api-docs/openapi/v3_0/                  # OpenAPI 文档
```

## 🔧 开发指南

### 技术栈

- **后端**：Java 21、Spring WebFlux、Reactor、Halo Extension API、FastExcel
- **前端**：Vue 3、TypeScript、Element Plus、ECharts、Rsbuild、Tailwind CSS
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
# 构建产物位于 build/libs/plugin-hitokoto-hub-*.jar
```

### 开发模式

```bash
# 前端开发模式（监听文件变化并实时构建）
cd ui
pnpm dev
```

后端可配合 [Halo Plugin DevTools](https://docs.halo.run/developer-guide/plugin/dev-tools)（`build.gradle` 已配置）进行本地调试：

```bash
./gradlew haloServer   # 以 Docker 方式启动 Halo 并加载插件（需 Docker）
./gradlew reload       # 代码变更后热重载插件
```

### 重新生成 API 客户端

后端 API 发生变化时，可重新生成 OpenAPI 文档与前端 API 客户端：

```bash
./gradlew generateOpenApiDocs      # 重新生成 api-docs/openapi/v3_0
./gradlew generateOpenApiClient    # 重新生成 ui/src/api/generated
```

## 📚 可用数据源

可从以下数据源导入句子到轻言：

- [sentences-bundle](https://github.com/hitokoto-osc/sentences-bundle) — 一言社区官方句子库

## ❓ 常见问题

### 更新插件后出现问题

部分版本可能调整了设置项结构，更新后请：

1. 进入「插件 → 轻言 → 设置」，点击「重置」恢复默认设置
2. 重新配置所需设置项
3. 若仍有问题，重启 Halo

### AI 生成功能不可用

AI 自动生成需要同时满足：

1. 已安装并启用 [AI Foundation](https://www.halo.run/store/apps/app-acslk9nu)
2. 在 AI Foundation 中已配置可用的语言模型
3. 在轻言设置中选择了对应的语言模型

### 句子未在前台显示

前台随机接口仅返回已发布（`status.isPublished = true`）的句子。非超级管理员创建的句子默认未发布，需管理员在后台手动发布。

### 访客投递接口返回 403

`code: "submitted_disabled"` 表示后台未开启访客提交。进入「插件 → 轻言 → 设置 → 访客提交设置」开启即可。

### 访客投递接口返回 429

- `code: "rate_limited"`：同一 IP 在冷却期内重复提交，或达到连续提交上限
- `code: "pending_limit_reached"`：同一 IP 的待审核提交数量已达「待审核句子数量上限」

可在「访客提交设置」中调整相关参数。注意：提交冷却基于内存缓存，插件重启后会重置。

### 点赞 / 提交限流失效

若 Halo 部署在 Nginx / CDN 等反向代理之后，请开启「信任反向代理头」以正确识别访客 IP；若 Halo 直连公网，则建议关闭该选项，防止伪造 `X-Forwarded-For` 头绕过点赞与提交限流。

## 📄 许可证

[GPL-3.0](./LICENSE) © [晨阳](https://github.com/imorisun)

## 🙏 致谢

- [Halo](https://github.com/halo-dev/halo) — 强大易用的开源建站工具
- [一言](https://hitokoto.cn) — 一言项目，灵感来源
- [sentences-bundle](https://github.com/hitokoto-osc/sentences-bundle) — 句子数据源
- [AI Foundation](https://www.halo.run/store/apps/app-acslk9nu) — Halo 官方 AI 基座
- 所有为项目做出贡献的开发者
