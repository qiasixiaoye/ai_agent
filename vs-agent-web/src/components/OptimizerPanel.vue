<template>
  <section class="op-panel">
    <div class="op-head">
      <h2>🔁 下游 · 自环优化（MASPO × Dify）</h2>
      <span class="op-tag">选工作流 → Dify 执行(还很弱) → 完善 prompt → 再执行 · 前后对比</span>
    </div>
    <p class="op-intro">
      挑一个上游产出的工作流，先在 Dify 上实跑看它现在多弱，再自动完善每个节点的提示词、回写 Dify 重跑，
      逐节点展示<strong>优化在了哪里</strong>、<strong>前后答案/分数对比</strong>。
    </p>

    <!-- sidecar 健康指示：「优化服务」= 本地跑 MASPO 算法的小服务(serve.py)，网页只是它的界面 -->
    <div class="op-side" :class="sideOk === true ? 'ok' : sideOk === false ? 'bad' : 'pending'">
      <span class="op-dot"></span>
      <template v-if="sideOk === null">正在检测本地优化服务（{{ base }}）…</template>
      <template v-else-if="sideOk">本地优化服务已连接 · {{ base }}（运行 MASPO 算法的小服务，本页是它的界面）</template>
      <template v-else>
        <span>
          本地优化服务未连接：本页由一个本地小服务（<code>serve.py</code>，:8770）实际执行；它没起来就跑不了。
          请在 <code>maspo-demo</code> 目录执行 <code>python serve.py</code> 后重试。
        </span>
        <button class="op-retry" @click="boot">重试</button>
      </template>
    </div>

    <!-- 控制区：选工作流 + 问题 + 运行 -->
    <div class="op-controls">
      <label class="op-field">
        <span class="op-field-l">优化哪个工作流</span>
        <select v-model="selectedId" class="op-select" :disabled="!!running || !workflows.length">
          <option v-for="w in workflows" :key="w.id" :value="w.id">{{ w.name }}</option>
        </select>
      </label>
      <div v-if="selected" class="op-wf-meta">
        <span class="op-chip" :class="selected.scorable ? 'good' : 'warn'">
          {{ selected.scorable ? '可验证 · 有真实数值前后分' : '无数据集 · 质性前后对比' }}
        </span>
        <span class="op-chip dim">{{ selected.app_mode }}</span>
        <span class="op-topo">{{ selected.topology }}</span>
      </div>
      <label class="op-field">
        <span class="op-field-l">测试问题（会发给 Dify 实跑）</span>
        <textarea v-model="question" class="op-q" rows="2" :disabled="!!running"></textarea>
      </label>
      <div class="op-run-row">
        <button class="op-run" :disabled="!!running || sideOk !== true" @click="run('selfloop')">
          {{ running === 'selfloop' ? '优化中…（真连 Dify 约 1–2 分钟）' : '▶ 开始优化' }}
        </button>
      </div>
      <p class="op-hint">
        算术样例：有真实数值前后分；后端真实 DSL：展示结构 + 提示词 diff（agent 形态另含 Dify 答案前后对比）。
        无 Dify 时仍可看离线提示词 diff。
      </p>
    </div>

    <!-- live console -->
    <div ref="consoleEl" class="op-console">
      <div v-if="!events.length" class="op-placeholder">选好工作流，点「▶ 开始优化」开始 ▶</div>
      <template v-for="(ev, i) in events" :key="i">
        <div v-if="ev.t === 'head'" class="op-section">{{ ev.text }}</div>
        <div v-else-if="ev.t === 'stage'" class="op-stage">
          <span class="op-sdot" :class="ev.status"></span>
          <span class="op-lbl">{{ ev.name }}</span>
          <span class="op-st">{{ statusMap[ev.status] || '' }}</span>
        </div>
        <div v-else-if="ev.t === 'line'" class="op-ln" :class="ev.level || 'info'">{{ ev.text }}</div>
        <div v-else-if="ev.t === 'metric'" class="op-metric">
          <div class="l">{{ ev.label }}</div>
          <div class="v">{{ ev.value }}</div>
        </div>
        <!-- 提示词 前→后 diff -->
        <div v-else-if="ev.t === 'diff'" class="op-diff">
          <div class="op-diff-title">{{ ev.title }}</div>
          <div class="op-diff-cols" :class="{ single: ev.after === null }">
            <pre class="op-diff-box before">{{ ev.before || '（空）' }}</pre>
            <template v-if="ev.after !== null">
              <span class="op-diff-arrow">→</span>
              <pre class="op-diff-box after"><span
                v-for="(ln, li) in ev.after.split('\n')"
                :key="li"
                :class="{ added: ev.added.includes(ln) }"
              >{{ ln }}<br></span></pre>
            </template>
          </div>
        </div>
        <!-- 答案 前/后 两栏对比 -->
        <div v-else-if="ev.t === 'compare'" class="op-compare">
          <div class="op-cmp-col">
            <div class="op-cmp-l before">优化前 · {{ ev.label }}</div>
            <div class="op-cmp-box">{{ ev.before }}</div>
          </div>
          <div class="op-cmp-col">
            <div class="op-cmp-l after">优化后 · {{ ev.label }}</div>
            <div class="op-cmp-box">{{ ev.after }}</div>
          </div>
        </div>
        <a v-else-if="ev.t === 'link'" class="op-link" :href="ev.url" target="_blank" rel="noopener">🔗 {{ ev.label }}</a>
        <div v-else-if="ev.t === 'done'" class="op-ln dim">— 完成 —</div>
        <div v-else-if="ev.t === 'err'" class="op-ln bad">{{ ev.text }}</div>
      </template>

      <table v-if="rows.length" class="op-rt">
        <tr v-for="(r, ri) in rows" :key="ri">
          <component
            :is="r.head ? 'th' : 'td'"
            v-for="(c, ci) in r.cells"
            :key="ci"
            :class="!r.head && ci === r.cells.length - 1 ? c : ''"
          >{{ c }}</component>
        </tr>
      </table>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const base = import.meta.env.VITE_MASPO_URL || 'http://localhost:8770'
