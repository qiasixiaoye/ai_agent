# 测试流程 Playbook：写一个能力 → 嵌入工作流 → 跑通

用"拍星空 / 银河摄影"做贯穿示例。核心思想：**能力写一次，自动出现在三处**——
本服务 REST、OpenAPI（给 Dify 导入）、MCP（给 Dify 动态拉取）。

> 现有星空相关能力（已注册）：`milkyway_rise`（银河升起时刻）、`light_pollution`（光污染）、
> `cloud_cover`（云量）、`astro_plan_summary`（LLM 汇总拍摄建议）。
> 本示例新增一个 `astrophoto_settings`（按 500 法则推荐快门/ISO/光圈），插进这条流程。

---

## 0. 两类能力怎么选

| | Tool（AgentTool） | Skill |
|---|---|---|
| 注册方式 | `@Component extends BaseAgentTool` | `@Component extends AbstractSkill` |
| 出现在 | `/agent-platform/tools` | `/skills` |
| 能进**本地多步编排** `/agent-platform/tasks/execute` | ✅（编排器查 ToolRegistry） | ❌（编排器只认 Tool） |
| OpenAPI 导入 Dify | `/agent-platform/openapi.json` | `/skills/openapi.json` |
| MCP 暴露 | ✅ | ✅ |

**要嵌入本地多步流程 → 写成 Tool**（本示例）。只在 Dify 里用 → Tool/Skill 都行。

---

## 1. 写能力：`astrophoto_settings`（Tool 版）

新建 `src/main/java/com/vs/vsaiagent/agentplatform/tool/impl/AstrophotoSettingsAgentTool.java`：

```java
package com.vs.vsaiagent.agentplatform.tool.impl;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.model.ToolSourceType;
import com.vs.vsaiagent.agentplatform.tool.BaseAgentTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 按 500 法则根据焦距/光圈/光污染推荐银河拍摄参数。纯计算，无外部依赖，演示稳定。 */
@Component
public class AstrophotoSettingsAgentTool extends BaseAgentTool {

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.builder()
                .toolName("astrophoto_settings")
                .displayName("星空拍摄参数")
                .description("按 500 法则根据镜头焦距、光圈、光污染 Bortle 等级推荐快门/ISO/光圈与构图建议")
                .sourceType(ToolSourceType.LOCAL)
                .tags(List.of("astro", "photography"))
                .requiredParams(List.of("focalLength"))   // 只有它必填；aperture/bortle 可选
                .timeoutMs(5000L)
                .build();
    }

    @Override
    public ToolExecuteResult execute(ToolExecuteRequest request) {
        long start = System.currentTimeMillis();
        Map<String, Object> a = request.getArguments();
        double focal    = toDouble(a.get("focalLength"), 24);
        double aperture = toDouble(a.get("aperture"), 2.8);
        int    bortle   = (int) toDouble(a.get("bortle"), 4);

        // 500 法则：最长快门 ≈ 500 / 焦距，避免星点拖线
        double shutter = Math.round((500.0 / focal) * 10) / 10.0;
        // 光污染越重(Bortle 越大)，ISO 越保守
        int iso = bortle <= 3 ? 3200 : (bortle <= 5 ? 1600 : 800);

        String output = String.format(
                "建议参数：快门 %ss（500法则，防拖线）｜光圈 f/%.1f（开到最大进光）｜ISO %d（Bortle %d）。\n"
              + "操作：对焦无穷远、关防抖、2 秒延时或快门线、RAW 格式、对准银河中心（夏季人马座方向）。",
                trim(shutter), aperture, iso, bortle);

        return ToolExecuteResult.builder()
                .toolName(toolName())
                .success(true)
                .output(output)
                .costMs(System.currentTimeMillis() - start)
                .build();
    }

    private double toDouble(Object v, double dft) {
        if (v == null) return dft;
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return dft; }
    }
    private String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
```

> **Skill 版**写法一样（`extends AbstractSkill`，重写 `defaultMetadata()` + `doExecute()`，
> 用 `SkillParam.required(...)`/`optional(...)`，参考 `skill/builtin/PDFGenerationSkill.java`），
> 区别只是它出现在 `/skills` 而非 `/agent-platform/tools`，进不了本地多步编排。

**不需要任何手动注册**：`ToolRegistryInitializer` 启动时自动把所有 `AgentTool` Bean 注册进
`ToolRegistry`；`SkillScanner` 自动注册所有 `Skill` Bean。

---

## 2. 构建并重启后端

```bash
cd C:\Users\lmh\Desktop\ai_agent\ai_agent
.\mvnw.cmd -o -DskipTests package
docker build -f Dockerfile.backend.runtime -t vs-ai-agent:latest .
docker compose up -d --force-recreate vs-ai-agent
# 等到 200
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/agent-platform/tools
```

---

## 3. 验证：能力一次编写，三处出现

```bash
# (1) REST 列表里有它
curl -s http://localhost:8081/api/agent-platform/tools | grep astrophoto_settings

# (2) 直接执行一次（单点验证）
curl -s -X POST http://localhost:8081/api/agent-platform/tools/astrophoto_settings/execute \
  -H "Content-Type: application/json" \
  -d '{"arguments":{"focalLength":24,"aperture":2.8,"bortle":3}}'
# 期望：{"code":0,"data":{"success":true,"output":"建议参数：快门 20.8s…"}}

# (3) OpenAPI 里自动多了它（给 Dify 导入）
curl -s http://localhost:8081/api/agent-platform/openapi.json | grep astrophoto_settings

# (4) MCP 暴露（需已重建带 MCP server 的后端；端点见第 5 节）
```

