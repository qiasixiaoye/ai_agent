<template>
  <main class="eval-page">
    <header class="page-header">
      <router-link to="/" class="back-link">返回首页</router-link>
      <div>
        <p class="eyebrow">Workflow Eval</p>
        <h1>工作流评测</h1>
        <p>这里只评测最终生成的 WorkflowDef，避免混入助手、外部 runner 等其它目标。</p>
      </div>
      <button class="ghost-btn" @click="refreshWorkflows" :disabled="loading">刷新工作流</button>
    </header>

    <section class="eval-layout">
      <aside class="workflow-panel">
        <div class="panel-title">选择工作流</div>
        <div v-if="loading" class="empty-state">加载中...</div>
        <div v-else-if="workflows.length === 0" class="empty-state">还没有工作流，先去“一句话工作流”生成。</div>
        <button
          v-for="item in workflows"
          v-else
          :key="item.id"
          type="button"
          :class="['workflow-item', { active: current?.id === item.id }]"
          @click="selectWorkflow(item)"
        >
          <strong>{{ item.name }}</strong>
          <span>{{ item.nodes?.length || 0 }} nodes · {{ item.id.slice(0, 8) }}</span>
        </button>
      </aside>

      <section class="detail-panel">
        <div v-if="!current" class="empty-state large">选择一个工作流后编辑评测用例。</div>
        <template v-else>
          <div class="detail-head">
            <div>
              <span class="workflow-id">{{ current.id }}</span>
              <h2>{{ current.name }}</h2>
              <p>{{ current.description }}</p>
            </div>
            <div class="judge-select">
              <label>Judge</label>
              <select v-model="judge">
                <option value="keyword_contains">keyword_contains</option>
                <option value="llm_as_judge">llm_as_judge</option>
              </select>
            </div>
          </div>

          <div class="case-editor">
            <div class="panel-title">Cases JSON</div>
            <textarea v-model="casesJson" rows="12"></textarea>
            <div class="actions">
              <button class="primary-btn" :disabled="running" @click="runEval">
                {{ running ? '评测中...' : '运行工作流评测' }}
              </button>
            </div>
            <p v-if="error" class="error-text">{{ error }}</p>
          </div>

          <section v-if="result" class="result-panel">
            <div class="summary-row">
              <article>
                <span>总数</span>
                <strong>{{ result.total }}</strong>
              </article>
              <article class="ok">
                <span>通过</span>
                <strong>{{ result.passed }}</strong>
              </article>
              <article class="fail">
                <span>失败</span>
                <strong>{{ result.failed }}</strong>
              </article>
              <article>
                <span>耗时</span>
                <strong>{{ (result.totalElapsedMs / 1000).toFixed(1) }}s</strong>
              </article>
            </div>

            <div class="case-list">
              <article v-for="item in result.cases" :key="item.caseId" class="case-row">
                <div>
                  <strong>{{ item.caseId }}</strong>
                  <span>{{ item.input }}</span>
                </div>
                <span :class="['case-status', item.pass ? 'pass' : 'fail']">{{ item.pass ? 'PASS' : 'FAIL' }}</span>
                <details>
                  <summary>{{ item.reason || '查看输出' }}</summary>
                  <pre>{{ item.actualOutput }}</pre>
                </details>
              </article>
            </div>
          </section>
        </template>
      </section>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { evalWorkflow, listWorkflows } from '../services/api'

const workflows = ref([])
const current = ref(null)
const loading = ref(false)
const running = ref(false)
const error = ref('')
const result = ref(null)
const judge = ref('keyword_contains')
const casesJson = ref(JSON.stringify({
  cases: [
    {
      id: 'summary-basic',
      input: 'Spring AI 是一个用于构建 AI 应用的 Java 框架，支持对话、RAG 和工具调用。',
      expectedContains: ['Spring AI', '框架']
    }
  ]
}, null, 2))

const refreshWorkflows = async () => {
  loading.value = true
  try {
    workflows.value = await listWorkflows()
    if (!current.value && workflows.value.length > 0) {
      selectWorkflow(workflows.value[0])
    }
  } finally {
    loading.value = false
  }
}

const selectWorkflow = (workflow) => {
  current.value = workflow
  result.value = null
  error.value = ''
}

