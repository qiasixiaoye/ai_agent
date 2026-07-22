<template>
  <section class="capability-workspace">
    <header class="workspace-hero">
      <div>
        <p class="eyebrow">能力中心</p>
        <h1>统一编排 Agent 可调用能力</h1>
        <p class="muted">
          本页把本地工具、外部 MCP 路由、复合技能和受管 MCP 统一成一张能力注册表。
          治理接口未接入时只显示配置提示，不再阻断本地能力展示。
        </p>
      </div>
      <button type="button" :disabled="loading" @click="reloadAll">
        {{ loading ? '刷新中…' : '刷新能力' }}
      </button>
    </header>

    <div class="metric-grid">
      <article>
        <span>可用能力</span>
        <strong>{{ stats.total }}</strong>
        <small>工具、技能与 MCP 统一计数</small>
      </article>
      <article>
        <span>本地工具</span>
        <strong>{{ stats.tools }}</strong>
        <small>由后端 Agent Platform 执行</small>
      </article>
      <article>
        <span>复合技能</span>
        <strong>{{ stats.skills }}</strong>
        <small>带说明、输入和多步执行语义</small>
      </article>
      <article>
        <span>MCP 能力</span>
        <strong>{{ stats.mcp }}</strong>
        <small>含外部路由与受管目录</small>
      </article>
      <article>
        <span>治理状态</span>
        <strong>{{ runtimeStatus }}</strong>
        <small>{{ runtime.unavailable ? '受管 MCP 接口未接入' : '运行时目录检查完成' }}</small>
      </article>
    </div>

    <p v-if="platformError" class="soft-notice">{{ platformError }}</p>
    <p v-if="skills.error" class="soft-notice">{{ skills.error }}</p>
    <p v-if="runtime.error" class="soft-notice">
      {{ runtime.error }} 本地工具与技能已独立展示，可继续调用。
    </p>

    <div class="toolbar">
      <input v-model="searchText" type="search" placeholder="搜索能力、标签或说明" />
      <select v-model="typeFilter" aria-label="能力类型">
        <option value="all">全部能力</option>
        <option value="tool">工具</option>
        <option value="skill">技能</option>
        <option value="mcp">MCP</option>
      </select>
    </div>

    <div class="workspace-grid">
      <WorkbenchSection title="能力注册表" class="catalog-panel">
        <template v-if="filteredCatalog.length">
          <div v-for="group in groupedCatalog" :key="group.name" class="catalog-group">
            <div class="group-title">
              <div>
                <strong>{{ group.name }}</strong>
                <span>{{ categoryMeta(group.name).summary }}</span>
              </div>
              <small>{{ group.items.length }} 项</small>
            </div>

            <button
              v-for="item in group.items"
              :key="item.id"
              type="button"
              class="capability-card"
              :class="{ selected: item.id === selectedCapability?.id }"
              @click="selectCapability(item)"
            >
              <span class="kind-pill">{{ kindLabel(item) }}</span>
              <strong>{{ item.label }}</strong>
              <span class="card-description">{{ describe(item.description, '该能力暂未提供中文说明。') }}</span>
              <span class="card-meta">
                <span>{{ item.functionGroup }}</span>
                <span>{{ item.permission?.label }}</span>
                <span>{{ sourceLabel(item) }}</span>
                <span v-if="item.timeoutMs">{{ Math.round(item.timeoutMs / 1000) }} 秒超时</span>
                <span v-if="item.requiredParams?.length">需 {{ item.requiredParams.length }} 个参数</span>
              </span>
              <span v-if="item.tags?.length" class="tag-row">
                <span v-for="tag in item.tags.slice(0, 4)" :key="`${item.id}:${tag}`">{{ tag }}</span>
              </span>
            </button>
          </div>
        </template>

        <div v-else class="empty-state">
          <strong>没有匹配的能力</strong>
          <p>请清空搜索条件，或确认后端工具与技能服务已经启动。</p>
        </div>
      </WorkbenchSection>

      <WorkbenchSection title="调用与治理" class="detail-panel">
        <template v-if="selectedCapability">
          <div class="detail-header">
            <div>
              <p class="eyebrow">{{ kindLabel(selectedCapability) }}</p>
              <h2>{{ selectedCapability.label }}</h2>
              <p class="muted">{{ describe(selectedCapability.description, '该能力暂未提供中文说明。') }}</p>
            </div>
            <span class="status-chip">{{ selectedCapability.executable ? '可调用' : '仅展示' }}</span>
          </div>

          <dl class="detail-grid">
            <div>
              <dt>调用范围</dt>
              <dd>{{ sourceLabel(selectedCapability) }}</dd>
            </div>
            <div>
              <dt>组织方式</dt>
              <dd>{{ selectedCapability.category }}</dd>
            </div>
            <div>
              <dt>治理策略</dt>
              <dd>{{ selectedCapability.governance }}</dd>
            </div>
            <div>
              <dt>功能域</dt>
              <dd>{{ selectedCapability.functionGroup }}</dd>
            </div>
            <div>
              <dt>权限等级</dt>
              <dd>{{ selectedCapability.permission?.label }} · {{ selectedCapability.permission?.scope }}</dd>
            </div>
            <div>
              <dt>必填参数</dt>
              <dd>
                <template v-if="selectedCapability.requiredParams?.length">
                  <code v-for="param in selectedCapability.requiredParams" :key="param">{{ param }}</code>
                </template>
                <span v-else>未声明</span>
              </dd>
            </div>
            <div>
              <dt>依赖能力</dt>
              <dd>
                <template v-if="selectedCapability.dependentTools?.length">
                  <code v-for="tool in selectedCapability.dependentTools" :key="tool">{{ tool }}</code>
                </template>
                <span v-else>无显式依赖</span>
              </dd>
            </div>
          </dl>

          <section class="governance-audit">
            <div class="audit-title">
              <div>
                <strong>治理审计</strong>
                <span>安全契约、权限范围、效果评估和上线状态</span>
              </div>
              <small>{{ auditIssuesForSelected.length }} 个问题</small>
            </div>
            <div class="audit-contract-grid">
              <span>风险：{{ governanceContract.riskLevel || '未声明' }}</span>
              <span>权限：{{ governanceContract.permissionScopes?.length ? governanceContract.permissionScopes.join(' / ') : '未声明' }}</span>
              <span>评估：{{ governanceContract.evaluationProfile || '未声明' }}</span>
              <span>状态：{{ governanceContract.reviewStatus || 'UNREVIEWED' }} · {{ governanceContract.lifecycleStatus || 'ACTIVE' }}</span>
            </div>
            <ul v-if="auditIssuesForSelected.length" class="audit-issue-list">
              <li v-for="issue in auditIssuesForSelected" :key="`${issue.capabilityType}:${issue.capabilityName}:${issue.category}:${issue.message}`">
                <strong>{{ auditSeverityLabel(issue.severity) }} · {{ issue.category }}</strong>
                <span>{{ issue.message }}</span>
                <small>{{ issue.recommendation }}</small>
              </li>
            </ul>
            <p v-else class="audit-clean">当前能力没有命中契约审计问题。</p>
          </section>

          <PermissionNotice
            v-if="capabilityRisk !== 'safe'"
            :risk="capabilityRisk"
            :reason="selectedCapability.permission?.reason || selectedCapability.raw?.reason || selectedCapability.description || ''"
            :confirmed="managedConfirmed"
            @confirm="executeCapability(true)"
          />

          <label for="capability-args">入参 JSON</label>
          <textarea id="capability-args" v-model="argumentsJson" rows="8" spellcheck="false" />
          <p v-if="jsonError || actionError" class="status-error">{{ jsonError || actionError }}</p>

          <div class="action-row">
            <button
              v-if="capabilityRisk !== 'blocked'"
              type="button"
              :disabled="invoking"
              @click="executeCapability(capabilityRisk !== 'safe')"
            >
              {{ invoking ? '调用中…' : actionLabel }}
            </button>
            <p v-else class="status-error">该能力已被运行策略阻止。</p>
          </div>

          <pre v-if="lastResult">{{ pretty(lastResult) }}</pre>
        </template>

        <div v-else class="empty-state">
          <strong>请选择一个能力</strong>
      <p>能力可以是原子工具、复合技能，或通过 MCP 接入的外部服务。</p>
        </div>
      </WorkbenchSection>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import PermissionNotice from '../../components/workbench/PermissionNotice.vue'
