# 轻言（Hitokoto Hub）

> 轻拾人间辞藻，言说万千心绪

[![Halo](https://img.shields.io/badge/Halo-%3E%3D2.25.0-blue.svg)](https://halo.run)
[![License](https://img.shields.io/badge/License-GPL--3.0-green.svg)](./LICENSE)
[![Version](https://img.shields.io/badge/Version-1.0.0-orange.svg)](https://github.com/imorisun/plugin-hitokoto-hub/releases)

轻言是一款 [Halo 2.x](https://halo.run) 生态的开源插件，为你的网站注入「一句话」的灵动与温度。无论是诗词名言、影视台词还是生活感悟，轻言都能帮助你轻松管理与展示，让你的网站成为一个会说话的角落。

## ✨ 功能特性

- **句子管理**：创建、编辑、删除句子，支持 JSON 批量导入与 Excel（.xlsx）导入，自动映射字段别名
- **分类归档**：自定义分类体系，侧边栏导航，自动统计各分类句子数量
- **随机获取**：基于索引分页的高效随机算法，海量数据秒级响应；支持多分类筛选、返回数量限制
- **模糊搜索**：基于索引的关键词搜索，支持按分类过滤
- **点赞互动**：开放点赞/取消点赞接口，基于 IP 的冷却机制防止刷赞
- **浏览统计**：自动累计浏览量，支持按天/周/月粒度的趋势分析
- **数据看板**：后台概览页面展示句子总数、分类总数、发布状态分布、各分类浏览/点赞量，集成 ECharts 可视化图表
- **主题集成**：提供 `hitokotoFinder` Finder API，可在 Halo 主题模板中直接调用；内置默认展示页面 `/hitokoto`
- **AI 生成**：可选集成 [AI Foundation](https://www.halo.run/store/apps/app-acslk9nu)，支持定时按主题自动生成句子
- **相似度检查**：基于余弦相似度（TF-IDF）与 Jaccard 算法自动检测重复或高度相似的句子，支持批量清理
- **访客投递**：内置访客提交入口，支持三态审核工作流（待审核/通过/拒绝），可配置 IP 冷却与提交限制
- **权限控制**：基于 Halo RBAC 的三层角色模板（公共接口/查看/管理）
- **数据自清理**：定时清理过期缓存、统计记录、日志数据，支持双重保留策略
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
| AI Foundation | 任意版本（可选） | 若需使用 AI 自动生成功能，需安装 [AI Foundation](https://www.halo.run/store/apps/app-acslk9nu) |

> AI Foundation 为可选依赖，未安装时 AI 生成与相似度检查的定时任务会自动跳过，不影响其他功能。

## 📦 安装

### 方式一：应用市场安装（推荐）

在 Halo 后台「应用市场」中搜索「轻言」并一键安装。

### 方式二：手动上传安装

1. 前往 [Releases](https://github.com/imorisun/plugin-hitokoto-hub/releases) 下载最新版 `plugin-hitokoto-hub-x.x.x.jar`
2. 在 Halo 后台的「插件」管理中点击「安装」，上传 jar 文件
3. 启用插件后，后台左侧菜单会出现「轻言」入口

### 方式三：从源码构建

参考下方 [开发指南](#-开发指南)。

## 🎯 快速开始

### 1. 创建分类

进入「轻言 → 数据管理」，在左侧分类面板点击 `+` 号，填写分类名称与描述。

> 插件启动时会自动创建名为「未分类」的内置分类，并将分类为空或已失效的句子自动归入其中。

### 2. 新建句子

点击右上角「新建句子」，填写句子内容、作者、来源并选择分类。

### 3. 批量导入

支持两种格式：

- **JSON 批量导入**：直接粘贴或上传符合 `Sentence` 结构的 JSON 数组
- **Excel 导入**：上传 `.xlsx` 文件，插件会自动识别表头并映射字段，也可在导入时手动指定列名

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
```

## 🎨 主题集成

插件内置默认展示模板，访问路径为 `/hitokoto`。你也可以通过 Finder API 或 REST API 在自己的主题中自定义展示方式。

### Finder API（推荐）

在主题 Thymeleaf 模板中直接调用 `hitokotoFinder`：

```html
<!-- 随机获取 1 条句子 -->
<div th:each="s : ${hitokotoFinder.randomSentences(1, null)}">
    <p th:text="${s.content}"></p>
    <span th:text="${s.author}"></span>
    <span th:text="${s.source}"></span>
</div>

<!-- 获取分类列表（仅返回有句子的分类） -->
<div th:each="c : ${hitokotoFinder.listCategories()}">
    <a th:href="@{/hitokoto(category=${c.name})}" th:text="${c.displayName}"></a>
    <span th:text="${c.sentenceCount}"></span>
</div>
```

### REST API

在前端脚本中直接调用公开 API：

```javascript
// 随机获取句子
fetch('/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/random?limit=8')
  .then(res => res.json())
```

## 📖 API 文档

### 公开 API（无需鉴权）

所有公开 API 前缀为 `/apis/public.api.hitokotohub.puresky.top/v1alpha1`：

| 接口 | 方法 | 说明 |
|------|------|------|
| `/sentence/random` | GET | 随机获取句子，支持分类、数量、格式参数 |
| `/sentence/like` | GET | 点赞/取消点赞 |
| `/category/list` | GET | 获取所有分类列表 |
| `/sentence-submission/submit` | POST | 访客投递句子 |

### 后台管理 API

后台管理 API 前缀为 `/apis/console.api.hitokotohub.puresky.top/v1alpha1`，需具备管理权限：

| 接口 | 方法 | 说明 |
|------|------|------|
| `/sentence` | GET | 分页查询句子 |
| `/sentence/-/batch` | POST | 批量创建句子 |
| `/sentence/-/import-excel` | POST | Excel 导入 |
| `/overview` | GET | 获取概览统计 |
| `/sentence-submissions` | GET | 分页查询访客提交记录 |
| `/sentence-submissions/{name}/approve` | POST | 审核通过 |
| `/sentence-submissions/{name}/reject` | POST | 审核拒绝 |
| `/ai-generate-logs/-/trigger` | POST | 手动触发 AI 生成 |
| `/similarity-check-logs/-/trigger` | POST | 手动触发相似度检查 |
| `/similarity-check-groups/-/delete-nonoptimal` | POST | 批量删除非最优句子 |

完整的 API 定义可在 `api-docs/openapi/v3_0/extensionApis.json` 中查看，或参考 [Apifox 文档](https://plugin-hitokoto-hub.apifox.cn/)。

## ⚙️ 配置说明

插件设置位于「轻言 → 设置」，分为四组：

### 基本设置

- 最大随机条数（默认 20）
- 默认随机条数（默认 1）
- 默认分类
- 默认返回格式（JSON/Text）
- 点赞冷却时间（默认 12 小时）
- 启用浏览量统计
- 统计数据保留策略

### AI 设置

- 启用 AI 生成
- 定时生成时间（Cron 表达式）
- 语言模型选择
- 角色设定与生成主题
- 生成数量与目标分类
- 是否自动发布
- AI 日志保留策略

### 访客提交设置

- 启用访客提交
- 默认提交分类
- 审核通过后自动发布
- 连续提交上限（默认 3 次）
- 提交冷却时间（默认 10 分钟）
- 提交记录保留策略

### 相似度检查设置

- 启用定时检查
- 定时检查时间（Cron 表达式）
- 相似度算法（COSINE/JACCARD）
- 相似度阈值（默认 0.8）

## 🏗️ 项目结构

```
plugin-hitokoto-hub/
├── src/main/java/top/puresky/hitokotohub/
│   ├── HitokotoHubPlugin.java              # 插件入口
│   ├── config/                             # 设置配置
│   ├── endpoint/                           # API 端点
│   ├── extension/                          # 扩展模型（GVK）
│   ├── finder/                             # Finder API
│   ├── reconciler/                         # 资源 Reconciler
│   ├── scheduled/                          # 定时任务
│   └── service/                            # 业务服务层
├── src/main/resources/
│   ├── extensions/                         # 角色模板与设置定义
│   ├── templates/                          # 默认展示模板
│   └── plugin.yaml                         # 插件清单
├── ui/                                     # 后台前端（Vue 3 + Element Plus）
│   └── src/
│       ├── api/generated/                  # OpenAPI 自动生成的 API 客户端
│       ├── components/                     # 业务组件
│       └── views/                          # 页面视图
└── api-docs/                               # OpenAPI 文档
```

## 🔧 开发指南

### 技术栈

- **后端**：Java 21、Spring WebFlux、Reactor、Halo Extension API
- **前端**：Vue 3、Element Plus、ECharts、TypeScript、Rsbuild
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
# 前端开发模式（监听文件变化）
cd ui
pnpm dev
```

后端可配合 [Halo Plugin Devtools](https://docs.halo.run/developer-guide/plugin/dev-tools) 进行调试。

### 重新生成 API 客户端

当后端 API 发生变化时，可重新生成前端 API 客户端：

```bash
./gradlew generateOpenApiClient
```

## 📚 可用数据源

可从以下数据源导入句子：

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

### 句子未在前台显示

前台随机接口仅返回已发布的句子。非超级管理员创建的句子默认未发布，需管理员手动发布。

### 访客投递接口返回 403

表示后台未开启访客提交。进入「轻言 → 设置 → 访客提交设置」开启即可。

## 📄 许可证

[GPL-3.0](./LICENSE) © [晨阳](https://github.com/imorisun)

## 🙏 致谢

- [Halo](https://github.com/halo-dev/halo) — 强大易用的开源建站工具
- [一言](https://hitokoto.cn) — 一言项目，灵感来源
- [sentences-bundle](https://github.com/hitokoto-osc/sentences-bundle) — 句子数据源
- [AI Foundation](https://www.halo.run/store/apps/app-acslk9nu) — Halo 官方 AI 基座
- 所有为项目做出贡献的开发者
