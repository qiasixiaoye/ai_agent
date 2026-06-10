<template>
  <main class="home-page">
    <section class="topbar">
      <div>
        <p class="eyebrow">AI Agent Platform</p>
        <h1>智能体研发工作台</h1>
        <p class="subtitle">助手、知识库、Skill、工作流、Dify、Eval 与可观测性统一入口</p>
      </div>
      <div class="runtime">
        <span :class="['status-dot', summaryLoading ? 'pending' : backendOk ? 'ok' : 'fail']"></span>
        <div>
          <strong>{{ summaryLoading ? '检查中' : backendOk ? '后端可用' : '后端异常' }}</strong>
          <span>{{ apiBase }}</span>
        </div>
      </div>
    </section>

    <section class="metrics-grid" aria-label="平台概览">
      <article v-for="item in metrics" :key="item.label" class="metric">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
    </section>

    <section class="workspace-grid" aria-label="能力入口">
      <router-link
        v-for="module in modules"
        :key="module.path"
        :to="module.path"
        class="module-card"
        :style="{ '--accent': module.accent }"
      >
        <div class="module-mark" aria-hidden="true">{{ module.mark }}</div>
        <div class="module-main">
          <div class="module-head">
            <h2>{{ module.title }}</h2>
            <span>{{ module.loop }}</span>
          </div>
          <p>{{ module.description }}</p>
          <div class="module-meta">
            <span v-for="tag in module.tags" :key="tag">{{ tag }}</span>
          </div>
        </div>
      </router-link>
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
  listWorkflows,
  querySessionRequests
} from '../services/api'

useHead({
  title: 'AI Agent Platform - 智能体研发工作台',
  meta: [
    {
      name: 'description',
      content: 'Spring AI 智能体平台工作台，覆盖助手、Manus、知识库、Skill、工作流、Dify、Eval 与可观测性。'
    }
  ]
})

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081/api'
const summaryLoading = ref(true)
const summary = ref({
  workflows: '-',
  skills: '-',
  tools: '-',
  documents: '-',
  evalSuites: '-',
  dify: '未知',
  traces: '-'
})

const backendOk = computed(() =>
  [summary.value.workflows, summary.value.skills, summary.value.tools, summary.value.evalSuites]
    .some((value) => value !== '-')
)

const metrics = computed(() => [
  { label: '工作流', value: summary.value.workflows },
  { label: 'Skills', value: summary.value.skills },
  { label: '工具', value: summary.value.tools },
  { label: '文档', value: summary.value.documents },
  { label: '评测集', value: summary.value.evalSuites },
  { label: 'Dify', value: summary.value.dify },
  { label: '默认会话日志', value: summary.value.traces }
])

const modules = [
  {
    title: '一句话工作流',
    loop: 'Workflow',
    path: '/workflow',
    mark: 'WF',
    accent: '#2563eb',
    description: '自然语言生成 DSL，支持执行、评测与 Dify YAML 导出。',
    tags: ['NL to DSL', 'Exec', 'Export']
  },
  {
    title: 'AI 助手',
    loop: 'Assistant',
    path: '/assistant-app',
    mark: 'AS',
    accent: '#0f766e',
    description: '面向普通对话与 RAG 的在线助手入口。',
    tags: ['Chat', 'RAG', 'SSE']
  },
  {
    title: 'Manus 智能体',
    loop: 'Manus',
    path: '/manus-app',
    mark: 'MS',
    accent: '#7c3aed',
    description: '多步推理与工具自动选择的智能体执行台。',
    tags: ['ReAct', 'Tools', 'Plan']
  },
  {
    title: '知识库管理',
    loop: 'KB',
    path: '/knowledge-base',
    mark: 'KB',
    accent: '#15803d',
    description: '上传、解析、切块、向量化和索引重建。',
    tags: ['Upload', 'Vector', 'Index']
  },
  {
    title: 'Skill 平台',
    loop: 'Skills',
    path: '/skills',
    mark: 'SK',
    accent: '#b45309',
    description: '扫描 SKILL.md，在线查看 Schema 并执行技能。',
    tags: ['Registry', 'Schema', 'Run']
  },
  {
    title: 'Agent 平台',
    loop: 'AgentPlatform',
    path: '/agent-platform',
    mark: 'AP',
    accent: '#be123c',
    description: '工具注册中心与可配置任务编排。',
    tags: ['Tools', 'Task', 'Trace']
  },
  {
    title: 'Dify 集成',
    loop: 'Dify',
    path: '/dify',
    mark: 'DF',
    accent: '#0891b2',
    description: '对接 Dify Workflow，同时导出平台 OpenAPI。',
    tags: ['Run', 'OpenAPI', 'Bridge']
  },
  {
    title: 'Eval 评测',
    loop: 'Eval',
    path: '/eval',
    mark: 'EV',
    accent: '#4338ca',
    description: '运行 YAML 评测集，支持 Keyword 与 LLM Judge。',
    tags: ['Suite', 'Judge', 'Report']
  },
  {
    title: '可观测性',
    loop: 'Observability',
    path: '/observability',
    mark: 'OB',
    accent: '#475569',
    description: '按 request、session 与失败时间窗查询执行链路。',
    tags: ['Trace', 'Stages', 'Failures']
  }
]

