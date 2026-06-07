<template>
  <div class="chat-container">
    <div class="chat-header">
      <router-link to="/" class="back-link">
        <span>←</span> 返回首页
      </router-link>
      <h1>AI 助手</h1>
    </div>

    <div class="chat-messages" ref="messagesContainer">
      <div v-if="messages.length === 0" class="empty-state">
        <div class="empty-icon">🤖</div>
        <p>欢迎使用 AI 助手，请发送消息开始对话</p>
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
      <div class="chat-options">
        <label :class="['rag-toggle', { active: useRag }]" @click="useRag = !useRag">
          <span class="rag-icon">RAG</span>
          <span class="rag-text"></span>
        </label>
        <span class="option-hint">有问题尽管问，shift+enter 换行</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import { useChatStore } from '../stores/chat'
import { connectToAssistantAppChat, connectToAssistantAppRagChat } from '../services/api'
import ChatMessage from '../components/ChatMessage.vue'
import ChatInput from '../components/ChatInput.vue'
import LoadingIndicator from '../components/LoadingIndicator.vue'
import { useHead } from '@vueuse/head'

useHead({
  title: 'AI 助手 - 通用 AI 对话与知识问答 | AI Agent Platform',
  meta: [
    { name: 'description', content: 'AI 助手提供通用 AI 对话、RAG 知识检索、工具调用与 MCP 协议集成，覆盖技术问答、文档生成、信息检索等多种场景。' },
    { name: 'keywords', content: 'AI 对话,知识问答,RAG,工具调用,MCP,人工智能助手,Spring AI' },
    { property: 'og:title', content: 'AI 助手 - 通用 AI 对话与知识问答' },
    { property: 'og:description', content: '基于 Spring AI 的通用 AI 助手，支持 RAG / 工具调用 / MCP 协议。' },
    { property: 'og:type', content: 'website' },
    { property: 'og:url', content: window.location.href },
    { property: 'og:site_name', content: 'AI Agent Platform' },
    { property: 'og:locale', content: 'zh_CN' },
    { name: 'twitter:card', content: 'summary_large_image' },
    { name: 'twitter:title', content: 'AI 助手 - 通用 AI 对话与知识问答' },
    { name: 'twitter:description', content: '基于 Spring AI 的通用 AI 助手，支持 RAG / 工具调用 / MCP 协议。' },
    { name: 'robots', content: 'index, follow' },
    { name: 'canonical', content: window.location.href }
  ]
})

const chatStore = useChatStore()
const messagesContainer = ref(null)
const loading = ref(false)
const chatId = ref('')
const eventSource = ref(null)
const useRag = ref(false)

const messages = ref([])

onMounted(() => {
  chatId.value = chatStore.createAssistantAppChat()

  const welcomeMessage = "您好，我是 AI 助手。请告诉我您想做什么，我可以帮您解答问题、检索知识库、调用工具或生成文档。"
  chatStore.addAssistantAppMessage(chatId.value, welcomeMessage, false)

  syncMessagesFromStore()
})

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

const sendMessage = async (message) => {
  if (loading.value) return

  chatStore.addAssistantAppMessage(chatId.value, message, true)
  syncMessagesFromStore()

  loading.value = true

  try {
    if (eventSource.value) {
      eventSource.value.close()
    }

    eventSource.value = useRag.value
      ? connectToAssistantAppRagChat(message, chatId.value)
      : connectToAssistantAppChat(message, chatId.value)

    let aiResponse = ''
    let messageAdded = false

    eventSource.value.onmessage = (event) => {
      if (event.data) {
        const data = event.data
        aiResponse += data

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

    eventSource.value.onerror = () => {
      eventSource.value.close()
      loading.value = false
    }

    eventSource.value.addEventListener('complete', () => {
      eventSource.value.close()
      loading.value = false
    })
  } catch (error) {
    console.error('连接聊天服务失败:', error)
    loading.value = false

    chatStore.addAssistantAppMessage(
      chatId.value,
      '抱歉，连接服务器时出现问题，请稍后再试。',
      false
    )
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
  background-color: #f5f5f5;
  color: #333;
}

.chat-header {
  display: flex;
  align-items: center;
  padding: 10px 20px;
  background-color: #4a6fa5;
  color: white;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
}

.chat-header h1 {
  margin: 0 auto;
  font-size: 1.5rem;
}

.back-link {
  color: white;
  text-decoration: none;
  display: flex;
  align-items: center;
}

.back-link span {
  font-size: 1.2rem;
  margin-right: 5px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  background-color: #f5f5f5;
}

.chat-footer {
  background-color: white;
  border-top: 1px solid #eaeaea;
}

.chat-options {
  display: flex;
  padding: 10px 16px;
  align-items: center;
  justify-content: space-between;
}

.rag-toggle {
  display: flex;
  align-items: center;
  background-color: #e7f1ff;
  color: #4a6fa5;
  border-radius: 18px;
  padding: 6px 12px;
  font-size: 14px;
  cursor: pointer;
  user-select: none;
  transition: all 0.2s ease;
  border: 1px solid transparent;
}

.rag-toggle.active {
  background-color: #3498db;
  color: white;
}

.rag-toggle:hover {
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
}

.rag-icon {
  font-weight: bold;
}

.option-hint {
  color: #888;
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
  .chat-options { padding: 8px 10px; flex-wrap: wrap; }
  .option-hint { margin-top: 5px; width: 100%; text-align: center; font-size: 11px; }
}

@media (max-width: 480px) {
  .chat-header { padding: 8px 12px; }
  .chat-messages { padding: 10px 8px; }
  .empty-icon { font-size: 3rem; }
  .chat-options { flex-direction: column; align-items: flex-start; }
  .rag-toggle { margin-bottom: 8px; }
}
</style>
