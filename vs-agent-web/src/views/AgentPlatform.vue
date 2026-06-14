<template>
  <div class="ap-page">
    <header class="ap-header">
      <router-link to="/" class="back-link"><span>←</span> 返回首页</router-link>
      <h1>Agent 工作台</h1>
      <p class="ap-subtitle">工具调用 · Skills · 多步骤任务编排</p>
    </header>

    <main class="ap-main">
      <!-- Flagship demo -->
      <AppCard title="银河摄影规划演示" subtitle="多步骤编排：天体计算 → 光污染评估 → 云量预测 → LLM 拍摄建议汇总">
        <template #actions>
          <TagChip label="任务编排" accent />
        </template>

        <div class="demo-form">
          <div class="demo-field">
            <label>纬度</label>
            <input v-model="astroForm.latitude" type="number" step="0.0001" placeholder="39.9042" />
          </div>
          <div class="demo-field">
            <label>经度</label>
            <input v-model="astroForm.longitude" type="number" step="0.0001" placeholder="116.4074" />
          </div>
          <div class="demo-field">
            <label>日期</label>
            <input v-model="astroForm.date" type="date" />
          </div>
          <button class="run-btn" :disabled="astroRunning" @click="runAstro">
            {{ astroRunning ? '编排执行中…' : '运行编排' }}
          </button>
        </div>

        <div v-if="astroError" class="error-banner">{{ astroError }}</div>

        <div v-if="astroResult" class="pipeline">
          <div class="pipeline-summary">
            <StatusBadge :status="astroResult.success" />
            <span class="pipeline-meta">traceId: {{ astroResult.traceId }}</span>
            <span class="pipeline-meta">{{ astroResult.executedSteps }} 步</span>
          </div>
          <PipelineStepCard
            v-for="(step, i) in astroSteps"
            :key="step.stepId || i"
            :index="i + 1"
            :title="step.title"
            :tool-name="step.toolName"
            :status="step.success"
            :cost-ms="step.costMs"
            :output="step.output"
            :error-message="step.errorMessage"
            :markdown="step.markdown"
            :last="i === astroSteps.length - 1"
            :default-expanded="step.markdown"
          />
        </div>
      </AppCard>

      <!-- Tabs -->
      <div class="section-tabs">
        <button class="section-tab" :class="{ active: tab === 'tools' }" @click="tab = 'tools'">
          能力目录 ({{ tools.length }})
        </button>
        <button class="section-tab" :class="{ active: tab === 'tasks' }" @click="tab = 'tasks'">
          自定义编排
        </button>
        <button class="reload-btn" @click="refreshTools" :disabled="loading">⟳ 刷新</button>
      </div>

      <!-- Capability catalog -->
      <div v-show="tab === 'tools'" class="catalog">
        <div v-if="loading" class="hint">加载中…</div>
        <div v-else-if="tools.length === 0" class="hint">没有可用工具</div>
        <div v-else class="catalog-grid">
          <AppCard
            v-for="t in tools"
            :key="t.toolName"
            clickable
            :title="t.displayName || t.toolName"
            :subtitle="t.description"
            @click="selectTool(t)"
          >
            <template #actions>
              <TagChip :label="t.sourceType" />
            </template>
            <div class="catalog-tags">
              <TagChip v-for="tag in (t.tags || [])" :key="tag" :label="`#${tag}`" />
            </div>
            <code class="tool-code">{{ t.toolName }}</code>

            <div v-if="current && current.toolName === t.toolName" class="tool-detail" @click.stop>
              <ParamField
                v-for="p in toolParams(t)"
                :key="p.name"
                :param="p"
                v-model="formData[p.name]"
              />
              <div class="custom-params" v-for="(c, i) in customParams" :key="i">
                <input class="key-input" v-model="c.key" placeholder="自定义参数 key" />
                <input class="val-input" v-model="c.value" placeholder="value (支持 JSON)" />
                <button class="del-btn" @click="customParams.splice(i, 1)">×</button>
              </div>
              <div class="tool-actions">
                <button class="add-btn" @click="addCustom">+ 自定义参数</button>
                <button class="run-btn" :disabled="executing" @click="executeTool">
                  {{ executing ? '执行中…' : '执行工具' }}
                </button>
              </div>

              <div v-if="toolError" class="error-banner">{{ toolError }}</div>
              <div v-if="toolResult">
                <div class="pipeline-summary">
                  <StatusBadge :status="toolResult.success" />
                  <span class="pipeline-meta">耗时 {{ toolResult.costMs }} ms</span>
                </div>
                <div v-if="!toolResult.success" class="error-banner">{{ toolResult.errorMessage }}</div>
                <CodeBlock v-else :content="toolResult.output" collapsible default-expanded />
              </div>
            </div>
          </AppCard>
        </div>
      </div>

      <!-- Custom orchestration -->
      <div v-show="tab === 'tasks'" class="orchestration">
        <AppCard title="自定义编排（JSON）" subtitle="编辑 steps 数组，每步指向已注册工具，支持 ${step:sX} 占位符引用上一步输出">
          <textarea v-model="taskJson" rows="14" class="task-editor"></textarea>
          <div class="tool-actions">
            <button class="run-btn" :disabled="taskRunning" @click="runCustomTask">
              {{ taskRunning ? '执行中…' : '运行编排' }}
            </button>
          </div>
          <div v-if="taskError" class="error-banner">{{ taskError }}</div>
        </AppCard>

        <div v-if="taskResult" class="pipeline">
          <div class="pipeline-summary">
            <StatusBadge :status="taskResult.success" />
            <span class="pipeline-meta">traceId: {{ taskResult.traceId }}</span>
            <span class="pipeline-meta">{{ taskResult.executedSteps }} 步</span>
          </div>
          <div v-if="taskResult.summary" class="task-summary">{{ taskResult.summary }}</div>
          <PipelineStepCard
            v-for="(r, i) in (taskResult.results || [])"
            :key="r.stepId || i"
            :index="i + 1"
            :title="r.stepId || `step-${i + 1}`"
            :tool-name="r.toolName"
            :status="r.success"
            :cost-ms="r.costMs"
            :output="r.output"
            :error-message="r.errorMessage"
            :last="i === (taskResult.results || []).length - 1"
          />
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import AppCard from '../components/ui/AppCard.vue'
import StatusBadge from '../components/ui/StatusBadge.vue'
import TagChip from '../components/ui/TagChip.vue'
import CodeBlock from '../components/ui/CodeBlock.vue'
import ParamField from '../components/ui/ParamField.vue'
import PipelineStepCard from '../components/ui/PipelineStepCard.vue'
import {
  listPlatformTools,
  executePlatformTool,
  executePlatformTask,
  runAstroDemo
} from '../services/api'

