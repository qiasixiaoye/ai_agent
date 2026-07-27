<template>
  <section class="workspace">
    <header class="workspace-header">
      <div>
        <h1>开发调试</h1>
        <p class="muted">用于验证底层 Tool、JSON 任务与多步骤编排。普通提问、Skill 和记忆请从对话入口开始。</p>
      </div>
      <div class="header-actions"><button type="button" @click="router.push('/')">从对话入口开始</button><button type="button" :disabled="catalogLoading" @click="refreshTools">{{ catalogLoading ? '刷新中…' : '刷新能力' }}</button></div>
    </header>

    <p v-if="localError" class="status-error">{{ localError }}</p>

    <div class="workspace-grid">
      <WorkbenchSection title="能力调用" class="panel">
        <p v-if="!catalogLoading && !tools.length" class="muted">当前后端未返回可直接调用的 Agent 能力。</p>
        <label for="platform-tool">能力</label>
        <select id="platform-tool" v-model="selectedToolName" :disabled="!tools.length || toolRunning">
          <option v-for="tool in tools" :key="toolKey(tool)" :value="toolKey(tool)">{{ toolLabel(tool) }}</option>
        </select>
        <p v-if="selectedTool" class="muted">{{ describe(selectedTool.description, '该能力暂未提供中文说明。') }}</p>
        <p v-if="selectedTool?.requiredParams?.length" class="muted">必填参数：{{ selectedTool.requiredParams.join(', ') }}</p>
        <label for="platform-arguments">入参 JSON 对象</label>
        <textarea id="platform-arguments" v-model="toolArgsJson" rows="7" spellcheck="false" />
        <p v-if="toolJsonError" class="status-error">{{ toolJsonError }}</p>
        <button type="button" :disabled="!selectedToolName || toolRunning" @click="runTool">{{ toolRunning ? '执行中…' : '执行能力' }}</button>
        <pre v-if="toolResult">{{ pretty(toolResult) }}</pre>
      </WorkbenchSection>

      <div class="stack">
        <WorkbenchSection title="任务 JSON" class="panel">
          <p class="muted">适合验证多步骤任务编排，粘贴任务对象后直接运行。</p>
          <textarea v-model="taskJson" rows="10" spellcheck="false" aria-label="Task JSON" />
          <p v-if="taskJsonError" class="status-error">{{ taskJsonError }}</p>
          <button type="button" :disabled="taskRunning" @click="runTask">{{ taskRunning ? '执行中…' : '运行任务' }}</button>
          <pre v-if="taskResult">{{ pretty(taskResult) }}</pre>
        </WorkbenchSection>

        <WorkbenchSection title="演示任务" class="panel">
          <label for="demo-query">任务描述</label>
          <input id="demo-query" v-model="demoQuery" placeholder="规划一个简短的研究任务" @keyup.enter="runDemoTask" />
          <button type="button" :disabled="demoRunning || !demoQuery.trim()" @click="runDemoTask">{{ demoRunning ? '执行中…' : '运行演示' }}</button>
          <pre v-if="demoResult">{{ pretty(demoResult) }}</pre>
        </WorkbenchSection>
      </div>
    </div>

    <WorkbenchSection title="星空摄影演示" class="panel astro-panel">
      <p class="muted">运行内置多步骤规划流程，用于证明 Agent 可串联多个工具完成任务。</p>
      <div class="astro-form">
        <label>纬度 <input v-model.number="astro.latitude" type="number" step="0.0001" /></label>
        <label>经度 <input v-model.number="astro.longitude" type="number" step="0.0001" /></label>
        <label>日期 <input v-model="astro.date" type="date" /></label>
        <button type="button" :disabled="astroRunning" @click="runAstro">{{ astroRunning ? '执行中…' : '运行星空演示' }}</button>
      </div>
      <pre v-if="astroResult">{{ pretty(astroResult) }}</pre>
    </WorkbenchSection>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import WorkbenchSection from '../../components/workbench/WorkbenchSection.vue'
import {
  executePlatformDemoTask,
  executePlatformTask,
  executePlatformTool,
  listPlatformTools,
  runAstroDemo
} from '../../services/api'
import { useWorkbenchStore } from '../../stores/workbench'
import { parseJsonObject } from '../../utils/streamLifecycle'
import { localizedDescription } from '../../utils/productText'
import { useRouter } from 'vue-router'

const workbench = useWorkbenchStore()
const router = useRouter()
const tools = ref([])
const catalogLoading = ref(false)
const selectedToolName = ref('')
const toolArgsJson = ref('{}')
const toolJsonError = ref('')
const toolResult = ref(null)
const toolRunning = ref(false)
const taskJson = ref(JSON.stringify({ maxSteps: 1, steps: [] }, null, 2))
const taskJsonError = ref('')
const taskResult = ref(null)
const taskRunning = ref(false)
const demoQuery = ref('规划一个简短的研究任务。')
const demoResult = ref(null)
const demoRunning = ref(false)
const astroResult = ref(null)
const astroRunning = ref(false)
const localError = ref('')
const astro = reactive({ latitude: 39.9042, longitude: 116.4074, date: new Date().toISOString().slice(0, 10) })

