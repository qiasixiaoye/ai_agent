import { defineStore } from 'pinia'

export const WORKBENCH_AREAS = [
  { key: 'chat', path: '/', label: 'Chat' },
  { key: 'agent', path: '/agent', label: 'Agent' },
  { key: 'tools', path: '/tools', label: 'Tools' },
  { key: 'skills', path: '/skills', label: 'Skills' },
  { key: 'mcp', path: '/mcp', label: 'MCP' },
  { key: 'runtime', path: '/runtime', label: 'Runtime' },
  { key: 'memory', path: '/memory', label: 'Memory' },
  { key: 'context', path: '/context', label: 'Context' },
  { key: 'workflow', path: '/workflow-studio', label: 'Workflow' },
  { key: 'knowledge', path: '/knowledge-base', label: 'Knowledge' },
  { key: 'observability', path: '/observability', label: 'Observability' }
]

export const useWorkbenchStore = defineStore('workbench', {
  state: () => ({
    currentConversationId: '',
    activeArea: 'chat',
    inspectorOpen: true,
    recentInvocations: [],
    permissionDetailOpen: true
  }),
  actions: {
    setConversation(id) {
      this.currentConversationId = id
    },
    setActiveArea(area) {
      this.activeArea = area
    },
    toggleInspector() {
      this.inspectorOpen = !this.inspectorOpen
    },
    addInvocation(invocation) {
      this.recentInvocations.unshift({
        id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
        createdAt: new Date().toISOString(),
        ...invocation
      })
      this.recentInvocations = this.recentInvocations.slice(0, 12)
    }
  }
})
