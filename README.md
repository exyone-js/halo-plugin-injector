## 这是什么?

一个用于按规则将 HTML 代码注入指定页面的 Halo 插件

相比 Halo 默认的全局注入功能, 本插件使用 "代码片段 + 注入规则" 组合管理, 可以控制注入页面, 注入位置和匹配方式

界面入口: Halo 管理后台 -> 工具 -> Injector

## 功能特性

- 四种注入模式: `HEAD`, `FOOTER`, `ID`, `SELECTOR`
- 五种注入位置: `APPEND`, `PREPEND`, `BEFORE`, `AFTER`, `REPLACE`
- 支持嵌套规则组和 `AND`, `OR`, `NOT`, `AND_NOT`, `OR_NOT`
- 四种路径匹配器: `PATH_PATTERN`, `ANT`, `REGEX`, `EXACT`
- 代码片段和注入规则关联管理
- 支持启用, 禁用, 批量修改和批量删除
- 支持未保存修改提示和单字段恢复
- 为完整 HTML 注入提供有界缓存

## 使用流程

最简单的场景只需两步:

1. 创建代码片段并填写需要注入的 HTML, CSS 或 JavaScript
2. 创建注入规则, 关联该片段即可

默认 `mode=HEAD`, 生效页面为全站 `/**`, 规则创建后默认启用, 无需额外配置即可在 5 分钟内完成一次全局注入。

需要精细化控制时再按需展开:

- 想限制页面: 在「生效页面」中每行填写一条路径 (多条为「或」), 并用「排除页面」排除例外
- 想注入到具体元素: 将模式切换为 `ID` 或 `SELECTOR`, 再填写目标元素和插入位置
- 需要复杂布尔逻辑 / 正则: 点击「高级: 自定义匹配表达式」使用规则树编辑器

代码片段与注入规则创建后均默认启用

## 快速上手 (5 分钟)

1. 工具 -> Injector -> 新建代码片段, 粘贴一段 `<script>` 或 `<style>`, 保存
2. 新建注入规则, 在「关联代码片段」中勾选上一步的片段
3. 保持默认 `HEAD` + 全站 `/**`, 保存后规则即生效
4. 打开站点任意页面查看注入结果

## 示例配置

- <a href="assets/images/config_code.png">代码片段配置</a>
- <a href="assets/images/config_rule_1.png">注入规则配置 1</a>
- <a href="assets/images/config_rule_2.png">注入规则配置 2</a>

## 核心概念

### 代码片段 (CodeSnippet)

代码片段保存需要注入的实际内容

一个代码片段可以被多个注入规则复用, 禁用后不会参与注入

### 注入规则 (InjectionRule)

注入规则定义代码片段需要注入到哪些页面和哪些位置

规则包含注入模式, 目标元素, 注入位置, 页面匹配规则和关联代码片段

关联关系以 `InjectionRule.snippetIds` 为准

`CodeSnippet.ruleIds` 仅保留用于兼容旧数据结构, 新关联不再写入该字段

## 注入模式

| 模式         | 处理阶段      | 说明                   |
|------------|-----------|----------------------|
| `HEAD`     | 模板渲染阶段    | 注入到 `<head>`         |
| `FOOTER`   | 模板渲染阶段    | 注入到主题 footer 扩展位置    |
| `ID`       | HTML 响应阶段 | 通过元素 `id` 查找目标       |
| `SELECTOR` | HTML 响应阶段 | 通过 CSS Selector 查找目标 |

`HEAD` 和 `FOOTER` 不解析完整 HTML, 常规场景建议优先使用

`ID` 和 `SELECTOR` 使用 Jsoup 解析完整 HTML, 功能更灵活, 但会增加内存和 CPU 开销

注入内容会使用以下标记包裹, 已包含完整标记的内容不会重复包裹

```html
<!-- PluginInjector start -->
<!-- injection code -->
<!-- PluginInjector end -->
```

## 注入位置

注入位置只用于 `ID` 和 `SELECTOR` 模式

| 位置        | 说明              |
|-----------|-----------------|
| `APPEND`  | 追加为目标元素的最后一个子节点 |
| `PREPEND` | 插入为目标元素的第一个子节点  |
| `BEFORE`  | 插入到目标元素之前       |
| `AFTER`   | 插入到目标元素之后       |
| `REPLACE` | 使用注入内容替换目标元素    |

注入到 `<head>` 时需要保证 HTML 合法, 例如 `<div>` 等块级标签可能被浏览器或解析器移动到 `<body>`

## 页面匹配

### 简单模式 (默认)

表单中直接填写「生效页面」与「排除页面」, 每行一条路径:

