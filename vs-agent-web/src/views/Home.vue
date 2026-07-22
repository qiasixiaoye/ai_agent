<template>
  <main class="console">
    <div class="scanline"></div>

    <!-- HERO -->
    <header class="hero">
      <div class="hero-left">
        <p class="eyebrow">// AI&nbsp;AGENT&nbsp;CONTROL&nbsp;CONSOLE</p>
        <h1 class="hero-title">智能体研发<span class="accent">控制台</span></h1>
        <p class="hero-sub">
          用 Agent（多轮 + RAG + 工具）· 配能力（知识库 / Skills / 编排）· 产工作流（一句话 → Dify 画布）
        </p>

        <div class="stat-row">
          <div class="stat">
            <span class="stat-num">{{ summary.tools }}</span>
            <span class="stat-label">TOOLS</span>
          </div>
          <div class="stat-sep"></div>
          <div class="stat">
            <span class="stat-num">{{ summary.skills }}</span>
            <span class="stat-label">SKILLS</span>
          </div>
          <div class="stat-sep"></div>
          <div class="stat">
            <span class="stat-num">{{ summary.documents }}</span>
            <span class="stat-label">DOCS</span>
          </div>
        </div>
      </div>

      <div class="hud-panel" :class="{ ok: backendOk, fail: !backendOk && !summaryLoading }">
        <div class="hud-ring"></div>
        <div class="hud-status">
          <span :class="['hud-dot', summaryLoading ? 'pending' : backendOk ? 'ok' : 'fail']"></span>
          <strong>{{ summaryLoading ? 'SCANNING' : backendOk ? 'ONLINE' : 'OFFLINE' }}</strong>
        </div>
        <code class="hud-url">{{ apiBase }}</code>
      </div>
    </header>

    <!-- LAYERS -->
    <section v-for="group in groups" :key="group.title" class="layer">
      <div class="layer-head">
        <span class="layer-no">{{ group.no }}</span>
        <div>
          <h3 class="layer-title">{{ group.title }}</h3>
          <p class="layer-desc">{{ group.desc }}</p>
        </div>
        <span class="layer-line"></span>
      </div>

      <div class="layer-grid">
        <component
          :is="item.external ? 'a' : 'button'"
          v-for="item in group.items"
          :key="item.path"
          class="card"
          :class="{ featured: item.featured }"
          :href="item.external ? item.path : null"
          :target="item.external ? '_blank' : null"
          :rel="item.external ? 'noopener' : null"
          @click="item.external ? null : $router.push(item.path)"
        >
          <span class="corner tl"></span>
          <span class="corner br"></span>
          <div class="card-head">
            <h4>{{ item.title }}</h4>
            <span class="card-state">{{ item.state }}</span>
          </div>
          <p class="card-desc">{{ item.description }}</p>
          <span class="card-go">{{ item.external ? 'OPEN ↗' : 'ENTER →' }}</span>
        </component>
      </div>
    </section>

    <footer class="console-foot">
      <span>VS · AI AGENT PLATFORM</span>
      <span>Spring AI · RAG · MCP · Dify</span>
    </footer>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useHead } from '@vueuse/head'
import { listKbDocuments, listPlatformTools, listSkills } from '../services/api'

useHead({
  title: 'AI Agent Platform · 智能体研发控制台',
  meta: [
    { name: 'description', content: '科幻控制台风格的 AI Agent 平台：对话/工具/知识库/一句话生成 Dify 工作流。' }
  ]
})

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081/api'
const difyConsoleUrl = import.meta.env.VITE_DIFY_CONSOLE_URL || 'http://localhost:3001'
const summaryLoading = ref(true)
const summary = ref({ skills: '–', tools: '–', documents: '–' })

const backendOk = computed(() =>
  [summary.value.skills, summary.value.tools].some((v) => v !== '–')
)

