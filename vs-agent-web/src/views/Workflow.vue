<template>
  <div class="wf-container">
    <div class="wf-header">
      <router-link to="/" class="back-link"><span>←</span> 返回首页</router-link>
      <h1>一句话生成工作流</h1>
      <button class="reload-btn" @click="refreshList" :disabled="loadingList">⟳ 列表</button>
    </div>

    <div class="wf-body">
      <!-- 左：生成 + 历史 -->
      <aside class="left-pane">
        <section class="card">
          <div class="card-title">一句话需求</div>
          <textarea v-model="prompt" rows="4" placeholder="例：把用户输入的话写一份 200 字摘要，再生成同名 PDF"></textarea>
          <button class="primary-btn" :disabled="generating" @click="generate">
            {{ generating ? '生成中…' : '生成工作流' }}
          </button>
          <div v-if="genError" class="err">{{ genError }}</div>
        </section>

        <section class="card">
          <div class="card-title">已生成 ({{ workflows.length }})</div>
          <div v-if="workflows.length === 0" class="hint">还没有工作流。</div>
          <ul v-else class="wf-list">
            <li
              v-for="w in workflows" :key="w.id"
              :class="['wf-item', { active: current && current.id === w.id }]"
              @click="select(w)"
            >
              <div class="wf-name">{{ w.name }}</div>
              <div class="wf-desc">{{ w.description }}</div>
              <div class="wf-meta">
                <span class="badge">{{ w.nodes.length }} nodes</span>
                <code class="wf-id">{{ w.id.slice(0, 8) }}</code>
              </div>
            </li>
          </ul>
        </section>
      </aside>

      <!-- 右：详情 / 执行 / 评测 -->
      <section class="right-pane">
        <div v-if="!current" class="empty">
          <div class="empty-icon">🌀</div>
          <p>左侧生成或选一个工作流开始</p>
        </div>

        <template v-else>
          <section class="card">
            <div class="card-title-row">
              <div class="card-title">{{ current.name }} <code class="wf-id">{{ current.id.slice(0, 8) }}</code></div>
              <a class="link-btn" :href="dslUrl(current.id)" target="_blank">📥 下载 Dify YAML</a>
            </div>
            <p class="desc">{{ current.description }}</p>
            <div class="nodes">
              <div v-for="(n, i) in current.nodes" :key="n.id" class="node">
                <div class="node-head">
                  <span class="step">{{ i + 1 }}</span>
                  <code class="node-id">{{ n.id }}</code>
                  <span :class="['ntype', n.type]">{{ n.type }}</span>
                  <span class="output">→ ${{ n.outputVar }}</span>
                </div>
                <div v-if="n.type === 'llm'" class="node-body">
                  <pre>{{ n.prompt }}</pre>
                </div>
                <div v-else-if="n.type === 'skill'" class="node-body">
                  <div>skill: <code>{{ n.skillName }}</code></div>
                  <pre>{{ JSON.stringify(n.args, null, 2) }}</pre>
                </div>
              </div>
            </div>
          </section>

          <section class="card">
            <div class="card-title">执行</div>
            <label>初始输入 ${input}</label>
            <textarea v-model="execInput" rows="2" placeholder="给这个工作流的初始输入"></textarea>
            <button class="primary-btn" :disabled="executing" @click="execute">
              {{ executing ? '执行中…' : '执行' }}
            </button>
            <div v-if="execResult" class="exec-result">
              <div class="result-summary">
                <span :class="['badge', execResult.success ? 'ok' : 'fail']">{{ execResult.success ? '成功' : '失败' }}</span>
                <span class="elapsed">{{ execResult.elapsedMs }} ms</span>
              </div>
              <div v-if="execResult.errorMessage" class="err">{{ execResult.errorMessage }}</div>
              <div class="sub-title">最终输出</div>
              <pre class="final-out">{{ execResult.output }}</pre>

              <div class="sub-title">步骤明细</div>
              <div v-for="s in execResult.steps" :key="s.nodeId" class="step-row">
                <div class="step-head">
                  <code>{{ s.nodeId }}</code>
                  <span :class="['ntype', s.type]">{{ s.type }}</span>
                  <span :class="['badge', s.success ? 'ok' : 'fail']">{{ s.success ? 'OK' : 'FAIL' }}</span>
                  <span class="elapsed">{{ s.elapsedMs }} ms</span>
                </div>
                <details>
                  <summary>查看 input / output</summary>
                  <div class="kv"><span>input</span><pre>{{ s.input }}</pre></div>
                  <div class="kv"><span>output</span><pre>{{ s.output }}</pre></div>
                  <div v-if="s.errorMessage" class="kv err"><span>error</span><pre>{{ s.errorMessage }}</pre></div>
                </details>
              </div>
            </div>
          </section>

          <section class="card">
            <div class="card-title">送到评测</div>
            <div class="hint">为该工作流定义若干 case，会即时跑 Eval Harness。</div>
            <label>Judge</label>
            <select v-model="evalJudge">
              <option value="keyword_contains">keyword_contains</option>
              <option value="llm_as_judge">llm_as_judge</option>
            </select>
            <label>Cases (JSON)</label>
            <textarea v-model="evalCasesJson" rows="10" class="editor"></textarea>
            <button class="primary-btn" :disabled="evaluating" @click="runEval">
              {{ evaluating ? '评测中…' : '执行评测' }}
            </button>
            <div v-if="evalError" class="err">{{ evalError }}</div>
            <div v-if="evalResult" class="exec-result">
              <div class="result-summary">
                <span class="badge ok">通过 {{ evalResult.passed }}</span>
                <span class="badge fail">失败 {{ evalResult.failed }}</span>
                <span class="elapsed">{{ ((evalResult.passed / Math.max(1, evalResult.total)) * 100).toFixed(1) }}% · {{ (evalResult.totalElapsedMs / 1000).toFixed(1) }}s</span>
              </div>
              <table class="cases-table">
                <thead>
                  <tr><th>Case</th><th>状态</th><th>原因</th></tr>
                </thead>
                <tbody>
                  <tr v-for="c in evalResult.cases" :key="c.caseId">
                    <td><code>{{ c.caseId }}</code></td>
                    <td><span :class="['badge', c.pass ? 'ok' : 'fail']">{{ c.pass ? 'PASS' : 'FAIL' }}</span></td>
                    <td class="reason">{{ c.reason }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import {
  generateWorkflow,
  listWorkflows,
  executeWorkflow,
  evalWorkflow,
  workflowDifyDslUrl
} from '../services/api'

const prompt = ref('把用户输入的句子写一份 200 字以内的摘要')
const workflows = ref([])
const current = ref(null)
const loadingList = ref(false)
const generating = ref(false)
const genError = ref(null)

const execInput = ref('Spring AI 是一个用于构建 AI 应用的 Java 框架')
const executing = ref(false)
const execResult = ref(null)

const evalJudge = ref('keyword_contains')
const evalCasesJson = ref(JSON.stringify({
  cases: [
    { id: 'c1', input: 'Spring AI 是一个用于构建 AI 应用的 Java 框架', expectedContains: ['Spring AI', '框架'] },
    { id: 'c2', input: 'RAG 通过检索外部知识降低幻觉', expectedContains: ['RAG', '检索'] }
  ]
}, null, 2))
const evaluating = ref(false)
const evalError = ref(null)
const evalResult = ref(null)

const refreshList = async () => {
  loadingList.value = true
  try { workflows.value = await listWorkflows() } catch (e) { genError.value = e.message } finally { loadingList.value = false }
}

const generate = async () => {
  generating.value = true
  genError.value = null
  try {
    const def = await generateWorkflow(prompt.value)
    workflows.value = [def, ...workflows.value.filter(w => w.id !== def.id)]
    current.value = def
    execResult.value = null
    evalResult.value = null
  } catch (e) {
    genError.value = e.message
  } finally {
    generating.value = false
  }
}

const select = (w) => { current.value = w; execResult.value = null; evalResult.value = null }

const execute = async () => {
  executing.value = true
  execResult.value = null
  try {
    execResult.value = await executeWorkflow(current.value.id, execInput.value)
  } catch (e) {
    execResult.value = { success: false, errorMessage: e.message, steps: [], elapsedMs: 0 }
  } finally {
    executing.value = false
  }
}

const runEval = async () => {
  evaluating.value = true
  evalError.value = null
  evalResult.value = null
  try {
    const body = JSON.parse(evalCasesJson.value)
    evalResult.value = await evalWorkflow(current.value.id, { judge: evalJudge.value, cases: body.cases || [] })
  } catch (e) {
    evalError.value = e.message
  } finally {
    evaluating.value = false
  }
}

const dslUrl = (id) => workflowDifyDslUrl(id)

onMounted(refreshList)
</script>

<style scoped>
.wf-container { display: flex; flex-direction: column; height: 100vh; background: #f5f7fb; color: #222; }
.wf-header { display: flex; align-items: center; padding: 12px 20px; background: #2b6dbf; color: white; }
.wf-header h1 { margin: 0 auto; font-size: 1.4rem; }
.back-link { color: white; text-decoration: none; display: flex; align-items: center; }
.back-link span { font-size: 1.2rem; margin-right: 5px; }
.reload-btn { background: rgba(255,255,255,0.18); color: white; border: 1px solid rgba(255,255,255,0.4); border-radius: 4px; padding: 4px 10px; cursor: pointer; }

.wf-body { flex: 1; display: flex; gap: 16px; padding: 16px 20px 20px; overflow: hidden; }
.left-pane { width: 360px; min-width: 320px; display: flex; flex-direction: column; gap: 16px; overflow-y: auto; }
.right-pane { flex: 1; overflow-y: auto; }

.card { background: white; border: 1px solid #e5e8f0; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
.card-title { font-weight: 600; color: #2a3a55; margin-bottom: 10px; }
.card-title-row { display: flex; align-items: center; justify-content: space-between; }
.hint { color: #8a96b3; font-size: 13px; margin-bottom: 8px; }
label { display: block; font-size: 12px; color: #555; margin: 8px 0 4px; }
textarea, input, select, .editor { width: 100%; box-sizing: border-box; padding: 8px 10px; border: 1px solid #d0d7e2; border-radius: 6px; font-size: 13px; font-family: inherit; }
textarea, .editor { resize: vertical; font-family: monospace; font-size: 12.5px; }

.primary-btn { margin-top: 12px; background: #2b6dbf; color: white; border: none; border-radius: 6px; padding: 8px 18px; font-size: 14px; cursor: pointer; }
.primary-btn:hover:not(:disabled) { background: #20559b; }
.primary-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.err { color: #b01a1a; font-size: 13px; margin-top: 8px; }

.wf-list { list-style: none; padding: 0; margin: 0; }
.wf-item { padding: 8px 10px; border-radius: 6px; margin-bottom: 4px; cursor: pointer; border: 1px solid transparent; }
.wf-item:hover { background: #f0f4fb; }
.wf-item.active { background: #e3edff; border-color: #2b6dbf; }
.wf-name { font-weight: 600; color: #2a3a55; font-size: 13px; }
.wf-desc { font-size: 11px; color: #6c7a99; }
.wf-meta { margin-top: 4px; display: flex; gap: 6px; align-items: center; }
.wf-id { background: #eef0f5; padding: 1px 5px; border-radius: 3px; font-size: 11px; color: #6c7a99; }

.desc { color: #555; margin: 4px 0 12px; font-size: 13px; }

.nodes { display: flex; flex-direction: column; gap: 8px; }
.node { border: 1px solid #e5e8f0; border-radius: 6px; padding: 8px 10px; }
.node-head { display: flex; gap: 8px; align-items: center; font-size: 12px; }
.step { background: #2b6dbf; color: white; border-radius: 50%; width: 18px; height: 18px; display: inline-flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 600; }
.node-id { background: #eef0f5; padding: 1px 5px; border-radius: 3px; }
.ntype { font-size: 10px; padding: 1px 6px; border-radius: 3px; font-weight: 600; }
.ntype.llm { background: #e3edff; color: #2b6dbf; }
.ntype.skill { background: #fff0e0; color: #b06000; }
.output { color: #8a96b3; font-family: monospace; font-size: 11px; }
.node-body { margin-top: 6px; font-size: 12px; color: #444; }
.node-body pre { background: #f7f9fc; border-radius: 4px; padding: 8px; margin: 4px 0 0; white-space: pre-wrap; word-break: break-word; font-size: 12px; }

.link-btn { background: #2b6dbf; color: white; text-decoration: none; padding: 4px 10px; border-radius: 4px; font-size: 12px; }
.exec-result { margin-top: 12px; }
.result-summary { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.badge { padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; background: #eef0f5; color: #555; }
.badge.ok { background: #e0f5e0; color: #1a7a1a; }
.badge.fail { background: #fde0e0; color: #b01a1a; }
.elapsed { color: #8a96b3; font-size: 12px; }
.sub-title { font-weight: 600; color: #6c7a99; margin: 10px 0 4px; font-size: 13px; }
.final-out { background: #1e1e1e; color: #d4d4d4; border-radius: 6px; padding: 12px; font-size: 12.5px; line-height: 1.5; max-height: 240px; overflow: auto; white-space: pre-wrap; }

.step-row { background: #f7f9fc; border-radius: 4px; padding: 6px 8px; margin: 6px 0; }
.step-head { display: flex; gap: 8px; align-items: center; font-size: 12px; }
.kv { display: flex; gap: 8px; margin: 4px 0; font-size: 12px; align-items: flex-start; }
.kv > span { font-weight: 600; color: #6c7a99; min-width: 56px; }
.kv pre { flex: 1; background: white; border: 1px solid #e5e8f0; border-radius: 4px; padding: 4px 6px; margin: 0; white-space: pre-wrap; word-break: break-word; font-size: 11.5px; max-height: 140px; overflow: auto; }
.kv.err pre { color: #b01a1a; }

.cases-table { width: 100%; border-collapse: collapse; font-size: 12px; margin-top: 8px; }
.cases-table th, .cases-table td { padding: 6px 8px; text-align: left; border-bottom: 1px solid #eef0f5; }
.reason { color: #555; max-width: 360px; }

.empty { text-align: center; padding-top: 120px; color: #8a96b3; }
.empty-icon { font-size: 56px; }
</style>
