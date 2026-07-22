---
name: coffee-tasting-notes
displayName: 咖啡品鉴笔记
description: 按豆子产地与烘焙度生成风味轮廓、酸苦平衡与建议冲煮方式
version: 1.0.0
tags:
  - coffee
  - tasting
  - 咖啡
  - 咖啡豆
  - 风味
  - 冲煮
  - 耶加雪菲
  - 哥伦比亚
inputs:
  - name: beans
    type: string
    description: 豆子产地/名称，如 埃塞俄比亚耶加雪菲 / 哥伦比亚
    required: true
  - name: roast
    type: string
    description: 烘焙度：浅/中/深，默认中
    required: false
    defaultValue: "中"
outputs:
  - name: notes
    type: string
    description: 风味品鉴笔记
steps:
  - name: 匹配产地风味
    description: 解析 beans 关键词，匹配该产地的风味轮廓（耶加/哥伦比亚/曼特宁/巴西…）
  - name: 按烘焙度调整
    description: 按 roast(浅/中/深) 调整酸苦平衡、醇厚与建议冲煮方式
  - name: 综合成笔记
    description: 合并产地风味 + 烘焙建议，输出一段品鉴笔记
examples:
  - 耶加雪菲 浅烘 的风味笔记
timeoutMs: 5000
security:
  riskLevel: low
  permissionScopes: [local_compute]
  sideEffects: none
  dataSensitivity: user_input
  requiresConfirmation: false
  reviewStatus: reviewed
  lifecycleStatus: active
evaluation:
  profile: creative
  successCriteria:
    - 输出风味轮廓、酸苦平衡和冲煮建议
    - 内容符合用户输入的产地和烘焙程度
  hardConstraints:
    - 不调用外部网络
    - 不写入本地文件
  goldenCaseTags: [normal, boundary]
  attributionStages: [execution, integration]
sourceType: LOCAL
---

# 咖啡品鉴笔记 Skill

按**豆子产地**与**烘焙度**生成一段风味品鉴笔记。它是一段**规则化的内部过程**：
先用产地关键词匹配风味轮廓，再按烘焙度修正酸苦/醇厚与冲煮建议，最后综合成笔记。

## 内部步骤（纯内部计算，不调用外部工具）

1. **匹配产地风味** → 耶加雪菲(花香柑橘莓果/高酸)、哥伦比亚(焦糖坚果红苹果/均衡)、曼特宁(草本黑巧木质/低酸厚体)、巴西(坚果可可奶油/适合意式)，其余给均衡甜感。
2. **按烘焙度调整** → 浅烘放大酸质花果香(手冲 92-94℃)、深烘强调焦糖苦甜醇厚(意式/法压)、中烘酸苦平衡两相宜。
3. **综合成笔记** → 合并产地风味 + 烘焙建议，输出可读的品鉴笔记。

## 输入

- `beans`：豆子产地/名称
- `roast`：浅 / 中 / 深（默认中）

## 维护人

- 默认：repo owner