- 生效页面之间为 `OR` (命中任意一条即注入)
- 排除页面恒为 `AND_NOT` (命中任意一条则不注入)

匹配器按表达式语法自动识别: 含 `*` `?` `{}` 时按路径模式处理, 含 `^` `$` `\` `(` 等正则特征时按正则处理, 其余按精确路径处理。大多数场景只需记住 `/**` 与 `/posts/**` 两类写法。

该精简表达在后端由 `RuleConfigCodec` 编译为下面的匹配树, 存储在 `InjectionRule.pages` 字段; 旧版本只含匹配树的数据会被自动兼容, 也可随时在表单中点击「高级: 自定义匹配表达式」切换为规则树。

### 高级模式 (匹配树)

每个注入规则包含一个页面匹配树, 节点类型为 `GROUP` 或 `PATH`

### 路径匹配器

| 匹配器            | 说明                    | 示例              |
|----------------|-----------------------|-----------------|
| `PATH_PATTERN` | Spring PathPattern 风格 | `/posts/{slug}` |
| `ANT`          | Ant 风格                | `/posts/**`     |
| `REGEX`        | Java 正则表达式, 匹配完整路径    | `/posts/.*`     |
| `EXACT`        | 完整字符串相等               | `/archives`     |

无效或空白的匹配规则不会命中

正则表达式会在保存时验证语法, 运行时使用有界缓存保存已编译 Pattern

### 逻辑操作

规则组按照子节点顺序计算, 每个子节点的操作符用于连接前一项

| 操作符       | 说明                |
|-----------|-------------------|
| `AND`     | 前一项和当前项都需要匹配      |
| `OR`      | 前一项或当前项任意匹配       |
| `NOT`     | 当前项取反并使用 `AND` 连接 |
| `AND_NOT` | 当前项取反并使用 `AND` 连接 |
| `OR_NOT`  | 当前项取反并使用 `OR` 连接  |

需要明确优先级时可以使用嵌套规则组

示例:

```text
GROUP(
  PATH('/posts/**')
  OR PATH('/archives/**')
  AND_NOT PATH('/admin/**')
)
```

## HTML 响应处理

`ID` 和 `SELECTOR` 通过 WebFilter 处理响应, 只处理符合以下条件的请求和响应

- 请求方法为 `GET`
- 响应状态码为 `200`
- 响应类型包含 `text/html`
- 响应未使用 gzip 等内容编码
- 已知 `Content-Length` 时不超过 `2 MiB`

以下系统路径默认跳过:

```text
/console/**
/uc/**
/login/**
/signup/**
/logout/**
/themes/**
/plugins/**
/actuator/**
/api/**
/apis/**
/system/**
/upload/**
/webjars/**
```

单条 HTML 规则执行失败时会跳过当前规则

完整响应处理失败时会返回原始 HTML, 不会阻断页面响应

## 缓存

缓存只用于 `ID` 和 `SELECTOR` 模式

缓存键包含请求路径, 原始 HTML 指纹和有序规则指纹, 规则顺序变化会产生新的缓存结果

- 最大缓存数量: `1024`
- 写入过期时间: `1 day`
- HTML 指纹算法: `FNV-1a 64`

代码片段或页面内容变化后会生成新指纹, 不需要手动清理缓存

## 安全提示

代码片段可以包含 HTML, CSS 和 JavaScript, 只应向受信任用户授予管理权限

注入脚本会在访客浏览器中执行, 使用前需要确认内容来源和站点 CSP 配置

复杂正则表达式可能增加路径匹配开销, 建议优先使用 `PATH_PATTERN`, `ANT` 或 `EXACT`

## 项目结构

```text
src/main/java/com/erzbir/injector
|-- api       公共接口和枚举
`-- halo
    |-- core      匹配和注入核心
    |-- filter    HTML 注入
    |-- manager   Extension 管理
    |-- process   HEAD 和 FOOTER 模板处理器
    |-- scheme    CodeSnippet 和 InjectionRule 模型
    `-- util      HTML, 上下文和指纹等工具

src/main/resources
|-- extensions    扩展点和角色模板
`-- plugin.yaml   Halo 插件清单

ui/src
|-- apis          Halo API 客户端
|-- types         前端数据类型
`-- views         管理界面, 编辑器和状态逻辑
```

## 开发环境

- Java `21+`
- Node.js `22`
- pnpm `10`

## 开发

```bash
# 构建插件
./gradlew build

# 开发前端
cd ui
pnpm install
pnpm dev
```

构建完成后, 可以在 `build/libs` 目录找到插件 jar 文件

## 许可证

[GPL-3.0](./LICENSE) © Erzbir 