// ---------------- Astro demo ----------------

const today = new Date().toISOString().slice(0, 10)
const astroForm = reactive({
  latitude: 39.9042,
  longitude: 116.4074,
  date: today
})
const astroRunning = ref(false)
const astroError = ref(null)
const astroResult = ref(null)

const STEP_TITLES = {
  milkyway_rise: '银河升落时间计算',
  light_pollution: '光污染评估',
  cloud_cover: '云量预测',
  astro_plan_summary: '拍摄方案汇总（LLM）'
}

const astroSteps = computed(() => {
  if (!astroResult.value) return []
  return (astroResult.value.results || []).map((r) => ({
    ...r,
    title: STEP_TITLES[r.toolName] || r.stepId || r.toolName,
    markdown: r.toolName === 'astro_plan_summary'
  }))
})

const runAstro = async () => {
  astroRunning.value = true
  astroError.value = null
  astroResult.value = null
  try {
    astroResult.value = await runAstroDemo({
      latitude: Number(astroForm.latitude),
      longitude: Number(astroForm.longitude),
      date: astroForm.date
    })
  } catch (e) {
    astroError.value = e.message
  } finally {
    astroRunning.value = false
  }
}

// ---------------- Capability catalog ----------------

const tab = ref('tools')
const tools = ref([])
const loading = ref(false)
const current = ref(null)
const formData = reactive({})
const customParams = reactive([])
const toolResult = ref(null)
const toolError = ref(null)
const executing = ref(false)

const refreshTools = async () => {
  loading.value = true
  try { tools.value = await listPlatformTools() } catch (e) { toolError.value = e.message } finally { loading.value = false }
}

const toolParams = (t) => (t.requiredParams || []).map((p) => ({
  name: p,
  type: 'string',
  required: true,
  description: ''
}))

const selectTool = (t) => {
  if (current.value && current.value.toolName === t.toolName) {
    current.value = null
    return
  }
  current.value = t
  toolResult.value = null
  toolError.value = null
  Object.keys(formData).forEach(k => delete formData[k])
  customParams.length = 0
  for (const p of (t.requiredParams || [])) formData[p] = ''
}

const addCustom = () => { customParams.push({ key: '', value: '' }) }

const buildArgs = () => {
  const args = {}
  for (const p of (current.value.requiredParams || [])) {
    if (formData[p] !== undefined && formData[p] !== '') args[p] = formData[p]
  }
  for (const c of customParams) {
    if (!c.key) continue
    try { args[c.key] = JSON.parse(c.value) } catch { args[c.key] = c.value }
  }
  return args
}