import WorkbenchSection from '../../components/workbench/WorkbenchSection.vue'
import { executePlatformTool, getCapabilityGovernanceAudit, listPlatformTools } from '../../services/api'
import { useRuntimeStore } from '../../stores/runtime'
import { useSkillsStore } from '../../stores/skills'
import { useWorkbenchStore } from '../../stores/workbench'
import {
  buildCapabilityCatalog,
  capabilityStats,
  groupCapabilitiesByCategory,
  normalizeSkill
} from '../../utils/capabilityCatalog'
import { parseJsonObject } from '../../utils/streamLifecycle'
import { localizedDescription, productErrorMessage, statusLabel } from '../../utils/productText'

const runtime = useRuntimeStore()
const skills = useSkillsStore()
const workbench = useWorkbenchStore()

const platformTools = ref([])
const platformLoading = ref(false)
const platformExecuting = ref(false)
const platformError = ref('')
const selectedCapabilityId = ref('')
const searchText = ref('')
const typeFilter = ref('all')
const argumentsJson = ref('{}')
const jsonError = ref('')
const actionError = ref('')
const lastResult = ref(null)
const managedConfirmed = ref(false)
const governanceAudit = ref(null)
const governanceAuditError = ref('')

const loading = computed(() => platformLoading.value || runtime.loading || skills.loading)
const invoking = computed(() => platformExecuting.value || runtime.invoking || skills.executing)
const runtimeStatus = computed(() => statusLabel(runtime.status))
const catalog = computed(() => buildCapabilityCatalog({
  platformTools: platformTools.value,
  skills: skills.catalog,
  managedTools: runtime.tools
}))
const stats = computed(() => capabilityStats(catalog.value))
const selectedCapability = computed(() => {
  const item = catalog.value.find((entry) => entry.id === selectedCapabilityId.value) || catalog.value[0] || null
  if (item?.kind === 'skill' && skills.selected?.name === item.name) return { ...item, ...normalizeSkill(skills.selected) }
  return item
})
const governanceContract = computed(() => selectedCapability.value?.governanceContract || {})
const auditIssuesForSelected = computed(() => {
  const selected = selectedCapability.value
  if (!selected || !governanceAudit.value?.issues) return []
  const auditType = selected.kind === 'skill' ? 'skill' : 'tool'
  return governanceAudit.value.issues.filter((issue) =>
    issue.capabilityType === auditType && issue.capabilityName === selected.name
  )
})
const capabilityRisk = computed(() => {
  const level = selectedCapability.value?.permission?.level
  if (level === 'blocked') return 'blocked'
  if (level === 'confirm' || level === 'local_write') return 'confirm'
  if (selectedCapability.value?.kind === 'managed') return runtime.riskForTool(selectedCapability.value.raw)
  return 'safe'
})
const actionLabel = computed(() => {
  if (!selectedCapability.value) return '调用能力'
  if (selectedCapability.value.kind === 'skill') return '执行技能'
  if (capabilityRisk.value !== 'safe') return '确认并调用'
  return '调用工具'
})
const filteredCatalog = computed(() => {
  const query = searchText.value.trim().toLowerCase()
  return catalog.value.filter((item) => {
    const matchesType = typeFilter.value === 'all'
      || item.kind === typeFilter.value
      || (typeFilter.value === 'mcp' && (item.sourceType === 'MCP' || item.kind === 'managed'))
    const haystack = [
      item.label,
      item.name,
      item.description,
      item.category,
      item.functionGroup,
      item.permission?.label,
      item.permission?.scope,
      item.sourceType,
      ...(item.tags || [])
    ].join(' ').toLowerCase()
    return matchesType && (!query || haystack.includes(query))
  })
})
const groupedCatalog = computed(() => {
  const groups = groupCapabilitiesByCategory(filteredCatalog.value)
  const order = ['原子工具', '外部 MCP', '复合技能', '受管 MCP', '其他']
  return order
    .filter((name) => groups[name]?.length)
    .map((name) => ({ name, items: groups[name] }))
})

