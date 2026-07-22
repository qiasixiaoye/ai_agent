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
      try {
        this.contextPreview = await previewConversationContext(conversationId, query, tokenBudget)
        this.error = ''
        return this.contextPreview
      } catch (error) {
        this.error = messageOf(error)
        throw error
      }
    },
    async loadDiagnostics(conversationId, query, mode = 'plain') {
      try {
        this.diagnostics = await getContextDiagnostics(conversationId, query, mode)
        this.error = ''
        return this.diagnostics
      } catch (error) {
        this.error = messageOf(error)
        throw error
      }
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
    updateSuggestion({ content, importance }) {
      this.suggestion = {
        content: String(content ?? this.suggestion?.content ?? ''),
        importance: Math.min(1, Math.max(0, Number(importance ?? this.suggestion?.importance ?? 0.8) || 0)),
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
    async writeManual(conversationId, { content, importance = 0.8 }) {
      const text = String(content || '').trim()
      if (!text) throw new Error('Memory content is required')
      this.writing = true
      try {
        const result = await addSemanticMemory(conversationId, text, Math.min(1, Math.max(0, Number(importance) || 0)))
        this.error = ''
        await this.loadConversation(conversationId)
        return result
      } catch (error) {
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