const toolKey = (tool) => tool?.toolName || tool?.name || tool?.id || ''
const toolLabel = (tool) => tool?.displayName || toolKey(tool) || '未命名能力'
const selectedTool = computed(() => tools.value.find((tool) => toolKey(tool) === selectedToolName.value) || null)
const pretty = (value) => JSON.stringify(value, null, 2)
const invocationStatus = (result) => result?.success === false ? 'failed' : 'complete'
const resultSummary = (result, fallback) => result?.errorMessage || result?.message || result?.summary || fallback
const record = (name, result, fallback) => workbench.addInvocation({ source: 'agent-platform', name, status: invocationStatus(result), summary: resultSummary(result, fallback) })
const describe = (value, fallback) => localizedDescription(value, fallback)

const refreshTools = async () => {
  catalogLoading.value = true
  localError.value = ''
  try {
    tools.value = await listPlatformTools()
    if (!tools.value.some((tool) => toolKey(tool) === selectedToolName.value)) selectedToolName.value = toolKey(tools.value[0])
  } catch (error) {
    localError.value = error.message || '能力目录加载失败。'
  } finally {
    catalogLoading.value = false
  }
}

const runTool = async () => {
  toolJsonError.value = ''
  localError.value = ''
  try {
    const args = parseJsonObject(toolArgsJson.value)
    toolRunning.value = true
    toolResult.value = await executePlatformTool(selectedToolName.value, args)
    record(toolLabel(selectedTool.value), toolResult.value, '能力执行完成。')
  } catch (error) {
    if (error.message?.startsWith('Arguments')) toolJsonError.value = error.message
    else localError.value = error.message || 'Platform tool execution failed.'
  } finally {
    toolRunning.value = false
  }
}

const runTask = async () => {
  taskJsonError.value = ''
  localError.value = ''
  try {
    const task = parseJsonObject(taskJson.value, 'Task')
    taskRunning.value = true
    taskResult.value = await executePlatformTask(task)
    record('执行任务', taskResult.value, '任务执行完成。')
  } catch (error) {
    if (error.message?.startsWith('Task')) taskJsonError.value = error.message
    else localError.value = error.message || 'Direct task execution failed.'
  } finally {
    taskRunning.value = false
  }
}

const runDemoTask = async () => {
  localError.value = ''
  demoRunning.value = true
  try {
    demoResult.value = await executePlatformDemoTask(demoQuery.value.trim())
    record('演示任务', demoResult.value, '演示任务完成。')
  } catch (error) {
    localError.value = error.message || 'Demo task execution failed.'
  } finally {
    demoRunning.value = false
  }
}

const runAstro = async () => {
  localError.value = ''
  astroRunning.value = true
  try {
    astroResult.value = await runAstroDemo(astro)
    record('星空演示', astroResult.value, '星空演示完成。')
  } catch (error) {
    localError.value = error.message || 'Astro demonstration failed.'
  } finally {
    astroRunning.value = false
  }
}

onMounted(refreshTools)
</script>

<style scoped>
.workspace { display: grid; gap: 16px; padding: 20px; }.workspace-header, .header-actions { display: flex; align-items: center; justify-content: space-between; gap: 14px; }.header-actions { flex-wrap: wrap; }.workspace-header h1 { margin: 0 0 4px; font-size: 1.2rem; }.workspace-header p { margin: 0; }.workspace-grid { display: grid; grid-template-columns: minmax(280px, .9fr) minmax(300px, 1.1fr); gap: 16px; }.stack, .panel { display: grid; align-content: start; gap: 10px; }.stack { gap: 16px; }.panel label { color: var(--color-text-muted); font-size: .8rem; }.panel textarea, .panel select, .panel input { width: 100%; box-sizing: border-box; padding: 9px; color: var(--color-text); background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }.panel pre { max-height: 300px; margin: 0; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; padding: 10px; color: var(--color-text); background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); font-size: .78rem; }.astro-form { display: flex; flex-wrap: wrap; align-items: end; gap: 10px; }.astro-form label { display: grid; gap: 5px; min-width: 150px; }.status-error { margin: 0; color: var(--color-danger); }button { cursor: pointer; }button:disabled { opacity: .55; cursor: not-allowed; }@media (max-width: 800px) { .workspace { padding: 14px; }.workspace-grid { grid-template-columns: 1fr; }.workspace-header { align-items: flex-start; flex-direction: column; } }
</style>