const executeTool = async () => {
  if (!current.value) return
  executing.value = true
  toolError.value = null
  toolResult.value = null
  try {
    toolResult.value = await executePlatformTool(current.value.toolName, buildArgs())
  } catch (e) {
    toolError.value = e.message
  } finally {
    executing.value = false
  }
}

// ---------------- Custom orchestration ----------------

const taskRunning = ref(false)
const taskError = ref(null)
const taskResult = ref(null)
const taskJson = ref(JSON.stringify({
  maxSteps: 3,
  steps: [
    { stepId: 's1', toolName: 'webSearch', args: { query: 'Spring AI quickstart' }, required: true },
    { stepId: 's2', toolName: 'webScraping', args: { url: '${step:s1}' }, required: false }
  ]
}, null, 2))

const runCustomTask = async () => {
  taskRunning.value = true
  taskError.value = null
  taskResult.value = null
  try {
    const task = JSON.parse(taskJson.value)
    taskResult.value = await executePlatformTask(task)
  } catch (e) {
    taskError.value = e.message
  } finally {
    taskRunning.value = false
  }
}

onMounted(refreshTools)
</script>

<style scoped>
.ap-page {
  min-height: 100vh;
  background: var(--color-bg);
  display: flex;
  flex-direction: column;
}

.ap-header {
  padding: var(--space-6) var(--space-8) var(--space-4);
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
  color: #fff;
}

.back-link {
  color: rgba(255, 255, 255, 0.85);
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.85rem;
}

.ap-header h1 {
  margin: var(--space-2) 0 var(--space-1);
  font-size: 1.6rem;
}

.ap-subtitle {
  margin: 0;
  opacity: 0.85;
  font-size: 0.9rem;
}

.ap-main {
  flex: 1;
  padding: var(--space-6) var(--space-8);
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
}

.demo-form {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: var(--space-3);
}

.demo-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.demo-field label {
  font-size: 0.78rem;
  color: var(--color-text-muted);
  font-weight: 600;
}

.demo-field input {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 0.88rem;
  width: 160px;
}

.run-btn {
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-sm);
  padding: var(--space-2) var(--space-5);
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  height: 38px;
}

.run-btn:hover:not(:disabled) {
  background: var(--color-primary-dark);
}

.run-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error-banner {
  color: var(--color-error);
  background: var(--color-error-bg);
  border-radius: var(--radius-sm);
  padding: var(--space-2) var(--space-3);
  font-size: 0.85rem;
  margin-top: var(--space-3);
}

.pipeline {
  margin-top: var(--space-5);
  display: flex;
  flex-direction: column;
}

.pipeline-summary {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.pipeline-meta {
  font-size: 0.8rem;
  color: var(--color-text-subtle);
  font-family: var(--font-mono);
}

.task-summary {
  background: var(--color-primary-light);
  color: var(--color-text);
  border-radius: var(--radius-md);
  padding: var(--space-3) var(--space-4);
  margin-bottom: var(--space-4);
  font-size: 0.88rem;
}

.section-tabs {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.section-tab {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  padding: var(--space-2) var(--space-5);
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--color-text-muted);
  cursor: pointer;
}

.section-tab.active {
  background: var(--color-primary);
  color: #fff;
  border-color: var(--color-primary);
}

.reload-btn {
  margin-left: auto;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-2) var(--space-3);
  cursor: pointer;
  font-size: 0.85rem;
  color: var(--color-text-muted);
}

.hint {
  color: var(--color-text-subtle);
  font-size: 0.88rem;
}

.catalog-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--space-4);
}

.catalog-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  margin-bottom: var(--space-2);
}

.tool-code {
  display: inline-block;
  font-size: 0.75rem;
  color: var(--color-primary);
  background: var(--color-primary-light);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
}

.tool-detail {
  margin-top: var(--space-4);
  border-top: 1px solid var(--color-border);
  padding-top: var(--space-4);
}

.custom-params {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.custom-params .key-input {
  flex: 0 0 120px;
}

.custom-params .val-input {
  flex: 1;
}

.custom-params input {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
}

.del-btn {
  background: var(--color-error-bg);
  border: 1px solid var(--color-error);
  color: var(--color-error);
  border-radius: var(--radius-sm);
  width: 32px;
  cursor: pointer;
}

.tool-actions {
  display: flex;
  gap: var(--space-2);
  justify-content: flex-end;
  margin-top: var(--space-3);
}

.add-btn {
  background: var(--color-surface);
  border: 1px dashed var(--color-border-strong);
  color: var(--color-text-muted);
  border-radius: var(--radius-sm);
  padding: var(--space-2) var(--space-4);
  cursor: pointer;
}

.orchestration {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.task-editor {
  width: 100%;
  box-sizing: border-box;
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 0.8rem;
  resize: vertical;
}
</style>
