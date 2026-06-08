import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081'
const api = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json'
  }
})

// ---------------- AssistantApp / Manus (SSE) ----------------

export const connectToAssistantAppChat = (message, chatId) => {
  const url = `${baseURL}/api/ai/assistant_app/chat/sse?message=${encodeURIComponent(message)}&chatId=${encodeURIComponent(chatId)}`
  return new EventSource(url)
}

export const connectToAssistantAppRagChat = (message, chatId) => {
  const url = `${baseURL}/api/ai/assistant_app/chat_rag/sse?message=${encodeURIComponent(message)}&chatId=${encodeURIComponent(chatId)}`
  return new EventSource(url)
}

export const connectToManusChat = (message, contentText) => {
  const url = `${baseURL}/api/ai/manus/chat?message=${encodeURIComponent(message)}&contentText=${encodeURIComponent(contentText)}`
  return new EventSource(url)
}

// ---------------- Common ----------------

const unwrap = (response) => {
  if (response?.data?.code !== 0) {
    throw new Error(response?.data?.message || '请求失败')
  }
  return response.data.data
}

// ---------------- Observability ----------------

export const queryRequestTrace = async (requestId) => {
  const response = await api.get(`/observability/requests/${encodeURIComponent(requestId)}`)
  return unwrap(response)
}

export const querySessionRequests = async (sessionId, limit = 20) => {
  const response = await api.get(`/observability/sessions/${encodeURIComponent(sessionId)}/requests`, {
    params: { limit }
  })
  return unwrap(response)
}

export const queryFailedRequests = async (startTime, endTime, limit = 100) => {
  const response = await api.post('/observability/requests/failures', {
    startTime,
    endTime,
    limit
  })
  return unwrap(response)
}

// ---------------- Files ----------------

export const fileDownloadUrl = (path) => {
  return `${baseURL}/api/files/download?path=${encodeURIComponent(path)}`
}

// ---------------- Skills ----------------

export const listSkills = async () => {
  const response = await api.get('/skills')
  return unwrap(response)
}

export const getSkill = async (name) => {
  const response = await api.get(`/skills/${encodeURIComponent(name)}`)
  return unwrap(response)
}

export const executeSkill = async (name, args) => {
  const response = await api.post(`/skills/${encodeURIComponent(name)}/execute`, args || {})
  return unwrap(response)
}

// ---------------- Knowledge Base ----------------

export const listKbDocuments = async (limit = 50) => {
  const response = await api.get('/kb/documents', { params: { limit } })
  return unwrap(response)
}

export const getKbDocument = async (documentId) => {
  const response = await api.get(`/kb/documents/${encodeURIComponent(documentId)}`)
  return unwrap(response)
}

export const uploadKbDocument = async (file, { source, tags } = {}) => {
  const form = new FormData()
  form.append('file', file)
  if (source) form.append('source', source)
  if (tags) form.append('tags', tags)
  const response = await api.post('/kb/documents/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return unwrap(response)
}

export const deleteKbDocument = async (documentId) => {
  const response = await api.delete(`/kb/documents/${encodeURIComponent(documentId)}`)
  return unwrap(response)
}

export const reprocessKbDocument = async (documentId) => {
  const response = await api.post(`/kb/documents/${encodeURIComponent(documentId)}/reprocess`)
  return unwrap(response)
}

export const rebuildKbIndex = async () => {
  const response = await api.post('/kb/documents/index/rebuild')
  return unwrap(response)
}

// ---------------- Eval ----------------

export const listEvalSuites = async () => {
  const response = await api.get('/eval/suites')
  return unwrap(response)
}

export const runEvalSuite = async (suiteName) => {
  const response = await api.post(`/eval/run/${encodeURIComponent(suiteName)}`, {}, { timeout: 600000 })
  return unwrap(response)
}

// ---------------- Agent Platform ----------------

export const listPlatformTools = async () => {
  const response = await api.get('/agent-platform/tools')
  return unwrap(response)
}

export const executePlatformTool = async (toolName, args, traceId) => {
  const response = await api.post(`/agent-platform/tools/${encodeURIComponent(toolName)}/execute`, {
    traceId,
    arguments: args || {}
  })
  return unwrap(response)
}

export const executePlatformTask = async (task) => {
  const response = await api.post('/agent-platform/tasks/execute', task, { timeout: 600000 })
  return unwrap(response)
}

export const executePlatformDemoTask = async (query) => {
  const response = await api.post('/agent-platform/tasks/demo', { query }, { timeout: 600000 })
  return unwrap(response)
}

export default api
