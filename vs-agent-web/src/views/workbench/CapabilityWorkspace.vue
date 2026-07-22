<template>
  <section class="capability-workspace">
    <header class="workspace-hero">
      <div>
        <p class="eyebrow">能力中心</p>
        <h1>统一管理 Agent 可调用能力</h1>
        <p class="muted">把工具、技能、MCP 运行态和权限提示收敛在一页。当前环境未开启的能力会降级为配置提示，不再作为页面错误暴露。</p>
      </div>
      <button type="button" :disabled="loading" @click="reloadAll">{{ loading ? '刷新中…' : '刷新能力' }}</button>
    </header>

    <div class="metric-grid">
      <article>
        <span>运行状态</span>
        <strong>{{ runtimeStatus }}</strong>
        <small>{{ runtime.unavailable ? '当前镜像未开放 MCP 治理接口' : '后端能力目录状态' }}</small>
      </article>
      <article>
        <span>受管工具</span>
        <strong>{{ runtime.tools.length }}</strong>
        <small>含风险、确认和熔断信息</small>
      </article>
      <article>
        <span>技能</span>
        <strong>{{ skills.catalog.length }}</strong>
        <small>可导出给 Dify 或由 Agent 调用</small>
      </article>
    </div>

    <p v-if="runtime.error" class="soft-notice">{{ runtime.error }}</p>
    <p v-if="skills.error" class="soft-notice">{{ skills.error }}</p>

    <div class="workspace-grid">
      <WorkbenchSection title="工具调用" class="panel">
        <template v-if="runtime.tools.length">
          <label for="capability-tool">选择工具</label>
          <select id="capability-tool" v-model="selectedToolName" @change="resetTool">
            <option v-for="tool in runtime.tools" :key="toolKey(tool)" :value="toolKey(tool)">{{ toolLabel(tool) }}</option>
          </select>
          <p class="muted">{{ describe(selectedTool?.description, '该工具暂未提供中文说明。') }}</p>
          <PermissionNotice :risk="risk" :reason="selectedTool?.reason || selectedTool?.description || ''" :confirmed="confirmed" @confirm="confirmAndInvoke" />
          <label for="capability-tool-args">入参 JSON</label>
          <textarea id="capability-tool-args" v-model="toolArgsJson" rows="7" spellcheck="false" />
          <p v-if="toolJsonError" class="status-error">{{ toolJsonError }}</p>
          <button v-if="risk === 'safe'" type="button" :disabled="runtime.invoking" @click="invoke(false)">{{ runtime.invoking ? '调用中…' : '调用工具' }}</button>
          <button v-else-if="risk !== 'blocked'" type="button" :disabled="runtime.invoking" @click="confirmAndInvoke">{{ runtime.invoking ? '调用中…' : '确认并调用' }}</button>
          <p v-else class="status-error">该工具已被运行策略阻止。</p>
          <pre v-if="runtime.lastResult">{{ pretty(runtime.lastResult) }}</pre>
        </template>
        <div v-else class="empty-state">
          <strong>当前没有可直接调用的受管工具</strong>
          <p>这通常表示 Docker 镜像未包含 MCP 治理接口，或后端尚未完成工具发现。你仍可以使用对话、知识库和工作流入口。</p>
        </div>
      </WorkbenchSection>

      <WorkbenchSection title="技能目录" class="panel">
        <input v-model="skillSearch" type="search" placeholder="搜索技能" />
        <div v-if="filteredSkills.length" class="skill-list">
          <button v-for="skill in filteredSkills" :key="skillKey(skill)" type="button" :class="{ selected: skillKey(skill) === skillKey(skills.selected) }" @click="selectSkill(skill)">
            <strong>{{ skillLabel(skill) }}</strong>
            <span>{{ describe(skill.description || skill.summary, '暂无中文说明') }}</span>
          </button>
        </div>
        <div v-else class="empty-state">
          <strong>当前没有可展示的技能</strong>
          <p>如果后端已经启动，请刷新；如果仍为空，说明当前镜像未注册 Skill Bean 或技能扫描未启用。</p>
        </div>
      </WorkbenchSection>
    </div>

    <WorkbenchSection title="能力详情" class="panel detail-panel">
      <template v-if="skills.selected">
        <div class="detail-header">
          <div>
            <h3>{{ skillLabel(skills.selected) }}</h3>
            <p class="muted">{{ describe(skills.selected.description || skills.selected.summary, '暂无中文说明') }}</p>
          </div>
          <button type="button" :disabled="skills.executing" @click="executeSkill">{{ skills.executing ? '执行中…' : '执行技能' }}</button>
        </div>
        <label for="skill-args">技能入参 JSON</label>
        <textarea id="skill-args" v-model="skillArgsJson" rows="6" spellcheck="false" />
        <p v-if="skillJsonError || localError" class="status-error">{{ skillJsonError || localError }}</p>
        <pre v-if="skills.lastResult">{{ pretty(skills.lastResult) }}</pre>
      </template>
      <p v-else class="muted">选择一个技能后查看参数与执行结果。</p>
    </WorkbenchSection>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import PermissionNotice from '../../components/workbench/PermissionNotice.vue'
