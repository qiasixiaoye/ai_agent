<template>
  <section class="chat-workspace">
    <header class="workspace-header">
      <div>
        <h1>AI 对话</h1>
        <p class="muted">普通对话、知识库问答和智能体模式共用同一个上下文。</p>
      </div>
      <SegmentedControl v-model="mode" :options="modeOptions" />
    </header>

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
      <LoadingIndicator v-if="loading" />
    </div>

    <ChatInput :loading="loading" @send="sendMessage" />
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

watch(mode, (value) => workbench.setMode(value), { immediate: true })

onMounted(() => {
  const existingChatId = workbench.currentConversationId
  if (existingChatId && chatStore.assistantAppChats[existingChatId]) {
    chatId.value = existingChatId
    workbench.setConversation(chatId.value)
    if (mode.value === 'agent') rebuildAgentHistory()
    return
  }

  chatId.value = chatStore.createConversation('normal')
  workbench.setConversation(chatId.value)
  chatStore.addMessage(chatId.value, {
    content: '你好，我可以帮你对话、查知识库，或切换到智能体模式执行任务。',
    isUser: false,
    status: 'complete'
  })
})

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
  grid-template-rows: auto minmax(260px, 1fr) auto;
  min-height: calc(100vh - 86px);
  overflow: hidden;
  background: var(--color-panel);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.workspace-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 20px 22px; border-bottom: 1px solid var(--color-border); }
.workspace-header h1 { margin: 0 0 4px; font-size: 1.25rem; }
.workspace-header p { margin: 0; }
.chat-transcript { min-height: 0; overflow-y: auto; padding: 22px; }

@media (max-width: 900px) {
  .chat-workspace { min-height: calc(100vh - 150px); border-radius: var(--radius-md); }
  .workspace-header { align-items: flex-start; flex-direction: column; }
}
</style>
