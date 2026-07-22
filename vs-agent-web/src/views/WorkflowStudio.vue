<template>
  <div class="ws-page">
    <header class="ws-header">
      <router-link to="/" class="back-link"><span>←</span> 返回首页</router-link>
      <h1>工作流生产</h1>
      <p class="ws-subtitle">上游：一句话 → 生成 Dify Workflow DSL（含 MCP / Skills 节点）→ 落到画布可运行；下游：抽成本地副本 → 自环优化提示词 → 回写 Dify 复核，全程就地可见</p>
    </header>

    <main class="ws-main">
      <!-- 页面级 Tab：① 上游产工作流 / ② 下游自环优化（同一条上下游链路，跳转最少） -->
      <div class="ws-tabs" role="tablist">
        <button class="ws-tab" :class="{ active: activeTab === 'build' }" @click="activeTab = 'build'">
          ① 上游 · 产工作流
        </button>
        <button class="ws-tab" :class="{ active: activeTab === 'optimize' }" @click="activeTab = 'optimize'">
          ② 下游 · 自环优化
        </button>
      </div>

      <!-- 后端多工具生成器：一句话 → DSL（LLM 自动选工具并填全参数）→ 一键导入 Dify -->
      <section v-show="activeTab === 'build'" class="gen-panel">
        <div class="gen-head">
          <h2>🧠 智能生成（一句话 → Dify 工作流 · 一键导入）</h2>
          <span class="gen-tag">后端规划器 · LLM 自动选工具</span>
        </div>

        <!-- 两种工具形态概念不同，用 Tab 分开选 -->
        <div class="gen-tabs" role="tablist">
          <button class="gen-tab" :class="{ active: genMode === 'http' }" @click="genMode = 'http'">
            🔧 直接调用 · HTTP
          </button>
          <button class="gen-tab" :class="{ active: genMode === 'agent' }" @click="genMode = 'agent'">
            🤖 智能体 · Agent
          </button>
        </div>
        <p class="gen-mode-desc">{{ modeInfo[genMode] }}</p>

        <!-- 应用形态：Dify 图编排仅有 workflow / advanced-chat 两种 app.mode，与上面的 http/agent 编排正交 -->
        <div class="gen-kind">
          <span class="gen-kind-label">应用形态</span>
          <button class="gen-seg" :class="{ active: appKind === 'chatflow' }" @click="appKind = 'chatflow'">
            💬 多轮对话
          </button>
          <button class="gen-seg" :class="{ active: appKind === 'workflow' }" @click="appKind = 'workflow'">
            📋 单轮工作流
          </button>
        </div>
        <p class="gen-mode-desc gen-kind-desc">{{ appKindInfo[appKind] }}</p>

        <div class="gen-samples">
          <span class="gen-samples-label">样例（标 ⇉ 的在 HTTP 形态会生成 N 路并行 + 汇聚）：</span>
          <button v-for="(s, i) in samples" :key="i" class="gen-chip" @click="requirement = s.text">
            {{ s.label }}<span v-if="s.n >= 2" class="gen-chip-badge" :title="`约 ${s.n} 路并行分支`">⇉{{ s.n }}</span>
          </button>
        </div>
        <textarea
          v-model="requirement"
          class="gen-input"
          rows="3"
          placeholder="用一句话描述需求，例如：做一个北京地区的星空拍摄计划助手，先算今晚银河升起时刻，再按 24mm 焦距给出星空拍摄参数，最后汇总成完整拍摄方案"
        ></textarea>
        <div class="gen-actions">
          <button class="gen-btn primary" :disabled="generating || !requirement.trim()" @click="onGenerate">
            {{ generating ? '生成中…' : '生成工作流' }}
          </button>
          <button v-if="genResult" class="gen-btn" :disabled="importing || !genResult.valid" @click="onImport">
            {{ importing ? '导入中…' : '导入到 Dify' }}
          </button>
        </div>

        <p v-if="error" class="gen-error">⚠ {{ error }}</p>

        <div v-if="genResult" class="gen-result">
          <div class="gen-result-row">
            <strong>{{ genResult.workflowName }}</strong>
            <span :class="['gen-valid', genResult.valid ? 'ok' : 'bad']">
              {{ genResult.valid ? '校验通过' : '校验未通过' }}
            </span>
          </div>
          <div class="gen-flow">
            <template v-for="(n, i) in flowNodes" :key="i">
              <span class="gen-node" :class="n.cls">{{ n.label }}</span>
              <span v-if="i < flowNodes.length - 1" class="gen-arrow">→</span>
            </template>
          </div>
          <!-- 诊断/告警：规划器选择、LLM 回退原因、形态降级等（错误分析审计） -->
          <div v-if="genResult.warnings && genResult.warnings.length" class="gen-warnings">
            <span class="gen-warn-label">诊断</span>
            <span v-for="(w, i) in genResult.warnings" :key="i" class="gen-warn-item">{{ w }}</span>
            <router-link v-if="genResult.requestId" class="audit-link"
                         :to="{ path: '/observability', query: { requestId: genResult.requestId } }"
                         title="打开完整审计轨迹">🔎 查看审计轨迹 →</router-link>
          </div>
          <div v-if="genResult.errors && genResult.errors.length" class="gen-warnings">
            <span class="gen-warn-label err">错误</span>
            <span v-for="(e, i) in genResult.errors" :key="i" class="gen-warn-item err">{{ e }}</span>
          </div>
          <div v-if="importResult" class="gen-import">
            <template v-if="importResult.success">
              ✅ 已导入 Dify ·
              <a :href="difyAppUrl" target="_blank" rel="noopener">在 Dify 打开工作流 ↗</a>
              <button class="gen-btn run-btn" :disabled="running" @click="onRunObserve">
                {{ running ? '运行观测中…' : '▶ 运行并观测' }}
              </button>
            </template>
            <template v-else>❌ 导入失败：{{ importResult.errorMessage }}</template>
          </div>

          <!-- 运行时观测：把 Dify 实际运行的每个节点状态/错误暴露出来，并落 observability 审计 -->
          <div v-if="runResult" class="run-trace">
            <div class="run-head">
              <span :class="['run-badge', runResult.success ? 'ok' : 'bad']">
                {{ runResult.success ? '运行成功' : '运行失败' }}
              </span>
              <span class="run-meta">状态 {{ runResult.status }} · 耗时 {{ runResult.elapsedTime?.toFixed(1) }}s · {{ runResult.totalTokens }} tokens</span>
              <router-link v-if="runResult.requestId" class="audit-link"
                           :to="{ path: '/observability', query: { requestId: runResult.requestId } }"
                           title="打开完整审计轨迹（含各节点与关联的后端工具调用）">🔎 查看完整审计轨迹 →</router-link>
            </div>
            <div v-if="runResult.error" class="run-err">⚠ {{ runResult.error }}</div>
            <div class="run-nodes">
              <span v-for="(n, i) in runResult.nodes" :key="i"
                    :class="['run-node', n.status === 'succeeded' ? 'ok' : 'bad']"
                    :title="n.error || ''">
                {{ n.title }} · {{ n.status }}{{ n.error ? ' ✕' : '' }}
              </span>
            </div>
            <div v-if="runResult.agentRounds && runResult.agentRounds.length" class="run-meta">
              Agent 轮次 {{ runResult.agentRounds.length }}（失败 {{ runResult.agentRounds.filter(r => r.error || r.status === 'error' || r.status === 'failed').length }}）
            </div>
            <!-- 关联到的后端 MCP 工具调用：只给一句人话摘要，逐次入参/耗时/报错在审计轨迹里看 -->
            <div v-if="correlatedToolSummary" class="run-meta">🔗 本次调用了 {{ runResult.correlatedToolCalls.length }} 个后端工具：{{ correlatedToolSummary }}</div>
          </div>
        </div>
      </section>

      <!-- ② 下游：自环优化面板（消费 maspo-demo sidecar 的 SSE，结果就地展示） -->
      <OptimizerPanel v-if="activeTab === 'optimize'" />
    </main>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useHead } from '@vueuse/head'