const categoryMeta = (name) => ({
  原子工具: { summary: '单次函数调用，适合被 Agent 直接组合' },
  '外部 MCP': { summary: '通过 MCP 路由对接外部工具或工作流' },
  复合技能: { summary: '封装提示、知识和多步工具编排' },
  '受管 MCP': { summary: '带确认、风险和熔断策略的治理目录' },
  其他: { summary: '尚未归类的能力' }
}[name] || { summary: '尚未归类的能力' })
const describe = (value, fallback) => localizedDescription(value, fallback)
const pretty = (value) => JSON.stringify(value, null, 2)
const kindLabel = (item) => ({
  tool: item?.sourceType === 'MCP' ? 'MCP 工具' : '工具',
  skill: '技能',
  managed: '受管 MCP'
}[item?.kind] || '能力')
const sourceLabel = (item) => ({
  LOCAL: '本地后端',
  MCP: '外部 MCP',
  MCP_MANAGED: '受管 MCP'
}[item?.sourceType] || item?.sourceType || '未知来源')

const auditSeverityLabel = (severity) => ({
  HIGH: '高风险',
  WARN: '需补齐',
  INFO: '提示'
}[severity] || severity || '提示')

const resetInvocation = () => {
  argumentsJson.value = '{}'
  jsonError.value = ''
  actionError.value = ''
  lastResult.value = null
  managedConfirmed.value = false
}

