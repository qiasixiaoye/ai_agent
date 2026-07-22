# Skill 命中闭环

## 目标

在把 Skill 交给大模型之前，先完成可复现、可解释的候选召回与阈值拒识，避免 Skill 数量增加后把全部工具 Schema 注入上下文。

## 运行链路

```text
SKILL.md / Java fallback metadata
        ↓ SkillScanner
SkillRegistry
        ↓ WeightedSkillRouter
名称 + 标签 + 示例 + 描述 + 输入/步骤的加权召回
        ↓ threshold=0.24, topK=3
命中：仅注入 Top-K ToolCallback ──→ LLM 选择并执行 ──→ TOOL 日志
拒识：不注入 Skill，回退普通对话
        ↓
skill_route 阶段日志（候选、分数、理由、阈值、最终选择）
```

`WeightedSkillRouter` 不调用额外大模型，因此结果稳定、成本可控。字符 bigram Dice 用于处理中英文混合表达，领域同义词放在各 Skill 的 `tags` 中维护。

## 可验证接口

- `GET /api/skills/route?query=帮我规划北京郊区今晚的银河摄影`
  - 返回候选 Skill、分数、命中理由、阈值和选择结果。
- `GET /api/skills/route/evaluate`
  - 执行 `classpath:eval/skill-routing-cases.yaml`，返回准确率、误命中和漏命中。
- `GET /api/ai/assistant_app/chat_skills/sync?message=...&chatId=...`
  - 先路由，再只向模型注入命中的 Skill；Skill 实际执行会写入 TOOL 阶段日志。

前端 `Skills` 页面提供路由预览和离线评测按钮，可直接演示闭环。

## 当前评测边界

版本化评测集目前包含 10 条样例：4 类已注册 Skill 各 2 条，以及 2 条无关请求。当前测试要求 10/10、误命中 0、漏命中 0。

该结果仅证明当前小型评测集上的回归正确性，不能外推为真实业务流量准确率。增加 Skill 或调整元数据时应同步扩充评测集。

## 测试

```powershell
.\mvnw.cmd "-Dtest=WeightedSkillRouterTest,SkillRoutingEvalServiceTest" test
npm.cmd run build
```

## 简历表述

> 设计可解释的 Skill 路由闭环，基于名称、标签、示例和结构元数据进行加权候选召回，通过 Top-K 与阈值拒识仅向模型注入相关 Skill；将路由证据、模型调用和工具执行接入统一链路日志，并建设版本化离线评测集统计准确率、误命中与漏命中。

