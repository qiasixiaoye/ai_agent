const normalizeTags = (tags) => Array.isArray(tags) ? tags.filter(Boolean).map(String) : []
const normalizeSource = (sourceType) => String(sourceType || 'LOCAL').toUpperCase()
const hasChinese = (value) => /[\u4e00-\u9fff]/.test(String(value || ''))

const CAPABILITY_TEXT = {
  astro_plan_summary: ['银河拍摄方案汇总', '汇总银心可见性、光污染和云量数据，生成可执行的银河拍摄建议。'],
  astrophoto_settings: ['星空拍摄参数', '根据焦距、光圈和光污染等级推荐快门、ISO、光圈与构图策略。'],
  cloud_cover: ['云量查询', '查询目标地点和日期的云量风险，辅助判断是否适合外拍。'],
  coffee_recipe: ['咖啡冲煮建议', '根据豆子、器具和口味偏好生成冲煮参数。'],
  exposure_advisor: ['曝光建议', '根据场景、器材和目标效果给出曝光组合。'],
  image_search: ['图片搜索', '按关键词检索图片素材或参考图。'],
  light_pollution: ['光污染评估', '按地点评估 Bortle 等级和夜空质量。'],
  mcp_router: ['MCP 路由器', '把自然语言指令路由到外部 MCP 工具或工作流服务。'],
  milkyway_rise: ['银心升起时间', '计算指定地点和日期的银河核心可见窗口。'],
  moto_trip_planner: ['摩旅路线规划', '按目的地、天数和偏好规划摩旅路线。'],
  result_summary: ['结果摘要', '把多工具返回结果整理成可读摘要。'],
  scenic_spots: ['景点推荐', '按目的地和偏好推荐沿途景点。'],
  trip_budget: ['旅行预算', '估算交通、住宿、餐饮和门票成本。'],
  trip_weather: ['旅行天气', '查询行程目的地天气并提示风险。'],
  web_search: ['网页搜索', '按问题检索网页信息并返回摘要。'],
  'coffee-tasting-notes': ['咖啡品鉴笔记', '按产地、烘焙度和冲煮条件生成风味轮廓与记录建议。'],
  'moto-gear-checklist': ['摩旅装备清单', '按季节、天数和路线风险生成装备、证件与工具清单。'],
  'pdf-generation': ['PDF 生成', '把结构化内容生成可下载的 PDF 文档。'],
  'astro-shoot-plan': ['银河拍摄计划', '组合天文、天气、光污染和摄影参数，生成完整拍摄计划。']
}

const localizedTitle = (name, displayName, fallback) => {
  if (hasChinese(displayName)) return displayName
  return CAPABILITY_TEXT[name]?.[0] || displayName || fallback
}

const localizedSummary = (name, description, fallback) => {
  if (hasChinese(description)) return description
  return CAPABILITY_TEXT[name]?.[1] || fallback
}

const requiredInputs = (inputs) => Array.isArray(inputs)
  ? inputs.filter((input) => input?.required !== false).map((input) => input?.name).filter(Boolean)
  : []

const hasTag = (tags, ...needles) => needles.some((needle) => tags.includes(needle))

const functionGroupFor = ({ name, tags, sourceType, kind }) => {
  if (sourceType === 'MCP' || sourceType === 'MCP_MANAGED' || tags.includes('mcp')) return '外部 MCP'
  if (hasTag(tags, 'astro', 'photography', 'weather', 'environment', 'calculation', 'exposure')) return '天文摄影'
  if (hasTag(tags, 'travel', 'motorcycle', 'sightseeing', 'budget', 'checklist')) return '旅行摩旅'
  if (hasTag(tags, 'coffee', 'tasting', 'recipe')) return '咖啡生活'
  if (hasTag(tags, 'file', 'document') || /pdf|file|document/i.test(name)) return '文档与文件'
  if (hasTag(tags, 'search', 'retrieval', 'image')) return '检索与资料'
  if (hasTag(tags, 'summary', 'llm')) return '汇总生成'
  return kind === 'skill' ? '复合技能' : '通用工具'
}

const permissionFor = ({ name, tags, sourceType, kind, raw }) => {
  const security = raw?.security || {}
  const risk = String(raw?.riskLevel || raw?.risk || security.riskLevel || '').toLowerCase()
  const confirmationRequired = raw?.confirmationRequired === true || security.requiresConfirmation === true
  const scopes = Array.isArray(security.permissionScopes) ? security.permissionScopes : []
  const highRiskScope = scopes.some((scope) => ['FILE_WRITE', 'EXTERNAL_WRITE', 'SYSTEM_COMMAND', 'PRIVATE_DATA', 'PAYMENT', 'DEPLOY'].includes(String(scope).toUpperCase()))
  const enabled = raw?.enabled !== false
  const circuitOpen = String(raw?.circuitState || '').toUpperCase() === 'OPEN'
  if (!enabled || circuitOpen) {
    return { level: 'blocked', label: '已阻止', scope: '策略阻止', reason: circuitOpen ? '熔断器已打开。' : '能力当前未启用。' }
  }
  if (sourceType === 'MCP' || sourceType === 'MCP_MANAGED' || tags.includes('mcp') || confirmationRequired || risk.includes('high') || highRiskScope) {
    return { level: 'confirm', label: '调用前确认', scope: '外部或受管能力', reason: '可能路由到外部工具或受治理策略影响，执行前需要确认。' }
  }
  if (hasTag(tags, 'file', 'document') || /pdf|file|document/i.test(name)) {
    return { level: 'local_write', label: '本地写入', scope: '本地文件', reason: '会生成或修改本地文件，需要明确输出意图。' }
  }
  if (hasTag(tags, 'search', 'retrieval', 'image', 'weather') || ['web_search', 'image_search', 'trip_weather', 'cloud_cover'].includes(name)) {
    return { level: 'external_read', label: '外部只读', scope: '外部数据源', reason: '会访问外部资料或数据源，但不应产生写入副作用。' }
  }
  return { level: 'direct', label: '可直接调用', scope: kind === 'skill' ? '本地技能' : '本地工具', reason: '本地计算或规则型能力，默认允许直接调用。' }
}