const ensureSelection = () => {
  if (!catalog.value.length) {
    selectedCapabilityId.value = ''
    return
  }
  if (!catalog.value.some((item) => item.id === selectedCapabilityId.value)) {
    selectedCapabilityId.value = catalog.value[0].id
  }
}

const loadPlatformTools = async () => {
  platformLoading.value = true
  try {
    platformTools.value = await listPlatformTools()
    platformError.value = ''
  } catch (error) {
    platformTools.value = []
    platformError.value = productErrorMessage(error, '本地工具目录')
  } finally {
    platformLoading.value = false
  }
}

const loadGovernanceAudit = async () => {
  try {
    governanceAudit.value = await getCapabilityGovernanceAudit()
    governanceAuditError.value = ''
  } catch (error) {
    governanceAudit.value = null
    governanceAuditError.value = productErrorMessage(error, '能力治理审计')
  }
}

const reloadAll = async () => {
  actionError.value = ''
  await Promise.allSettled([
    loadPlatformTools(),
    loadGovernanceAudit(),
    skills.loadSkills(),
    runtime.loadHealth(),
    runtime.loadTools()
  ])
  ensureSelection()
}

const selectCapability = async (item) => {
  selectedCapabilityId.value = item.id
  resetInvocation()
  if (item.kind === 'skill') {
    try {
      await skills.selectSkill(item.name)
      const refreshed = normalizeSkill(skills.selected)
      selectedCapabilityId.value = refreshed.id
    } catch {
      actionError.value = skills.error || '技能详情加载失败。'
    }
  }
}

const executeCapability = async (confirmed = false) => {
  if (!selectedCapability.value) return
  jsonError.value = ''
  actionError.value = ''
  try {
    const args = parseJsonObject(argumentsJson.value || '{}', '入参')
    const item = selectedCapability.value
    if (item.kind === 'tool') {
      platformExecuting.value = true
      lastResult.value = await executePlatformTool(item.name, args, `capability-${Date.now()}`)
      workbench.addInvocation({ source: 'capabilities', name: item.label, status: 'complete', summary: '工具调用完成' })
    } else if (item.kind === 'skill') {
      if (skills.selected?.name !== item.name) await skills.selectSkill(item.name)
      lastResult.value = await skills.executeSelected(args)
      workbench.addInvocation({ source: 'skills', name: item.label, status: 'complete', summary: '技能执行完成' })
    } else if (item.kind === 'managed') {
      managedConfirmed.value = confirmed
      lastResult.value = await runtime.invokeTool({
        toolName: item.name,
        argumentsJson: JSON.stringify(args),
        confirmed
      })
      workbench.addInvocation({ source: 'mcp', name: item.label, status: 'complete', summary: '受管 MCP 调用完成' })
    }
  } catch (error) {
    if (/JSON/.test(error?.message || '')) {
      jsonError.value = '请输入合法的 JSON 对象。'
    } else {
      actionError.value = platformError.value || runtime.error || skills.error || error?.message || '能力调用失败。'
    }
  } finally {
    platformExecuting.value = false
  }
}

onMounted(reloadAll)
</script>

