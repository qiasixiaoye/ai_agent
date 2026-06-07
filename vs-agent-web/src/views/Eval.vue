<template>
  <div class="eval-container">
    <div class="eval-header">
      <router-link to="/" class="back-link"><span>←</span> 返回首页</router-link>
      <h1>Eval 评测</h1>
      <button class="reload-btn" @click="refresh" :disabled="loadingSuites">⟳ 刷新</button>
    </div>

    <div class="eval-body">
      <aside class="suite-list">
        <div class="list-title">Suite 列表 ({{ suites.length }})</div>
        <div v-if="loadingSuites" class="hint">加载中…</div>
        <div v-else-if="suites.length === 0" class="hint">没有 suite，确认 classpath:eval/suites/*.yaml 是否存在。</div>
        <ul v-else>
          <li
            v-for="s in suites"
            :key="s.name"
            :class="['suite-item', { active: current && current.name === s.name }]"
            @click="select(s)"
          >
            <div class="suite-name">{{ s.name }}</div>
            <div class="suite-desc">{{ s.description }}</div>
            <div class="suite-meta">
              <span class="badge">{{ s.cases?.length || 0 }} cases</span>
              <span class="badge runner">{{ s.runner }}</span>
            </div>
          </li>
        </ul>
      </aside>

      <section class="suite-detail">
        <div v-if="!current" class="empty">
          <div class="empty-icon">📊</div>
          <p>左侧选一份 suite 开始评测</p>
        </div>

        <template v-else>
          <div class="detail-head">
            <h2>{{ current.name }}</h2>
            <p class="detail-desc">{{ current.description }}</p>
            <div class="detail-meta">
              <span class="badge runner">runner: {{ current.runner }}</span>
              <span class="badge">{{ current.cases?.length || 0 }} cases</span>
            </div>
            <div class="actions">
              <button class="run-btn" :disabled="running" @click="runSuite">
                {{ running ? `执行中… (${doneCount}/${current.cases?.length || 0})` : '执行 Suite' }}
              </button>
            </div>
            <div v-if="runError" class="err">{{ runError }}</div>
          </div>

          <div v-if="result" class="result-card">
            <div class="result-summary">
              <div class="kpi">
                <div class="kpi-num">{{ result.total }}</div><div class="kpi-label">总数</div>
              </div>
              <div class="kpi ok">
                <div class="kpi-num">{{ result.passed }}</div><div class="kpi-label">通过</div>
              </div>
              <div class="kpi fail">
                <div class="kpi-num">{{ result.failed }}</div><div class="kpi-label">失败</div>
              </div>
              <div class="kpi">
                <div class="kpi-num">{{ ((result.passed / Math.max(1, result.total)) * 100).toFixed(1) }}%</div>
                <div class="kpi-label">通过率</div>
              </div>
              <div class="kpi">
                <div class="kpi-num">{{ (result.totalElapsedMs / 1000).toFixed(1) }}s</div>
                <div class="kpi-label">耗时</div>
              </div>
            </div>

            <table class="cases-table">
              <thead>
                <tr>
                  <th>Case</th>
                  <th>状态</th>
                  <th>耗时</th>
                  <th>原因</th>
                  <th>展开</th>
                </tr>
              </thead>
              <tbody>
                <template v-for="c in result.cases" :key="c.caseId">
                  <tr>
                    <td class="case-id">{{ c.caseId }}</td>
                    <td>
                      <span :class="['status', c.pass ? 'pass' : 'fail']">{{ c.pass ? 'PASS' : 'FAIL' }}</span>
                    </td>
                    <td>{{ c.runnerElapsedMs }} ms</td>
                    <td class="reason">{{ c.reason }}</td>
                    <td><button class="toggle-btn" @click="toggle(c.caseId)">{{ expanded[c.caseId] ? '收起' : '展开' }}</button></td>
                  </tr>
                  <tr v-if="expanded[c.caseId]" class="case-detail">
                    <td colspan="5">
                      <div class="kv"><span>Input:</span><pre>{{ c.input }}</pre></div>
                      <div class="kv"><span>Actual:</span><pre>{{ c.actualOutput }}</pre></div>
                      <div v-if="c.missedKeywords && c.missedKeywords.length" class="kv">
                        <span>Missed:</span>
                        <code class="missed">{{ c.missedKeywords.join(', ') }}</code>
                      </div>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listEvalSuites, runEvalSuite } from '../services/api'

const suites = ref([])
const current = ref(null)
const loadingSuites = ref(false)
const running = ref(false)
const runError = ref(null)
const result = ref(null)
const doneCount = ref(0)
const expanded = reactive({})

const refresh = async () => {
  loadingSuites.value = true
  try {
    suites.value = await listEvalSuites()
  } catch (e) {
    runError.value = e.message
  } finally {
    loadingSuites.value = false
  }
}

const select = (s) => {
  current.value = s
  result.value = null
  runError.value = null
  Object.keys(expanded).forEach(k => delete expanded[k])
}

const runSuite = async () => {
  if (!current.value) return
  running.value = true
  runError.value = null
  result.value = null
  doneCount.value = 0
  try {
    result.value = await runEvalSuite(current.value.name)
    doneCount.value = result.value.total
  } catch (e) {
    runError.value = e.message
  } finally {
    running.value = false
  }
}

const toggle = (id) => { expanded[id] = !expanded[id] }

onMounted(refresh)
</script>

<style scoped>
.eval-container { display: flex; flex-direction: column; height: 100vh; background: #f5f7fb; color: #222; }
.eval-header { display: flex; align-items: center; padding: 12px 20px; background: #6d4aa5; color: white; }
.eval-header h1 { margin: 0 auto; font-size: 1.4rem; }
.back-link { color: white; text-decoration: none; display: flex; align-items: center; }
.back-link span { font-size: 1.2rem; margin-right: 5px; }
.reload-btn { background: rgba(255,255,255,0.18); color: white; border: 1px solid rgba(255,255,255,0.4); border-radius: 4px; padding: 4px 10px; cursor: pointer; }
.reload-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.eval-body { flex: 1; display: flex; overflow: hidden; }
.suite-list { width: 320px; min-width: 280px; background: white; border-right: 1px solid #e5e8f0; overflow-y: auto; padding: 12px; }
.list-title { font-weight: 600; margin-bottom: 8px; color: #6d4aa5; }
.suite-list ul { list-style: none; padding: 0; margin: 0; }
.suite-item { padding: 10px 12px; border-radius: 6px; margin-bottom: 6px; cursor: pointer; border: 1px solid transparent; }
.suite-item:hover { background: #f4f0fa; }
.suite-item.active { background: #efe7ff; border-color: #6d4aa5; }
.suite-name { font-weight: 600; color: #2a3a55; }
.suite-desc { font-size: 12px; color: #6c7a99; margin-top: 4px; }
.suite-meta { margin-top: 6px; display: flex; flex-wrap: wrap; gap: 4px; }
.badge { display: inline-block; font-size: 11px; padding: 2px 6px; border-radius: 4px; background: #e5e8f0; color: #6d4aa5; }
.badge.runner { background: #fff0e0; color: #b06000; }
.suite-detail { flex: 1; overflow-y: auto; padding: 20px 28px; }
.empty { text-align: center; padding-top: 120px; color: #8a96b3; }
.empty-icon { font-size: 56px; }
.detail-head { margin-bottom: 18px; }
.detail-head h2 { margin: 0; }
.detail-desc { color: #555; }
.detail-meta { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 6px; }
.actions { margin-top: 14px; }
.run-btn { background: #6d4aa5; color: white; border: none; border-radius: 6px; padding: 8px 18px; font-size: 14px; cursor: pointer; }
.run-btn:hover:not(:disabled) { background: #5a3a8a; }
.run-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.err { margin-top: 10px; color: #b01a1a; font-size: 13px; }
.result-card { background: white; border: 1px solid #e5e8f0; border-radius: 8px; padding: 16px; }
.result-summary { display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 16px; }
.kpi { background: #f7f9fc; border-radius: 6px; padding: 10px 14px; min-width: 70px; text-align: center; }
.kpi-num { font-size: 22px; font-weight: 700; color: #2a3a55; }
.kpi-label { font-size: 11px; color: #6c7a99; margin-top: 2px; }
.kpi.ok .kpi-num { color: #1a7a1a; }
.kpi.fail .kpi-num { color: #b01a1a; }
.cases-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.cases-table th, .cases-table td { padding: 8px 10px; text-align: left; border-bottom: 1px solid #eef0f5; vertical-align: top; }
.cases-table th { background: #f7f9fc; color: #6d4aa5; font-weight: 600; }
.case-id { font-family: monospace; font-weight: 600; }
.status { padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 700; }
.status.pass { background: #e0f5e0; color: #1a7a1a; }
.status.fail { background: #fde0e0; color: #b01a1a; }
.reason { color: #555; max-width: 320px; overflow: hidden; text-overflow: ellipsis; }
.toggle-btn { border: 1px solid #d0d7e2; background: white; border-radius: 4px; padding: 4px 8px; font-size: 12px; cursor: pointer; }
.toggle-btn:hover { background: #f0f4fb; }
.case-detail { background: #fafbfd; }
.case-detail .kv { display: flex; align-items: flex-start; gap: 8px; margin: 6px 0; font-size: 12px; }
.case-detail .kv > span { font-weight: 600; color: #6d4aa5; min-width: 56px; }
.case-detail pre { background: #fff; border: 1px solid #e5e8f0; border-radius: 4px; padding: 6px 8px; margin: 0; white-space: pre-wrap; word-break: break-word; flex: 1; }
.missed { color: #b01a1a; background: #fff5f5; padding: 1px 6px; border-radius: 3px; }
.hint { color: #8a96b3; font-size: 13px; }
</style>
