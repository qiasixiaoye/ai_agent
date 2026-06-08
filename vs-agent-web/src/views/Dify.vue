<template>
  <div class="dify-container">
    <div class="dify-header">
      <router-link to="/" class="back-link"><span>←</span> 返回首页</router-link>
      <h1>Dify 工作流</h1>
      <button class="reload-btn" @click="refreshHealth" :disabled="loadingHealth">⟳ 检测连接</button>
    </div>

    <!-- 连接状态 -->
    <section class="status-card">
      <div class="status-row">
        <span :class="['status-dot', healthOk ? 'ok' : 'fail']"></span>
        <span class="status-text">
          <template v-if="!health">检测中…</template>
          <template v-else-if="health.configured">
            Dify 已配置：<code>{{ health.baseUrl }}</code>
            <template v-if="health.defaultWorkflowId"> · 默认 workflow <code>{{ health.defaultWorkflowId }}</code></template>
          </template>
          <template v-else>
            <strong>Dify 未配置</strong> — 在 application-*.yml 设置
            <code>app.dify.base-url</code> 和 <code>app.dify.api-key</code>
          </template>
        </span>
      </div>
    </section>

    <div class="body">
      <!-- 左：调用 Dify Workflow -->
      <section class="card">
        <div class="card-title">调用 Dify Workflow</div>
        <div class="hint">如果留空将使用 app.dify.default-workflow-id。inputs 按你 workflow 的变量填。</div>
        <label>Workflow ID（可选）</label>
        <input v-model="workflowId" placeholder="default workflow id" />
        <label>User（end-user 标识，留空自动生成）</label>
        <input v-model="user" placeholder="caller-id" />
        <label>Inputs (JSON)</label>
        <textarea v-model="inputsJson" rows="8" class="editor"></textarea>

        <button class="run-btn" :disabled="running || !healthOk" @click="runWorkflow">
          {{ running ? '执行中…' : '执行 Workflow' }}
        </button>
        <div v-if="runError" class="err">{{ runError }}</div>
      </section>

      <!-- 右：双向集成提示 + 结果 -->
      <section class="card-stack">
        <div class="card info-card">
          <div class="card-title">反向：让 Dify 调用本服务的 Skill</div>
          <p class="hint">
            点击下方下载 OpenAPI spec，导入 Dify 后台「自定义工具集合」，即可在 Dify 工作流里拖拽调用所有已注册 Skill。
          </p>
          <div class="action-row">
            <a class="link-btn" :href="openApiUrl" target="_blank">📥 下载 OpenAPI</a>
            <code class="path">{{ openApiUrl }}</code>
          </div>
        </div>

        <div v-if="result || runError" class="card result-card">
          <div class="card-title">执行结果</div>
          <template v-if="result">
            <div class="result-summary">
              <span :class="['badge', result.success ? 'ok' : 'fail']">{{ result.success ? '成功' : '失败' }}</span>
              <span class="elapsed" v-if="result.elapsedMs">{{ result.elapsedMs }} ms</span>
              <span class="badge gray" v-if="result.status">{{ result.status }}</span>
              <span class="badge gray" v-if="result.workflowRunId">run: {{ result.workflowRunId.slice(0, 8) }}</span>
            </div>
            <div v-if="result.errorMessage" class="err">{{ result.errorMessage }}</div>
            <div v-if="result.outputs">
              <div class="sub-title">Outputs</div>
              <pre class="json">{{ JSON.stringify(result.outputs, null, 2) }}</pre>
            </div>
            <details v-if="result.rawResponse" class="raw">
              <summary>原始响应</summary>
              <pre class="json">{{ result.rawResponse }}</pre>
            </details>
          </template>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { difyHealth, runDifyWorkflow, skillOpenApiUrl } from '../services/api'

const health = ref(null)
const loadingHealth = ref(false)
const workflowId = ref('')
const user = ref('')
const inputsJson = ref('{\n  "query": "hello dify"\n}')
const running = ref(false)
const runError = ref(null)
const result = ref(null)

const healthOk = computed(() => health.value?.configured === true)
const openApiUrl = computed(() => skillOpenApiUrl())

const refreshHealth = async () => {
  loadingHealth.value = true
  try { health.value = await difyHealth() } catch (e) { runError.value = e.message } finally { loadingHealth.value = false }
}