import { generateWorkflowFromRequirement, importGeneratedWorkflowToDify, runImportedDifyApp } from '../services/api'
import OptimizerPanel from '../components/OptimizerPanel.vue'

// 命名以便 <keep-alive include> 缓存本页（跳去审计轨迹再返回时不丢失已生成结果）
defineOptions({ name: 'WorkflowStudio' })

// 页面级 Tab：上游产工作流 / 下游自环优化
const activeTab = ref('build')

// 浏览器侧打开 Dify 用 localhost（后端容器内访问用 host.docker.internal，两者分开）
const difyConsoleUrl = import.meta.env.VITE_DIFY_CONSOLE_URL || 'http://localhost:3001'

// 两种工具形态的适用场景说明（随 Tab 切换）
const modeInfo = {
  http: '工具用「HTTP 请求」节点固定编排，参数在生成时就算好（含当前日期），顺序与结果最稳定，不依赖任何 Dify 插件。适合要可复现、参数必须准的场景。',
  agent: '工具挂在一个「Agent」节点上，由 Dify 里的 LLM 在运行时自主决定调用哪些、调几次。更像真 Agent，但参数由模型即时填充（日期等已在指令里注入当前日期校正）。适合要体现自主编排的场景。',
}

// 应用形态说明（Dify 图编排仅此两种 app.mode）
const appKindInfo = {
  chatflow: '生成 Chatflow（advanced-chat）：对话框输入、answer 节点回复、带会话记忆，支持多轮追问与逐步优化。导入后是一个可对话的应用。',
  workflow: '生成 Workflow（单轮）：Start 带输入表单、end 节点输出，跑一次出结果、不保留上下文。适合一次性批处理类任务。',
}

