---
name: astro-shoot-plan
displayName: 银河拍摄计划
description: 给定经纬度与日期，内部依次编排银河升起/光污染/云量三个工具，综合成一份银河拍摄计划
version: 1.0.0
tags:
  - astro
  - photography
  - composite
  - 银河
  - 银河摄影
  - 星空摄影
  - 拍摄计划
inputs:
  - name: latitude
    type: number
    description: 机位纬度，如 39.9
    required: true
  - name: longitude
    type: number
    description: 机位经度，如 116.4
    required: true
  - name: date
    type: string
    description: 拍摄日期 yyyy-MM-dd
    required: true
outputs:
  - name: plan
    type: string
    description: 综合拍摄计划
steps:
  - name: 银河升起时间
    uses: tool:milkyway_rise
    description: 算出银河核心升起/中天/落下时间与方位，确定拍摄时间窗
  - name: 光污染评估
    uses: tool:light_pollution
    description: 估算机位 Bortle 等级，判断是否需要更暗的备选点
  - name: 夜间云量
    uses: tool:cloud_cover
    description: 查当晚云量预报，决定能否成行 / 是否改期
  - name: 综合成拍摄计划
    description: 把上面三步结果综合成时间窗 + 机位 + 风险提示
examples:
  - 纬度39.9 经度116.4 2026-06-25 的银河拍摄计划
timeoutMs: 20000
sourceType: LOCAL
---

# 银河拍摄计划 Skill

这是一个**结构化技能（composite skill）**：它本身不是一个原子工具，而是一段**有序的操作过程**，
内部会按下面的步骤**依次编排调用多个已注册的后端工具**，再把结果综合成一份可执行的拍摄计划。
这正是「Skill」区别于「单个工具/接口」的地方——工具是一次函数调用，技能是一套带知识的流程。

## 内部步骤（既是文档，也是执行计划）

1. **银河升起时间** → 调用 `tool:milkyway_rise`，得到银河核心升起/中天/落下时间与方位，确定**拍摄时间窗**。
2. **光污染评估** → 调用 `tool:light_pollution`，估算机位 Bortle 等级；等级偏高时建议更暗的备选机位。
3. **夜间云量** → 调用 `tool:cloud_cover`，查当晚云量预报，决定**能否成行 / 是否改期**。
4. **综合成拍摄计划** → 不调工具，按下面的规则把三步结果综合输出。

> 执行时 `AstroShootPlanSkill.doExecute` 会**遍历上述步骤**、对每个 `uses: tool:*` 的步骤实际发起工具调用，
> 因此这里的步骤不是摆设，而是真正驱动执行的计划。

## 综合规则

- 在「银河升起时间」给出的时间窗内拍摄，避开月光与中天前后的城市方位；
- 若「光污染评估」Bortle ≥ 5，优先换到更暗的备选机位；
- 若「夜间云量」> 50%，建议改期或准备备用题材（地景 / 星轨）。

## 输入

- `latitude` / `longitude`：机位经纬度
- `date`：拍摄日期 `yyyy-MM-dd`

## 维护人

- 默认：repo owner