onMounted(async () => {
  const [
    workflows,
    skills,
    tools,
    documents,
    suites,
    dify,
    traces
  ] = await Promise.allSettled([
    listWorkflows(),
    listSkills(),
    listPlatformTools(),
    listKbDocuments(20),
    listEvalSuites(),
    difyHealth(),
    querySessionRequests('default', 20)
  ])

  summary.value = {
    workflows: countValue(workflows),
    skills: countValue(skills),
    tools: countValue(tools),
    documents: countValue(documents),
    evalSuites: countValue(suites),
    dify: dify.status === 'fulfilled' && dify.value?.configured ? '已配置' : '未配置',
    traces: countValue(traces)
  }
  summaryLoading.value = false
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
  background:
    linear-gradient(180deg, rgba(238, 242, 247, 0.9), rgba(248, 250, 252, 1)),
    #f8fafc;
  color: #172033;
  padding: 28px;
}

.topbar {
  max-width: 1240px;
  margin: 0 auto 18px;
  min-height: 148px;
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

h1 {
  margin: 0;
  font-size: 38px;
  line-height: 1.15;
}

.subtitle {
  margin: 12px 0 0;
  color: #5f6f86;
  font-size: 16px;
}

.runtime {
  min-width: 244px;
  display: flex;
  align-items: center;
  gap: 12px;
  background: #ffffff;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  padding: 14px 16px;
  box-shadow: 0 8px 26px rgba(31, 45, 61, 0.06);
}

.runtime strong,
.runtime span:last-child {
  display: block;
}

.runtime span:last-child {
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

.metrics-grid,
.workspace-grid {
  max-width: 1240px;
  margin: 0 auto;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(120px, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.metric {
  background: #ffffff;
  border: 1px solid #dbe3ef;
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

.workspace-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(260px, 1fr));
  gap: 14px;
}

.module-card {
  min-height: 184px;
  display: grid;
  grid-template-columns: 52px 1fr;
  gap: 14px;
  color: inherit;
  text-decoration: none;
  background: #ffffff;
  border: 1px solid #dbe3ef;
  border-top: 3px solid var(--accent);
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 8px 26px rgba(31, 45, 61, 0.05);
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.module-card:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--accent), #dbe3ef 48%);
  box-shadow: 0 14px 34px rgba(31, 45, 61, 0.1);
}

.module-mark {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: color-mix(in srgb, var(--accent), white 88%);
  color: var(--accent);
  font-weight: 800;
  letter-spacing: 0;
}

.module-main {
  min-width: 0;
}

.module-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.module-head h2 {
  margin: 0;
  font-size: 18px;
}

.module-head span {
  flex: 0 0 auto;
  color: var(--accent);
  font-size: 12px;
  font-weight: 700;
}

.module-card p {
  margin: 12px 0 14px;
  color: #5f6f86;
  line-height: 1.55;
  font-size: 14px;
}

.module-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.module-meta span {
  color: #475569;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 4px 7px;
  font-size: 12px;
}

@media (max-width: 1080px) {
  .metrics-grid {
    grid-template-columns: repeat(4, minmax(120px, 1fr));
  }

  .workspace-grid {
    grid-template-columns: repeat(2, minmax(260px, 1fr));
  }
}

@media (max-width: 720px) {
  .home-page {
    padding: 18px;
  }

  .topbar {
    align-items: stretch;
    flex-direction: column;
    padding-bottom: 18px;
  }

  h1 {
    font-size: 28px;
  }

  .metrics-grid,
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .runtime {
    min-width: 0;
  }
}
</style>
