<template>
  <div class="obs-container">
    <div class="obs-header">
      <router-link to="/" class="back-link">
        <span>←</span> 返回首页
      </router-link>
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
          <h3>请求概览</h3>
          <p>requestId: {{ traceResult.request?.requestId }}</p>
          <p>traceId: {{ traceResult.request?.traceId }}</p>
          <p>sessionId: {{ traceResult.request?.sessionId }}</p>
          <p>scene: {{ traceResult.request?.scene }}</p>
          <p>status: {{ traceResult.request?.status }}</p>
          <p>model: {{ traceResult.request?.modelName || '-' }}</p>
          <p>cost: {{ traceResult.request?.totalCostMs ?? '-' }} ms</p>
          <h3>阶段事件</h3>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>time</th>
                  <th>type</th>
                  <th>name</th>
                  <th>tool</th>
                  <th>cost(ms)</th>
                  <th>success</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="stage in traceResult.stages || []" :key="stage.id">
                  <td>{{ stage.eventTime || '-' }}</td>
                  <td>{{ stage.stageType || '-' }}</td>
                  <td>{{ stage.stageName || '-' }}</td>
                  <td>{{ stage.toolName || '-' }}</td>
                  <td>{{ stage.costMs ?? '-' }}</td>
                  <td>{{ stage.success === null || stage.success === undefined ? '-' : stage.success }}</td>
                </tr>
              </tbody>
            </table>
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
import { ref } from 'vue'
import { queryFailedRequests, queryRequestTrace, querySessionRequests } from '../services/api'

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
  background: #f5f7fb;
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
}

.obs-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  display: grid;
  gap: 16px;
}

.panel {
  background: #fff;
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
  border: 1px solid #d1d5db;
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
  background: #9ca3af;
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
  border: 1px solid #e5e7eb;
  padding: 8px;
  text-align: left;
  font-size: 13px;
}

th {
  background: #f3f4f6;
}

.error-text {
  color: #b91c1c;
}
</style>
