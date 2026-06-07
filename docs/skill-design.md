# Skill 抽象层设计

> Phase 1 的核心交付物：把现有 `tools/` 包下硬编码的 Java Tool 类，升级为「可声明、可热加载、可被外部系统共用」的 Skill 单元。
>
> 本文档定义抽象、元数据规范、注册流程、与现有体系的适配方式，以及迁移路径。

---

## 1. 设计目标

1. **可声明**：每个能力以 `SKILL.md` 描述（名字、用途、输入输出、示例），不必读 Java 代码即可了解。
2. **可热加载**：启动时扫描 `skills/` 目录，未来支持运行时新增/下线 Skill（Phase 2 引入）。
3. **可被外部共用**：通过 MCP Server 自动暴露全部 Skill，让 Dify、Claude Desktop、Cursor 等外部系统直接复用。
4. **向下兼容**：不破坏现有 `AssistantApp / VsManus / agentplatform` 的任何调用路径，老 `tools/*Tool.java` 保留并存一段时间。
5. **对齐 Anthropic Skill 规范**：`SKILL.md` 字段命名、目录结构尽量贴近 Claude Skills 生态，未来可双向迁移。

---

## 2. 与现有抽象的关系

项目现有两套「能力对象」：

- `tools/*Tool.java` — 直接 `@Tool` 注解，由 `ToolRegistration` 通过 `ToolCallbacks.from(...)` 一次性注入。
- `agentplatform/tool/AgentTool` — 平台层抽象，带 `ToolMetadata`、`validate`、`execute(ToolExecuteRequest)`，由 `ToolRegistry` 管理。

`Skill` **不是第三种平行抽象**，而是把上面两套统一起来：

```
SKILL.md (元数据)
   │
   ▼
Skill 实例 (Java 类)
   │
   ├── 通过 SkillCallbackAdapter → Spring AI ToolCallback (供 AssistantApp / VsManus 使用)
   ├── 通过 SkillAgentToolAdapter → AgentTool (供 agentplatform 使用)
   └── 通过 MCP Server 暴露 → 外部系统调用
```

这样三条调用路径共用同一份能力实现，避免重复维护。

---

## 3. 抽象类型

### 3.1 `Skill` 接口

```java
package com.vs.vsaiagent.skill;

import java.util.Map;

public interface Skill {

    /** 元数据，启动时从 SKILL.md 解析或代码声明 */
    SkillMetadata metadata();

    /** 同步执行 */
    SkillResult execute(Map<String, Object> arguments, SkillContext context);

    /** 默认拿元数据里的 name */
    default String name() {
        return metadata().name();
    }
}
```

### 3.2 `SkillMetadata`

```java
public record SkillMetadata(
        String name,                       // 唯一英文 ID，如 "pdf-generation"
        String displayName,                // 中文/英文展示名
        String description,                // 一句话描述（喂给 LLM）
        String version,                    // semver
        List<String> tags,                 // 路由/分类用
        List<SkillParam> inputs,           // 输入参数 schema
        List<SkillParam> outputs,          // 输出字段
        List<String> examples,             // 调用样例
        Long timeoutMs,                    // 默认超时
        SkillSourceType sourceType         // LOCAL / MCP / DIFY / REMOTE_HTTP
) {}

public record SkillParam(
        String name,
        String type,            // string / int / boolean / object / array
        String description,
        boolean required,
        Object defaultValue
) {}
```

### 3.3 `SkillContext`

```java
public record SkillContext(
        String traceId,
        String requestId,
        String sessionId,
        Map<String, Object> attrs   // 透传业务上下文
) {}
```

通过 `TraceContext.get()` 在执行前自动填充，使 Skill 与 observability 模块天然打通。

### 3.4 `SkillResult`

```java
public record SkillResult(
        boolean success,
        Object data,
        String errorMessage,
        long elapsedMs,
        Map<String, Object> metrics    // 可选：token 数、外部 API 耗时等
) {
    public static SkillResult ok(Object data, long elapsedMs) { ... }
    public static SkillResult fail(String msg, long elapsedMs) { ... }
}
```

### 3.5 `AbstractSkill` 基类

提供默认实现：

- `metadata()` 从 `SKILL.md` 解析（fallback 到子类 `defaultMetadata()`）
- 参数校验（基于 `inputs` 中的 `required`）
- 计时、异常包装、统一返回 `SkillResult`
- 子类只需实现 `protected abstract Object doExecute(Map<String,Object> args, SkillContext ctx)`

---

## 4. SKILL.md 规范

每个 Skill 一个目录，目录名 = `metadata().name()`：

```
skills/
├── pdf-generation/
│   ├── SKILL.md
│   ├── handler.java       (可选：纯 markdown 描述 + 外部脚本时用)
│   ├── examples/
│   │   └── invoice.json
│   └── tests/
│       └── case_001.yaml
└── web-search/
    ├── SKILL.md
    └── ...
```

`SKILL.md` 采用 YAML front-matter + Markdown 正文：

```markdown
---
name: pdf-generation
displayName: PDF 生成
description: Generate a PDF file with given content
version: 1.0.0
tags: [file, document]
inputs:
  - name: fileName
    type: string
    description: Name of the file to save the generated PDF
    required: true
  - name: content
    type: string
    description: Content to be included in the PDF
    required: true
outputs:
  - name: filePath
    type: string
    description: 生成后的 PDF 绝对路径
examples:
  - 把"hello world"生成一个 hello.pdf
timeoutMs: 30000
sourceType: LOCAL
---

# PDF 生成

更详细的使用说明、限制、依赖、维护人……
```

