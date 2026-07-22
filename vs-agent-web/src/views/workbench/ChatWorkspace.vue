<template>
  <section class="chat-workspace">
    <header class="workspace-header">
      <div>
        <h1>AI 对话</h1>
        <p class="muted">普通对话、知识库问答和智能体模式共用同一个上下文。</p>
      </div>
      <SegmentedControl v-model="mode" :options="modeOptions" />
    </header>

    <div class="chat-body">
      <aside class="conversation-panel" aria-label="会话管理">
        <div class="conversation-panel-header">
          <div>
            <strong>会话</strong>
            <span>{{ conversations.length }} 个本地会话</span>
          </div>
          <button type="button" class="text-button" @click="createNewConversation">+ 新建</button>
        </div>

        <div v-for="group in conversationGroups" :key="group.key" class="conversation-group">
          <p class="conversation-group-label">{{ group.label }}</p>
          <button
            v-for="conversation in group.items"
            :key="conversation.id"
            type="button"
            class="conversation-item"
            :class="{ active: conversation.id === chatId }"
            @click="selectConversation(conversation)"
          >
            <span class="conversation-item-title">
              <span v-if="conversation.pinned" aria-label="已置顶">●</span>
              {{ conversation.title || '未命名会话' }}
            </span>
            <span class="conversation-item-meta">{{ categoryLabel(conversation.category || conversation.mode) }} · {{ formatUpdatedAt(conversation.updatedAt) }}</span>
          </button>
        </div>
      </aside>

      <div class="conversation-main">
        <div class="conversation-actions">
          <label>
            分类
            <select :value="currentConversation?.category || currentConversation?.mode || 'normal'" @change="updateCurrentCategory($event.target.value)">
              <option v-for="option in categoryOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
          </label>
          <div>
            <button type="button" class="text-button" @click="renameCurrentConversation">重命名</button>
            <button type="button" class="text-button" @click="toggleCurrentPin">{{ currentConversation?.pinned ? '取消置顶' : '置顶' }}</button>
            <button type="button" class="text-button" @click="clearCurrentConversation">清空</button>
            <button type="button" class="danger-button" @click="deleteCurrentConversation">删除</button>
          </div>
        </div>

        <div ref="messagesContainer" class="chat-transcript">
          <ChatMessage
            v-for="message in messages"
            :key="message.id"
            :content="message.content"
            :is-user="message.isUser"
            :timestamp="message.timestamp"
            :status="message.status"
            :details="message.details"
          />
          <div v-if="!messages.length" class="empty-transcript">
            <strong>这是一个空会话</strong>
            <span>输入问题开始对话，Tool、Skill 和记忆能力仍按原流程触发。</span>
          </div>
          <LoadingIndicator v-if="loading" />
        </div>

        <ChatInput :loading="loading" @send="sendMessage" />
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import ChatInput from '../../components/ChatInput.vue'
import ChatMessage from '../../components/ChatMessage.vue'
import LoadingIndicator from '../../components/LoadingIndicator.vue'
import SegmentedControl from '../../components/workbench/SegmentedControl.vue'
import { useChatStore } from '../../stores/chat'
import { useMemoryStore } from '../../stores/memory'
import { useWorkbenchStore } from '../../stores/workbench'
import { streamClosureStatus } from '../../utils/streamLifecycle'
import { resolveChatSession } from '../../utils/chatSession'
import {
  connectToAssistantAppChat,
  connectToAssistantAppRagChat,
  connectToManusChat
} from '../../services/api'

const modeOptions = [
  { value: 'normal', label: '普通对话' },
  { value: 'rag', label: '知识库' },
  { value: 'agent', label: '智能体' }
]

const categoryOptions = modeOptions
const WELCOME_MESSAGE = '你好，我可以帮你对话、查知识库，或切换到智能体模式执行任务。'

const chatStore = useChatStore()
const memoryStore = useMemoryStore()
const workbench = useWorkbenchStore()
const mode = ref(workbench.currentMode || 'normal')
const chatId = ref('')
const loading = ref(false)
const eventSource = ref(null)
const messagesContainer = ref(null)
const lastUserMessage = ref('')
let agentHistory = []

const messages = computed(() => chatStore.assistantAppChats[chatId.value]?.messages || [])
const conversations = computed(() => Object.values(chatStore.assistantAppChats)
  .sort((left, right) => Number(Boolean(right.pinned)) - Number(Boolean(left.pinned))
    || String(right.updatedAt || '').localeCompare(String(left.updatedAt || ''))))
