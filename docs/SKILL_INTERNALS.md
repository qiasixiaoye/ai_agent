# Skill 内部结构：从"和工具没区别"到"看得见、调得到"

> 解决一个真实问题：**之前的 Skill 退化成了"一个函数 + 一组参数",和 Tool/接口没区别——因为 SKILL.md 的正文(真正的技能知识)被解析器丢弃了。**
> 本次改造让 Skill 重新有"内部结构"(操作手册 + 有序步骤),并打通 agent 实际调用。

---

## 1. 问题：Skill 为什么看起来像个普通工具？

改造前的链路：

```
SKILL.md ──► SkillMdParser ──► 只取 YAML 头(name/inputs/...) 当元数据
   │                            ❌ front-matter 之后的 markdown 正文被整段丢弃
   ▼
Skill = metadata() + execute()   ←── 这不就是个带参数的函数吗？和 Tool 一模一样
   │
   └──► SkillCallbackAdapter ──► 包成 Spring AI ToolCallback ──► 对 agent 来说就是"又一个工具"
```

所以"Skill"这个抽象**名存实亡**:它本该携带的"操作手册、内部步骤、对其它工具的编排"全都不可见。

---

## 2. Skill 到底应该比 Tool 多什么？

```
Tool   (AgentTool)     一次原子调用：execute(args) → result。是一个「点」。

Skill  (Skill)         一段过程 + 知识：
                       ① instructions —— SKILL.md 正文，技能的「操作手册」
                       ② steps[]      —— 有序内部步骤，每步可 uses: tool:xxx 调别的工具
                       ③ execute      —— 按 steps 实际编排多个工具，再综合输出
                       是一条「线」，可以是对多个 Tool 的可复用编排 + 领域知识。
```

---

## 3. 改造点（本次实现）

### 3.1 保留 SKILL.md 正文 + 解析步骤

- `SkillMetadata` 新增两个字段：
  - `instructions`（String）= SKILL.md front-matter **之后的 markdown 正文**（操作手册）；
  - `steps`（`List<SkillStep>`）= 有序内部步骤。
- 新增 `SkillStep(name, uses, description)`：`uses` 形如 `tool:milkyway_rise`，指明该步调用哪个能力。
- `SkillMdParser`：
  - 不再丢弃正文 —— 截取闭合 `---` 之后的内容存进 `instructions`；
  - 解析 front-matter 的 `steps:`（支持字符串或 `{name,uses,description}` 对象）。

### 3.2 内部结构对外可见

`GET /api/skills/{name}` 的 `SkillDetailVO` 现在多返回 `instructions` + `steps`：

```jsonc
{
  "name": "astro-shoot-plan",
  "displayName": "银河拍摄计划",
  "steps": [
    { "name": "银河升起时间", "uses": "tool:milkyway_rise", "description": "确定拍摄时间窗" },
    { "name": "光污染评估",   "uses": "tool:light_pollution", "description": "判断是否换更暗机位" },
    { "name": "夜间云量",     "uses": "tool:cloud_cover", "description": "决定能否成行/改期" },
    { "name": "综合成拍摄计划", "uses": null, "description": "综合三步结果" }
  ],
  "instructions": "# 银河拍摄计划 Skill\n这是一个结构化技能……"   // SKILL.md 正文
}
```

→ **不再是黑盒**：调用方/前端能直接看到这个技能"内部分几步、每步调什么工具、有什么操作手册"。

### 3.3 一个真正有内部结构的示例 Skill

新增 `AstroShootPlanSkill`（`astro-shoot-plan`）——它**不是原子工具,而是对多个工具的内部编排**：

```
astro-shoot-plan.execute(lat, lon, date)
   │  遍历 steps（steps 既是文档，也是执行计划）
   ├─ step1 uses tool:milkyway_rise   ──► ToolExecutionService.executeByName(...)
   ├─ step2 uses tool:light_pollution ──► ToolExecutionService.executeByName(...)
   ├─ step3 uses tool:cloud_cover     ──► ToolExecutionService.executeByName(...)
   └─ step4 综合三步结果 ──► 输出一份拍摄计划
```

关键：`doExecute` **遍历声明的 `STEPS`、对每个 `uses: tool:*` 的步骤实际发起工具调用**——
所以"内部结构"不是装饰,而是真正驱动执行的计划。这清楚地展示了 **Skill = 工具之上的编排层**。

---

## 4. 让 agent 实际调用到 Skill

改造前,Skill 只通过 MCP 暴露给 Dify;**本地 agent 的工具集(`AssistantApp.allTools`)根本没装 Skill**。本次打通：

- `AssistantApp` 注入 `SkillRegistry`，新增 `doChatWithSkills(message, chatId)`：
  把所有已注册 Skill 用 `SkillCallbackAdapter` 适配成 `ToolCallback` 注入 ChatClient，**由 LLM 自主决定调用哪个 Skill**。
- 新增端点：`GET /api/ai/assistant_app/chat_skills/sync?message=...&chatId=...`

调用示例：

```
GET /api/ai/assistant_app/chat_skills/sync
    ?message=帮我做北京 2026-06-25 的银河拍摄计划，纬度39.9 经度116.4&chatId=demo
```

链路：

```
用户问 ──► LLM 看到工具列表里有 astro-shoot-plan ──► 决定调用它
        ──► Skill 内部再依次编排 milkyway_rise / light_pollution / cloud_cover
        ──► 综合成拍摄计划返回
```

> 三种调用通道现在统一了：**本地 agent 对话(`doChatWithSkills`)、Dify 经 MCP、以及 `POST /api/skills/{name}/execute` 直接执行**——都能调到同一个 Skill。

---

## 5. 涉及文件

| 改动 | 文件 |
|------|------|
| Skill 元数据加 `instructions`/`steps` | `skill/SkillMetadata.java`、新增 `skill/SkillStep.java` |
| 解析正文 + steps（不再丢弃正文） | `skill/loader/SkillMdParser.java` |
| 内部结构对外可见 | `skill/controller/SkillDetailVO.java`（`GET /api/skills/{name}`） |
| 结构化示例 Skill（内部编排工具） | `skill/builtin/AstroShootPlanSkill.java` + `resources/skills/astro-shoot-plan/SKILL.md` |
| agent 实际调用 Skill | `app/AssistantApp.java#doChatWithSkills` + `controller/AiController.java`（`/ai/assistant_app/chat_skills/sync`） |

---

## 6. 验证方式（部署后手动）

1. 看内部结构：`GET /api/skills/astro-shoot-plan` → 返回里有 `steps`（4 步、前 3 步带 `uses`）+ `instructions`（SKILL.md 正文）。
2. 直接执行：`POST /api/skills/astro-shoot-plan/execute`，body `{"latitude":39.9,"longitude":116.4,"date":"2026-06-25"}` → 返回 `plan`（含三个工具的真实输出 + 综合建议）。
3. agent 调用：`GET /api/ai/assistant_app/chat_skills/sync?message=帮我做北京今晚的银河拍摄计划，纬度39.9 经度116.4 日期2026-06-25&chatId=demo` → LLM 自主选中并执行该 Skill。
4. 审计：以上每次执行都在 `/api/observability` 有 requestId 可回查。
