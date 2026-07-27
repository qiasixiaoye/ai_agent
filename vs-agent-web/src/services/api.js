import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081/api'
const difyServiceURL = import.meta.env.VITE_DIFY_SERVICE_URL || 'http://localhost:8090'
const api = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' }
})

const difyService = axios.create({
  baseURL: difyServiceURL,
  headers: { 'Content-Type': 'application/json' }
})

// ---------------- AssistantApp / Manus (SSE) ----------------

export const connectToAssistantAppChat = (message, chatId) =>
  new EventSource(`${baseURL}/ai/assistant_app/chat/sse?message=${encodeURIComponent(message)}&chatId=${encodeURIComponent(chatId)}`)

export const connectToAssistantAppRagChat = (message, chatId) =>
  new EventSource(`${baseURL}/ai/assistant_app/chat_rag/sse?message=${encodeURIComponent(message)}&chatId=${encodeURIComponent(chatId)}`)

export const connectToManusChat = (message, sessionId) =>
  new EventSource(`${baseURL}/ai/manus/chat?message=${encodeURIComponent(message)}&sessionId=${encodeURIComponent(sessionId)}`)

export const connectToOrchestrator = (message, conversationId, requestId = '', traceId = '') => {
  const params = new URLSearchParams({
    message: String(message || ''),
    conversationId: String(conversationId || '')
  })
  if (requestId) params.set('requestId', requestId)
  if (traceId) params.set('traceId', traceId)
  return new EventSource(`${baseURL}/ai/orchestrate/stream?${params.toString()}`)
}

// ---------------- Common ----------------

const unwrap = (response) => {
  if (response?.data?.code !== 0) throw new Error(response?.data?.message || '请求失败')
  return response.data.data
}

// ---------------- Observability ----------------

export const queryRequestTrace = async (requestId) =>
  unwrap(await api.get(`/observability/requests/${encodeURIComponent(requestId)}`))

export const querySessionRequests = async (sessionId, limit = 20) =>
  unwrap(await api.get(`/observability/sessions/${encodeURIComponent(sessionId)}/requests`, { params: { limit } }))

export const queryFailedRequests = async (startTime, endTime, limit = 100) =>
  unwrap(await api.post('/observability/requests/failures', { startTime, endTime, limit }))

// ---------------- Files ----------------

export const fileDownloadUrl = (path) => `${baseURL}/files/download?path=${encodeURIComponent(path)}`

// ---------------- Skills ----------------

export const listSkills = async () => unwrap(await api.get('/skills'))
export const getSkill = async (name) => unwrap(await api.get(`/skills/${encodeURIComponent(name)}`))
export const executeSkill = async (name, args) => unwrap(await api.post(`/skills/${encodeURIComponent(name)}/execute`, args || {}))
export const skillOpenApiUrl = () => `${baseURL}/skills/openapi.json`
export const previewSkillRoute = async (query, topK = 3, threshold = 0.24) =>
  unwrap(await api.get('/skills/route', { params: { query, topK, threshold } }))
export const evaluateSkillRouting = async () => unwrap(await api.get('/skills/route/evaluate'))

// ---------------- Runtime: MCP governance + hierarchical memory ----------------

export const getMcpRuntimeHealth = async () => unwrap(await api.get('/mcp-management/health'))
export const listManagedMcpTools = async () => unwrap(await api.get('/mcp-management/tools'))
export const refreshManagedMcpTools = async () => unwrap(await api.post('/mcp-management/tools/refresh'))
export const invokeManagedMcpTool = async ({ toolName, argumentsJson, confirmed }) =>
  unwrap(await api.post('/mcp-management/tools/invoke', { toolName, argumentsJson, confirmed }))
export const getConversationMemory = async (conversationId) =>
  unwrap(await api.get(`/memory/conversations/${encodeURIComponent(conversationId)}`))
export const previewConversationContext = async (conversationId, query, tokenBudget = 2500) =>
  unwrap(await api.get(`/memory/conversations/${encodeURIComponent(conversationId)}/context`, { params: { query, tokenBudget } }))
export const getContextDiagnostics = async (conversationId, query, mode = 'plain') =>
  unwrap(await api.get('/context-management/diagnostics', { params: { conversationId, query, mode } }))
export const addSemanticMemory = async (conversationId, content, importance = 0.8) =>
  unwrap(await api.post(`/memory/conversations/${encodeURIComponent(conversationId)}/semantic`, { content, importance }))
export const clearConversationMemory = async (conversationId) =>
  unwrap(await api.delete(`/memory/conversations/${encodeURIComponent(conversationId)}`))

// ---------------- Knowledge Base ----------------