const currentConversation = computed(() => chatStore.assistantAppChats[chatId.value] || null)
const conversationGroups = computed(() => {
  const unpinned = conversations.value.filter((conversation) => !conversation.pinned)
  const sections = [
    { key: 'pinned', label: '置顶', items: conversations.value.filter((conversation) => conversation.pinned) },
    ...categoryOptions.map((option) => ({
      key: option.value,
      label: option.label,
      items: unpinned.filter((conversation) => (conversation.category || conversation.mode) === option.value)
    }))
  ]
  return sections.filter((section) => section.items.length)
})

watch(mode, (value) => workbench.setMode(value), { immediate: true })

onMounted(() => {
  chatId.value = resolveChatSession({
    chatStore,
    workbench,
    welcomeMessage: WELCOME_MESSAGE
  }).chatId
  if (mode.value === 'agent') rebuildAgentHistory()
})

const categoryLabel = (category) => categoryOptions.find((option) => option.value === category)?.label || '普通对话'

const formatUpdatedAt = (value) => {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '刚刚'
  return date.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const createNewConversation = () => {
  if (loading.value) return
  const newChatId = chatStore.createConversation(mode.value, { category: mode.value })
  chatStore.addMessage(newChatId, { content: WELCOME_MESSAGE, isUser: false, mode: mode.value, status: 'complete' })
  chatId.value = newChatId
  workbench.setConversation(newChatId)
  agentHistory = []
  scrollToBottom()
}

const selectConversation = (conversation) => {
  if (loading.value || !conversation?.id) return
  chatId.value = conversation.id
  workbench.setConversation(conversation.id)
  mode.value = conversation.mode || 'normal'
  if (mode.value === 'agent') rebuildAgentHistory()
  else agentHistory = []
  scrollToBottom()
}

const renameCurrentConversation = () => {
  if (!currentConversation.value) return
  const title = window.prompt('输入会话名称', currentConversation.value.title || '')
  if (title?.trim()) chatStore.updateConversation(chatId.value, { title })
}

const updateCurrentCategory = (category) => {
  if (currentConversation.value) chatStore.updateConversation(chatId.value, { category })
}

const toggleCurrentPin = () => {
  if (currentConversation.value) chatStore.updateConversation(chatId.value, { pinned: !currentConversation.value.pinned })
}

const clearCurrentConversation = () => {
  if (!currentConversation.value || loading.value) return
  if (window.confirm('清空当前会话中的消息？会话名称和分类会保留。')) {
    chatStore.clearConversation(chatId.value)
    agentHistory = []
  }
}

const deleteCurrentConversation = () => {
  if (!currentConversation.value || loading.value) return
  if (!window.confirm('删除当前会话？该本地记录无法恢复。')) return
  chatStore.deleteConversation(chatId.value)
  const nextSession = resolveChatSession({ chatStore, workbench, welcomeMessage: WELCOME_MESSAGE })
  chatId.value = nextSession.chatId
  mode.value = chatStore.assistantAppChats[chatId.value]?.mode || 'normal'
  agentHistory = []
  scrollToBottom()
}

onUnmounted(() => eventSource.value?.close())

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
}

const rebuildAgentHistory = () => {
  agentHistory = messages.value
    .filter((message) => message.content)
    .map((message) => message.isUser ? { user: message.content } : { assistant: message.content })
}

const openConnection = (message, requestMode) => {
  if (requestMode === 'rag') return connectToAssistantAppRagChat(message, chatId.value)
  if (requestMode === 'agent') {
    agentHistory.push({ user: message })
    return connectToManusChat(message, JSON.stringify(agentHistory))
  }
  return connectToAssistantAppChat(message, chatId.value)
}

const sendMessage = (message) => {
  if (loading.value) return

  const requestMode = mode.value
  lastUserMessage.value = message
  chatStore.addMessage(chatId.value, { content: message, isUser: true, mode: requestMode })
  loading.value = true
  scrollToBottom()

  try {
    eventSource.value?.close()
    const source = openConnection(message, requestMode)
    eventSource.value = source
    let aiResponse = ''
    let assistantMessageAdded = false
    let finalized = false

    const finalize = (status = aiResponse ? 'complete' : 'incomplete') => {
      if (finalized) return
      finalized = true
      source.close()
      if (eventSource.value === source) eventSource.value = null
      chatStore.updateLastAssistantMessage(chatId.value, { status })
      if (requestMode === 'agent' && aiResponse) agentHistory.push({ assistant: aiResponse })
      memoryStore.suggestMemory({ userMessage: lastUserMessage.value, assistantMessage: aiResponse })
      workbench.addInvocation({
        source: requestMode,
        name: '对话',
        status: aiResponse ? 'complete' : 'incomplete',
        summary: lastUserMessage.value.slice(0, 120)
      })
      loading.value = false
      scrollToBottom()
    }

    source.onmessage = (event) => {
      if (!event.data) return
      aiResponse += event.data
      if (!assistantMessageAdded) {
        chatStore.addMessage(chatId.value, {
          content: aiResponse,
          isUser: false,
          mode: requestMode,
          status: 'streaming'
        })
        assistantMessageAdded = true
      } else {
        chatStore.updateLastAssistantMessage(chatId.value, { content: aiResponse, status: 'streaming' })
      }
      scrollToBottom()
    }

    source.addEventListener('complete', () => finalize())
    source.onerror = () => {
      if (!assistantMessageAdded) {
        chatStore.addMessage(chatId.value, {
          content: '无法连接到对话服务，请确认后端容器已启动后重试。',
          isUser: false,
          mode: requestMode,
          status: 'error',
          details: '后端对话流未能打开，或连接被中断。'
        })
      }
      finalize(streamClosureStatus(aiResponse))
    }
  } catch {
    loading.value = false
    chatStore.addMessage(chatId.value, {
      content: '无法启动对话，请确认后端容器已启动后重试。',
      isUser: false,
      mode: requestMode,
      status: 'error',
      details: '浏览器未能创建对话流。'
    })
    workbench.addInvocation({
      source: requestMode,
      operation: 'assistant-response',
      mode: requestMode,
      status: 'error',
      summary: '对话流启动失败。'
    })
    scrollToBottom()
  }
}
</script>