<style scoped>
.capability-workspace { display: grid; gap: 18px; padding: 20px; }
.workspace-hero { display: flex; justify-content: space-between; gap: 18px; padding: 22px; background: linear-gradient(135deg, rgba(41, 98, 255, .16), rgba(24, 189, 188, .08)), var(--color-panel); border: 1px solid var(--color-border); border-radius: var(--radius-lg); }
.workspace-hero > button { align-self: center; min-width: 108px; padding-inline: 18px; }
.workspace-hero h1 { margin: 4px 0 8px; font-size: clamp(1.5rem, 3vw, 2.2rem); letter-spacing: -0.04em; }
.workspace-hero p { max-width: 780px; margin: 0; }
.eyebrow { margin: 0; color: var(--color-primary); font-size: .75rem; font-weight: 800; letter-spacing: .16em; text-transform: uppercase; }
.metric-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
.metric-grid article { display: grid; gap: 5px; min-height: 104px; padding: 16px; background: var(--color-panel); border: 1px solid var(--color-border); border-radius: var(--radius-md); }
.metric-grid span, .metric-grid small { color: var(--color-text-muted); }
.metric-grid strong { font-size: 1.55rem; }
.soft-notice { margin: 0; padding: 10px 12px; color: var(--color-warning); background: rgba(216, 168, 79, .08); border: 1px solid rgba(216, 168, 79, .24); border-radius: var(--radius-sm); }
.toolbar { display: grid; grid-template-columns: minmax(260px, 1fr) 160px; gap: 12px; }
.toolbar input, .toolbar select, .detail-panel textarea { width: 100%; padding: 10px 11px; color: var(--color-text); background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }
.workspace-grid { display: grid; grid-template-columns: minmax(340px, .85fr) minmax(460px, 1.15fr); gap: 16px; align-items: start; }
.catalog-panel, .detail-panel { display: grid; align-content: start; gap: 14px; }
.catalog-group { display: grid; gap: 9px; }
.group-title { display: flex; justify-content: space-between; gap: 12px; padding: 4px 2px 0; }
.group-title div { display: grid; gap: 2px; }
.group-title span, .group-title small { color: var(--color-text-muted); font-size: .78rem; }
.capability-card { position: relative; display: grid; gap: 7px; width: 100%; padding: 14px; color: var(--color-text); text-align: left; background: linear-gradient(135deg, rgba(255,255,255,.035), rgba(90,167,255,.035)); border: 1px solid var(--color-border); border-radius: var(--radius-md); cursor: pointer; }
.capability-card:hover, .capability-card.selected { border-color: rgba(90, 167, 255, .76); background: rgba(90, 167, 255, .10); }
.kind-pill, .status-chip, .tag-row span { width: fit-content; padding: 3px 8px; color: var(--color-primary); background: rgba(90, 167, 255, .12); border: 1px solid rgba(90, 167, 255, .26); border-radius: 999px; font-size: .7rem; font-weight: 800; }
.card-description { color: var(--color-text-muted); font-size: .88rem; line-height: 1.55; }
.card-meta, .tag-row { display: flex; flex-wrap: wrap; gap: 6px; color: var(--color-text-muted); font-size: .72rem; }
.tag-row span { color: var(--color-text-muted); background: rgba(255,255,255,.04); border-color: var(--color-border); }
.detail-header { display: flex; justify-content: space-between; gap: 14px; align-items: start; }
.detail-header h2 { margin: 6px 0 8px; }
.detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin: 0; }
.detail-grid div { min-height: 72px; padding: 12px; background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }
.detail-grid dt { margin-bottom: 6px; color: var(--color-text-muted); font-size: .78rem; }
.detail-grid dd { margin: 0; color: var(--color-text); }
.detail-grid code { display: inline-flex; margin: 0 6px 6px 0; padding: 3px 7px; color: var(--color-primary); background: rgba(90, 167, 255, .10); border: 1px solid rgba(90, 167, 255, .22); border-radius: 8px; }
.governance-audit { display: grid; gap: 12px; padding: 14px; background: linear-gradient(135deg, rgba(24, 189, 188, .08), rgba(90, 167, 255, .06)); border: 1px solid rgba(90, 167, 255, .22); border-radius: var(--radius-md); }
.audit-title { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; }
.audit-title div { display: grid; gap: 3px; }
.audit-title span, .audit-title small, .audit-clean { color: var(--color-text-muted); font-size: .78rem; }
.audit-contract-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.audit-contract-grid span { padding: 8px 10px; color: var(--color-text); background: rgba(0,0,0,.16); border: 1px solid var(--color-border); border-radius: var(--radius-sm); font-size: .78rem; }
.audit-issue-list { display: grid; gap: 8px; margin: 0; padding: 0; list-style: none; }
.audit-issue-list li { display: grid; gap: 3px; padding: 9px 10px; background: rgba(216, 168, 79, .08); border: 1px solid rgba(216, 168, 79, .24); border-radius: var(--radius-sm); }
.audit-issue-list span { color: var(--color-text); font-size: .82rem; }
.audit-issue-list small { color: var(--color-text-muted); }
.detail-panel label { color: var(--color-text-muted); font-size: .8rem; font-weight: 700; }
.action-row { display: flex; align-items: center; gap: 10px; }
.detail-panel pre { max-height: 360px; margin: 0; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; padding: 12px; color: var(--color-text); background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); font-size: .78rem; }
.empty-state { display: grid; gap: 8px; padding: 18px; background: var(--color-bg-elevated); border: 1px dashed var(--color-border); border-radius: var(--radius-md); }
.empty-state p { margin: 0; color: var(--color-text-muted); }
.status-error { margin: 0; color: var(--color-danger); }
button { cursor: pointer; }
button:disabled { opacity: .55; cursor: not-allowed; }
@media (max-width: 1120px) {
  .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .workspace-grid { grid-template-columns: 1fr; }
}
@media (max-width: 720px) {
  .capability-workspace { padding: 14px; }
  .workspace-hero { flex-direction: column; }
  .metric-grid, .toolbar, .detail-grid, .audit-contract-grid { grid-template-columns: 1fr; }
}
</style>
