import { defineStore } from 'pinia'
import {
  getConversationMemory,
  previewConversationContext,
  getContextDiagnostics,
  addSemanticMemory,
  clearConversationMemory
} from '../services/api'
import { productErrorMessage } from '../utils/productText'

const messageOf = (error) => productErrorMessage(error, '记忆服务')

export const useMemoryStore = defineStore('memory', {
  state: () => ({
    conversation: null,
    contextPreview: null,
    diagnostics: null,
    suggestion: null,
    memoryStatus: 'idle',
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
      // 候选记忆只接受后端 final_completed 事件，避免原始 Tool 输出进入长期记忆。
      return { userMessage, assistantMessage }
    },
    consumeCandidate(candidate) {
      if (!candidate) {
        this.memoryStatus = 'none'
        return
      }
      this.suggestion = {
        content: String(candidate.content || ''),
        importance: Number(candidate.importance ?? 0.8),
        status: candidate.status || 'pending',
        type: candidate.type || 'semantic',
        confidence: Number(candidate.confidence ?? 0)
      }
      this.memoryStatus = this.suggestion.status === 'written' ? 'written' : 'pending'
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
        this.memoryStatus = 'written'
        this.error = ''
        await this.loadConversation(conversationId)
        return result
      } catch (error) {
        this.suggestion.status = 'failed'
        this.memoryStatus = 'failed'
        this.error = messageOf(error)
        throw error
      } finally {
        this.writing = false
      }
    },
    async writeManual(conversationId, { content, importance = 0.8 }) {
      const text = String(content || '').trim()
      if (!text) throw new Error('记忆内容不能为空')
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
      this.memoryStatus = 'idle'
    }
  }
})