// n = 该样例在 HTTP 形态下会触发的并行工具数（≥2 即生成「并行 fan-out + 汇聚」复杂图）
const samples = [
  { label: '📷 星空拍摄全案', n: 6, text: '周末去河北坝上（纬度41.8，经度116.9）拍银河，带 24mm F1.4 镜头、机身最高 ISO6400。请算出银河升起与最佳拍摄时段，评估当地光污染等级和夜间云量，按这支镜头给星空摄影参数，再针对高原暗夜无光污染场景给曝光建议，最后整理成带时间表的中文拍摄计划。' },
  { label: '🔍 相机选购报告', n: 3, text: '联网搜索适合星空摄影的全画幅微单相机推荐，找几张代表机型的样张图，汇总成带要点对比的中文选购报告，并生成一份 PDF。' },
  { label: '☕ 手冲＋品鉴卡', n: 2, text: '我有一包耶加雪菲日晒、中浅烘焙，想用 V60 手冲出明亮花香。请给完整的粉水比、水温和分段注水的时间与克数，并基于豆子和烘焙度预测风味特征与品鉴要点，整理成新手能照做的中文「冲煮＋品鉴」卡片。' },
  { label: '🏍️ 川西摩旅手册', n: 2, text: '计划一次 800 公里的川西摩旅，夏季出行 5 天。请按总里程规划每天骑行距离、休息与大致住宿节奏，并根据季节和天数给出完整的骑行装备与安全清单，最后整理成一份中文出行手册。' },
]

const requirement = ref('')
const genMode = ref('http')
const lastMode = ref('http')
const appKind = ref('chatflow')
const lastAppKind = ref('chatflow')
const generating = ref(false)
const importing = ref(false)
const genResult = ref(null)
const importResult = ref(null)
const error = ref('')

const NODE_META = {
  start: { label: '开始', cls: 'n-start' },
  llm: { label: 'LLM 汇总', cls: 'n-llm' },
  answer: { label: '回复', cls: 'n-answer' },
}
// 收尾节点随应用形态而变：chatflow→answer（回复），workflow→end（结束）
const terminalNode = computed(() =>
  lastAppKind.value === 'workflow'
    ? { label: '结束', cls: 'n-answer' }
    : { label: '回复', cls: 'n-answer' }
)
const flowNodes = computed(() => {
  const nodes = genResult.value?.ir?.nodes || []
  const tools = nodes
    .filter((n) => n.type === 'tool')
    .map((n) => (n.toolRef || '').replace(/^(tool|skill):/, ''))
  // Agent 形态：≥4 工具拆成「双智能体并行 → 汇聚 → 综合 LLM」，否则单 Agent 折叠全部工具
  if (lastMode.value === 'agent' && tools.length) {
    if (tools.length >= 4) {
      const half = Math.ceil(tools.length / 2)
      return [
        NODE_META.start,
        { label: `⇉ 双智能体并行 · 组1：${tools.slice(0, half).join('、')}｜组2：${tools.slice(half).join('、')}`, cls: 'n-agent' },
        { label: '🔗 汇聚', cls: 'n-agg' },
        NODE_META.llm,
        terminalNode.value,
      ]
    }
    return [
      NODE_META.start,
      { label: '🤖 Agent · 工具：' + tools.join('、'), cls: 'n-agent' },
      terminalNode.value,
    ]
  }
  // HTTP 形态且 ≥2 工具：实际 DSL 是 开始 →(并行)各工具→ 汇聚 → LLM → 收尾
  if (lastMode.value === 'http' && tools.length >= 2) {
    return [
      NODE_META.start,
      { label: '⇉ 并行 · ' + tools.join('、'), cls: 'n-tool' },
      { label: '🔗 汇聚', cls: 'n-agg' },
      NODE_META.llm,
      terminalNode.value,
    ]
  }
  return nodes.map((n) => {
    if (n.type === 'tool') {
      const ref = (n.toolRef || '').replace(/^(tool|skill):/, '')
      return { label: '🔧 ' + ref, cls: 'n-tool' }
    }
    if (n.type === 'answer') {
      return terminalNode.value
    }
    return NODE_META[n.type] || { label: n.type, cls: '' }
  })
})
const difyAppUrl = computed(() =>
  importResult.value?.appId ? `${difyConsoleUrl}/app/${importResult.value.appId}/workflow` : '#'
)

