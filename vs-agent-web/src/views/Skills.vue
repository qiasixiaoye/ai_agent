<template>
  <div class="sk-page">
    <header class="sk-header">
      <router-link to="/" class="back-link"><span>←</span> 返回首页</router-link>
      <h1>Skills 目录</h1>
      <p class="sk-subtitle">可复用技能：标准化输入参数 · 标准化输出 · 文件产物</p>
      <button class="reload-btn" @click="refresh" :disabled="loadingList">⟳ 刷新</button>
    </header>

    <main class="sk-main">
      <section class="routing-panel">
        <div class="routing-title">Skill 命中验证</div>
        <p class="hint">先召回并评分候选 Skill，仅把超过阈值的 Top-K 能力交给模型。</p>
        <div class="routing-actions">
          <input v-model="routeQuery" class="routing-input" placeholder="例如：帮我规划北京郊区今晚的银河摄影"
                 @keyup.enter="previewRoute" />
          <button class="run-btn" :disabled="routing || !routeQuery.trim()" @click="previewRoute">
            {{ routing ? '路由中...' : '预览命中' }}
          </button>
          <button class="eval-btn" :disabled="evaluating" @click="runRoutingEval">
            {{ evaluating ? '评测中...' : '运行评测集' }}
          </button>
        </div>
        <div v-if="routeResult" class="route-result">
          <strong>{{ routeResult.matched ? `命中：${routeResult.selectedSkillNames.join(', ')}` : '未命中，回退普通对话' }}</strong>
          <div v-for="c in routeResult.candidates" :key="c.skillName" class="candidate-row" :class="{ selected: c.selected }">
            <code>{{ c.skillName }}</code><span>score={{ c.score }}</span><span>{{ (c.reasons || []).join(' · ') || '无匹配证据' }}</span>
          </div>
        </div>
        <div v-if="evalResult" class="eval-result">
          评测集 {{ evalResult.suiteName }}：{{ evalResult.correct }}/{{ evalResult.total }}，
          accuracy={{ (evalResult.accuracy * 100).toFixed(1) }}%，误命中 {{ evalResult.falsePositives }}，漏命中 {{ evalResult.falseNegatives }}
        </div>
      </section>

      <div v-if="loadingList" class="hint">加载中...</div>
      <div v-else-if="skills.length === 0" class="hint">暂无已注册 Skill。请确认后端启动并扫描到 SKILL.md。</div>

      <div class="sk-grid">
        <AppCard
          v-for="s in skills"
          :key="s.name"
          clickable
          :title="s.displayName || s.name"
          :subtitle="s.description"
          @click="select(s.name)"
        >
          <template #actions>
            <TagChip v-if="s.version" :label="`v${s.version}`" />
            <TagChip :label="s.sourceType" accent />
          </template>
          <div class="sk-tags">
            <TagChip v-for="t in (s.tags || [])" :key="t" :label="`#${t}`" />
          </div>
          <code class="sk-code">{{ s.name }}</code>

          <div v-if="current && current.name === s.name" class="sk-detail" @click.stop>
            <!-- 技能内部结构：有序步骤(每步调哪个工具) + 操作手册。这是 Skill 区别于裸工具的地方。 -->
            <div v-if="(current.steps && current.steps.length) || current.instructions" class="sk-internals">
              <div class="internals-title">🧩 技能内部结构</div>
              <ol v-if="current.steps && current.steps.length" class="step-list">
                <li v-for="(st, i) in current.steps" :key="i" class="step-item">
                  <span class="step-name">{{ st.name }}</span>
                  <code v-if="st.uses" class="step-uses">{{ st.uses }}</code>
                  <span v-else class="step-uses none">综合</span>
                  <span v-if="st.description" class="step-desc">{{ st.description }}</span>
                </li>
              </ol>
              <details v-if="current.instructions" class="manual">
                <summary>📖 操作手册（SKILL.md 正文）</summary>
                <pre class="manual-body">{{ current.instructions }}</pre>
              </details>
            </div>

            <div v-if="!current.inputs || current.inputs.length === 0" class="hint">此 Skill 不需要参数。</div>
            <ParamField
              v-for="p in (current.inputs || [])"
              :key="p.name"
              :param="p"
              v-model="formData[p.name]"
            />

            <div v-if="current.examples && current.examples.length > 0" class="examples">
              <span class="examples-label">示例：</span>
              <button
                v-for="(ex, i) in current.examples"
                :key="i"
                class="example-chip"
                @click="applyExample(ex)"
              >{{ ex }}</button>
            </div>

            <div class="sk-actions">
              <button class="run-btn" :disabled="executing" @click="execute">
                {{ executing ? '执行中...' : '执行 Skill' }}
              </button>
            </div>

            <div v-if="error" class="error-banner">{{ error }}</div>
            <div v-if="result">
              <div class="result-summary">
                <StatusBadge :status="result.success" />
                <span class="result-meta">耗时 {{ result.elapsedMs }} ms</span>
              </div>
              <div v-if="!result.success" class="error-banner">{{ result.errorMessage }}</div>
              <template v-else>
                <div v-if="filePath" class="file-output">
                  <span class="file-label">📄 生成文件</span>
                  <code class="file-path">{{ filePath }}</code>
                  <a class="file-action" :href="downloadHref" target="_blank">下载</a>
                  <button class="file-action secondary" @click="copyPath">{{ copied ? '已复制' : '复制路径' }}</button>
                </div>
                <CodeBlock :content="result.data" collapsible default-expanded />
              </template>
            </div>
          </div>
        </AppCard>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import AppCard from '../components/ui/AppCard.vue'
