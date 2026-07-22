<template>
  <div class="obs-container">
    <div class="obs-header">
      <button class="back-link" @click="goBack"><span>←</span> 返回上一页</button>
      <router-link to="/" class="back-link home">首页</router-link>
      <h1>执行日志查询</h1>
    </div>

    <div class="obs-content">
      <section class="panel">
        <h2>按请求查询完整链路</h2>
        <div class="form-row">
          <input v-model.trim="requestId" placeholder="请输入 requestId" />
          <button :disabled="loadingTrace || !requestId" @click="handleTraceQuery">查询</button>
        </div>
        <p v-if="traceError" class="error">{{ traceError }}</p>
        <div v-if="traceResult" class="result-card">
          <!-- 顶部故障横幅：失败时直接给整体错误 + 定位故障点（第一个失败 stage） -->
          <div :class="['trace-banner', traceFailed ? 'bad' : 'ok']">
            <template v-if="traceFailed">
              <div class="banner-title">
                ❌ 运行失败
                <span v-if="firstFailedStage" class="fault-point">
                  · 故障点：{{ firstFailedStage.stageName }}{{ firstFailedStage.toolName ? '(' + firstFailedStage.toolName + ')' : '' }}
                </span>
              </div>
              <div v-if="bannerError" class="banner-err">{{ bannerError }}</div>
            </template>
            <div v-else class="banner-title">✅ 全部成功</div>
          </div>

          <div class="trace-meta">
            <span>scene {{ traceResult.request?.scene }}</span>
            <span>· status {{ traceResult.request?.status }}</span>
            <span>· {{ traceResult.request?.totalCostMs ?? '-' }} ms</span>
            <span>· model {{ traceResult.request?.modelName || '-' }}</span>
            <span class="mono">· {{ traceResult.request?.requestId }}</span>
          </div>

          <label class="only-failed">
            <input type="checkbox" v-model="onlyFailed" /> 只看失败（{{ failedCount }}）
          </label>

          <!-- 时间线卡片：每个 stage 一张，失败标红并露出 error + 入/出参 -->
          <div class="timeline">
            <div v-for="stage in visibleStages" :key="stage.id"
                 :class="['stage-card', stage.success === false ? 'bad' : 'ok']">
              <div class="stage-head">
                <span class="stage-icon">{{ stage.success === false ? '✗' : '✓' }}</span>
                <span class="stage-type">{{ stage.stageType }}</span>
                <span class="stage-name">{{ stage.stageName }}{{ stage.toolName ? ' · ' + stage.toolName : '' }}</span>
                <span class="stage-cost">{{ stage.costMs != null ? stage.costMs + 'ms' : '' }}</span>
                <span class="stage-time">{{ stage.eventTime || '' }}</span>
              </div>
              <div v-if="stage.errorMessage" class="stage-err">err: {{ stage.errorMessage }}</div>
              <div v-if="stage.inputPayload || stage.outputPayload" class="stage-io">
                <button v-if="stage.inputPayload" class="io-toggle" @click="toggleIO(stage.id, 'in')">
                  {{ isIOOpen(stage.id, 'in') ? '▾' : '▸' }} 入参
                </button>
                <button v-if="stage.outputPayload" class="io-toggle" @click="toggleIO(stage.id, 'out')">
                  {{ isIOOpen(stage.id, 'out') ? '▾' : '▸' }} 出参
                </button>
                <pre v-if="isIOOpen(stage.id, 'in')" class="io-body">{{ stage.inputPayload }}</pre>
                <pre v-if="isIOOpen(stage.id, 'out')" class="io-body">{{ stage.outputPayload }}</pre>
              </div>
            </div>
            <p v-if="!visibleStages.length" class="muted">无{{ onlyFailed ? '失败' : '' }}阶段</p>
          </div>
        </div>
      </section>

      <section class="panel">
        <h2>按会话查询历史</h2>
        <div class="form-row">
          <input v-model.trim="sessionId" placeholder="请输入 sessionId" />
          <input v-model.number="sessionLimit" type="number" min="1" max="200" placeholder="limit" />
          <button :disabled="loadingSession || !sessionId" @click="handleSessionQuery">查询</button>
        </div>
        <p v-if="sessionError" class="error">{{ sessionError }}</p>
        <div v-if="sessionRows.length" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>requestId</th>
                <th>scene</th>
                <th>status</th>
                <th>cost(ms)</th>
                <th>startedAt</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in sessionRows" :key="row.requestId">
                <td>{{ row.requestId }}</td>
                <td>{{ row.scene || '-' }}</td>
                <td>{{ row.status || '-' }}</td>
                <td>{{ row.totalCostMs ?? '-' }}</td>
                <td>{{ row.startedAt || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="panel">
        <h2>按时间范围查询失败请求</h2>
        <div class="form-row">
          <input v-model="failStart" type="datetime-local" />
          <input v-model="failEnd" type="datetime-local" />
          <input v-model.number="failLimit" type="number" min="1" max="500" placeholder="limit" />
          <button :disabled="loadingFail || !failStart || !failEnd" @click="handleFailQuery">查询</button>
        </div>
        <p v-if="failError" class="error">{{ failError }}</p>
        <div v-if="failedRows.length" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>requestId</th>
                <th>sessionId</th>
                <th>scene</th>
                <th>error</th>
                <th>startedAt</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in failedRows" :key="row.requestId">
                <td>{{ row.requestId }}</td>
                <td>{{ row.sessionId || '-' }}</td>
                <td>{{ row.scene || '-' }}</td>
                <td class="error-text">{{ row.errorMessage || '-' }}</td>
                <td>{{ row.startedAt || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { queryFailedRequests, queryRequestTrace, querySessionRequests } from '../services/api'

const route = useRoute()
const router = useRouter()

// 返回来时的页面（如工作流生产）；无历史则回首页。配合 App.vue 的 keep-alive，
// 返回后上一页已生成的结果仍在。
const goBack = () => {
  if (window.history.length > 1) router.back()
  else router.push('/')
}
const requestId = ref('')
const sessionId = ref('')
const sessionLimit = ref(20)
const failStart = ref('')
const failEnd = ref('')
const failLimit = ref(100)

const loadingTrace = ref(false)
const loadingSession = ref(false)
const loadingFail = ref(false)

const traceError = ref('')
const sessionError = ref('')
const failError = ref('')

const traceResult = ref(null)
const sessionRows = ref([])
const failedRows = ref([])

// ---- 轨迹错误分析视图 ----
const onlyFailed = ref(false)
const openIO = ref({})

const traceStages = computed(() => traceResult.value?.stages || [])
const traceFailed = computed(() => {
  const s = traceResult.value?.request?.status
  return s ? String(s).toUpperCase() === 'FAILED' : false
})
const firstFailedStage = computed(() => traceStages.value.find((s) => s.success === false) || null)
const failedCount = computed(() => traceStages.value.filter((s) => s.success === false).length)
const bannerError = computed(() =>
  traceResult.value?.request?.errorMessage || firstFailedStage.value?.errorMessage || '')
const visibleStages = computed(() =>
  onlyFailed.value ? traceStages.value.filter((s) => s.success === false) : traceStages.value)

const ioKey = (id, which) => `${id}:${which}`
const toggleIO = (id, which) => {
  const k = ioKey(id, which)
  openIO.value = { ...openIO.value, [k]: !openIO.value[k] }
}
const isIOOpen = (id, which) => !!openIO.value[ioKey(id, which)]

const toBackendTime = (value) => value

const handleTraceQuery = async () => {
  traceError.value = ''
  loadingTrace.value = true
  try {
    traceResult.value = await queryRequestTrace(requestId.value)
  } catch (error) {
    traceResult.value = null
    traceError.value = error?.message || '查询失败'
  } finally {
    loadingTrace.value = false
  }
}

const handleSessionQuery = async () => {
  sessionError.value = ''
  loadingSession.value = true
  try {
    sessionRows.value = await querySessionRequests(sessionId.value, sessionLimit.value || 20)
  } catch (error) {
    sessionRows.value = []
    sessionError.value = error?.message || '查询失败'
  } finally {
    loadingSession.value = false
  }
}

// 从 /observability?requestId=xxx（&sessionId=yyy）带参进入时，自动查询，无需手动粘贴 id
onMounted(() => {
  const qrid = route.query.requestId
  if (typeof qrid === 'string' && qrid) {
    requestId.value = qrid
    handleTraceQuery()
  }
  const qsid = route.query.sessionId
  if (typeof qsid === 'string' && qsid) {
    sessionId.value = qsid
    handleSessionQuery()
  }
})

const handleFailQuery = async () => {
  failError.value = ''
  loadingFail.value = true
  try {
    failedRows.value = await queryFailedRequests(
      toBackendTime(failStart.value),
      toBackendTime(failEnd.value),
      failLimit.value || 100
    )
  } catch (error) {
    failedRows.value = []
    failError.value = error?.message || '查询失败'
  } finally {
    loadingFail.value = false
  }
}
</script>

<style scoped>
.obs-container {
  min-height: 100vh;
  background: transparent;
}

.obs-header {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
  color: #fff;
}

.obs-header h1 {
  margin: 0 auto;
  font-size: 20px;
}

.back-link {
  color: #fff;
  text-decoration: none;
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 7px;
  padding: 4px 12px;
  font-size: 0.85rem;
  cursor: pointer;
  margin-right: 8px;
}
.back-link:hover {
  background: rgba(255, 255, 255, 0.12);
}
.back-link.home {
  border-style: dashed;
}

.obs-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  display: grid;
  gap: 16px;
}

.panel {
  background: var(--color-surface);
  border-radius: 10px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.panel h2 {
  margin: 0 0 12px;
  font-size: 18px;
}

.panel h3 {
  margin: 12px 0 8px;
}

.form-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

input {
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
}

button {
  height: 36px;
  border: none;
  border-radius: 6px;
  background: var(--color-primary);
  color: #fff;
  padding: 0 12px;
  cursor: pointer;
}

button:disabled {
  background: var(--color-skipped);
  cursor: not-allowed;
}

.error {
  color: #dc2626;
  margin: 10px 0 0;
}

.table-wrap {
  overflow: auto;
  margin-top: 10px;
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 800px;
}

th,
td {
  border: 1px solid var(--color-border);
  padding: 8px;
  text-align: left;
  font-size: 13px;
}

th {
  background: var(--color-surface-alt);
}

.error-text {
  color: #b91c1c;
}

/* ---- 轨迹错误分析视图 ---- */
.result-card {
  margin-top: 12px;
}

.trace-banner {
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 10px;
}

.trace-banner.ok {
  background: rgba(52, 211, 153, 0.12);
  border: 1px solid rgba(52, 211, 153, 0.4);
}

.trace-banner.bad {
  background: rgba(220, 38, 38, 0.1);
  border: 1px solid rgba(220, 38, 38, 0.45);
}

.banner-title {
  font-weight: 700;
}

.fault-point {
  font-weight: 600;
  color: #b91c1c;
}

.banner-err {
  margin-top: 6px;
  color: #b91c1c;
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}

.trace-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-muted, #6b7280);
  margin-bottom: 8px;
}

.trace-meta .mono {
  font-family: ui-monospace, Menlo, Consolas, monospace;
}

.only-failed {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  margin-bottom: 10px;
  cursor: pointer;
}

.only-failed input {
  height: auto;
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stage-card {
  border-radius: 8px;
  padding: 8px 10px;
  border-left: 3px solid var(--color-border);
  background: var(--color-surface-alt, #f8fafc);
}

.stage-card.bad {
  border-left-color: #dc2626;
  background: rgba(220, 38, 38, 0.06);
}

.stage-card.ok {
  border-left-color: #34d399;
}

.stage-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
}

.stage-icon {
  font-weight: 700;
}

.stage-card.bad .stage-icon {
  color: #dc2626;
}

.stage-card.ok .stage-icon {
  color: #059669;
}

.stage-type {
  font-weight: 700;
  font-size: 11px;
  letter-spacing: 0.04em;
  color: var(--color-text-muted, #6b7280);
}

.stage-name {
  font-weight: 600;
}

.stage-cost,
.stage-time {
  color: var(--color-text-muted, #9ca3af);
  font-size: 12px;
  margin-left: auto;
}

.stage-time {
  margin-left: 0;
}

.stage-err {
  margin-top: 6px;
  color: #b91c1c;
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}

.stage-io {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.io-toggle {
  align-self: flex-start;
  height: auto;
  padding: 2px 8px;
  font-size: 12px;
  background: transparent;
  color: var(--color-primary);
  border: 1px solid var(--color-border);
}

.io-body {
  margin: 0;
  padding: 8px;
  background: var(--color-surface, #fff);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  font-size: 12px;
  max-height: 260px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

.muted {
  color: var(--color-text-muted, #9ca3af);
  font-size: 13px;
}
</style>
