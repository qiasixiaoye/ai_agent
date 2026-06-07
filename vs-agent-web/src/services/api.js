import axios from 'axios'


const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081'
const api = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json'
  }
})

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

const unwrap = (response) => {
  if (response?.data?.code !== 0) {
    throw new Error(response?.data?.message || '请求失败')
  }
  return response.data.data
}

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

// ---------------- Skills ----------------

export c