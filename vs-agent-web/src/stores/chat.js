import { defineStore } from 'pinia'
import { v4 as uuidv4 } from 'uuid'

export const useChatStore = defineStore('chat', {
  state: () => ({
    assistantAppChats: {},
    manusAppChats: []
  }),

  actions: {
    createConversation(mode = 'normal', metadata = {}) {
      const chatId = uuidv4()
      const createdAt = new Date().toISOString()
      this.assistantAppChats[chatId] = {
        id: chatId,
        title: String(metadata.title || '').trim() || '未命名会话',
        mode: metadata.mode || mode,
        category: metadata.category || metadata.mode || mode,
        pinned: Boolean(metadata.pinned),
        messages: [],
        createdAt,
        updatedAt: createdAt
      }
      return chatId
    },

    hydrateAssistantAppChats(conversations = []) {
      conversations.forEach((conversation) => {
        if (!conversation?.id) return
        this.assistantAppChats[conversation.id] = {
          id: conversation.id,
          title: conversation.title || '未命名会话',
          mode: conversation.mode || 'normal',
          category: conversation.category || conversation.mode || 'normal',
          pinned: Boolean(conversation.pinned),
          createdAt: conversation.createdAt || new Date().toISOString(),
          updatedAt: conversation.updatedAt || new Date().toISOString(),
          messages: Array.isArray(conversation.messages)
            ? conversation.messages.map((message) => ({
                ...message,
                timestamp: message.timestamp ? new Date(message.timestamp) : new Date()
              }))
            : []
        }
      })
    },

    addMessage(chatId, payload) {
      if (!this.assistantAppChats[chatId]) {
        this.assistantAppChats[chatId] = {
          id: chatId,
          title: '未命名会话',
          mode: payload.mode || 'normal',
          category: payload.mode || 'normal',
          pinned: false,
          messages: [],
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString()
        }
      }

      const message = {
        id: uuidv4(),
        content: payload.content || '',
        isUser: Boolean(payload.isUser),
        role: payload.isUser ? 'user' : 'assistant',
        mode: payload.mode || this.assistantAppChats[chatId].mode || 'normal',
        status: payload.status || 'complete',
        details: payload.details || null,
        timestamp: new Date()
      }
      this.assistantAppChats[chatId].messages.push(message)
      this.assistantAppChats[chatId].updatedAt = new Date().toISOString()
      if (message.isUser && (!this.assistantAppChats[chatId].title || this.assistantAppChats[chatId].title === '未命名会话')) {
        this.assistantAppChats[chatId].title = message.content.slice(0, 80) || this.assistantAppChats[chatId].title
      }
      return message.id
    },

    updateConversation(chatId, patch = {}) {
      const chat = this.assistantAppChats[chatId]
      if (!chat) return false
      const title = typeof patch.title === 'string' ? patch.title.trim() : ''
      if (title) chat.title = title.slice(0, 80)
      if (typeof patch.category === 'string' && patch.category.trim()) chat.category = patch.category.trim()
      if (typeof patch.mode === 'string' && patch.mode.trim()) chat.mode = patch.mode.trim()
      if (typeof patch.pinned === 'boolean') chat.pinned = patch.pinned
      chat.updatedAt = new Date().toISOString()
      return true
    },

    clearConversation(chatId) {
      const chat = this.assistantAppChats[chatId]
      if (!chat) return false
      chat.messages = []
      chat.updatedAt = new Date().toISOString()
      return true
    },

    deleteConversation(chatId) {
      if (!this.assistantAppChats[chatId]) return false
      delete this.assistantAppChats[chatId]
      return true
    },

    updateLastAssistantMessage(chatId, patch) {
      const chat = this.assistantAppChats[chatId]
      if (!chat) return
      const message = [...chat.messages].reverse().find((item) => !item.isUser)
      if (message) {
        Object.assign(message, patch)
        chat.updatedAt = new Date().toISOString()
      }
    },

    createAssistantAppChat() {
      return this.createConversation('normal')
    },

    addAssistantAppMessage(chatId, message, isUser = true) {
      return this.addMessage(chatId, { content: message, isUser })
    },

    addManusAppMessage(message, isUser = true) {
      this.manusAppChats.push({
        id: uuidv4(),
        content: message,
        isUser,
        timestamp: new Date()
      })
    },

    clearManusChat() {
      this.manusAppChats = []
    }
  }
})
