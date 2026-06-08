<template>
  <div class="ap-container">
    <div class="ap-header">
      <router-link to="/" class="back-link"><span>←</span> 返回首页</router-link>
      <h1>Agent 平台</h1>
      <div class="header-actions">
        <button class="tab" :class="{ active: tab === 'tools' }" @click="tab = 'tools'">工具 ({{ tools.length }})</button>
        <button class="tab" :class="{ active: tab === 'tasks' }" @click="tab = 'tasks'">任务编排</button>
        <button class="reload-btn" @click="refreshTools" :disabled="loading">⟳</button>
      </div>
    </div>

    <!-- Tools Tab -->
    <div v-show="tab === 'tools'" class="tab-body">
      <aside class="tools-list">
        <div class="list-title">已注册工具</div>
        <div v-if="loading" class="hint">加载中…</div>
        <div v-else-if="tools.length === 0" class="hint">没有工具</div>
        <ul v-else>
          <li
            v-for="t in tools"
            :key="t.toolName"
            :class="['tool-item', { active: current && current.toolName === t.toolName }]"
            @click="selectTool(t)"
          >
            <div class="tool-name">{{ t.displayName || t.toolName }}</div>
            <div class="tool-desc">{{ t.description }}</div>
            <div class="tool-meta">
              <span class="badge">{{ t.sourceType }}</span>
              <span class="badge tag" v-for="tag in (t.tags || [])" :key="tag">#{{ tag }}</span>
            </div>
          </li>
        </ul>
      </aside>

      <section class="tools-detail">
        <div v-if="!current" class="empty">
          <div class="empty-icon">🛠</div>
          <p>左侧选一个工具开始</p>
        </div>

        <template v-else>
          <div class="detail-head">
            <h2>{{ current.displayName || current.toolName }}</h2>
            <code class="tool-code">{{ current.toolName }}</code>
            <p class="detail-desc">{{ current.description }}</p>
            <div class="detail-meta">
              <span class="badge">{{ current.sourceType }}</span>
              <span class="badge" v-if="current.timeoutMs">timeout {{ current.timeoutMs }}ms</span>
              <span class="badge tag" v-for="tag in (current.tags || [])" :key="tag">#{{ tag }}</span>
            </div>
          </div>

          <div class="form-card">
            <div class="card-title">参数</div>
            <div v-if="(current.requiredParams || []).length === 0 && customParams.length === 0" class="hint">
              该工具未声明必填参数，可点击下方按钮添加自定义参数。
            </div>

            <!-- 必填参数（来自 metadata） -->
            <div class="form-row" v-for="p in (current.requiredParams || [])" :key="p">
              <label><span class="pname">{{ p }}</span><span class="prequired">*</span></label>
              <textarea v-model="formData[p]" rows="2" :placeholder="`${p} (required)`"></textarea>
            </div>

            <!-- 自定义参数（任意 key/value） -->
            <div class="form-row custom" v-for="(c, i) in customParams" :key="i">
              <label>
                <input class="key-input" v-model="c.key" placeholder="key" />
                <button class="del-btn" @click="customParams.splice(i, 1)">×</button>
              </label>
              <textarea v-model="c.value" rows="2" placeholder="value"></textarea>
            </div>

            <div class="actions">
              <button class="add-btn" @click="addCustom">+ 添加参数</button>
              <button class="execute-btn" :disabled="executing" @click="executeTool">
                {{ executing ? '执行中…' : '执行工具' }}
              </button>
            </div>
          </div>

          <div v-if="toolResult || toolError" class="result-card">
            <div class="card-title">执行结果</div>
            <div v-if="toolError" class="fail">{{ toolError }}</div>
            <div v-else>
              <div class="result-summary">
                <span :class="['result-badge', toolResult.success ? 'ok' : 'fail']">{{ toolResult.success ? '成功' : '失败' }}</span>
                <span class="result-elapsed">耗时 {{ toolResult.costMs }} ms</span>
              </div>
              <div v-if="!toolResult.success" class="fail"><strong>错误：</strong>{{ toolResult.errorMessage }}</div>
              <pre v-else class="result-data">{{ toolResult.output }}</pre>
            </div>
          </div>
        </template>
      </section>
    </div>

    <!-- Tasks Tab -->
    <div v-show="tab === 'tasks'" class="tab-body tasks-tab">
      <div class="tasks-left">
        <div class="card">
          <div class="card-title">快速 Demo</div>
          <div class="hint">用 query 触发后端预置的 demo 编排流程（搜索 → 抓取 → 汇总）。</div>
          <input v-model="demoQuery" placeholder="例：Spring AI 最佳实践" />
          <button class="execute-btn" :disabled="demoRunning" @click="runDemo">{{ demoRunning ? '执行中…' : '运行 Demo 编排' }}</button>
          <div v-if="demoError" class="fail">{{ demoError }}</div>
        </div>

        <div class="card">
          <div class="card-title">自定义任务（JSON）</div>
          <div class="hint">编辑下方 JSON 指定 steps；每步指向已有 toolName。</div>
          <textarea v-model="taskJson" rows="16" class="task-editor"></textarea>
          <button class="execute-btn" :disabled="taskRunning" @click="runCustomTask">{{ taskRunning ? '执行中…' : '运行任务' }}</button>
          <div v-if="taskError" class="fail">{{ taskError }}</div>
        </div>
      </div>

      <section class="tasks-right">
        <div v-if="!taskResult" class="empty">
          <div class="empty-icon">🧬</div>
          <p>左侧执行任务后这里看到每一步结果</p>
        </div>
        <template v-else>
          <div class="card">
            <div class="result-summary">
              <span :class="['result-badge', taskResult.success ? 'ok' : 'fail']">{{ taskResult.success ? '成功' : '失败' }}</span>
              <span class="result-elapsed">{{ taskResult.executedSteps }} 步</span>
            </div>
            <div v-if="taskResult.summary" class="task-summary">
              <strong>Summary:</strong> {{ taskResult.summary }}
            </div>
          </div>
          <div class="card" v-for="(r, i) in (taskResult.results || [])" :key="i">
            <div class="step-head">
              <strong>Step {{ i + 1 }}</strong>
              <code>{{ r.toolName }}</code>
              <span :class="['result-badge', r.success ? 'ok' : 'fail']">{{ r.success ? 'OK' : 'FAIL' }}</span>
              <span class="result-elapsed">{{ r.costMs }} ms</span>
            </div>
            <pre class="result-data" v-if="r.success">{{ r.output }}</pre>
            <div v-else class="fail">{{ r.errorMessage }}</div>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import {
  listPlatformTools,
  executePlatformTool,
  executePlatformTask,
  executePlatformDemoTask
} from '../services/api'

