<template>
  <div class="chat-container">
    <div class="chat-header">
      <router-link to="/" class="back-link">
        <span>←</span> 返回首页
      </router-link>
      <h1>AI 对话</h1>
    </div>

    <div class="mode-bar">
      <button
        v-for="m in modes"
        :key="m.key"
        :class="['mode-btn', { active: mode === m.key }]"
        @click="switchMode(m.key)"
      >
        <strong>{{ m.label }}</strong>
        <span>{{ m.hint }}</span>
      </button>
    </div>

    <div class="chat-messages" ref="messagesContainer">
      <div v-if="messages.length === 0" class="empty-state">
        <div class="empty-icon">🤖</div>
        <p>欢迎使用 AI 对话，请发送消息开始</p>
      </div>

      <template v-else>
        <ChatMessage
          v-for="message in messages"
          :key="message.id"
          :content="message.content"
          :isUser="message.isUser"
          :timestamp="message.timestamp"
        />
        <LoadingIndicator v-if="loading" />
      </template>
    </div>

    <ChatInput :loading="loading" @send="sendMessage" />

    <div class="chat-footer">
      <span class="option-hint">当前模式：{{ currentMode.label }} —— {{ currentMode.hint }}；shift+enter 换行</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useChatStore } from '../stores/chat'
import {
  connectToAssistantAppChat,
  connectToAssistantAppRagChat,
  connectToManusChat
} from '../services/api'
import ChatMessage from '../components/ChatMessage.vue'
import ChatInput from '../components/ChatInput.vue'
import LoadingIndicator from '../components/LoadingIndicator.vue'
import { useHead } from '@vueuse/head'

useHead({
  title: 'AI 对话 - 普通 / RAG 知识问答 / 智能体 | AI Agent Platform',
  meta: [
    { name: 'description', content: '一个对话入口，三种模式：普通对话、RAG 知识检索、Manus 工具自动编排智能体。基于 Spring AI。' }
  ]
})

const modes = [
  { key: 'normal', label: '普通对话', hint: '多轮对话，纯模型回答' },
  { key: 'rag', label: 'RAG 问答', hint: '检索知识库后再回答' },
  { key: 'agent', label: '智能体(Manus)', hint: '自动多步推理 + 工具调用' }
]

const chatStore = useChatStore()
const messagesContainer = ref(null)
const loading = ref(false)
const chatId = ref('')
const eventSource = ref(null)
const mode = ref('normal')
const messages = ref([])

// Manus 模式自带的多轮历史（随对话累积，作为 contentText 回传）
let agentHistory = []

const currentMode = computed(() => modes.find((m) => m.key === mode.value))

onMounted(() => {
  chatId.value = chatStore.createAssistantAppChat()
  chatStore.addAssistantAppMessage(
    chatId.value,
    '您好，我是 AI 助手。上方可切换「普通 / RAG / 智能体」三种模式，请发送消息开始。',
    false
  )
  syncMessagesFromStore()
})

const switchMode = (key) => {
  if (loading.value) return
  mode.value = key
}

const syncMessagesFromStore = () => {
  if (chatId.value && chatStore.assistantAppChats[chatId.value]) {
    messages.value = chatStore.assistantAppChats[chatId.value].messages
  }
}

watch(() => messages.value.length, async () => {
  await nextTick()
  scrollToBottom()
})

watch(
  () => messages.value.length > 0 ? messages.value[messages.value.length - 1].content : '',
  async () => {
    await nextTick()
    scrollToBottom()
  }
)

const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

const openConnection = (message) => {
  if (mode.value === 'agent') {
    agentHistory.push({ user: message })
    return connectToManusChat(message, JSON.stringify(agentHistory))
  }
  if (mode.value === 'rag') {
    return connectToAssistantAppRagChat(message, chatId.value)
  }
  return connectToAssistantAppChat(message, chatId.value)
}

const sendMessage = async (message) => {
  if (loading.value) return

  chatStore.addAssistantAppMessage(chatId.value, message, true)
  syncMessagesFromStore()
  loading.value = true

  try {
    if (eventSource.value) {
      eventSource.value.close()
    }

    eventSource.value = openConnection(message)

    let aiResponse = ''
    let messageAdded = false

    eventSource.value.onmessage = (event) => {
      if (event.data) {
        aiResponse += event.data
        if (!messageAdded) {
          loading.value = false
          chatStore.addAssistantAppMessage(chatId.value, aiResponse, false)
          messageAdded = true
        } else {
          const lastMessage = chatStore.assistantAppChats[chatId.value].messages.slice(-1)[0]
          if (lastMessage && !lastMessage.isUser) {
            lastMessage.content = aiResponse
          }
        }
        syncMessagesFromStore()
        scrollToBottom()
      }
    }

    const finalize = () => {
      eventSource.value.close()
      if (mode.value === 'agent') {
        agentHistory.push({ assistant: aiResponse })
      }
      loading.value = false
    }

    eventSource.value.onerror = finalize
    eventSource.value.addEventListener('complete', finalize)
  } catch (error) {
    console.error('连接聊天服务失败:', error)
    loading.value = false
    chatStore.addAssistantAppMessage(chatId.value, '抱歉，连接服务器时出现问题，请稍后再试。', false)
    syncMessagesFromStore()
  }
}
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 1200px;
  max-width: 100%;
  margin: 0 auto;
  background-color: transparent;
  color: var(--color-text);
}

.chat-header {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  background: var(--gradient-surface);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text);
}

.chat-header h1 {
  margin: 0 auto;
  font-size: 1.4rem;
  letter-spacing: 0.04em;
}

.back-link {
  color: var(--color-primary);
  text-decoration: none;
  display: flex;
  align-items: center;
  font-size: 0.85rem;
}

.back-link span {
  font-size: 1.2rem;
  margin-right: 5px;
}

.mode-bar {
  display: flex;
  gap: 8px;
  padding: 10px 16px;
  background: rgba(12, 18, 34, 0.55);
  backdrop-filter: blur(6px);
  border-bottom: 1px solid var(--color-border);
}

.mode-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  color: var(--color-text);
  cursor: pointer;
  transition: all 0.15s ease;
}
.mode-btn:hover { border-color: var(--color-primary-soft); }

.mode-btn strong { font-size: 0.92rem; }
.mode-btn span { font-size: 0.72rem; color: var(--color-text-subtle); }

.mode-btn.active {
  background: linear-gradient(160deg, rgba(34, 211, 238, 0.18), rgba(99, 102, 241, 0.14));
  border-color: var(--color-primary-soft);
  box-shadow: var(--glow-cyan);
}
.mode-btn.active strong { color: var(--color-primary); }
.mode-btn.active span { color: var(--color-text-muted); }

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  background-color: transparent;
}

.chat-footer {
  background: rgba(12, 18, 34, 0.55);
  backdrop-filter: blur(6px);
  border-top: 1px solid var(--color-border);
  padding: 8px 16px;
}

.option-hint {
  color: var(--color-text-subtle);
  font-size: 12px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  opacity: 0.6;
  color: #666;
}

.empty-icon {
  font-size: 4rem;
  margin-bottom: 1rem;
}

@media (max-width: 768px) {
  .chat-container { width: 100%; }
  .chat-header h1 { font-size: 1.2rem; }
  .chat-messages { padding: 15px 10px; }
  .mode-btn span { display: none; }
}
</style>