export const listKbDocuments = async (limit = 50) => unwrap(await api.get('/kb/documents', { params: { limit } }))
export const getKbDocument = async (id) => unwrap(await api.get(`/kb/documents/${encodeURIComponent(id)}`))
export const uploadKbDocument = async (file, { source, tags } = {}) => {
  const form = new FormData()
  form.append('file', file)
  if (source) form.append('source', source)
  if (tags) form.append('tags', tags)
  return unwrap(await api.post('/kb/documents/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } }))
}
export const deleteKbDocument = async (id) => unwrap(await api.delete(`/kb/documents/${encodeURIComponent(id)}`))
export const reprocessKbDocument = async (id) => unwrap(await api.post(`/kb/documents/${encodeURIComponent(id)}/reprocess`))
export const rebuildKbIndex = async () => unwrap(await api.post('/kb/documents/index/rebuild'))

// ---------------- Eval ----------------

export const listEvalSuites = async () => unwrap(await api.get('/eval/suites'))
export const runEvalSuite = async (name) => unwrap(await api.post(`/eval/run/${encodeURIComponent(name)}`, {}, { timeout: 600000 }))
export const getCapabilityGovernanceAudit = async () => unwrap(await api.get('/capability-governance/audit'))
export const getCapabilityGovernanceExamples = async () => unwrap(await api.get('/capability-governance/examples'))

// ---------------- Agent Platform ----------------

export const listPlatformTools = async () => unwrap(await api.get('/agent-platform/tools'))
export const executePlatformTool = async (toolName, args, traceId) =>
  unwrap(await api.post(`/agent-platform/tools/${encodeURIComponent(toolName)}/execute`, { traceId, arguments: args || {} }))
export const executePlatformTask = async (task) => unwrap(await api.post('/agent-platform/tasks/execute', task, { timeout: 600000 }))
export const executePlatformDemoTask = async (query) => unwrap(await api.post('/agent-platform/tasks/demo', { query }, { timeout: 600000 }))
export const runAstroDemo = async ({ latitude, longitude, date } = {}) =>
  unwrap(await api.post('/agent-platform/tasks/astro-demo', { latitude, longitude, date }, { timeout: 600000 }))

// ---------------- Dify ----------------

export const difyHealth = async () => unwrap(await api.get('/dify/health'))
export const runDifyWorkflow = async ({ workflowId, inputs, user }) =>
  unwrap(await api.post('/dify/run', {
    workflowId: workflowId || undefined,
    inputs: inputs || {},
    user: user || undefined,
    responseMode: 'blocking'
  }, { timeout: 600000 }))

export const difyBridgeHealth = async () => (await difyService.get('/health')).data
export const runDifyBridgeWorkflow = async ({ workflowId, inputs, user }) =>
  (await difyService.post('/run', {
    workflowId: workflowId || undefined,
    inputs: inputs || {},
    user: user || undefined,
    responseMode: 'blocking'
  }, { timeout: 600000 })).data
export const difyBridgeUrl = () => difyServiceURL

// ---------------- Workflow (NL → DSL → exec/eval/export) ----------------

export const generateWorkflow = async (prompt) =>
  unwrap(await api.post('/workflow/generate', { prompt }, { timeout: 600000 }))

export const listWorkflows = async () => unwrap(await api.get('/workflow'))
export const getWorkflow = async (id) => unwrap(await api.get(`/workflow/${encodeURIComponent(id)}`))

export const executeWorkflow = async (id, input) =>
  unwrap(await api.post(`/workflow/${encodeURIComponent(id)}/execute`, { input }, { timeout: 600000 }))

export const evalWorkflow = async (id, body) =>
  unwrap(await api.post(`/workflow/${encodeURIComponent(id)}/eval`, body, { timeout: 600000 }))

export const workflowDifyDslUrl = (id) => `${baseURL}/workflow/${encodeURIComponent(id)}/dify-dsl`

// ---------------- Workflow Builder (NL → IR → Dify DSL) ----------------

// mode:    'http'（工具→HTTP 请求节点固定编排）| 'agent'（单 Agent 节点挂载 MCP 工具，LLM 自主调用）
// appKind: 'chatflow'（默认，多轮对话 advanced-chat，answer 收尾+记忆）| 'workflow'（单轮，end 收尾）
export const generateWorkflowFromRequirement = async (requirement, mode = 'http', appKind = 'chatflow') =>
  unwrap(await api.post('/workflow-builder/generate', { requirement, mode, appKind }, { timeout: 600000 }))

export const runGeneratedWorkflow = async (ir, input) =>
  unwrap(await api.post('/workflow-builder/run', { ir, input }, { timeout: 600000 }))

export const exportWorkflowDslUrl = (workflowId) =>
  `${baseURL}/workflow-builder/export/${encodeURIComponent(workflowId)}`

export const importGeneratedWorkflowToDify = async (workflowId) =>
  unwrap(await api.post(`/workflow-builder/import/${encodeURIComponent(workflowId)}`, {}, { timeout: 600000 }))

// 在 Dify 里草稿运行并观测运行时轨迹（节点状态 / Agent 轮次 / 错误），结果落 observability
export const runImportedDifyApp = async (appId, appKind, query) =>
  unwrap(await api.post('/workflow-builder/dify-run', { appId, appKind, query }, { timeout: 300000 }))

export default api