<style scoped>
.chat-workspace {
  display: grid;
  grid-template-rows: auto minmax(260px, 1fr);
  min-height: calc(100vh - 86px);
  overflow: hidden;
  background: var(--color-panel);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.workspace-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 20px 22px; border-bottom: 1px solid var(--color-border); }
.workspace-header h1 { margin: 0 0 4px; font-size: 1.25rem; }
.workspace-header p { margin: 0; }
.conversation-actions, .conversation-actions > div { display: flex; align-items: center; gap: 8px; }
.text-button, .danger-button { border: 1px solid var(--color-border); border-radius: 8px; padding: 8px 11px; font: inherit; cursor: pointer; }
.text-button { background: transparent; color: var(--color-text); }
.danger-button { background: transparent; color: #ff8f98; border-color: rgba(255, 143, 152, .36); }
.chat-body { min-height: 0; display: grid; grid-template-columns: 238px minmax(0, 1fr); }
.conversation-panel { min-height: 0; overflow-y: auto; padding: 16px 12px; border-right: 1px solid var(--color-border); background: rgba(10, 21, 39, .35); }
.conversation-panel-header { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 0 4px 12px; }
.conversation-panel-header strong, .conversation-panel-header span { display: block; }
.conversation-panel-header span { margin-top: 3px; color: var(--color-muted); font-size: .75rem; }
.conversation-group { margin: 0 0 15px; }
.conversation-group-label { margin: 0 0 6px; padding: 0 6px; color: var(--color-muted); font-size: .75rem; }
.conversation-item { display: block; width: 100%; border: 1px solid transparent; border-radius: 9px; padding: 9px 10px; background: transparent; color: var(--color-text); cursor: pointer; text-align: left; }
.conversation-item:hover, .conversation-item.active { border-color: var(--color-border); background: rgba(79, 155, 255, .12); }
.conversation-item-title, .conversation-item-meta { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conversation-item-title { font-size: .85rem; }
.conversation-item-title span { color: var(--color-primary); font-size: .65rem; margin-right: 4px; }
.conversation-item-meta { margin-top: 4px; color: var(--color-muted); font-size: .7rem; }
.conversation-main { min-height: 0; display: grid; grid-template-rows: auto minmax(220px, 1fr) auto; }
.conversation-actions { justify-content: space-between; padding: 10px 18px; border-bottom: 1px solid var(--color-border); }
.conversation-actions label { display: flex; align-items: center; gap: 6px; color: var(--color-muted); font-size: .8rem; }
.conversation-actions select { border: 1px solid var(--color-border); border-radius: 7px; padding: 6px 8px; background: var(--color-panel); color: var(--color-text); font: inherit; }
.chat-transcript { min-height: 0; overflow-y: auto; padding: 22px; }
.empty-transcript { display: grid; gap: 6px; max-width: 520px; margin: 28px auto; padding: 20px; border: 1px dashed var(--color-border); border-radius: 12px; color: var(--color-text-muted); text-align: center; }
.empty-transcript strong { color: var(--color-text); }

@media (max-width: 900px) {
  .chat-workspace { min-height: calc(100vh - 150px); border-radius: var(--radius-md); }
  .workspace-header, .conversation-actions { align-items: flex-start; flex-direction: column; }
  .chat-body { grid-template-columns: 1fr; }
  .conversation-panel { max-height: 250px; border-right: 0; border-bottom: 1px solid var(--color-border); }
  .conversation-actions > div { flex-wrap: wrap; }
}
</style>
