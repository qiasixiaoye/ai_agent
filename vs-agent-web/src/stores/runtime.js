import { defineStore } from 'pinia'
import {
  getMcpRuntimeHealth,
  listManagedMcpTools,
  refreshManagedMcpTools,
  invokeManagedMcpTool
} from '../services/api'

const messageOf = (error) => error?.message || 'Request failed'

export const useRuntimeStore = defineStore('runtime', {
  state: () => ({
    health: null,
    tools: [],
    loading: false,
    invoking: false,
    error: '',
    lastResult: null
  }),
  getters: {
    status: (state) => state.health?.status || (state.error ? 'error' : 'unknown')
  },
  actions: {
    async loadHealth() {
      try {
        this.health = await getMcpRuntimeHealth()
        this.error = ''
      } catch (error) {
        this.error = messageOf(error)
      }
    },
    async loadTools() {
      this.loading = true
      try {
        this.tools = await listManagedMcpTools()
        this.error = ''
      } catch (error) {
        this.error = messageOf(error)
      } finally {
        this.loading = false
      }
    },
    async refreshTools() {
      this.loading = true
      try {
        this.tools = await refreshManagedMcpTools()
        this.error = ''
      } catch (error) {
        this.error = messageOf(error)
      } finally {
        this.loading = false
      }
    },
    riskForTool(tool) {
      const risk = String(tool?.riskLevel || tool?.risk || '').toLowerCase()
      if (risk.includes('high') || tool?.requiresConfirmation) return 'confirm'
      if (risk.includes('block')) return 'blocked'
      if (risk.includes('safe') || risk.includes('low')) return 'safe'
      return 'unknown'
    },
    async invokeTool({ toolName, argumentsJson, confirmed = false }) {
      this.invoking = true
      try {
        this.lastResult = await invokeManagedMcpTool({ toolName, argumentsJson, confirmed })
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