const statusMap = { run: '运行中…', ok: '✓ 完成', skip: '跳过', fail: '✗ 失败' }

const sideOk = ref(null)
const workflows = ref([])
const selectedId = ref('chatflow')
const question = ref('')
const running = ref(null)
const events = ref([])
const rows = ref([])
const consoleEl = ref(null)
let es = null

const selected = computed(() => workflows.value.find((w) => w.id === selectedId.value) || null)

const scroll = () => nextTick(() => { if (consoleEl.value) consoleEl.value.scrollTop = consoleEl.value.scrollHeight })

const handle = (ev) => {
  if (ev.t === 'stage') {
    const existing = events.value.find((e) => e.t === 'stage' && e.name === ev.name)
    if (existing) existing.status = ev.status
    else events.value.push(ev)
  } else if (ev.t === 'row') {
    rows.value.push({ cells: ev.cells, head: ev.head })
  } else if (ev.t === 'end') {
    cleanup()
    events.value.push({ t: 'done' })
  } else {
    events.value.push(ev)
  }
  scroll()
}

const cleanup = () => {
  if (es) { es.close(); es = null }
  running.value = null
}

const run = (api) => {
  cleanup()
  events.value = []
  rows.value = []
  running.value = api
  events.value.push({ t: 'line', text: '连接中…', level: 'dim' })
  const url = `${base}/api/${api}?dsl=${encodeURIComponent(selectedId.value)}&question=${encodeURIComponent(question.value)}`
  let first = true
  es = new EventSource(url)
  es.onmessage = (m) => {
    if (first) { events.value = []; first = false }
    handle(JSON.parse(m.data))
  }
  es.onerror = () => {
    cleanup()
    events.value.push({ t: 'err', text: '连接中断（请确认 Dify/后端在跑，或本地优化服务 python serve.py 是否启动）' })
  }
}

// when the selected workflow changes, prefill its default test question
const syncQuestion = () => { if (selected.value) question.value = selected.value.question }