const groups = computed(() => [
  {
    no: '01',
    title: '用 Agent',
    desc: '一个对话入口，三种模式：普通 / RAG 知识问答 / 智能体(Manus)。',
    items: [
      { title: 'AI 对话', description: '普通对话 · RAG 知识检索 · Manus 智能体（多步推理 + 工具调用），页内一键切换。', path: '/assistant-app', state: '3 MODES', featured: true }
    ]
  },
  {
    no: '02',
    title: '配能力',
    desc: '为智能体配置可调用的能力与知识资产。',
    items: [
      { title: 'Agent 工作台', description: '工具调用 + 多步骤任务编排，内置「银河摄影规划」演示。', path: '/agent-platform', state: `${summary.value.tools} TOOLS`, featured: true },
      { title: 'Skills 目录', description: '查看技能 Schema 并直接执行，导出 OpenAPI 给 Dify。', path: '/skills', state: `${summary.value.skills} SKILLS` },
      { title: 'Agent Runtime', description: 'MCP 工具目录、策略与熔断；工作/摘要/语义/情景四层记忆和上下文预算。', path: '/runtime', state: 'MCP + MEMORY' },
      { title: '知识库', description: '上传、解析、切块、向量化与索引重建（pgvector RAG）。', path: '/knowledge-base', state: `${summary.value.documents} DOCS` }
    ]
  },
  {
    no: '03',
    title: '产工作流',
    desc: '把一段需求固化成可复用、Dify 画布可见的工作流，并自环优化提示词。',
    items: [
      { title: '工作流生产 + 下游优化', description: '一句话 → 自动生成 Dify Workflow DSL（含 MCP/Skills 节点）→ 画布可运行；页内切到「下游」可抽成本地副本、自环优化提示词并回写 Dify 复核。', path: '/workflow-studio', state: 'NL → DIFY → OPT', featured: true },
      { title: 'Dify 控制台', description: '在 Dify 画布查看 / 编辑 / 运行已生成的工作流（外部页面，新标签打开）。', path: difyConsoleUrl, state: 'CONSOLE ↗', external: true }
    ]
  },
  {
    no: '04',
    title: '可观测 · 横切',
    desc: '贯穿每一步：检索 / 工具 / 模型各阶段耗时与链路追踪。',
    items: [
      { title: 'Observability', description: '查看请求链路：检索/工具/模型各阶段耗时与成败。', path: '/observability', state: 'TRACE' }
    ]
  }
])

onMounted(async () => {
  const [skills, tools, documents] = await Promise.allSettled([
    listSkills(),
    listPlatformTools(),
    listKbDocuments(20)
  ])
  summary.value = {
    skills: countValue(skills),
    tools: countValue(tools),
    documents: countValue(documents)
  }
  summaryLoading.value = false
})

const countValue = (settled) => {
  if (settled.status !== 'fulfilled') return '–'
  if (Array.isArray(settled.value)) return settled.value.length
  return settled.value == null ? '–' : '1'
}
</script>

<style scoped>
.console {
  position: relative;
  min-height: 100vh;
  max-width: 1240px;
  margin: 0 auto;
  padding: var(--space-10) var(--space-8) var(--space-8);
}

/* moving scan line across the whole console */
.scanline {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(to bottom, transparent, rgba(34, 211, 238, 0.05), transparent);
  height: 180px;
  opacity: 0.6;
  animation: scan 7s linear infinite;
  z-index: 0;
}
@keyframes scan {
  0% { transform: translateY(-200px); }
  100% { transform: translateY(100vh); }
}

.hero,
.layer,
.console-foot { position: relative; z-index: 1; }

/* ---------- HERO ---------- */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-8);
  flex-wrap: wrap;
  margin-bottom: var(--space-10);
}

.eyebrow {
  margin: 0 0 var(--space-3);
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.22em;
  color: var(--color-primary);
  text-shadow: 0 0 14px rgba(34, 211, 238, 0.45);
}

.hero-title {
  margin: 0;
  font-size: clamp(2.1rem, 5vw, 3.4rem);
  font-weight: 800;
  letter-spacing: 0.01em;
  line-height: 1.05;
  color: #f2f6fb;
  text-shadow: 0 0 38px rgba(120, 180, 255, 0.18);
}
.hero-title .accent {
  background: var(--gradient-brand);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  filter: drop-shadow(0 0 18px rgba(34, 211, 238, 0.35));
}

.hero-sub {
  margin: var(--space-4) 0 0;
  max-width: 560px;
  color: var(--color-text-muted);
  font-size: 0.98rem;
  line-height: 1.7;
}

.stat-row {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  margin-top: var(--space-6);
}
.stat { display: flex; flex-direction: column; }
.stat-num {
  font-family: var(--font-mono);
  font-size: 1.7rem;
  font-weight: 700;
  color: var(--color-primary);
  text-shadow: 0 0 18px rgba(34, 211, 238, 0.35);
  line-height: 1;
}
.stat-label {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.2em;
  color: var(--color-text-subtle);
  margin-top: 6px;
}
.stat-sep { width: 1px; height: 30px; background: var(--color-border-strong); }

