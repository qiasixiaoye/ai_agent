# 测试样例（MCP 工具 / Skills / 一句话生成工作流）

后端 `http://localhost:8081/api`，前端 `http://localhost:5173`，Dify `http://localhost:3001`，
工作流生产(Gradio) `http://localhost:7861`。以下命令在 Git Bash 里发**中文**易踩编码坑，示例用英文 body。

---

## 1. Skills 验证

```bash
B=http://localhost:8081/api
# 列出技能
curl -s $B/skills
# 执行 pdf-generation（成功返回 filePath）
curl -s -X POST $B/skills/pdf-generation/execute -H "Content-Type: application/json" \
  -d '{"fileName":"plan.pdf","content":"milky way shooting plan"}'
# 给 Dify 导入用的 OpenAPI
curl -s $B/skills/openapi.json | head -c 200
```
✅ 通过标志：execute 返回 `{"code":0,"data":{"success":true,"data":{"filePath":...}}}`

---

## 2. MCP 工具验证

**(a) 确认 MCP server 暴露了能力**
```bash
docker logs vs-ai-agent 2>&1 | grep -i "Registered tools"   # 期望 Registered tools 15
# 注意：MCP 端点在根路径 /sse（不带 /api），与 REST(/api/*) 分离，避免 context-path 错位
curl -s -i --max-time 2 http://localhost:8081/sse | grep -iE "HTTP/|content-type"  # 200 text/event-stream
```

**(b) 直接执行工具（REST，等价于 MCP 工具的底层能力）**
```bash
B=http://localhost:8081/api
curl -s -X POST $B/agent-platform/tools/astrophoto_settings/execute -H "Content-Type: application/json" \
  -d '{"arguments":{"focalLength":24,"aperture":2.8,"bortle":3}}'
curl -s -X POST $B/agent-platform/tools/milkyway_rise/execute -H "Content-Type: application/json" \
  -d '{"arguments":{"latitude":39.9,"longitude":116.4,"date":"2026-06-20"}}'
```

**(c) 在 Dify 里挂 MCP（动态，真实时）**
- Dify(`localhost:3001`) → 工具 → 添加 MCP 服务 → 端点 `http://host.docker.internal:8081/sse`
  （**根路径 /sse，不要带 /api**；MCP server 的 SSE 通告端点不带 context-path，
  若后端仍配 `context-path: /api` 会握手 404 一直"授权中"，本项目已移除该 context-path）
- 期望：看到 15 个工具（含 `astrophoto_settings`/`exposure_advisor`/`coffee_recipe`/`moto_trip_planner`），可拖进工作流
- 新增能力后重启后端，Dify 刷新即见，无需重导
- 兜底（若 Dify 客户端只认 Streamable HTTP）：改用 OpenAPI 自定义工具导入
  `http://host.docker.internal:8081/api/agent-platform/openapi.json` 与 `.../api/skills/openapi.json`

---

## 3. 一句话生成工作流

**(a) UI（推荐）**：前端首页 → 工作流生产 → 内嵌 Gradio 输入：
> 拍银河的工作流：先联网搜索今晚银河升起时刻和光污染，再给出相机拍摄参数，最后汇总成拍摄计划

生成的工作流会自动导入 Dify，画布可见可运行。

**(b) 后端直测**（看 IR + DSL，不依赖 Dify）：
```bash
curl -s -X POST http://localhost:8081/api/workflow-builder/generate -H "Content-Type: application/json" \
  -d '{"requirement":"search web for today AI news and summarize key points"}'
```
✅ 实测样例输出：
- `workflowName = "AI新闻摘要工作流"`
- 节点：`start → tool:web_search → llm → answer`（规划器识别"search web"自动插入 web_search）
- `valid = true`，附带可导入 Dify 的 `dslYaml`

---

## 4. 端到端星空编排（多步 + 状态传递 + LLM 汇总）
```bash
curl -s -X POST http://localhost:8081/api/agent-platform/tasks/astro-demo -H "Content-Type: application/json" \
  -d '{"latitude":43.8,"longitude":87.6,"date":"2026-06-20"}'
```
✅ 通过标志：`success:true, executedSteps:4`，`summary` 是 LLM 把银河时刻/光污染/云量/拍摄参数
汇总成的完整拍摄方案。

