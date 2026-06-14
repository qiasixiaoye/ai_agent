# Workflow Builder 组件（MVP）

自然语言需求 → Workflow IR → Dify DSL YAML → 结构校验 → 返回/导出。

当前 MVP 只生成最简流程：`Start → LLM → Answer`，IR 由规则生成（不调用大模型），DSL 由 Java 模板拼装（禁止 LLM 直接写 YAML）。

## 包结构

```
com.vs.vsaiagent.workflowbuilder
├── controller   WorkflowBuilderController + 模块级异常处理
├── service      WorkflowPlanningService（规则 IR）
│                WorkflowDslGenerateService（IR → YAML）
│                WorkflowDslValidateService（IR/DSL 校验）
│                WorkflowFileService（落盘 tmp/workflow-builder/{id}.yml）
├── model        WorkflowIR / WorkflowNode / WorkflowEdge / DTO / ValidateResult
├── template     DifyWorkflowTemplate（app+workflow 骨架）/ DifyNodeTemplate（节点模板）
└── util         YamlUtil（SnakeYAML 封装）/ GraphValidateUtil（Kahn 判环）
```

与既有 `workflow` 包（WorkflowGenerator/Executor，LLM 生成 + Java 内执行）互不依赖；本组件目标是产出可导入 Dify 的 DSL 文件。

## API（context-path：/api）

POST `/api/workflow-builder/generate`

```json
{ "requirement": "输入招聘 JD，提取岗位、地点、技能要求，输出 JSON。" }
```

响应：`workflowId / workflowName / ir / dslYaml / valid / errors`。校验通过的 DSL 同时落盘。

POST `/api/workflow-builder/validate`

```json
{ "dslYaml": "..." }
```

响应：`{ "valid": true, "errors": [] }`

GET `/api/workflow-builder/export/{workflowId}` → 下载 `generated_workflow.yml`（404 表示未生成或校验未通过）。

POST `/api/workflow-builder/import/{workflowId}` → 通过 Dify Console API 把已生成的 DSL 直接导入本地 Dify，返回 `success / appId / status / appUrl / errorMessage / rawResponse`。需要先配置 `app.dify.console.*`（见下）。兼容 Dify 1.x 的 `/console/api/apps/imports`（yaml-content 模式），404 时自动回退旧版 `/console/api/apps/import`；token 进程内缓存，401 自动重登一次。

## 校验项

存在 start 节点、存在 answer 节点、节点 id 唯一、边 source/target 指向真实节点、无环、YAML 可解析且含 `workflow.graph`。节点类型兼容 Dify 标准（顶层 `type: custom` + `data.type`）和简化写法（顶层直接写业务类型）。

## 配置

```yaml
app:
  workflow-builder:
    model:
      provider: tongyi   # 默认值，对应 Dify 里的模型供应商标识
      name: qwen-max
  dify:
    console:             # 自动导入 Dify 用
      base-url: http://localhost:3001
      email: 你的Dify控制台邮箱
      password: 你的密码
      # access-token:    # 或者直接填 console token
```

## DSL 模板说明

模板按 Dify 0.x 导出格式（version 0.1.5）拼装：`app(mode: workflow)` + `workflow.features` + `workflow.graph.nodes/edges`，LLM user 消息引用 `{{#start.input#}}`，answer 引用 `{{#llm_task.text#}}`。**不同 Dify 版本字段有差异**：若导入失败，从本地 Dify 导出一个同结构工作流的真实 DSL，对照调整 `template/DifyNodeTemplate.java` 和 `DifyWorkflowTemplate.java`（重点核对 version、model.provider、start variables 字段）。

## 测试

```
mvn test -Dtest=WorkflowBuilderTest
```

覆盖：3 个样例需求的 IR 生成、DSL 生成且自校验通过、缺 start、缺 answer、重复 id、边指向不存在节点、环检测、非法 YAML、文件读写与路径穿越防护。

## 三个演示样例

1. `输入一段文本，生成三条摘要。` → 文本总结工作流
2. `输入招聘 JD，提取岗位、地点、技能要求，输出 JSON。` → 信息提取工作流（instruction 自动附加"只输出合法 JSON"约束）
3. `输入论文摘要，提取研究问题、方法、数据集和结论。` → 信息提取工作流

## 二期及以后（未实现）

Spring AI Planner 生成 IR、Code/Tool/If-Else/Knowledge Retrieval 节点、MCP 工具发现与自动绑定 Tool 节点、调用 Dify API 自动导入、可视化画布。
