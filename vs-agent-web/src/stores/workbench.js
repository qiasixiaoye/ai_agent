import { defineStore } from 'pinia'

export const WORKBENCH_AREAS = [
  { key: 'chat', path: '/', label: '对话中枢', description: '普通对话、知识问答、智能体模式' },
  { key: 'agent', path: '/agent', label: '任务执行', description: '多步骤任务与演示能力' },
  { key: 'capabilities', path: '/capabilities', label: '能力中心', description: '工具、技能、权限与运行态' },
  { key: 'knowledge', path: '/knowledge-base', label: '知识资产', description: '文档、记忆、上下文' },
  { key: 'workflow', path: '/workflow-studio', label: '工作流', description: '需求生成、导入、观测 Dify' },
  { key: 'observability', path: '/observability', label: '运行观测', description: '请求链路与失败排查' }
]

export const useWorkbenchStore = defineStore('workbench', {
  state: () => ({
    currentConversationId: '',
    currentMode: 'normal',
    activeArea: 'chat',
    inspectorOpen: true,
    recentInvocations: [],
    permissionDetailOpen: true
  }),
  actions: {
    setConversation(id) {
      this.currentConversationId = id
    },
    setMode(mode) {
      this.currentMode = mode || 'normal'
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
        status: 'complete',
        ...invocation
      })
      this.recentInvocations = this.recentInvocations.slice(0, 12)
    }
  }
})