async function onGenerate() {
  error.value = ''
  importResult.value = null
  genResult.value = null
  generating.value = true
  lastMode.value = genMode.value
  lastAppKind.value = appKind.value
  try {
    genResult.value = await generateWorkflowFromRequirement(requirement.value.trim(), genMode.value, appKind.value)
  } catch (e) {
    error.value = e?.message || '生成失败'
  } finally {
    generating.value = false
  }
}

async function onImport() {
  error.value = ''
  importResult.value = null
  runResult.value = null
  importing.value = true
  try {
    importResult.value = await importGeneratedWorkflowToDify(genResult.value.workflowId)
  } catch (e) {
    error.value = e?.message || '导入失败'
  } finally {
    importing.value = false
  }
}

const running = ref(false)
const runResult = ref(null)

// 关联到的后端工具：去重后取前若干个工具名，拼成一句摘要（详情在审计轨迹里看）
const correlatedToolSummary = computed(() => {
  const calls = runResult.value?.correlatedToolCalls
  if (!calls || !calls.length) return ''
  const names = [...new Set(calls.map(c => c.toolName).filter(Boolean))]
  const shown = names.slice(0, 5).join('、')
  return names.length > 5 ? `${shown} 等 ${names.length} 个` : shown
})

async function onRunObserve() {
  error.value = ''
  runResult.value = null
  running.value = true
  try {
    runResult.value = await runImportedDifyApp(importResult.value.appId, lastAppKind.value, requirement.value.trim())
  } catch (e) {
    error.value = e?.message || '运行失败'
  } finally {
    running.value = false
  }
}

useHead({
  title: '工作流生产 - 一句话生成 Dify 工作流',
  meta: [{ name: 'description', content: '一句话需求自动生成可在 Dify 画布查看的 Workflow DSL，内置 MCP 与 Skills 节点编排。' }]
})
</script>

<style scoped>
.ws-page {
  min-height: 100vh;
  background: var(--color-bg);
  display: flex;
  flex-direction: column;
}

.ws-header {
  padding: var(--space-6) var(--space-8) var(--space-4);
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
  color: #fff;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: rgba(255, 255, 255, 0.9);
  text-decoration: none;
  font-size: 0.85rem;
  margin-bottom: var(--space-3);
}

.ws-header h1 {
  margin: 0;
  font-size: 1.6rem;
}

.ws-subtitle {
  margin: var(--space-2) 0 0;
  color: rgba(255, 255, 255, 0.85);
  font-size: 0.92rem;
  max-width: 820px;
}

.ws-main {
  flex: 1;
  padding: var(--space-5) var(--space-8) var(--space-8);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

/* ---- 页面级 Tab（上游/下游） ---- */
.ws-tabs {
  display: flex;
  gap: var(--space-2);
  border-bottom: 1px solid var(--color-border);
  margin-bottom: var(--space-2);
}
.ws-tab {
  cursor: pointer;
  border: none;
  background: transparent;
  color: var(--color-text-soft, #8b97a7);
  font-size: 0.95rem;
  font-weight: 600;
  padding: 10px 18px;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: 0.15s;
}
.ws-tab:hover { color: var(--color-text); }
.ws-tab.active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
}

/* ---- 后端多工具生成面板 ---- */
.gen-panel {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  padding: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.gen-head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.gen-head h2 {
  margin: 0;
  font-size: 1.15rem;
  color: var(--color-text);
}

.gen-tag {
  font-size: 0.75rem;
  color: var(--color-primary);
  border: 1px solid var(--color-primary);
  border-radius: 999px;
  padding: 2px 10px;
}

.gen-samples {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.gen-samples-label {
  font-size: 0.82rem;
  color: var(--color-text-muted);
}

.gen-chip {
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 0.8rem;
  color: var(--color-text);
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}

.gen-chip:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.gen-chip-badge {
  margin-left: 6px;
  padding: 0 6px;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 700;
  background: color-mix(in srgb, #14b8a6 16%, transparent);
  color: #14b8a6;
}

.gen-tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--color-border);
}

.gen-tab {
  background: transparent;
  border: 1px solid transparent;
  border-bottom: none;
  border-radius: var(--radius-md) var(--radius-md) 0 0;
  padding: 8px 18px;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-text-muted);
  cursor: pointer;
  margin-bottom: -1px;
  transition: color 0.15s, background 0.15s, border-color 0.15s;
}

.gen-tab:hover {
  color: var(--color-text);
}

.gen-tab.active {
  color: var(--color-primary);
  border-color: var(--color-border);
  border-bottom: 1px solid var(--color-surface);
  background: var(--color-surface);
}

.gen-mode-desc {
  margin: 0;
  font-size: 0.84rem;
  line-height: 1.6;
  color: var(--color-text-muted);
}

.gen-kind {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.gen-kind-label {
  font-size: 0.82rem;
  color: var(--color-text-muted);
}

.gen-seg {
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 4px 14px;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}

.gen-seg:hover {
  color: var(--color-text);
}

.gen-seg.active {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 10%, transparent);
}

.gen-kind-desc {
  margin-top: -4px;
}

.gen-input {
  width: 100%;
  box-sizing: border-box;
  resize: vertical;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text);
  padding: 10px 12px;
  font-size: 0.9rem;
  font-family: inherit;
  line-height: 1.6;
}

