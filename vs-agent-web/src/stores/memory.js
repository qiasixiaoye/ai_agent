import { defineStore } from 'pinia'
import {
  getConversationMemory,
  previewConversationContext,
  getContextDiagnostics,
  addSemanticMemory,
  clearConversationMemory
} from '../services/api'

const messageOf = (error) => error?.message || 'Request failed'

export const useMemoryStore = defineStore('memory', {
  state: () => ({
    conversation: null,
    contextPreview: null,
    diagnostics: null,
    suggestion: null,
    autoWriteEnabled: false,
    loading: false,
    writing: false,
    error: ''
  }),
  actions: {
    async loadConversation(conversationId) {
      if (!conversationId) return
      this.loading = true
      try {
        this.conversation = await getConversationMemory(conversationId)
        this.error = ''
      } catch (error) {
        this.error = messageOf(error)
      } finally {
        this.loading = false
      }
    },
    async previewContext(conversationId, query, tokenBudget = 2500) {
      this.contextPreview = await previewConversationContext(conversationId, query, tokenBudget)
      return this.contextPreview
    },
    async loadDiagnostics(conversationId, query, mode = 'plain') {
      this.diagnostics = await getContextDiagnostics(conversationId, query, mode)
      return this.diagnostics
    },
    suggestMemory({ userMessage, assistantMessage }) {
      const user = String(userMessage || '').trim()
      const assistant = String(assistantMessage || '').trim()
      if (!user || !assistant || assistant.length < 80) return
      this.suggestion = {
        content: `User asked: ${user}\nUseful answer summary: ${assistant.slice(0, 600)}`,
        importance: 0.8,
        status: 'pending'
      }
    },
    async writeSuggestion(conversationId) {
      if (!this.suggestion?.content) return
      this.writing = true
      try {
        const result = await addSemanticMemory(conversationId, this.suggestion.content, this.suggestion.importance)
        this.suggestion.status = 'written'
        this.error = ''
        await this.loadConversation(conversationId)
        return result
      } catch (error) {
        this.suggestion.status = 'failed'
        this.error = messageOf(error)
        throw error
      } finally {
        this.writing = false
      }
    },
    async clearConversation(conversationId) {
      await clearConversationMemory(conversationId)
      this.conversation = null
      this.contextPreview = null
      this.diagnostics = null
      this.suggestion = null
    }
  }
})