import TagChip from '../components/ui/TagChip.vue'
import StatusBadge from '../components/ui/StatusBadge.vue'
import CodeBlock from '../components/ui/CodeBlock.vue'
import ParamField from '../components/ui/ParamField.vue'
import { listSkills, getSkill, executeSkill, fileDownloadUrl, previewSkillRoute, evaluateSkillRouting } from '../services/api'

const skills = ref([])
const current = ref(null)
const formData = reactive({})
const result = ref(null)
const error = ref(null)
const loadingList = ref(false)
const executing = ref(false)
const copied = ref(false)
const routeQuery = ref('')
const routeResult = ref(null)
const evalResult = ref(null)
const routing = ref(false)
const evaluating = ref(false)

const previewRoute = async () => {
  if (!routeQuery.value.trim()) return
  routing.value = true
  error.value = null
  try {
    routeResult.value = await previewSkillRoute(routeQuery.value.trim())
  } catch (e) {
    error.value = e.message
  } finally {
    routing.value = false
  }
}

const runRoutingEval = async () => {
  evaluating.value = true
  error.value = null
  try {
    evalResult.value = await evaluateSkillRouting()
  } catch (e) {
    error.value = e.message
  } finally {
    evaluating.value = false
  }
}

const filePath = computed(() => {
  const d = result.value?.data
  if (!d || typeof d !== 'object') return null
  if (typeof d.filePath === 'string') return d.filePath
  for (const [k, v] of Object.entries(d)) {
    if (typeof v === 'string' && /(^|[A-Z])(P|p)ath$/.test(k)) return v
  }
  return null
})

const downloadHref = computed(() => filePath.value ? fileDownloadUrl(filePath.value) : '#')

const copyPath = async () => {
  if (!filePath.value) return
  try {
    await navigator.clipboard.writeText(filePath.value)
    copied.value = true
    setTimeout(() => { copied.value = false }, 1500)
  } catch (e) {
    console.warn('copy failed', e)
  }
}

const refresh = async () => {
  loadingList.value = true
  try {
    skills.value = await listSkills()
  } catch (e) {
    error.value = e.message
  } finally {
    loadingList.value = false
  }
}

const select = async (name) => {
  if (current.value && current.value.name === name) {
    current.value = null
    return
  }
  result.value = null
  error.value = null
  Object.keys(formData).forEach(k => delete formData[k])
  try {
    const detail = await getSkill(name)
    current.value = detail
    for (const p of (detail.inputs || [])) {
      formData[p.name] = p.defaultValue ?? ''
    }
  } catch (e) {
    error.value = e.message
  }
}

const applyExample = (ex) => {
  if (!current.value) return
  const firstInput = (current.value.inputs || [])[0]
  if (firstInput) formData[firstInput.name] = ex
}

const execute = async () => {
  if (!current.value) return
  executing.value = true
  error.value = null
  result.value = null
  try {
    const args = {}
    for (const p of (current.value.inputs || [])) {
      const v = formData[p.name]
      if (v !== undefined && v !== '') {
        if (p.type === 'int' || p.type === 'number') args[p.name] = Number(v)
        else if (p.type === 'boolean') args[p.name] = Boolean(v)
        else if (p.type === 'object' || p.type === 'array') {
          try { args[p.name] = JSON.parse(v) } catch { args[p.name] = v }
        } else args[p.name] = v
      }
    }
    result.value = await executeSkill(current.value.name, args)
  } catch (e) {
    error.value = e.message
  } finally {
    executing.value = false
  }
}

onMounted(refresh)
</script>

<style scoped>
.sk-page {
  min-height: 100vh;
  background: var(--color-bg);
  display: flex;
  flex-direction: column;
}

.sk-header {
  padding: var(--space-6) var(--space-8) var(--space-4);
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
  color: #fff;
  position: relative;
}

.back-link {
  color: rgba(255, 255, 255, 0.85);
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.85rem;
}

.sk-header h1 {
  margin: var(--space-2) 0 var(--space-1);
  font-size: 1.6rem;
}