const runEval = async () => {
  if (!current.value) return
  running.value = true
  error.value = ''
  result.value = null
  try {
    const body = JSON.parse(casesJson.value)
    result.value = await evalWorkflow(current.value.id, {
      judge: judge.value,
      cases: body.cases || []
    })
  } catch (e) {
    error.value = e.message || '评测失败'
  } finally {
    running.value = false
  }
}

onMounted(refreshWorkflows)
</script>

<style scoped>
.eval-page {
  min-height: 100vh;
  background: #f6f8fb;
  color: #172033;
  padding: 24px;
}

.page-header,
.eval-layout {
  max-width: 1240px;
  margin: 0 auto;
}

.page-header {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) auto;
  gap: 18px;
  align-items: start;
  padding-bottom: 18px;
  border-bottom: 1px solid #dbe3ef;
}

.back-link {
  color: #2563eb;
  text-decoration: none;
  font-weight: 700;
}

.eyebrow {
  margin: 0 0 6px;
  color: #4338ca;
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
}

h1,
h2 {
  margin: 0;
}

h1 {
  font-size: 32px;
}

.page-header p,
.detail-head p {
  margin: 8px 0 0;
  color: #64748b;
}

button,
select {
  min-height: 34px;
  border-radius: 7px;
  font: inherit;
}

.ghost-btn {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #172033;
  padding: 0 12px;
  cursor: pointer;
}

.primary-btn {
  border: 1px solid #4338ca;
  background: #4338ca;
  color: #ffffff;
  padding: 0 14px;
  cursor: pointer;
}

button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.eval-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
  margin-top: 18px;
}

.workflow-panel,
.detail-panel,
.case-editor,
.result-panel {
  background: #ffffff;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  box-shadow: 0 8px 26px rgba(31, 45, 61, 0.05);
}

.workflow-panel,
.detail-panel {
  padding: 16px;
}

.panel-title {
  font-weight: 800;
  margin-bottom: 12px;
}

.workflow-item {
  width: 100%;
  display: block;
  text-align: left;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 8px;
  padding: 12px;
  cursor: pointer;
}

.workflow-item:hover,
.workflow-item.active {
  background: #eef2ff;
  border-color: #c7d2fe;
}

.workflow-item strong,
.workflow-item span {
  display: block;
}

.workflow-item span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.empty-state {
  color: #64748b;
  padding: 28px 0;
  text-align: center;
}

.empty-state.large {
  padding: 120px 0;
}

.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e2e8f0;
}

.workflow-id {
  display: inline-block;
  color: #64748b;
  font-size: 12px;
  margin-bottom: 6px;
}

.judge-select {
  min-width: 180px;
}

label {
  display: block;
  margin-bottom: 5px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

select,
textarea {
  width: 100%;
  border: 1px solid #cbd5e1;
  border-radius: 7px;
}

select {
  padding: 0 8px;
}

.case-editor {
  margin-top: 16px;
  padding: 16px;
}

textarea {
  padding: 10px;
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 13px;
  resize: vertical;
}

.actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.error-text {
  color: #b91c1c;
  margin: 10px 0 0;
}

.result-panel {
  margin-top: 16px;
  padding: 16px;
}

.summary-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(90px, 1fr));
  gap: 10px;
}

.summary-row article {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
}

.summary-row span,
.case-row span {
  color: #64748b;
  font-size: 12px;
}

.summary-row strong {
  display: block;
  margin-top: 7px;
  font-size: 22px;
}

.summary-row .ok strong {
  color: #15803d;
}

.summary-row .fail strong {
  color: #b91c1c;
}

.case-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.case-row {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) auto;
  gap: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
}

.case-row strong,
.case-row span {
  display: block;
}

.case-status {
  justify-self: end;
  align-self: start;
  border-radius: 6px;
  padding: 4px 8px;
  font-weight: 800;
}

.case-status.pass {
  background: #dcfce7;
  color: #166534;
}

.case-status.fail {
  background: #fee2e2;
  color: #991b1b;
}

details {
  grid-column: 1 / -1;
  color: #475569;
}

summary {
  cursor: pointer;
}

pre {
  white-space: pre-wrap;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  padding: 12px;
  overflow: auto;
}

@media (max-width: 900px) {
  .page-header,
  .eval-layout,
  .detail-head {
    grid-template-columns: 1fr;
    display: grid;
  }
}
</style>
