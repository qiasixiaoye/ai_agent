import { defineStore } from 'pinia'
import {
  getMcpRuntimeHealth,
  listManagedMcpTools,
  refreshManagedMcpTools,
  invokeManagedMcpTool
} from '../services/api'
import { isOptionalCapabilityUnavailable, productErrorMessage } from '../utils/productText'

const messageOf = (error) => productErrorMessage(error, '能力目录')

export const useRuntimeStore = defineStore('runtime', {
  state: () => ({
    health: null,
    tools: [],
    loading: false,
    invoking: false,
    error: '',
    unavailable: false,
    lastResult: null
  }),
  getters: {
    status: (state) => {
      if (state.health?.status) return state.health.status
      if (state.unavailable) return 'unavailable'
      if (state.error) return 'error'
      if (state.health?.providerAvailable === true) return 'healthy'
      if (state.health?.providerAvailable === false) return 'down'
      return 'unknown'
    }
  },
  actions: {
    normalizeArgumentsJson(value) {
      if (!value || !value.trim()) return '{}'
      JSON.parse(value)
      return value
    },
    async loadHealth() {
      try {
        this.health = await getMcpRuntimeHealth()
        this.error = ''
        this.unavailable = false
      } catch (error) {
        this.error = messageOf(error)
        this.unavailable = isOptionalCapabilityUnavailable(error)
      }
    },
    async loadTools() {
      this.loading = true
      try {
        this.tools = await listManagedMcpTools()
        this.error = ''
        this.unavailable = false
      } catch (error) {
        this.tools = []
        this.error = messageOf(error)
        this.unavailable = isOptionalCapabilityUnavailable(error)
      } finally {
        this.loading = false
      }
    },
    async refreshTools() {
      this.loading = true
      try {
        this.tools = await refreshManagedMcpTools()
        this.error = ''
        this.unavailable = false
      } catch (error) {
        this.tools = []
        this.error = messageOf(error)
        this.unavailable = isOptionalCapabilityUnavailable(error)
      } finally {
        this.loading = false
      }
    },
    riskForTool(tool) {
      if (!tool) return 'unknown'
      const risk = String(tool.riskLevel || tool.risk || '').toLowerCase()
      const reason = typeof tool.reason === 'string' ? tool.reason.trim().toLowerCase() : ''
      const circuitState = String(tool.circuitState || '').toLowerCase()
      if (circuitState === 'open') return 'blocked'
      if (reason && /(deny|denied|block|blocked|not\s+(?:included\s+in|in)\s+(?:the\s+)?allow|blank\s+name)/.test(reason)) return 'blocked'
      if (tool.confirmationRequired === true) return 'confirm'
      if (tool.enabled === false) return 'blocked'
      if (risk.includes('high')) return 'confirm'
      if (risk.includes('safe') || risk.includes('low')) return 'safe'
      return 'unknown'
    },
    async invokeTool({ toolName, argumentsJson, confirmed = false }) {
      this.invoking = true
      try {
        const normalizedArgumentsJson = this.normalizeArgumentsJson(argumentsJson)
        this.lastResult = await invokeManagedMcpTool({ toolName, argumentsJson: normalizedArgumentsJson, confirmed })
        this.error = ''
        return this.lastResult
      } catch (error) {
        this.error = messageOf(error)
        throw error
      } finally {
        this.invoking = false
      }
    }
  }
})
