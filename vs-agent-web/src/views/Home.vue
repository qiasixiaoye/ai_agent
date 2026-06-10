<template>
  <main class="home-page">
    <header class="shell-header">
      <div>
        <p class="eyebrow">AI Agent Platform</p>
        <h1>智能体研发控制台</h1>
        <p class="subtitle">按“准备资源 → 生成编排 → 连接运行 → 评测交付”的顺序组织入口。</p>
      </div>
      <div class="runtime-panel">
        <span :class="['status-dot', summaryLoading ? 'pending' : backendOk ? 'ok' : 'fail']"></span>
        <div>
          <strong>{{ summaryLoading ? '检查中' : backendOk ? '后端可用' : '后端异常' }}</strong>
          <span>{{ apiBase }}</span>
        </div>
      </div>
    </header>

    <section class="layout">
      <aside class="flow-rail" aria-label="研发阶段">
        <button
          v-for="phase in phases"
          :key="phase.id"
          type="button"
          :class="['phase-button', { active: selectedPhase === phase.id }]"
          @click="selectedPhase = phase.id"
        >
          <span class="phase-index">{{ phase.index }}</span>
          <span>
            <strong>{{ phase.title }}</strong>
            <small>{{ phase.summary }}</small>
          </span>
        </button>
      </aside>

      <section class="workspace">
        <div class="workspace-head">
          <div>
            <span class="phase-label">阶段 {{ activePhase.index }}</span>
            <h2>{{ activePhase.title }}</h2>
            <p>{{ activePhase.description }}</p>
          </div>
          <router-link class="primary-action" :to="activePhase.primary.path">
            {{ activePhase.primary.label }}
          </router-link>
        </div>

        <div class="metrics-row" aria-label="平台状态">
          <article v-for="item in activePhase.metrics" :key="item.label" class="metric">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </article>
        </div>

        <div class="module-list">
          <router-link
            v-for="module in activePhase.modules"
            :key="module.path"
            :to="module.path"
            class="module-row"
            :style="{ '--accent': module.accent }"
          >
            <span class="module-mark">{{ module.mark }}</span>
            <span class="module-copy">
              <strong>{{ module.title }}</strong>
              <small>{{ module.description }}</small>
            </span>
            <span class="module-state">{{ module.state }}</span>
          </router-link>
        </div>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useHead } from '@vueuse/head'
import {
  difyHealth,
  listEvalSuites,
  listKbDocuments,
  listPlatformTools,
  listSkills,
  listWorkflows
} from '../services/api'

useHead({
  title: 'AI Agent Platform - 智能体研发控制台',
  meta: [
    {
      name: 'description',
      content: '智能体研发控制台，按资源准备、工作流编排、运行连接和评测交付组织核心入口。'
    }
  ]
})

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081/api'
const selectedPhase = ref('prepare')
const summaryLoading = ref(true)
const summary = ref({
  workflows: '-',
  skills: '-',
  tools: '-',
  documents: '-',
  evalSuites: '-',
  dify: '未配置'
})

const backendOk = computed(() =>
  [summary.value.workflows, summary.value.skills, summary.value.tools, summary.value.evalSuites]
    .some((value) => value !== '-')
)