.sk-subtitle {
  margin: 0;
  opacity: 0.85;
  font-size: 0.9rem;
}

.reload-btn {
  position: absolute;
  top: var(--space-6);
  right: var(--space-8);
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: var(--radius-sm);
  padding: var(--space-1) var(--space-3);
  cursor: pointer;
}

.reload-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.sk-main {
  flex: 1;
  padding: var(--space-6) var(--space-8);
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
}

.hint {
  color: var(--color-text-subtle);
  font-size: 0.88rem;
}

.sk-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--space-4);
}

.routing-panel {
  margin-bottom: var(--space-5);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.routing-title { font-weight: 700; margin-bottom: var(--space-1); }
.routing-actions { display: flex; gap: var(--space-2); margin-top: var(--space-3); flex-wrap: wrap; }
.routing-input { flex: 1; min-width: 280px; border: 1px solid var(--color-border); border-radius: var(--radius-sm); padding: var(--space-2) var(--space-3); }
.eval-btn { border: 1px solid var(--color-primary); color: var(--color-primary); background: transparent; border-radius: var(--radius-sm); padding: var(--space-2) var(--space-4); cursor: pointer; }
.route-result, .eval-result { margin-top: var(--space-3); padding: var(--space-3); background: var(--color-surface-alt); border-radius: var(--radius-sm); font-size: 0.82rem; }
.candidate-row { display: grid; grid-template-columns: minmax(160px, 1fr) 90px 2fr; gap: var(--space-2); margin-top: var(--space-2); color: var(--color-text-muted); }
.candidate-row.selected { color: var(--color-primary); font-weight: 600; }

.sk-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  margin-bottom: var(--space-2);
}

.sk-code {
  display: inline-block;
  font-size: 0.75rem;
  color: var(--color-primary);
  background: var(--color-primary-light);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
}

.sk-detail {
  margin-top: var(--space-4);
  border-top: 1px solid var(--color-border);
  padding-top: var(--space-4);
}

.sk-internals {
  margin-bottom: var(--space-4);
  background: var(--color-surface-alt);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-3);
}

.internals-title {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: var(--space-2);
}

.step-list {
  margin: 0 0 var(--space-2);
  padding-left: 1.2rem;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.step-item {
  font-size: 0.8rem;
  color: var(--color-text);
}

.step-name {
  font-weight: 600;
}

.step-uses {
  margin-left: 6px;
  font-size: 0.72rem;
  color: var(--color-primary);
  background: var(--color-primary-light);
  border-radius: var(--radius-sm);
  padding: 1px 6px;
  font-family: var(--font-mono);
}

.step-uses.none {
  color: var(--color-text-subtle);
  background: var(--color-surface);
  font-family: inherit;
}

.step-desc {
  display: block;
  margin-top: 2px;
  font-size: 0.74rem;
  color: var(--color-text-muted);
}

.manual > summary {
  cursor: pointer;
  font-size: 0.78rem;
  color: var(--color-text-muted);
  font-weight: 600;
}

.manual-body {
  margin: var(--space-2) 0 0;
  padding: var(--space-2) var(--space-3);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 0.74rem;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 320px;
  overflow: auto;
}

.examples {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  align-items: center;
  margin-bottom: var(--space-3);
}

.examples-label {
  font-size: 0.78rem;
  color: var(--color-text-muted);
  font-weight: 600;
}

.example-chip {
  font-size: 0.75rem;
  border: 1px solid var(--color-border);
  background: var(--color-surface-alt);
  border-radius: var(--radius-pill);
  padding: 2px 10px;
  cursor: pointer;
  color: var(--color-text-muted);
}

.example-chip:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.sk-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--space-3);
}

.run-btn {
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-sm);
  padding: var(--space-2) var(--space-5);
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
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
  margin-bottom: var(--space-3);
}

.result-summary {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-2);
}

.result-meta {
  font-size: 0.8rem;
  color: var(--color-text-subtle);
  font-family: var(--font-mono);
}

.file-output {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  background: var(--color-primary-light);
  border: 1px solid var(--color-primary-soft);
  border-radius: var(--radius-sm);
  padding: var(--space-2) var(--space-3);
  margin-bottom: var(--space-2);
  font-size: 0.8rem;
}

.file-label {
  font-weight: 600;
  color: var(--color-text);
}

.file-path {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 2px 6px;
  font-family: var(--font-mono);
  font-size: 0.75rem;
  word-break: break-all;
}

.file-action {
  text-decoration: none;
  border: none;
  background: var(--color-primary);
  color: #fff;
  border-radius: var(--radius-sm);
  padding: 4px 10px;
  font-size: 0.75rem;
  cursor: pointer;
}

.file-action.secondary {
  background: var(--color-text-muted);
}

.file-action:hover {
  opacity: 0.9;
}
</style>
