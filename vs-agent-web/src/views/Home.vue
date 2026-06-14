<template>
  <main class="home-page">
    <header class="hero">
      <div>
        <p class="eyebrow">AI Agent Platform</p>
        <h1>智能体研发控制台</h1>
        <p class="subtitle">多轮对话 + RAG 检索 · Function Calling 工具调用 · 多步骤任务编排 · 自然语言驱动 Workflow Builder</p>
      </div>
      <div class="runtime-panel">
        <span :class="['status-dot', summaryLoading ? 'pending' : backendOk ? 'ok' : 'fail']"></span>
        <div>
          <strong>{{ summaryLoading ? '检查中' : backendOk ? '后端可用' : '后端异常' }}</strong>
          <span>{{ apiBase }}</span>
        </div>
      </div>
    </header>

    <section class="primary-grid">
      <router-link to="/assistant-app" class="primary-card">
        <span class="primary-icon">💬</span>
        <h2>对话助手</h2>
        <p>多轮对话 + RAG 知识检索，支持流式输出与上下文记忆。</p>
        <span class="primary-cta">进入对话 →</span>
      </router-link>

      <router-link to="/agent-platform" class="primary-card featured">
        <span class="primary-icon">🛠</span>
        <h2>Agent 工作台</h2>
        <p>工具调用 + Skills + 多步骤任务编排，内置「银河摄影规划」一键演示。</p>
        <span class="primary-cta">进入工作台 →</span>
        <span class="primary-badge">推荐演示</span>
      </router-link>

      <router-link to="/workflow" class="primary-card">
        <span class="primary-icon">🧬</span>
        <h2>Workflow Builder</h2>
        <p>自然语言 → Workflow IR → 执行 / 评测 / 导出 Dify DSL。</p>
        <span class="primary-cta">进入构建器 →</span>
      </router-link>
    </section>

    <section class="secondary-section">
      <h3 class="secondary-title">能力总览</h3>
      <div class="secondary-grid">
        <AppCard
          v-for="item in secondaryItems"
          :key="item.path"
          clickable
          :title="item.title"
          :subtitle="item.description"
          @click="$router.push(item.path)"
        >
          <template #actions>
            <TagChip :label="item.state" accent />
          </template>
        </AppCard>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useHead } from '@vueuse/head'
import AppCard from '../components/ui/AppCard.vue'
import TagChip from '../components/ui/TagChip.vue'
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
      content: '智能体研发控制台：对话助手、Agent 工作台（工具调用/Skills/编排）、Workflow Builder。'
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
  dify: '未配置'
})

const backendOk = computed(() =>
  [summary.value.workflows, summary.value.skills, summary.value.tools, summary.value.evalSuites]
    .some((value) => value !== '-')
)

const secondaryItems = computed(() => [
  { title: '知识库管理', description: '上传、解析、切块、向量化与索引重建。', path: '/knowledge-base', state: `${summary.value.documents} 文档` },
  { title: 'Skills 目录', description: '查看技能 Schema 并直接执行。', path: '/skills', state: `${summary.value.skills} skills` },
  { title: 'Eval 评测', description: '运行 YAML 评测集，Keyword / LLM Judge。', path: '/eval', state: `${summary.value.evalSuites} suites` },
  { title: 'Observability', description: '查看请求链路：检索/工具/模型各阶段耗时。', path: '/observability', state: '链路追踪' },
  { title: 'Dify Bridge', description: '调用外部 Dify Workflow，导出 Skill OpenAPI。', path: '/dify', state: summary.value.dify },
  { title: 'Manus 智能体', description: '多步推理与工具自动选择的执行台。', path: '/manus-app', state: 'agent' }
])

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

const countValue = (settled) => {
  if (settled.status !== 'fulfilled') return '-'
  if (Array.isArray(settled.value)) return settled.value.length
  return settled.value == null ? '-' : '1'
}
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: var(--color-bg);
  padding: var(--space-8);
}

.hero {
  max-width: 1240px;
  margin: 0 auto var(--space-8);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-6);
  flex-wrap: wrap;
}

.eyebrow {
  margin: 0 0 var(--space-2);
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

h1 {
  margin: 0;
  font-size: 2.25rem;
  line-height: 1.15;
  color: var(--color-text);
}

.subtitle {
  margin: var(--space-3) 0 0;
  font-size: 1rem;
  color: var(--color-text-muted);
  max-width: 640px;
}

.runtime-panel {
  min-width: 248px;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-3) var(--space-4);
  box-shadow: var(--shadow-sm);
}

.runtime-panel strong,
.runtime-panel span:last-child {
  display: block;
}

.runtime-panel span:last-child {
  color: var(--color-text-subtle);
  font-size: 0.75rem;
  margin-top: 3px;
  word-break: break-all;
}

.status-dot {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  flex: 0 0 auto;
  background: var(--color-skipped);
}

.status-dot.ok { background: var(--color-success); }
.status-dot.fail { background: var(--color-error); }
.status-dot.pending { background: var(--color-warning); }

.primary-grid {
  max-width: 1240px;
  margin: 0 auto var(--space-8);
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-5);
}

.primary-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
  text-decoration: none;
  color: var(--color-text);
  box-shadow: var(--shadow-sm);
  transition: box-shadow 0.15s ease, transform 0.15s ease, border-color 0.15s ease;
}

.primary-card:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-3px);
  border-color: var(--color-primary-soft);
}

.primary-card.featured {
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
  color: #fff;
  border-color: transparent;
}

.primary-card.featured p,
.primary-card.featured .primary-cta {
  color: rgba(255, 255, 255, 0.85);
}

.primary-icon {
  font-size: 2rem;
}

.primary-card h2 {
  margin: 0;
  font-size: 1.25rem;
}

.primary-card p {
  margin: 0;
  font-size: 0.9rem;
  color: var(--color-text-muted);
  line-height: 1.6;
  flex: 1;
}

.primary-cta {
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--color-primary);
}

.primary-badge {
  position: absolute;
  top: var(--space-4);
  right: var(--space-4);
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: var(--radius-pill);
  padding: 2px 10px;
  font-size: 0.72rem;
  font-weight: 600;
}

.secondary-section {
  max-width: 1240px;
  margin: 0 auto;
}

.secondary-title {
  margin: 0 0 var(--space-4);
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-text-muted);
}

.secondary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--space-4);
}

@media (max-width: 920px) {
  .primary-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .home-page {
    padding: var(--space-4);
  }

  .hero {
    flex-direction: column;
    align-items: stretch;
  }

  h1 {
    font-size: 1.75rem;
  }
}
</style>
