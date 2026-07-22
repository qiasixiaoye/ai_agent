import { createRouter, createWebHistory } from 'vue-router'
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue'

const workbenchChildren = [
  { path: '', name: 'ChatWorkspace', component: () => import('../views/workbench/ChatWorkspace.vue'), meta: { area: 'chat' } },
  { path: 'chat', redirect: '/' },
  { path: 'agent', name: 'AgentWorkspace', component: () => import('../views/workbench/AgentWorkspace.vue'), meta: { area: 'agent' } },
  { path: 'capabilities', name: 'CapabilityWorkspace', component: () => import('../views/workbench/CapabilityWorkspace.vue'), meta: { area: 'capabilities' } },
  { path: 'tools', redirect: '/capabilities' },
  { path: 'skills', redirect: '/capabilities' },
  { path: 'mcp', redirect: '/capabilities' },
  { path: 'runtime', redirect: '/capabilities' },
  { path: 'memory', redirect: '/knowledge-base' },
  { path: 'context', redirect: '/knowledge-base' },
  { path: 'workflow-studio', name: 'WorkflowWorkspace', component: () => import('../views/workbench/WorkflowWorkspace.vue'), meta: { area: 'workflow' } },
  { path: 'knowledge-base', name: 'KnowledgeWorkspace', component: () => import('../views/workbench/KnowledgeWorkspace.vue'), meta: { area: 'knowledge' } },
  { path: 'observability', name: 'ObservabilityWorkspace', component: () => import('../views/workbench/ObservabilityWorkspace.vue'), meta: { area: 'observability' } }
]

const routes = [
  { path: '/', component: WorkbenchLayout, children: workbenchChildren },
  { path: '/assistant-app', redirect: '/' },
  { path: '/agent-platform', redirect: '/agent' },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