const dependentToolsFor = (name) => ({
  'astro-shoot-plan': ['milkyway_rise', 'light_pollution', 'cloud_cover']
}[name] || [])

const governanceFor = (raw = {}) => ({
  security: raw.security || null,
  evaluation: raw.evaluation || null,
  riskLevel: raw.security?.riskLevel || raw.riskLevel || raw.risk || null,
  permissionScopes: raw.security?.permissionScopes || [],
  reviewStatus: raw.security?.reviewStatus || 'UNREVIEWED',
  lifecycleStatus: raw.security?.lifecycleStatus || 'ACTIVE',
  evaluationProfile: raw.evaluation?.profile || null,
  successCriteria: raw.evaluation?.successCriteria || [],
  goldenCaseTags: raw.evaluation?.goldenCaseTags || []
})

export const toolKey = (tool) => tool?.toolName || tool?.name || tool?.id || ''
export const skillKey = (skill) => skill?.name || skill?.id || ''

export const normalizePlatformTool = (tool) => {
  const name = toolKey(tool)
  const sourceType = normalizeSource(tool?.sourceType)
  const tags = normalizeTags(tool?.tags)
  const functionGroup = functionGroupFor({ name, tags, sourceType, kind: 'tool' })
  const permission = permissionFor({ name, tags, sourceType, kind: 'tool', raw: tool })
  const governanceContract = governanceFor(tool)
  return {
    id: `tool:${name}`,
    kind: 'tool',
    name,
    label: localizedTitle(name, tool?.displayName, name || '未命名工具'),
    description: localizedSummary(name, tool?.description, '该工具暂未提供中文说明。'),
    sourceType,
    tags,
    requiredParams: Array.isArray(tool?.requiredParams) ? tool.requiredParams : [],
    timeoutMs: tool?.timeoutMs,
    raw: tool,
    governanceContract,
    category: sourceType === 'MCP' ? '外部 MCP' : '原子工具',
    functionGroup,
    permission,
    dependentTools: dependentToolsFor(name),
    executable: true,
    governance: sourceType === 'MCP' ? '通过本地路由代理接入' : '本地后端直接执行'
  }
}

export const normalizeSkill = (skill) => {
  const name = skillKey(skill)
  const sourceType = normalizeSource(skill?.sourceType)
  const tags = normalizeTags(skill?.tags)
  const functionGroup = functionGroupFor({ name, tags, sourceType, kind: 'skill' })
  const permission = permissionFor({ name, tags, sourceType, kind: 'skill', raw: skill })
  const governanceContract = governanceFor(skill)
  return {
    id: `skill:${name}`,
    kind: 'skill',
    name,
    label: localizedTitle(name, skill?.displayName, name || '未命名技能'),
    description: localizedSummary(name, skill?.description || skill?.summary, '该技能暂未提供中文说明。'),
    sourceType,
    tags,
    requiredParams: requiredInputs(skill?.inputs),
    version: skill?.version,
    timeoutMs: skill?.timeoutMs,
    raw: skill,
    governanceContract,
    category: '复合技能',
    functionGroup,
    permission,
    dependentTools: dependentToolsFor(name),
    executable: true,
    governance: '由技能 Bean 与说明元数据注册'
  }
}

export const normalizeManagedTool = (tool) => {
  const name = toolKey(tool)
  const sourceType = 'MCP_MANAGED'
  const tags = normalizeTags(tool?.tags)
  const functionGroup = functionGroupFor({ name, tags, sourceType, kind: 'managed' })
  const permission = permissionFor({ name, tags, sourceType, kind: 'managed', raw: tool })
  const governanceContract = governanceFor(tool)
  return {
    id: `managed:${name}`,
    kind: 'managed',
    name,
    label: localizedTitle(name, tool?.displayName, name || '未命名受管工具'),
    description: localizedSummary(name, tool?.description, '该受管工具暂未提供中文说明。'),
    sourceType,
    tags,
    requiredParams: [],
    raw: tool,
    governanceContract,
    category: '受管 MCP',
    functionGroup,
    permission,
    dependentTools: dependentToolsFor(name),
    executable: true,
    governance: tool?.confirmationRequired ? '调用前需要确认' : '由 MCP 治理策略控制'
  }
}

export const buildCapabilityCatalog = ({ platformTools = [], skills = [], managedTools = [] } = {}) => [
  ...platformTools.map(normalizePlatformTool),
  ...skills.map(normalizeSkill),
  ...managedTools.map(normalizeManagedTool)
]

export const capabilityStats = (catalog = []) => ({
  total: catalog.length,
  tools: catalog.filter((item) => item.kind === 'tool').length,
  skills: catalog.filter((item) => item.kind === 'skill').length,
  mcp: catalog.filter((item) => item.sourceType === 'MCP' || item.kind === 'managed').length,
  managed: catalog.filter((item) => item.kind === 'managed').length
})

export const groupCapabilitiesByCategory = (catalog = []) => catalog.reduce((groups, item) => {
  const key = item.category || '其他'
  if (!groups[key]) groups[key] = []
  groups[key].push(item)
  return groups
}, {})