---

## 5. 多领域能力 + 一句话生成 DSL 测试用例（已实测）

新增能力（`@Component` 自动注册，已进 tools/skills + OpenAPI + MCP，`Registered tools 15`）：

| 领域 | 能力 | 类型 | 必填参数 |
|---|---|---|---|
| 摄影 | `exposure_advisor` 曝光参数建议 | tool | scene |
| 摄影 | `astrophoto_settings` 星空拍摄参数 | tool | focalLength |
| 咖啡 | `coffee_recipe` 冲煮配方 | tool | method |
| 咖啡 | `coffee-tasting-notes` 品鉴笔记 | skill | beans |
| 摩旅 | `moto_trip_planner` 行程规划 | tool | distanceKm |
| 摩旅 | `moto-gear-checklist` 装备清单 | skill | season |

**一句话生成 DSL 实测结果**（`POST /workflow-builder/generate`，中文 body 请用 UTF-8，勿在 Git Bash 直接 `-d` 发中文）：

| 一句话需求 | 生成的工作流名 | 节点流（valid=true） |
|---|---|---|
| 根据拍摄场景给出相机曝光参数，并整理成拍摄建议 | 场景曝光参数与拍摄建议 | start → **exposure_advisor** → llm → answer |
| 联网搜索今晚银河升起时刻，再给出星空拍摄参数并汇总 | 银河拍摄计划生成 | start → **web_search → milkyway_rise → astrophoto_settings** → llm → answer |
| 做一个手冲咖啡冲煮配方工作流，给出粉水比和步骤 | 手冲咖啡冲煮配方工作流 | start → **coffee_recipe** → llm → answer |
| 按咖啡豆产地和烘焙度输出风味品鉴笔记 | 咖啡风味品鉴笔记 | start → **skill:coffee-tasting-notes** → llm → answer |
| 规划一条摩托旅行路线，根据里程估算天数和加油点 | 摩托旅行路线规划 | start → **moto_trip_planner** → llm → answer |
| 按季节和天数生成摩旅装备清单 | 摩旅装备清单生成 | start → **skill:moto-gear-checklist** → llm → answer |

### 5.1 复杂案例：多工具编排（每个工具自动获得完整参数）

LLM 规划器现在为**每个工具产出完整参数**（不再是所有工具都绑同一个 `${start}`），且 LLM 汇总节点
会引用**所有**前序工具的输出。一句话即可生成可直接在 Dify 运行的多工具工作流。

样例需求（工作流生产页输入）：
> 做一个北京地区的星空拍摄计划助手：先计算今晚银河升起时刻，再按 24mm 焦距给出星空拍摄相机参数，
> 最后把银河时刻和拍摄参数汇总成完整拍摄方案

实测生成（`milkyway_rise → astrophoto_settings → llm → answer`，已导入 Dify 运行 5 节点全 succeeded）：
- `milkyway_rise` 参数 = `{"latitude":39.9,"longitude":116.4,"date":"<当天>"}`（"北京"→经纬度、"今晚"→当天日期，均由 LLM 推断）
- `astrophoto_settings` 参数 = `{"focalLength":24}`（从"24mm"抽取）
- LLM 节点 user 消息同时引用 `工具1执行结果 {{#...body#}}` 与 `工具2执行结果 {{#...body#}}`
- 运行输出：银河 20:15 升起 / 00:24 银心最高 + 快门 20.8s(500法则)/f2.8/ISO1600 的完整拍摄方案

> 实现：`LlmWorkflowPlanner` 的 `LlmPlan.capabilities` 由 `List<String>` 升级为
> `List<CapabilityCall>{ref, arguments}`，prompt 中附带每个能力的参数名与当前日期；
> 缺省（LLM 未给参数）时退回"首个必填参数绑定 ${start}"，保持兜底可用。

要点：LLM 规划器按需求自动选能力并填全参数（星空那条串了 2 个工具且各自参数正确），规则规划器作兜底（已加对应关键词）。
单点验证任一能力：
```bash
curl -s -X POST http://localhost:8081/api/agent-platform/tools/coffee_recipe/execute \
  -H "Content-Type: application/json" -d '{"arguments":{"method":"pourover","cups":2}}'
```