---

## 4. 嵌入「流程」并跑通 —— 路径 A：本地多步编排（最快）

把它接在银河升起时刻之后，一条 `/agent-platform/tasks/execute` 串起来。
注意 `${step:sX}` 可把上一步输出传给下一步（状态传递），最后一步输出即汇总。

```bash
curl -s -X POST http://localhost:8081/api/agent-platform/tasks/execute \
  -H "Content-Type: application/json" \
  -d '{
    "maxSteps": 4,
    "steps": [
      {"stepId":"s1","toolName":"milkyway_rise","args":{"latitude":39.9,"longitude":116.4,"date":"2026-06-20"},"required":true},
      {"stepId":"s2","toolName":"light_pollution","args":{"latitude":39.9,"longitude":116.4},"required":true},
      {"stepId":"s3","toolName":"astrophoto_settings","args":{"focalLength":24,"aperture":2.8,"bortle":3},"required":true},
      {"stepId":"s4","toolName":"result_summary","args":{"searchResult":"${step:s1}","imageResult":"${step:s3}"},"required":true}
    ]
  }'
```
**跑通标志**：返回 `success:true`、`executedSteps:4`，`summary` 是 LLM 把"银河升起时刻 + 拍摄参数"
整合后的拍摄方案；`results[]` 里每步 `success:true`。

> 前端对应入口：**Agent 工作台 → 自定义编排** tab，把上面 JSON 贴进去点"运行编排"，
> 会以 pipeline 卡片逐步展示。

---

## 5. 嵌入「流程」并跑通 —— 路径 B：Dify 画布（一句话生成 + 原生工具）

### B1. 让 Dify 能调到你的能力（二选一）

**(a) OpenAPI 导入（快照）**：Dify 控制台 → 工具 → 自定义 → 从 OpenAPI Schema 导入：
```
http://host.docker.internal:8081/api/agent-platform/openapi.json   # 工具
http://host.docker.internal:8081/api/skills/openapi.json           # 技能
```
> Dify 在容器内，URL 必须用 `host.docker.internal`，不能 `localhost`。新增能力后要回 Dify **重新导入**才更新。

**(b) MCP（动态，推荐）**：在 Dify 注册本服务 MCP 端点（需后端已带 mcp-server，见仓库改动）。
注册一次后，新增能力**重启后端**即在 Dify 刷新可见，无需重新导入。
MCP SSE 端点（context-path 在 `/api` 下）：
```
http://host.docker.internal:8081/api/sse
```
> 端点以后端启动日志里 MCP server 实际打印的路径为准。

### B2. 一句话生成工作流 → 落到 Dify 画布

前端 **工作流生产** 页（iframe 的 dify-builder Gradio，`http://localhost:7861`）里输入：
> "拍银河的工作流：先算今晚银河升起时刻和光污染，再给出相机拍摄参数，最后汇总成拍摄计划"

dify-builder 会自动生成 Dify Workflow DSL 并导入 Dify，**画布可见**。其中调用你能力的节点：
- 若用 (a)，表现为调用导入的自定义工具 / 或 HTTP Request 节点；
- 若用 (b)，表现为 MCP 工具节点。

### B3. 在 Dify 跑通
打开 Dify 画布 → 运行该 workflow → 输入地点/日期 → 看到 `astrophoto_settings` 节点输出拍摄参数，
末节点汇总成完整"星空拍摄计划"。**跑通标志**：workflow run 成功，输出含快门/ISO/光圈建议。

---

## 6. 端到端验收清单（一条龙）

| 步骤 | 看什么 | 通过标志 |
|---|---|---|
| 写能力 | 新增一个 `@Component` 类 | `mvnw package` BUILD SUCCESS |
| 重启 | `docker compose up -d --force-recreate vs-ai-agent` | `/api/agent-platform/tools` HTTP 200 |
| 注册 | `GET /agent-platform/tools` | 列表含 `astrophoto_settings` |
| 单点 | `POST /tools/astrophoto_settings/execute` | `data.success=true`，output 有快门/ISO |
| 本地编排 | `POST /tasks/execute`（4 步） | `success=true`，`summary` 含拍摄方案 |
| OpenAPI | `GET /agent-platform/openapi.json` | paths 里有该工具 |
| Dify 导入/MCP | Dify 工具面板 | 能看到并拖进画布 |
| Dify 跑通 | workflow run | 输出含拍摄参数，末节点汇总 |

---

## 7. 排障速查
- **工具没出现**：忘了 `@Component`，或没重启后端（改 Java 必须 `package`+重建镜像，不是热更）。
- **Dify 调不到（连接超时）**：URL 写了 `localhost`，应为 `host.docker.internal`；或 Dify/后端不在可达网络。
- **本地编排某步失败**：`required:true` 的步失败会中断；先用单点 `/execute` 把每个工具单测过。
- **`${step:sX}` 没替换**：占位符格式必须是 `${step:s1}` 这种，且引用的步要在前面、已成功。
- **MCP 看不到能力**：确认后端启动日志有 MCP server 注册工具的输出；端点路径以日志为准。
