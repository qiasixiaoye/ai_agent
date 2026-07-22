<template>
  <section class="chat-workspace">
    <header class="chat-workspace-header">
      <div>
        <h2>Workbench chat</h2>
        <p class="muted">Start a conversation, search knowledge, or run an agent task.</p>
      </div>
      <SegmentedControl v-model="mode" :options="modeOptions" />
    </header>

    <div ref="messagesContainer" class="chat-workspace-messages">
      <p v-if="messages.length === 0" class="muted">Send a message to begin.</p>
      <ChatMessage
        v-for="message in messages"
        :key="message.id"
        :content="message.content"
        :is-user="message.isUser"
        :timestamp="message.timestamp"
      />
      <LoadingIndicator v-if="loading" />
    </div>

    <ChatInput :loading="loading" @send="sendMessage" />
    <p class="chat-mode-hint muted">{{ currentMode.hint }}</p>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import ChatInput from '../../components/ChatInput.vue'
import ChatMessage from '../../components/ChatMessage.vue'
import LoadingIndicator from '../../components/LoadingIndicator.vue'
import SegmentedControl from '../../components/workbench/SegmentedControl.vue'
import { useChatStore } from '../../stores/chat'
import { useWorkbenchStore } from '../../stores/workbench'
import {
  connectToAssistantAppChat,
  connectToAssistantAppRagChat,
  connectToManusChat
} from '../../services/api'

const modeOptions = [
  { value: 'normal', label: 'Chat', hint: 'Stream a standard assistant reply.' },
  { value: 'rag', label: 'Knowledge', hint: 'Search the knowledge base before replying.' },
  { value: 'agent', label: 'Agent', hint: 'Run a multi-step agent task with available tools.' }
]

const chatStore = useChatStore()
const workbench = useWorkbenchStore()
const mode = ref('normal')
const chatId = ref('')
const loading = ref(false)
const eventSource = ref(null)
const messagesContainer = ref(null)
let agentHistory = []

const currentMode = computed(() => modeOptions.find((option) => option.value === mode.value))
const messages = computed(() => chatStore.assistantAppChats[chatId.value]?.messages || [])

onMounted(() => {
  chatId.value = chatStore.createAssistantAppChat()
  workbench.setConversation(chatId.value)
  chatStore.addAssistantAppMessage(chatId.value, 'Hello. What would you like to work on?', false)
})

onUnmounted(() => eventSource.value?.close())

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
}

const openConnection = (message) => {
  if (mode.value === 'rag') return connectToAssistantAppRagChat(message, chatId.value)
  if (mode.value === 'agent') {
    agentHistory.push({ user: message })
    return connectToManusChat(message, JSON.stringify(agentHistory))
  }
  return connectToAssistantAppChat(message, chatId.value)
}

const sendMessage = (message) => {
  if (loading.value) return

  chatStore.addAssistantAppMessage(chatId.value, message, true)
  loading.value = true
  scrollToBottom()

  try {
    eventSource.value?.close()
    const source = openConnection(message)
    eventSource.value = source
    let response = ''
    let assistantMessageAdded = false

    const finish = () => {
      source.close()
      if (eventSource.value === source) eventSource.value = null
      if (mode.value === 'agent' && response) agentHistory.push({ assistant: response })
      loading.value = false
    }

    source.onmessage = (event) => {
      if (!event.data) return
      response += event.data
      if (!assistantMessageAdded) {
        chatStore.addAssistantAppMessage(chatId.value, response, false)
        assistantMessageAdded = true
      } else {
        const lastMessage = messages.value.at(-1)
        if (lastMessage && !lastMessage.isUser) lastMessage.content = response
      }
      scrollToBottom()
    }

    source.addEventListener('complete', finish)
    source.onerror = () => {
      if (!assistantMessageAdded) {
        chatStore.addAssistantAppMessage(chatId.value, 'Unable to connect to the assistant. Please try again.', false)
      }
      finish()
    }
  } catch {
    loading.value = false
    chatStore.addAssistantAppMessage(chatId.value, 'Unable to start the assistant. Please try again.', false)
  }
}
</script>

<style scoped>
.chat-workspace {
  --color-surface: var(--color-panel);
  --color-surface-alt: var(--color-panel-muted);
  --color-primary-soft: rgba(90, 167, 255, 0.45);
  --gradient-brand: var(--color-primary);
  display: grid;
  grid-template-rows: auto minmax(260px, 1fr) auto auto;
  min-height: calc(100vh - 80px);
  overflow: hidden;
  background: var(--color-panel);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-panel);
}

.chat-workspace-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 18px 20px; border-bottom: 1px solid var(--color-border); }
.chat-workspace-header h2 { margin: 0 0 4px; font-size: 1.1rem; }
.chat-workspace-header p { margin: 0; }
.chat-workspace-messages { min-height: 0; overflow-y: auto; padding: 20px; }
.chat-mode-hint { margin: 0; padding: 8px 20px; border-top: 1px solid var(--color-border); font-size: 0.8rem; }

@media (max-width: 900px) {
  .chat-workspace { min-height: calc(100vh - 150px); }
  .chat-workspace-header { align-items: flex-start; flex-direction: column; }
}
</style>