const phases = computed(() => [
  {
    id: 'prepare',
    index: '01',
    title: '准备资源',
    summary: '知识、技能、工具',
    description: '先整理可被智能体使用的材料与能力，后续编排会直接依赖这些资源。',
    primary: { label: '管理知识库', path: '/knowledge-base' },
    metrics: [
      { label: '文档', value: summary.value.documents },
      { label: 'Skills', value: summary.value.skills },
      { label: '工具', value: summary.value.tools }
    ],
    modules: [
      module('KB', '知识库管理', '上传、解析、切块、向量化和索引重建。', '/knowledge-base', '#15803d', `${summary.value.documents} documents`),
      module('SK', 'Skill 平台', '扫描 SKILL.md，查看 Schema 并执行技能。', '/skills', '#b45309', `${summary.value.skills} skills`),
      module('AP', 'Agent 平台', '注册工具并配置多步任务编排。', '/agent-platform', '#be123c', `${summary.value.tools} tools`)
    ]
  },
  {
    id: 'compose',
    index: '02',
    title: '生成编排',
    summary: '一句话到 DSL',
    description: '把自然语言需求转成可执行 Workflow，再查看节点、导出 Dify YAML。',
    primary: { label: '生成工作流', path: '/workflow' },
    metrics: [
      { label: '工作流', value: summary.value.workflows },
      { label: 'Skills', value: summary.value.skills },
      { label: 'Dify', value: summary.value.dify }
    ],
    modules: [
      module('WF', '一句话工作流', '自然语言生成 DSL，支持执行、评测与 Dify YAML 导出。', '/workflow', '#2563eb', `${summary.value.workflows} workflows`),
      module('SK', 'Skill Schema', '生成器会参考已注册 Skill，避免杜撰工具名。', '/skills', '#b45309', 'schema source'),
      module('DF', 'Dify YAML', '将内部 WorkflowDef 导出为 Dify 可导入结构。', '/dify', '#0891b2', summary.value.dify)
    ]
  },
  {
    id: 'run',
    index: '03',
    title: '连接运行',
    summary: '助手、Manus、Dify',
    description: '选择交互入口：普通助手、自动推理智能体，或通过独立 Dify Bridge 调用外部工作流。',
    primary: { label: '打开 AI 助手', path: '/assistant-app' },
    metrics: [
      { label: '后端', value: backendOk.value ? '可用' : '-' },
      { label: 'Dify', value: summary.value.dify },
      { label: '工具', value: summary.value.tools }
    ],
    modules: [
      module('AS', 'AI 助手', '面向普通对话与 RAG 的在线助手入口。', '/assistant-app', '#0f766e', 'chat'),
      module('MS', 'Manus 智能体', '多步推理与工具自动选择的智能体执行台。', '/manus-app', '#7c3aed', 'agent'),
      module('DF', 'Dify Bridge', '通过独立服务调用 Dify Workflow，并导出本地 Skill OpenAPI。', '/dify', '#0891b2', summary.value.dify)
    ]
  },
  {
    id: 'validate',
    index: '04',
    title: '评测交付',
    summary: '用例、Judge、报告',
    description: '对助手或工作流跑评测集，确认输出满足关键字或 LLM-as-Judge 标准。',
    primary: { label: '运行评测', path: '/eval' },
    metrics: [
      { label: '评测集', value: summary.value.evalSuites },
      { label: '工作流', value: summary.value.workflows },
      { label: 'Skills', value: summary.value.skills }
    ],
    modules: [
      module('EV', 'Eval 评测', '运行 YAML 评测集，支持 Keyword 与 LLM Judge。', '/eval', '#4338ca', `${summary.value.evalSuites} suites`),
      module('WF', '工作流评测', '生成后的 Workflow 可直接进入 Eval Harness。', '/workflow', '#2563eb', 'workflow runner'),
      module('AS', '助手评测', '复用 Assistant runner 评估真实对话输出。', '/assistant-app', '#0f766e', 'assistant runner')
    ]
  }
])

const activePhase = computed(() =>
  phases.value.find((item) => item.id === selectedPhase.value) || phases.value[0]
)

onMounted(async () => {
  const [workflows, skills, tools, documents, suites, dify] = await Promise.allSettled([
    listWorkflows(),
    listSkills(),
    listPlatformTools(),
    listKbDocuments(20),
    listEvalSuites(),
    difyHealth()
  ])

  summary.value = {
    workflows: countValue(workflows),
    skills: countValue(skills),
    tools: countValue(tools),
    documents: countValue(documents),
    evalSuites: countValue(suites),
    dify: dify.status === 'fulfilled' && dify.value?.configured ? '已配置' : '未配置'
  }
  summaryLoading.value = false
})

const module = (mark, title, description, path, accent, state) => ({
  mark,
  title,
  description,
  path,
  accent,
  state
})

const countValue = (settled) => {
  if (settled.status !== 'fulfilled') return '-'
  if (Array.isArray(settled.value)) return settled.value.length
  return settled.value == null ? '-' : '1'
}
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: #f6f8fb;
  color: #172033;
  padding: 28px;
}

.shell-header {
  max-width: 1240px;
  margin: 0 auto 20px;
  min-height: 134px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  border-bottom: 1px solid #dbe3ef;
}

.eyebrow {
  margin: 0 0 8px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
  text-transform: uppercase;
}