.gen-input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.gen-actions {
  display: flex;
  gap: var(--space-3);
}

.gen-btn {
  border: 1px solid var(--color-border);
  background: var(--color-bg);
  color: var(--color-text);
  border-radius: var(--radius-md);
  padding: 8px 20px;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
}

.gen-btn.primary {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}

.gen-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.gen-error {
  margin: 0;
  color: #f87171;
  font-size: 0.85rem;
}

.gen-result {
  border-top: 1px dashed var(--color-border);
  padding-top: var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.gen-result-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.gen-valid {
  font-size: 0.75rem;
  border-radius: 999px;
  padding: 2px 10px;
}

.gen-valid.ok {
  color: #34d399;
  border: 1px solid #34d399;
}

.gen-valid.bad {
  color: #f87171;
  border: 1px solid #f87171;
}

.gen-flow {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.gen-node {
  font-size: 0.82rem;
  border-radius: var(--radius-md);
  padding: 5px 12px;
  border: 1px solid var(--color-border);
  background: var(--color-bg);
  color: var(--color-text);
}

.gen-node.n-tool {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.gen-node.n-llm {
  border-color: #a855f7;
  color: #a855f7;
}

.gen-node.n-agent {
  border-color: #f59e0b;
  color: #f59e0b;
}

.gen-node.n-agg {
  border-color: #14b8a6;
  color: #14b8a6;
}

.gen-warnings {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.gen-warn-label {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--color-text-muted);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 1px 8px;
}

.gen-warn-label.err {
  color: #f87171;
  border-color: #f87171;
}

.gen-warn-item {
  font-size: 0.76rem;
  color: var(--color-text-muted);
  background: var(--color-bg);
  border-radius: var(--radius-sm, 6px);
  padding: 2px 8px;
}

.gen-warn-item.err {
  color: #f87171;
}

.gen-arrow {
  color: var(--color-text-subtle);
}

.gen-import {
  font-size: 0.88rem;
  color: var(--color-text);
}

.gen-import a {
  color: var(--color-primary);
  font-weight: 600;
}

.run-btn {
  margin-left: 12px;
  padding: 4px 14px;
  font-size: 0.8rem;
}

.run-trace {
  border-top: 1px dashed var(--color-border);
  padding-top: var(--space-3);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.audit-link {
  font-size: 0.8rem;
  color: var(--color-primary);
  text-decoration: none;
  font-weight: 600;
  white-space: nowrap;
}

.audit-link:hover {
  text-decoration: underline;
}

.run-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.run-badge {
  font-size: 0.75rem;
  font-weight: 700;
  border-radius: 999px;
  padding: 2px 10px;
}

.run-badge.ok {
  color: #34d399;
  border: 1px solid #34d399;
}

.run-badge.bad {
  color: #f87171;
  border: 1px solid #f87171;
}

.run-meta {
  font-size: 0.78rem;
  color: var(--color-text-muted);
}

.run-err {
  font-size: 0.82rem;
  color: #f87171;
}

.run-nodes {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.run-node {
  font-size: 0.76rem;
  border-radius: var(--radius-sm, 6px);
  padding: 3px 9px;
  border: 1px solid var(--color-border);
}

.run-node.ok {
  border-color: #34d399;
  color: #34d399;
}

.run-node.bad {
  border-color: #f87171;
  color: #f87171;
}
</style>