import WorkbenchSection from '../../components/workbench/WorkbenchSection.vue'
import { useRuntimeStore } from '../../stores/runtime'
import { useSkillsStore } from '../../stores/skills'
import { useWorkbenchStore } from '../../stores/workbench'
import { localizedDescription, statusLabel } from '../../utils/productText'

const runtime = useRuntimeStore()
const skills = useSkillsStore()
const workbench = useWorkbenchStore()
const selectedToolName = ref('')
const toolArgsJson = ref('{}')
const skillArgsJson = ref('{}')
const confirmed = ref(false)
const toolJsonError = ref('')
const skillJsonError = ref('')
const localError = ref('')
const skillSearch = ref('')

const loading = computed(() => runtime.loading || skills.loading)
const runtimeStatus = computed(() => statusLabel(runtime.status))
const toolKey = (tool) => tool?.name || tool?.toolName || tool?.id || ''
const toolLabel = (tool) => tool?.displayName || toolKey(tool) || '未命名工具'
const skillKey = (skill) => skill?.name || skill?.id || ''
const skillLabel = (skill) => skill?.displayName || skillKey(skill) || '未命名技能'
const selectedTool = computed(() => runtime.tools.find((tool) => toolKey(tool) === selectedToolName.value) || null)
const risk = computed(() => runtime.riskForTool(selectedTool.value))
const filteredSkills = computed(() => {
  const query = skillSearch.value.trim().toLowerCase()
  return skills.catalog.filter((skill) => !query || `${skillLabel(skill)} ${skill.description || skill.summary || ''}`.toLowerCase().includes(query))
})
const pretty = (value) => JSON.stringify(value, null, 2)
const describe = (value, fallback) => localizedDescription(value, fallback)

const resetTool = () => {
  toolArgsJson.value = '{}'
  confirmed.value = false
  toolJsonError.value = ''
  localError.value = ''
}

const initializeTool = () => {
  if (!runtime.tools.length) {
    selectedToolName.value = ''
    return
  }
  if (!runtime.tools.some((tool) => toolKey(tool) === selectedToolName.value)) selectedToolName.value = toolKey(runtime.tools[0])
  resetTool()
}

const reloadAll = async () => {
  localError.value = ''
  await Promise.all([runtime.loadHealth(), runtime.loadTools(), skills.loadSkills()])
  initializeTool()
  if (!skills.selected && skills.catalog.length) await selectSkill(skills.catalog[0])
}

const invoke = async (isConfirmed) => {
  toolJsonError.value = ''
  localError.value = ''
  try {
    const normalized = runtime.normalizeArgumentsJson(toolArgsJson.value)
    const result = await runtime.invokeTool({ toolName: selectedToolName.value, argumentsJson: normalized, confirmed: isConfirmed })
    workbench.addInvocation({ source: 'capabilities', name: toolLabel(selectedTool.value), status: result?.success === false ? 'failed' : 'complete', summary: result?.message || result?.errorMessage || '工具调用完成' })
  } catch (error) {
    toolJsonError.value = error instanceof SyntaxError ? '请输入合法的 JSON 对象。' : ''
    if (!toolJsonError.value) localError.value = runtime.error || error?.message || '工具调用失败。'
  }
}

