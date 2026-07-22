---
name: moto-gear-checklist
displayName: 摩旅装备清单
description: 按季节与天数生成摩托旅行的护具、衣物、工具、电子与证件打包清单
version: 1.0.0
tags:
  - motorcycle
  - travel
  - checklist
  - 摩旅
  - 摩托旅行
  - 装备
  - 打包清单
  - 雨季骑行
inputs:
  - name: season
    type: string
    description: 季节：春/夏/秋/冬 或 雨季
    required: true
  - name: days
    type: number
    description: 天数，默认 3
    required: false
    defaultValue: "3"
outputs:
  - name: checklist
    type: string
    description: 打包清单
steps:
  - name: 识别季节
    description: 解析 season，选出该季节的专项装备（冬季保暖 / 夏季透气 / 雨季防水 / 通用分层）
  - name: 组装基础清单
    description: 拼装护具、随车工具、电子设备、随身证件四大固定模块
  - name: 按天数算衣物
    description: 按 days 计算速干衣套数与洗漱/药品/雨具用量
  - name: 汇总输出
    description: 合并季节专项 + 基础模块 + 衣物，输出成结构化打包清单
examples:
  - 夏季 5 天摩旅的装备清单
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
  profile: functional
  successCriteria:
    - 输出护具、衣物、工具、电子设备和证件清单
    - 根据季节和天数调整装备
  hardConstraints:
    - 不调用外部网络
    - 不写入本地文件
  goldenCaseTags: [normal, boundary]
  attributionStages: [execution, integration]
sourceType: LOCAL
---

# 摩旅装备清单 Skill

按**季节**与**天数**生成一份摩托旅行打包清单。它不是查询某个外部数据，而是一段**规则化的内部过程**：
先按季节挑出专项装备，再拼上护具/工具/电子/证件四个固定模块，最后按天数算衣物用量，汇总成清单。

## 内部步骤（纯内部计算，不调用外部工具）

1. **识别季节** → 冬季加保暖/电加热、夏季透气/防晒、雨季两件套雨衣/防锈、其余给可拆卸内胆分层。
2. **组装基础清单** → 护具(头盔/护甲/手套/骑行靴/护膝)、工具(补胎+打气/随车工具)、电子(支架车充/充电宝/记录仪)、证件(身份证/驾照/行驶证/保险单)。
3. **按天数算衣物** → 速干衣按天数准备（上限 4 套）+ 洗漱、常用药、雨具。
4. **汇总输出** → 合并以上模块为一份可直接照着打包的清单。

## 输入

- `season`：春 / 夏 / 秋 / 冬 / 雨季
- `days`：行程天数（默认 3）

## 维护人

- 默认：repo owner
