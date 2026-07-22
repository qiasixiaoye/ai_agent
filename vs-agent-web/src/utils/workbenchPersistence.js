export const WORKBENCH_STORAGE_KEY = 'vs-agent-workbench:v1'
export const WORKBENCH_STORAGE_VERSION = 1
const MAX_CONVERSATIONS = 12
const MAX_MESSAGES_PER_CONVERSATION = 40
const MAX_MESSAGE_CHARS = 4000

const asString = (value, fallback = '') => {
  const text = String(value ?? '').trim()
  return text || fallback
}

const truncate = (value, limit = MAX_MESSAGE_CHARS) => {
  const text = String(value ?? '')
  return text.length > limit ? `${text.slice(0, limit)}…` : text
}

const normalizeMessage = (message = {}) => ({
  id: asString(message.id, `${Date.now()}-${Math.random().toString(16).slice(2)}`),
  content: truncate(message.content),
  isUser: Boolean(message.isUser),
  role: message.role || (message.isUser ? 'user' : 'assistant'),
  mode: message.mode || 'normal',
  status: message.status || 'complete',
  details: message.details || null,
  timestamp: message.timestamp instanceof Date ? message.timestamp.toISOString() : asString(message.timestamp, new Date().toISOString())
})

const normalizeConversation = (conversation = {}) => {
  const messages = Array.isArray(conversation.messages) ? conversation.messages : []
  const selectedMessages = messages.slice(-MAX_MESSAGES_PER_CONVERSATION).map(normalizeMessage)
  const titleSource = [...selectedMessages].reverse().find((message) => message.isUser)?.content
    || selectedMessages[0]?.content
    || '未命名会话'
  return {
    id: asString(conversation.id),
    title: truncate(titleSource, 80),
    mode: conversation.mode || selectedMessages.at(-1)?.mode || 'normal',
    createdAt: asString(conversation.createdAt, new Date().toISOString()),
    updatedAt: selectedMessages.at(-1)?.timestamp || asString(conversation.updatedAt, new Date().toISOString()),
    messages: selectedMessages
  }
}

export const buildWorkbenchSnapshot = ({ workbench = {}, chats = {} } = {}) => {
  const conversations = Object.values(chats)
    .map(normalizeConversation)
    .filter((conversation) => conversation.id)
    .sort((a, b) => String(b.updatedAt).localeCompare(String(a.updatedAt)))
    .slice(0, MAX_CONVERSATIONS)

  return {
    version: WORKBENCH_STORAGE_VERSION,
    currentConversationId: asString(workbench.currentConversationId || conversations[0]?.id),
    currentMode: workbench.currentMode || conversations[0]?.mode || 'normal',
    activeArea: workbench.activeArea || 'chat',
    ui: {
      inspectorOpen: workbench.inspectorOpen !== false
    },
    conversations
  }
}

export const restoreWorkbenchSnapshot = (raw) => {
  if (!raw) return null
  let parsed
  try {
    parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
  } catch {
    return null
  }
  if (!parsed || parsed.version !== WORKBENCH_STORAGE_VERSION || !Array.isArray(parsed.conversations)) return null
  const conversations = parsed.conversations.map(normalizeConversation).filter((conversation) => conversation.id)
  if (!conversations.length) return null
  const currentConversationId = conversations.some((conversation) => conversation.id === parsed.currentConversationId)
    ? parsed.currentConversationId
    : conversations[0].id
  return {
    version: WORKBENCH_STORAGE_VERSION,
    currentConversationId,
    currentMode: parsed.currentMode || conversations.find((conversation) => conversation.id === currentConversationId)?.mode || 'normal',
    activeArea: parsed.activeArea || 'chat',
    ui: {
      inspectorOpen: parsed.ui?.inspectorOpen !== false
    },
    conversations
  }
}

export const readWorkbenchSnapshot = (storage = globalThis.localStorage) => {
  if (!storage) return null
  return restoreWorkbenchSnapshot(storage.getItem(WORKBENCH_STORAGE_KEY))
}

export const writeWorkbenchSnapshot = (snapshot, storage = globalThis.localStorage) => {
  if (!storage || !snapshot) return
  storage.setItem(WORKBENCH_STORAGE_KEY, JSON.stringify(snapshot))
}

export const clearWorkbenchSnapshot = (storage = globalThis.localStorage) => {
  if (!storage) return
  storage.removeItem(WORKBENCH_STORAGE_KEY)
}
