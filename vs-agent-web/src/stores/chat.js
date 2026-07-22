import { defineStore } from 'pinia'
import { v4 as uuidv4 } from 'uuid'

export const useChatStore = defineStore('chat', {
  state: () => ({
    assistantAppChats: {},
    manusAppChats: []
  }),

  actions: {
    createConversation(mode = 'normal') {
      const chatId = uuidv4()
      this.assistantAppChats[chatId] = {
        id: chatId,
        mode,
        messages: [],
        createdAt: new Date().toISOString()
      }
      return chatId
    },

    addMessage(chatId, payload) {
      if (!this.assistantAppChats[chatId]) {
        this.assistantAppChats[chatId] = {
          id: chatId,
          mode: payload.mode || 'normal',
          messages: []
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
      return message.id
    },

    updateLastAssistantMessage(chatId, patch) {
      const chat = this.assistantAppChats[chatId]
      if (!chat) return
      const message = [...chat.messages].reverse().find((item) => !item.isUser)
      if (message) Object.assign(message, patch)
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
