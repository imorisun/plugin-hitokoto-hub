# 轻言（Hitokoto Hub）

> 轻拾人间辞藻，言说万千心绪

轻言是一款 Halo 2.x 插件，为你的网站注入"一句话"的灵动与温度。支持创建、管理海量句子，按分类归档，并提供随机获取、关键词搜索、点赞互动等丰富的开放接口。
### 注意
1. 最新版插件需要依赖官方的AI基座 [AI Foundation](https://www.halo.run/store/apps/app-acslk9nu)
2. 由于修改了一些设置项，更新后请重置插件设置
3. 更新后若遇到问题，请先重置插件设置，并重启Halo
## 🌐 演示与交流

- **演示站点**：[https://www.puresky.top/hitokoto](https://www.puresky.top/hitokoto)
- **文档**：[https://www.puresky.top/docs/](https://www.puresky.top/docs/)
- **QQ 交流群**：

<a href="https://www.lik.cc/upload/iShot_2025-03-03_16.03.00.png">
  <img src="https://www.lik.cc/upload/iShot_2025-03-03_16.03.00.png" width="180" alt="QQ群">
</a>

## 功能特性

- **句子管理**：创建、编辑、删除句子，支持批量导入（JSON/Excel）
- **分类归档**：自定义分类，侧边栏导航，实时统计各分类句子数量
- **随机获取**：内置随机算法，基于索引分页优化，6000 条数据依然秒级响应
- **模糊搜索**：支持关键词搜索句子内容
- **点赞互动**：开放点赞/取消点赞接口，支持 IP 冷却机制
- **主题集成**：提供 Finder API，可在 Halo 主题模板中直接调用
- **数据看板**：后台概览页面，展示句子总数、分类分布、发布状态统计
- **权限控制**：基于 Halo RBAC 角色模板，支持公开 API 和后台管理权限分离

## 插件截图

|                                                                |                                                                |
|----------------------------------------------------------------|----------------------------------------------------------------|
| ![轻言数据概览](https://www.puresky.top/upload/1777469591419.webp)   | ![轻言数据管理](https://www.puresky.top/upload/1777469648699.webp)   |
| 轻言数据概览                                                         | 轻言数据管理                                                         |
| ![轻言数据批量导入](https://www.puresky.top/upload/1777469675652.webp) | ![轻言单条数据创建](https://www.puresky.top/upload/1777469694151.webp) |
| 轻言数据批量导入                                                       | 轻言单条数据创建                                                       |
| ![轻言默认模板](https://www.puresky.top/upload/1777735550856.webp)   |
| 轻言默认模板                                                         |

## 安装

1. 下载最新版本的 [Releases](https://github.com/imorisun/plugin-hitokoto-hub/releases)
2. 在 Halo 后台的"插件"管理中上传并启用
3. 启用后，左侧菜单会出现"轻言"入口

## 使用指南

### 可用数据源

- [sentences-bundle](https://github.com/hitokoto-osc/sentences-bundle)
- [sentences-bundle-JSDelivr](https://cdn.jsdelivr.net/gh/hitokoto-osc/sentences-bundle@1.0.647/)

### API文档

[Apifox文档](https://plugin-hitokoto-hub.apifox.cn/)

### 基础使用

1. **创建分类**：在左侧分类面板中点击 `+` 号，创建句子分类
2. **新建句子**：点击右上角"新建句子"，填写内容、选择分类
3. **批量导入**：支持 JSON 和 Excel 两种格式，自动映射字段
4. **随机获取句子**：前端调用 `/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/random` 接口
5. **获取分类数据**：前端调用 `/apis/public.api.hitokotohub.puresky.top/v1alpha1/category/list` 接口

### 主题集成

#### 插件提供一个默认的模板，模板地址为`/hitokoto`，当然您也可以通过 Finder API 自定义模板

#### 方式一：Finder API（推荐）

可用的 Finder API：

- `hitokotoFinder.listCategories()`
- `hitokotoFinder.randomSentences(int limit, String category)`

在主题模板中直接调用：

```html
<!-- 随机获取 1 条随机的句子 -->
<div th:each="s : ${hitokotoFinder.randomSentences(1,null)}">
    <p th:text="${s.content}"></p>
    <span th:text="${s.author}"></span>
</div>

<!-- 获取分类列表 -->
<div th:each="c : ${hitokotoFinder.listCategories()}">
    <a th:href="@{/hitokoto(category=${c.name})}" th:text="${c.displayName}"></a>
</div>

<!-- 按分类随机获取 -->
<div th:each="s : ${hitokotoFinder.randomSentences(8, 'category-xxx')}">
    <p th:text="${s.content}"></p>
</div>
```

#### 方式二：REST API

直接调用插件提供的公开 API：

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
fetch('/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/like?action=like')
        .then(res => res.json())

// 取消点赞
fetch('/apis/public.api.hitokotohub.puresky.top/v1alpha1/sentence/like?action=unlike')
        .then(res => res.json())
```

## 公开 API 说明

| 接口                 | 方法  | 说明                                                               |
|--------------------|-----|------------------------------------------------------------------|
| `/sentence/random` | GET | 随机获取句子，支持 `categoryName` 和 `limit` 参数                            |
| `/sentence/like`   | GET | 点赞/取消点赞，参数 `action=like` 或 `action=unlike`以及`name=sentence-xxxx` |
| `/category/list`   | GET | 获取分类列表                                                           |

## 角色权限

插件部署后会自动创建一个名为"轻言-公共接口"的角色模板，已自动授权给匿名用户。若需后台管理权限，请确保当前用户具有
`plugin:hitokoto-hub:manage` 权限。

## 开发

```bash
# 克隆项目
git clone https://github.com/imorisun/plugin-hitokoto-hub.git

# 构建前端
cd ui
pnpm install
pnpm build

# 构建插件
cd ..
./gradlew build
```

## 许可

[GPL-3.0](./LICENSE) © [imorisun](https://github.com/imorisun)

## 致谢

- [Halo](https://github.com/halo-dev/halo) — 强大易用的开源建站工具