const tab = ref('tools')

// Tools state
const tools = ref([])
const current = ref(null)
const formData = reactive({})
const customParams = reactive([])
const toolResult = ref(null)
const toolError = ref(null)
const executing = ref(false)
const loading = ref(false)

// Tasks state
const demoQuery = ref('Spring AI 最佳实践')
const demoRunning = ref(false)
const demoError = ref(null)
const taskRunning = ref(false)
const taskError = ref(null)
const taskResult = ref(null)
const taskJson = ref(JSON.stringify({
  maxSteps: 3,
  steps: [
    { stepId: 's1', toolName: 'webSearch', args: { query: 'Spring AI quickstart' }, required: true },
    { stepId: 's2', toolName: 'webScraping', args: { url: '<上一步选一个 url>' }, required: false }
  ]
}, null, 2))

const refreshTools = async () => {
  loading.value = true
  try { tools.value = await listPlatformTools() } catch (e) { toolError.value = e.message } finally { loading.value = false }
}

const selectTool = (t) => {
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
    // 尝试 JSON 解析，失败就当字符串
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

const runDemo = async () => {
  demoRunning.value = true
  demoError.value = null
  taskResult.value = null
  try {
    taskResult.value = await executePlatformDemoTask(demoQuery.value)
  } catch (e) {
    demoError.value = e.message
  } finally {
    demoRunning.value = false
  }
}

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
.ap-container { display: flex; flex-direction: column; height: 100vh; background: #f5f7fb; color: #222; }
.ap-header { display: flex; align-items: center; padding: 12px 20px; background: #8a5a2b; color: white; }
.ap-header h1 { margin: 0 auto; font-size: 1.4rem; }
.back-link { color: white; text-decoration: none; display: flex; align-items: center; }
.back-link span { font-size: 1.2rem; margin-right: 5px; }
.header-actions { display: flex; gap: 6px; align-items: center; }
.tab { background: rgba(255,255,255,0.15); color: white; border: 1px solid rgba(255,255,255,0.35); border-radius: 4px; padding: 5px 10px; cursor: pointer; font-size: 13px; }
.tab.active { background: white; color: #8a5a2b; font-weight: 600; }
.reload-btn { background: rgba(255,255,255,0.18); color: white; border: 1px solid rgba(255,255,255,0.4); border-radius: 4px; padding: 4px 10px; cursor: pointer; }

.tab-body { flex: 1; display: flex; overflow: hidden; }

/* Tools tab */
.tools-list { width: 320px; min-width: 280px; background: white; border-right: 1px solid #e5e8f0; overflow-y: auto; padding: 12px; }
.list-title { font-weight: 600; margin-bottom: 8px; color: #8a5a2b; }
.tools-list ul { list-style: none; padding: 0; margin: 0; }
.tool-item { padding: 10px 12px; border-radius: 6px; margin-bottom: 6px; cursor: pointer; border: 1px solid transparent; }
.tool-item:hover { background: #fbf4ec; }
.tool-item.active { background: #f5e8d6; border-color: #8a5a2b; }
.tool-name { font-weight: 600; color: #2a3a55; }
.tool-desc { font-size: 12px; color: #6c7a99; margin-top: 4px; }
.tool-meta { margin-top: 6px; display: flex; flex-wrap: wrap; gap: 4px; }
.badge { display: inline-block; font-size: 11px; padding: 2px 6px; border-radius: 4px; background: #e5e8f0; color: #8a5a2b; }
.badge.tag { background: #fff0e0; color: #b06000; }
.tools-detail { flex: 1; overflow-y: auto; padding: 20px 28px; }
.empty { text-align: center; padding-top: 100px; color: #8a96b3; }
.empty-icon { font-size: 56px; }
.detail-head { margin-bottom: 16px; }
.detail-head h2 { margin: 0; }
.tool-code { display: inline-block; background: #f0f2f7; padding: 2px 6px; border-radius: 4px; font-size: 13px; color: #8a5a2b; }
.detail-desc { color: #555; margin: 8px 0; }
.detail-meta { display: flex; gap: 6px; flex-wrap: wrap; }
.form-card, .result-card, .card { background: white; border: 1px solid #e5e8f0; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
.card-title { font-weight: 600; color: #2a3a55; margin-bottom: 10px; }
.form-row { margin-bottom: 12px; }
.form-row label { display: flex; gap: 8px; align-items: center; font-size: 13px; margin-bottom: 4px; }
.pname { font-weight: 600; color: #2a3a55; }
.prequired { color: #d33; }
.form-row textarea, .form-row input, .task-editor, .tasks-left input {
  width: 100%; box-sizing: border-box; padding: 8px 10px; border: 1px solid #d0d7e2; border-radius: 6px; font-size: 14px; font-family: inherit;
}
.form-row textarea, .task-editor { resize: vertical; font-family: monospace; font-size: 12.5px; }
.key-input { flex: 1; padding: 4px 8px; border: 1px solid #d0d7e2; border-radius: 4px; }
.del-btn { background: #fff5f5; border: 1px solid #f3d2d2; color: #b01a1a; border-radius: 4px; width: 26px; height: 26px; cursor: pointer; }
.actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 12px; }
.add-btn { background: white; border: 1px dashed #b0b6c2; color: #6c7a99; border-radius: 6px; padding: 6px 12px; cursor: pointer; }
.execute-btn { background: #8a5a2b; color: white; border: none; border-radius: 6px; padding: 8px 18px; font-size: 14px; cursor: pointer; }
.execute-btn:hover:not(:disabled) { background: #6f471f; }
.execute-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.result-summary { display: flex; gap: 10px; align-items: center; margin-bottom: 8px; }
.result-badge { padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; }
.result-badge.ok { background: #e0f5e0; color: #1a7a1a; }
.result-badge.fail { background: #fde0e0; color: #b01a1a; }
.result-elapsed { color: #8a96b3; font-size: 12px; }
.fail { color: #b01a1a; font-size: 13px; }
.result-data { background: #1e1e1e; color: #d4d4d4; border-radius: 6px; padding: 12px; overflow-x: auto; font-size: 12.5px; line-height: 1.5; max-height: 360px; overflow-y: auto; white-space: pre-wrap; }

/* Tasks tab */
.tasks-tab { padding: 16px 20px; gap: 16px; }
.tasks-left { width: 480px; min-width: 380px; display: flex; flex-direction: column; gap: 16px; overflow-y: auto; }
.tasks-right { flex: 1; overflow-y: auto; padding-left: 16px; }
.hint { color: #8a96b3; font-size: 12px; margin: 6px 0 8px; }
.task-summary { margin-top: 6px; font-size: 13px; color: #555; }
.step-head { display: flex; gap: 10px; align-items: center; margin-bottom: 8px; }
.step-head code { background: #f0f2f7; padding: 2px 6px; border-radius: 4px; font-size: 12px; color: #8a5a2b; }
</style>
