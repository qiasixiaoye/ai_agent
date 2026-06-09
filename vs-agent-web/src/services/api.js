import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081'
const api = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' }
})

// ---------------- AssistantApp / Manus (SSE) ----------------

export const connectToAssistantAppChat = (message, chatId) =>
  new EventSource(`${baseURL}/api/ai/assistant_app/chat/sse?message=${encodeURIComponent(message)}&chatId=${encodeURIComponent(chatId)}`)

export const connectToAssistantAppRagChat = (message, chatId) =>
  new EventSource(`${baseURL}/api/ai/assistant_app/chat_rag/sse?message=${encodeURIComponent(message)}&chatId=${encodeURIComponent(chatId)}`)

export const connectToManusChat = (message, contentText) =>
  new EventSource(`${baseURL}/api/ai/manus/chat?message=${encodeURIComponent(message)}&contentText=${encodeURIComponent(contentText)}`)

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

export const fileDownloadUrl = (path) => `${baseURL}/api/files/download?path=${encodeURIComponent(path)}`

// ---------------- Skills ----------------

export const listSkills = async () => unwrap(await api.get('/skills'))
export const getSkill = async (name) => unwrap(await api.get(`/skills/${encodeURIComponent(name)}`))
export const executeSkill = async (name, args) => unwrap(await api.post(`/skills/${encodeURIComponent(name)}/execute`, args || {}))
export const skillOpenApiUrl = () => `${baseURL}/api/skills/openapi.json`

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

// ---------------- Agent Platform ----------------

export const listPlatformTools = async () => unwrap(await api.get('/agent-platform/tools'))
export const executePlatformTool = async (toolName, args, traceId) =>
  unwrap(await api.post(`/agent-platform/tools/${encodeURIComponent(toolName)}/execute`, { traceId, arguments: args || {} }))
export const executePlatformTask = async (task) => unwrap(await api.post('/agent-platform/tasks/execute', task, { timeout: 600000 }))
export const executePlatformDemoTask = async (query) => unwrap(await api.post('/agent-platform/tasks/demo', { query }, { timeout: 600000 }))

// ---------------- Dify ----------------

export const difyHealth = async () => unwrap(await api.get('/dify/health'))
export const runDifyWorkflow = async ({ workflowId, inputs, user }) =>
  unwrap(await api.post('/dify/run', {
    workflowId: workflowId || undefined,
    inputs: inputs || {},
    user: user || undefined,
    responseMode: 'blocking'
  }, { timeout: 600000 }))

// ---------------- Workflow (NL → DSL → exec/eval/export) ----------------

export const generateWorkflow = async (prompt) =>
  unwrap(await api.post('/workflow/generate', { prompt }, { timeout: 600000 }))

export const listWorkflows = async () => unwrap(await api.get('/workflow'))
export const getWorkflow = async (id) => unwrap(await api.get(`/workflow/${encodeURIComponent(id)}`))

export const executeWorkflow = async (id, input) =>
  unwrap(await api.post(`/workflow/${encodeURIComponent(id)}/execute`, { input }, { timeout: 600000 }))

export const evalWorkflow = async (id, body) =>
  unwrap(await api.post(`/workflow/${encodeURIComponent(id)}/eval`, body, { timeout: 600000 }))

export const workflowDifyDslUrl = (id) => `${baseURL}/api/workflow/${encodeURIComponent(id)}/dify-dsl`

export default api