h1,
h2 {
  margin: 0;
}

h1 {
  font-size: 36px;
  line-height: 1.15;
}

h2 {
  font-size: 28px;
}

.subtitle,
.workspace-head p {
  color: #5f6f86;
}

.subtitle {
  margin: 12px 0 0;
  font-size: 16px;
}

.runtime-panel {
  min-width: 248px;
  display: flex;
  align-items: center;
  gap: 12px;
  background: #ffffff;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  padding: 14px 16px;
  box-shadow: 0 8px 26px rgba(31, 45, 61, 0.06);
}

.runtime-panel strong,
.runtime-panel span:last-child {
  display: block;
}

.runtime-panel span:last-child {
  color: #64748b;
  font-size: 12px;
  margin-top: 3px;
  word-break: break-all;
}

.status-dot {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  flex: 0 0 auto;
  background: #94a3b8;
}

.status-dot.ok {
  background: #16a34a;
}

.status-dot.fail {
  background: #dc2626;
}

.status-dot.pending {
  background: #f59e0b;
}

.layout {
  max-width: 1240px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 296px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.flow-rail {
  background: #ffffff;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  padding: 10px;
  box-shadow: 0 8px 26px rgba(31, 45, 61, 0.05);
}

.phase-button {
  width: 100%;
  display: grid;
  grid-template-columns: 42px 1fr;
  gap: 10px;
  align-items: center;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #172033;
  padding: 12px;
  text-align: left;
  cursor: pointer;
}

.phase-button + .phase-button {
  margin-top: 4px;
}

.phase-button:hover,
.phase-button.active {
  background: #eef4ff;
  border-color: #c9dafc;
}

.phase-index {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #e8eef8;
  color: #2563eb;
  font-weight: 800;
}

.phase-button strong,
.phase-button small {
  display: block;
}

.phase-button small {
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
}

.workspace {
  min-height: 568px;
  background: #ffffff;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  padding: 22px;
  box-shadow: 0 8px 26px rgba(31, 45, 61, 0.05);
}

.workspace-head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
  padding-bottom: 18px;
  border-bottom: 1px solid #e5ebf3;
}

.phase-label {
  display: inline-block;
  margin-bottom: 8px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.workspace-head p {
  margin: 10px 0 0;
  line-height: 1.6;
}

.primary-action {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  min-height: 38px;
  color: #ffffff;
  background: #2563eb;
  text-decoration: none;
  border-radius: 7px;
  padding: 0 14px;
  font-weight: 700;
}

.metrics-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(120px, 1fr));
  gap: 10px;
  margin: 18px 0;
}

.metric {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
  min-height: 72px;
}

.metric span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.metric strong {
  display: block;
  margin-top: 8px;
  font-size: 22px;
}

.module-list {
  display: grid;
  gap: 10px;
}

.module-row {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  color: inherit;
  text-decoration: none;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-left: 4px solid var(--accent);
  border-radius: 8px;
  padding: 14px;
  transition: background 0.16s ease, border-color 0.16s ease;
}

.module-row:hover {
  background: #f8fafc;
  border-color: color-mix(in srgb, var(--accent), #dbe3ef 55%);
}

.module-mark {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: color-mix(in srgb, var(--accent), white 88%);
  color: var(--accent);
  font-weight: 800;
}

.module-copy {
  min-width: 0;
}

.module-copy strong,
.module-copy small {
  display: block;
}

.module-copy small {
  margin-top: 4px;
  color: #64748b;
  line-height: 1.5;
}

.module-state {
  color: #475569;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 5px 8px;
  font-size: 12px;
  white-space: nowrap;
}

@media (max-width: 920px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .flow-rail {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .phase-button + .phase-button {
    margin-top: 0;
  }
}

@media (max-width: 680px) {
  .home-page {
    padding: 18px;
  }

  .shell-header,
  .workspace-head {
    flex-direction: column;
    align-items: stretch;
  }

  h1 {
    font-size: 28px;
  }

  h2 {
    font-size: 24px;
  }

  .flow-rail,
  .metrics-row {
    grid-template-columns: 1fr;
  }

  .module-row {
    grid-template-columns: 42px minmax(0, 1fr);
  }

  .module-state {
    grid-column: 2;
    justify-self: start;
  }
}
</style>