const boot = async () => {
  sideOk.value = null
  try {
    const r = await fetch(`${base}/health`, { mode: 'cors' })
    sideOk.value = r.ok
    if (r.ok) {
      const list = await (await fetch(`${base}/api/workflows`, { mode: 'cors' })).json()
      workflows.value = list
      if (list.length && !list.find((w) => w.id === selectedId.value)) selectedId.value = list[0].id
      syncQuestion()
    }
  } catch {
    sideOk.value = false
  }
}

watch(selectedId, syncQuestion)

onMounted(boot)
onBeforeUnmount(cleanup)
</script>

<style scoped>
.op-panel { --bd: #2a3242; --dim: #8b97a7; --accent: #4f9cff; --good: #3fb950; --bad: #f85149; --warn: #d29922;
  background: #161b22; border: 1px solid var(--bd); border-radius: 14px; padding: 20px 22px; color: #e6edf3; }
.op-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.op-head h2 { margin: 0; font-size: 17px; font-weight: 650; }
.op-tag { font-size: 12px; color: var(--dim); border: 1px solid var(--bd); border-radius: 999px; padding: 3px 10px; }
.op-intro { color: var(--dim); font-size: 13px; margin: 8px 0 14px; line-height: 1.6; }
.op-intro strong { color: #e6edf3; }

.op-side { display: flex; align-items: center; gap: 9px; font-size: 12.5px; border: 1px solid var(--bd);
  border-radius: 9px; padding: 8px 12px; margin-bottom: 16px; }
.op-side code { background: #0a0e14; padding: 1px 6px; border-radius: 5px; color: var(--accent); }
.op-side .op-dot { width: 9px; height: 9px; border-radius: 50%; flex: 0 0 auto; }
.op-side.ok { color: #cfe9d4; } .op-side.ok .op-dot { background: var(--good); }
.op-side.bad { color: #f6c6c2; } .op-side.bad .op-dot { background: var(--bad); }
.op-side.pending { color: var(--dim); } .op-side.pending .op-dot { background: var(--warn); }
.op-retry { margin-left: auto; flex: 0 0 auto; cursor: pointer; background: transparent;
  border: 1px solid var(--bad); color: #f6c6c2; border-radius: 7px; padding: 3px 12px; font-size: 12px; }
.op-retry:hover { background: rgba(248,81,73,.12); }

/* controls */
.op-controls { display: flex; flex-direction: column; gap: 10px; margin-bottom: 14px; }
.op-field { display: flex; flex-direction: column; gap: 5px; }
.op-field-l { font-size: 12px; color: var(--dim); }
.op-select, .op-q { background: #0a0e14; border: 1px solid var(--bd); color: #e6edf3; border-radius: 9px;
  padding: 9px 11px; font-size: 13px; font-family: inherit; }
.op-select:focus, .op-q:focus { outline: none; border-color: var(--accent); }
.op-q { resize: vertical; }
.op-wf-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 12px; }
.op-chip { border: 1px solid var(--bd); border-radius: 999px; padding: 2px 9px; }
.op-chip.good { color: #9be0a8; border-color: rgba(63,185,80,.5); }
.op-chip.warn { color: #e6c878; border-color: rgba(210,153,34,.5); }
.op-chip.dim { color: var(--dim); }
.op-topo { color: var(--dim); }
.op-run-row { display: flex; gap: 10px; flex-wrap: wrap; }
.op-run { cursor: pointer; border: 1px solid var(--accent); background: rgba(79,156,255,.14); color: #cfe2ff;
  border-radius: 10px; padding: 10px 18px; font-size: 13.5px; font-weight: 600; }
.op-run:hover:not(:disabled) { background: rgba(79,156,255,.24); }
.op-run.ghost { border-color: var(--bd); background: transparent; color: var(--dim); font-weight: 500; }
.op-run:disabled { opacity: .45; cursor: not-allowed; }
.op-hint { color: var(--dim); font-size: 12px; margin: 2px; line-height: 1.5; }

/* console */
.op-console { background: #0a0e14; border: 1px solid var(--bd); border-radius: 10px; min-height: 320px;
  max-height: 64vh; overflow: auto; padding: 14px 16px; font-family: "Cascadia Code", Consolas, monospace; font-size: 13px; }
.op-placeholder { color: var(--dim); text-align: center; padding: 80px 0; }
.op-section { margin: 14px 0 6px; padding: 6px 10px; font-weight: 700; font-size: 13.5px; color: #e6edf3;
  border-left: 3px solid var(--accent); background: rgba(79,156,255,.07); border-radius: 0 6px 6px 0; }
.op-stage { display: flex; align-items: center; gap: 9px; padding: 5px 0; }
.op-sdot { width: 9px; height: 9px; border-radius: 50%; flex: 0 0 auto; background: var(--dim); }
.op-sdot.run { background: var(--warn); animation: oppulse 1s infinite; }
.op-sdot.ok { background: var(--good); } .op-sdot.skip { background: var(--dim); } .op-sdot.fail { background: var(--bad); }
@keyframes oppulse { 0% { box-shadow: 0 0 0 0 rgba(210,153,34,.5); } 70% { box-shadow: 0 0 0 7px rgba(210,153,34,0); } 100% { box-shadow: 0 0 0 0 rgba(210,153,34,0); } }
.op-lbl { font-size: 13px; } .op-st { margin-left: auto; font-size: 11px; color: var(--dim); }
.op-ln { white-space: pre-wrap; padding: 1px 0; }
.op-ln.dim { color: var(--dim); } .op-ln.good { color: var(--good); } .op-ln.bad { color: var(--bad); } .op-ln.warn { color: var(--warn); }
.op-metric { display: inline-block; margin: 8px 8px 4px 0; background: #1c2330; border: 1px solid var(--bd);
  border-radius: 8px; padding: 8px 12px; }
.op-metric .l { color: var(--dim); font-size: 11px; } .op-metric .v { font-size: 16px; font-weight: 700; color: var(--accent); }

/* prompt diff */
.op-diff { margin: 8px 0; }
.op-diff-title { font-size: 12.5px; color: #cfe2ff; margin-bottom: 5px; }
.op-diff-cols { display: grid; grid-template-columns: 1fr auto 1fr; gap: 8px; align-items: stretch; }
.op-diff-cols.single { grid-template-columns: 1fr; }
.op-diff-box { margin: 0; padding: 9px 11px; background: #11161f; border: 1px solid var(--bd); border-radius: 8px;
  white-space: pre-wrap; word-break: break-word; font-size: 12px; line-height: 1.55; color: var(--dim); }
.op-diff-box.after { color: #e6edf3; }
.op-diff-box.after .added { color: var(--good); background: rgba(63,185,80,.12); display: inline-block; width: 100%; }
.op-diff-arrow { align-self: center; color: var(--accent); font-weight: 700; }

/* answer compare */
.op-compare { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin: 10px 0; }
.op-cmp-l { font-size: 12px; margin-bottom: 5px; }
.op-cmp-l.before { color: var(--dim); } .op-cmp-l.after { color: var(--good); }
.op-cmp-box { background: #11161f; border: 1px solid var(--bd); border-radius: 8px; padding: 10px 12px;
  font-size: 12.5px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }

.op-link { display: inline-block; margin: 6px 8px 0 0; color: var(--accent); text-decoration: none;
  border: 1px solid var(--accent); border-radius: 7px; padding: 6px 11px; font-size: 12px; }
.op-link:hover { background: rgba(79,156,255,.12); }
.op-rt { border-collapse: collapse; margin: 10px 0; font-size: 13px; }
.op-rt td, .op-rt th { border: 1px solid var(--bd); padding: 6px 12px; text-align: left; }
.op-rt th { background: #1c2330; color: var(--dim); font-weight: 600; }
.op-rt .ACCEPT { color: var(--good); font-weight: 700; }
.op-rt .ROLLBACK { color: var(--bad); font-weight: 700; }
.op-rt .BASELINE { color: var(--dim); }
</style>