const confirmAndInvoke = async () => {
  confirmed.value = true
  await invoke(true)
}

const selectSkill = async (skill) => {
  localError.value = ''
  skillJsonError.value = ''
  skillArgsJson.value = '{}'
  try {
    await skills.selectSkill(skillKey(skill))
  } catch {
    localError.value = skills.error || '技能详情加载失败。'
  }
}

const executeSkill = async () => {
  skillJsonError.value = ''
  localError.value = ''
  try {
    const args = JSON.parse(skillArgsJson.value || '{}')
    if (!args || Array.isArray(args) || typeof args !== 'object') {
      skillJsonError.value = '技能入参必须是 JSON 对象。'
      return
    }
    const result = await skills.executeSelected(args)
    workbench.addInvocation({ source: 'skills', name: skillLabel(skills.selected), status: 'complete', summary: result?.message || '技能执行完成' })
  } catch (error) {
    skillJsonError.value = error instanceof SyntaxError ? '请输入合法的 JSON 对象。' : ''
    if (!skillJsonError.value) localError.value = skills.error || error?.message || '技能执行失败。'
  }
}

onMounted(reloadAll)
</script>

<style scoped>
.capability-workspace { display: grid; gap: 18px; padding: 20px; }
.workspace-hero { display: flex; justify-content: space-between; gap: 18px; padding: 22px; background: linear-gradient(135deg, rgba(41, 98, 255, .16), rgba(24, 189, 188, .08)), var(--color-panel); border: 1px solid var(--color-border); border-radius: var(--radius-lg); }
.workspace-hero > button { align-self: center; min-width: 108px; padding-inline: 18px; }
.workspace-hero h1 { margin: 4px 0 8px; font-size: clamp(1.5rem, 3vw, 2.2rem); letter-spacing: -0.04em; }
.workspace-hero p { max-width: 720px; margin: 0; }
.eyebrow { color: var(--color-primary); font-size: .75rem; font-weight: 800; letter-spacing: .16em; }
.metric-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.metric-grid article { display: grid; gap: 5px; padding: 16px; background: var(--color-panel); border: 1px solid var(--color-border); border-radius: var(--radius-md); }
.metric-grid span, .metric-grid small { color: var(--color-text-muted); }
.metric-grid strong { font-size: 1.5rem; }
.workspace-grid { display: grid; grid-template-columns: minmax(300px, 1fr) minmax(300px, 1fr); gap: 16px; }
.panel { display: grid; align-content: start; gap: 12px; }
.panel input, .panel select, .panel textarea { width: 100%; padding: 10px 11px; color: var(--color-text); background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }
.panel label { color: var(--color-text-muted); font-size: .8rem; font-weight: 700; }
.skill-list { display: grid; gap: 8px; max-height: 380px; overflow: auto; }
.skill-list button { display: grid; gap: 4px; padding: 11px; color: var(--color-text); text-align: left; background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }
.skill-list button.selected { border-color: var(--color-primary); background: rgba(90, 167, 255, .12); }
.skill-list span { color: var(--color-text-muted); font-size: .8rem; }
.detail-header { display: flex; justify-content: space-between; align-items: start; gap: 12px; }
.detail-header h3 { margin: 0 0 4px; }
.detail-panel pre, .panel pre { max-height: 320px; margin: 0; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; padding: 12px; color: var(--color-text); background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); font-size: .78rem; }
.empty-state { display: grid; gap: 8px; padding: 18px; background: var(--color-bg-elevated); border: 1px dashed var(--color-border); border-radius: var(--radius-md); }
.empty-state p { margin: 0; color: var(--color-text-muted); }
.soft-notice { margin: 0; padding: 10px 12px; color: var(--color-warning); background: rgba(216, 168, 79, .08); border: 1px solid rgba(216, 168, 79, .24); border-radius: var(--radius-sm); }
.status-error { margin: 0; color: var(--color-danger); }
button { cursor: pointer; }
button:disabled { opacity: .55; cursor: not-allowed; }
@media (max-width: 900px) {
  .capability-workspace { padding: 14px; }
  .workspace-hero { flex-direction: column; }
  .metric-grid, .workspace-grid { grid-template-columns: 1fr; }
}
</style>