const runWorkflow = async () => {
  running.value = true
  runError.value = null
  result.value = null
  try {
    let inputs = {}
    try { inputs = JSON.parse(inputsJson.value) } catch { throw new Error('inputs 不是合法 JSON') }
    result.value = await runDifyWorkflow({
      workflowId: workflowId.value || undefined,
      inputs,
      user: user.value || undefined
    })
  } catch (e) {
    runError.value = e.message
  } finally {
    running.value = false
  }
}

onMounted(refreshHealth)
</script>

<style scoped>
.dify-container { display: flex; flex-direction: column; height: 100vh; background: #f5f7fb; color: #222; }
.dify-header { display: flex; align-items: center; padding: 12px 20px; background: #1f9b9b; color: white; }
.dify-header h1 { margin: 0 auto; font-size: 1.4rem; }
.back-link { color: white; text-decoration: none; display: flex; align-items: center; }
.back-link span { font-size: 1.2rem; margin-right: 5px; }
.reload-btn { background: rgba(255,255,255,0.2); color: white; border: 1px solid rgba(255,255,255,0.4); border-radius: 4px; padding: 5px 10px; cursor: pointer; }
.reload-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.status-card { margin: 16px 20px 0; background: white; border: 1px solid #e5e8f0; border-radius: 8px; padding: 12px 16px; }
.status-row { display: flex; gap: 10px; align-items: center; font-size: 14px; }
.status-dot { width: 10px; height: 10px; border-radius: 50%; background: #d33; }
.status-dot.ok { background: #1a7a1a; }
.status-text code { background: #f0f2f7; padding: 1px 5px; border-radius: 3px; font-size: 12px; color: #1f9b9b; }

.body { flex: 1; display: flex; gap: 16px; padding: 16px 20px 20px; overflow: hidden; }
.card { background: white; border: 1px solid #e5e8f0; border-radius: 8px; padding: 16px; }
.card-stack { flex: 1; display: flex; flex-direction: column; gap: 16px; overflow-y: auto; }
.card.info-card { background: #f0fbfb; border-color: #b8e1e1; }
.card-title { font-weight: 600; color: #2a3a55; margin-bottom: 10px; }
.sub-title { font-weight: 600; color: #6c7a99; margin: 10px 0 4px; font-size: 13px; }
.hint { color: #6c7a99; font-size: 13px; margin-bottom: 8px; }
label { display: block; font-size: 12px; color: #555; margin: 8px 0 4px; }
input, .editor { width: 100%; box-sizing: border-box; padding: 7px 10px; border: 1px solid #d0d7e2; border-radius: 6px; font-size: 13px; font-family: inherit; }
.editor { font-family: monospace; font-size: 12.5px; resize: vertical; }
.body > .card:first-child { width: 480px; min-width: 380px; overflow-y: auto; }
.run-btn { margin-top: 14px; background: #1f9b9b; color: white; border: none; border-radius: 6px; padding: 8px 18px; font-size: 14px; cursor: pointer; }
.run-btn:hover:not(:disabled) { background: #157777; }
.run-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.err { margin-top: 8px; color: #b01a1a; font-size: 13px; }
.action-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: 6px; }
.link-btn { background: #1f9b9b; color: white; text-decoration: none; padding: 6px 12px; border-radius: 6px; font-size: 13px; }
.path { background: white; border: 1px solid #d8dde6; border-radius: 4px; padding: 2px 6px; font-family: monospace; font-size: 12px; word-break: break-all; color: #555; }
.result-summary { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 8px; }
.badge { padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; background: #e5e8f0; color: #555; }
.badge.ok { background: #e0f5e0; color: #1a7a1a; }
.badge.fail { background: #fde0e0; color: #b01a1a; }
.badge.gray { background: #eef0f5; color: #6c7a99; font-weight: normal; }
.elapsed { color: #8a96b3; font-size: 12px; }
.json { background: #1e1e1e; color: #d4d4d4; border-radius: 6px; padding: 12px; overflow: auto; font-size: 12.5px; line-height: 1.5; max-height: 300px; white-space: pre-wrap; }
.raw summary { color: #6c7a99; cursor: pointer; font-size: 12px; margin-top: 8px; }
</style>
