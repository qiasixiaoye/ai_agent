<template>
  <section class="chat-workspace">
    <header class="workspace-header">
      <div>
        <h1>Chat</h1>
        <p class="muted">Normal, RAG, and Agent modes share this conversation context.</p>
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
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import ChatInput from '../../components/ChatInput.vue'
import ChatMessage from '../../components/ChatMessage.vue'
import LoadingIndicator from '../../components/LoadingIndicator.vue'
import SegmentedControl from '../../components/workbench/SegmentedControl.vue'
import { useChatStore } from '../../stores/chat'
import { useMemoryStore } from '../../stores/memory'
import { useWorkbenchStore } from '../../stores/workbench'
import {
  connectToAssistantAppChat,
  connectToAssistantAppRagChat,
  connectToManusChat
} from '../../services/api'

const modeOptions = [
  { value: 'normal', label: 'Chat' },
  { value: 'rag', label: 'Knowledge' },
  { value: 'agent', label: 'Agent' }
]

const chatStore = useChatStore()
const memoryStore = useMemoryStore()
const workbench = useWorkbenchStore()
const mode = ref('normal')
const chatId = ref('')
const loading = ref(false)
const eventSource = ref(null)
const messagesContainer = ref(null)
const lastUserMessage = ref('')
let agentHistory = []

const messages = computed(() => chatStore.assistantAppChats[chatId.value]?.messages || [])

onMounted(() => {
  const existingChatId = workbench.currentConversationId
  if (existingChatId && chatStore.assistantAppChats[existingChatId]) {
    chatId.value = existingChatId
    workbench.setConversation(chatId.value)
    return
  }

  chatId.value = chatStore.createConversation('normal')
  workbench.setConversation(chatId.value)
  chatStore.addMessage(chatId.value, {
    content: 'Hello. What would you like to work on?',
    isUser: false,
    status: 'complete'
  })
})

onUnmounted(() => eventSource.value?.close())

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
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
        source: mode.value,
        name: 'chat',
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
          content: 'Unable to connect to the assistant. Please check that the backend is running, then try again.',
          isUser: false,
          mode: requestMode,
          status: 'error',
          details: 'The assistant stream could not be opened or was interrupted.'
        })
      }
      finalize(assistantMessageAdded ? 'incomplete' : 'error')
    }
  } catch {
    loading.value = false
    chatStore.addMessage(chatId.value, {
      content: 'Unable to start the assistant. Please check that the backend is running, then try again.',
      isUser: false,
      mode: requestMode,
      status: 'error',
      details: 'The browser could not create an assistant stream.'
    })
    workbench.addInvocation({
      source: 'chat',
      operation: 'assistant-response',
      mode: requestMode,
      status: 'error',
      summary: 'Assistant stream could not be started.'
    })
    scrollToBottom()
  }
}
</script>

<style scoped>
.chat-workspace {
  display: grid;
  grid-template-rows: auto minmax(260px, 1fr) auto;
  min-height: calc(100vh - 80px);
  overflow: hidden;
  background: var(--color-panel);
}

.workspace-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 18px 20px; border-bottom: 1px solid var(--color-border); }
.workspace-header h1 { margin: 0 0 4px; font-size: 1.15rem; }
.workspace-header p { margin: 0; }
.chat-transcript { min-height: 0; overflow-y: auto; padding: 20px; }

@media (max-width: 900px) {
  .chat-workspace { min-height: calc(100vh - 150px); }
  .workspace-header { align-items: flex-start; flex-direction: column; }
}
</style>