YAML 字段与 `SkillMetadata` 一一对应。Markdown 正文不被代码读取，仅供人类阅读 / 喂给文档站点。

---

## 5. 注册流程

```
启动
 │
 ▼
SkillScanner.scan("classpath:skills/**/SKILL.md")
 │      ├─ 解析 YAML front-matter → SkillMetadata
 │      └─ 通过 ApplicationContext 查找同名 @Component Skill 实例
 │
 ▼
SkillRegistry.register(skill)
 │
 ▼
SkillCallbackAdapter 把每个 Skill wrap 成 ToolCallback
 │
 ▼
注入 ChatClient (AssistantApp / VsManus) + agentplatform ToolRegistry + MCP Server
```

- `SkillRegistry` 接口 + `InMemorySkillRegistry` 默认实现。
- `SkillScanner` 基于 `PathMatchingResourcePatternResolver` + 一个轻量 YAML 解析（snakeyaml）。
- Skill Java 实例本身仍是 `@Component`，方便依赖注入（如 `WebSearchSkill` 注入 API Key）。
- 适配器分两个：
  - `SkillCallbackAdapter implements ToolCallback` — 把 `Skill.execute(args, ctx)` 适配成 Spring AI 的 `call(toolInput)`
  - `SkillAgentToolAdapter implements AgentTool` — 把 `Skill.execute` 适配成 agentplatform 的 `execute(ToolExecuteRequest)`

---

## 6. 与 observability 打通

`SkillCallbackAdapter` 在 `call` 前后：

1. 从 `TraceContext` 读取 `traceId / requestId / sessionId` 注入 `SkillContext`
2. 调用 `ExecutionLogService.logStage(..., TOOL, skillName, ...)` 记入参 / 出参 / 耗时 / 是否成功

`SkillAgentToolAdapter` 同理。这样 Skill 的执行细节天然落入现有可观测性表，**无需 Skill 自己关心日志**。

---

## 7. MCP 暴露

新增 `skill/mcp/SkillMcpServer.java`（或扩展现有 `vs-image-search-mcp-server`）：

- 启动时遍历 `SkillRegistry.listAll()`，把每个 Skill 注册成 MCP Tool
- Tool name = `metadata.name()`，description = `metadata.description()`
- 输入 schema 从 `metadata.inputs()` 自动生成 JSON Schema
- 通过 SSE / stdio 两种协议供外部 MCP 客户端连接

这样 Dify / Claude Desktop / Cursor 可以一键连上你的 MCP，直接用你所有 Skill。

---

## 8. OpenAPI 暴露（Dify 友好）

在 `agentplatform/controller` 新增端点：

```
GET /agent-platform/skills              # 列出所有 Skill 元信息
GET /agent-platform/skills/{name}       # 单个 Skill 详情
GET /agent-platform/skills/openapi.json # 全量 Skill 的 OpenAPI 3.0 描述
POST /agent-platform/skills/{name}/execute  # 直接调用
```

`openapi.json` 把每个 Skill 包装成一个 `/skills/{name}/execute` 路径，方便 Dify 后台一键导入为自定义工具集合。

---

## 9. 迁移路径

老的 `tools/` 包**完全保留**，新 Skill 与之并存：

| 阶段 | 动作 |
| --- | --- |
| 步骤 1 | 落地 Skill 抽象代码骨架（本批 PR） |
| 步骤 2 | 把 `PDFGenerationTool` 重写为 `PDFGenerationSkill`，新建 `skills/pdf-generation/SKILL.md`（本批 PR） |
| 步骤 3 | 在 `ToolRegistration` 中把 PDF 工具来源从老 Tool 切到 Skill 适配器（下一批 PR） |
| 步骤 4 | 按 1 个/天的节奏迁移剩余 7 个工具 |
| 步骤 5 | 全部迁移完，删除 `tools/*Tool.java`，废止 `ToolRegistration`（PR 中独立 commit，便于回滚） |

---

## 10. 包结构规划

```
src/main/java/com/vs/vsaiagent/skill/
├── Skill.java
├── SkillMetadata.java
├── SkillParam.java
├── SkillContext.java
├── SkillResult.java
├── SkillSourceType.java
├── AbstractSkill.java
├── registry/
│   ├── SkillRegistry.java
│   └── InMemorySkillRegistry.java
├── loader/
│   ├── SkillScanner.java
│   └── SkillMdParser.java
├── adapter/
│   ├── SkillCallbackAdapter.java
│   └── SkillAgentToolAdapter.java
├── config/
│   └── SkillAutoConfiguration.java
└── builtin/
    └── PDFGenerationSkill.java   # 第一个样板
```

```
src/main/resources/skills/
└── pdf-generation/
    ├── SKILL.md
    └── examples/
        └── invoice.json
```

---

## 11. 未尽事项（留给后续）

- 远程 Skill（HTTP / gRPC）通过 `SkillSourceType.REMOTE_HTTP` 支持
- Skill 沙箱执行（针对 `TerminalOperationSkill` 等高危类型）
- Skill 版本灰度（同名多版本路由）
- 权限/限流装饰器（按 tag 路由）
- 与 Eval Harness 联动：每个 Skill 自带 `tests/*.yaml`，eval 模块直接消费