/* HUD status panel */
.hud-panel {
  position: relative;
  min-width: 230px;
  padding: var(--space-5) var(--space-5) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--gradient-surface);
  backdrop-filter: blur(8px);
  box-shadow: var(--shadow-md);
  overflow: hidden;
}
.hud-panel.ok { box-shadow: var(--shadow-md), var(--glow-cyan); }
.hud-ring {
  position: absolute;
  top: -40px; right: -40px;
  width: 120px; height: 120px;
  border-radius: 50%;
  border: 1px dashed rgba(34, 211, 238, 0.35);
  animation: spin 14s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.hud-status { display: flex; align-items: center; gap: 10px; }
.hud-status strong {
  font-family: var(--font-mono);
  letter-spacing: 0.18em;
  font-size: 0.95rem;
  color: var(--color-text);
}
.hud-dot {
  width: 11px; height: 11px; border-radius: 50%;
  background: var(--color-skipped);
}
.hud-dot.ok { background: var(--color-success); box-shadow: 0 0 12px var(--color-success); animation: pulse 1.8s ease-in-out infinite; }
.hud-dot.fail { background: var(--color-error); box-shadow: 0 0 12px var(--color-error); }
.hud-dot.pending { background: var(--color-warning); box-shadow: 0 0 12px var(--color-warning); }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.35; } }
.hud-url {
  display: block;
  margin-top: var(--space-3);
  font-size: 11px;
  color: var(--color-text-subtle);
  word-break: break-all;
}

/* ---------- LAYERS ---------- */
.layer { margin-bottom: var(--space-8); }
.layer-head {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}
.layer-no {
  flex: 0 0 auto;
  font-family: var(--font-mono);
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--color-primary);
  width: 42px; height: 42px;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid var(--color-primary-soft);
  border-radius: var(--radius-md);
  background: var(--color-primary-light);
  box-shadow: inset 0 0 16px rgba(34, 211, 238, 0.12);
}
.layer-title { margin: 0; font-size: 1.12rem; font-weight: 700; color: var(--color-text); letter-spacing: 0.01em; }
.layer-desc { margin: 3px 0 0; font-size: 0.84rem; color: var(--color-text-muted); }
.layer-line { flex: 1; height: 1px; background: linear-gradient(90deg, var(--color-border-strong), transparent); }

.layer-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--space-4);
}

/* ---------- CARD ---------- */
.card {
  position: relative;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-5);
  min-height: 150px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  backdrop-filter: blur(6px);
  color: var(--color-text);
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
  overflow: hidden;
}
.card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 2px;
  background: var(--gradient-brand);
  opacity: 0.5;
  transition: opacity 0.18s ease;
}
.card:hover {
  transform: translateY(-4px);
  border-color: var(--color-primary-soft);
  box-shadow: var(--shadow-lg), var(--glow-cyan);
}
.card:hover::before { opacity: 1; }
.card.featured { background: linear-gradient(160deg, rgba(34, 211, 238, 0.10), rgba(99, 102, 241, 0.08)); }

.corner {
  position: absolute;
  width: 12px; height: 12px;
  border: 1px solid var(--color-primary-soft);
  opacity: 0.5;
}
.corner.tl { top: 8px; left: 8px; border-right: 0; border-bottom: 0; }
.corner.br { bottom: 8px; right: 8px; border-left: 0; border-top: 0; }

.card-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-3); }
.card-head h4 { margin: 0; font-size: 1.08rem; font-weight: 700; }
.card-state {
  flex: 0 0 auto;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  color: var(--color-primary);
  padding: 3px 8px;
  border: 1px solid var(--color-primary-soft);
  border-radius: var(--radius-pill);
  background: var(--color-primary-light);
}
.card-desc { margin: 0; flex: 1; font-size: 0.86rem; line-height: 1.6; color: var(--color-text-muted); }
.card-go {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--color-primary);
}

/* ---------- FOOT ---------- */
.console-foot {
  margin-top: var(--space-10);
  padding-top: var(--space-5);
  border-top: 1px solid var(--color-border);
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-2);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--color-text-subtle);
}

@media (max-width: 720px) {
  .console { padding: var(--space-6) var(--space-4); }
  .hud-panel { width: 100%; }
}
</style>
