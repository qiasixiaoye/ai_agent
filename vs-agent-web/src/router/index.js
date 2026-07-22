import { createRouter, createWebHistory } from 'vue-router'
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue'

const workbenchChildren = [
  { path: '', name: 'ChatWorkspace', component: () => import('../views/workbench/ChatWorkspace.vue'), meta: { area: 'chat' } },
  { path: 'chat', redirect: '/' },
  { path: 'tools', name: 'ToolsWorkspace', component: () => import('../views/workbench/ToolsWorkspace.vue'), meta: { area: 'tools' } },
  { path: 'skills', name: 'SkillsWorkspace', component: () => import('../views/workbench/SkillsWorkspace.vue'), meta: { area: 'skills' } },
  { path: 'runtime', name: 'RuntimeWorkspace', component: () => import('../views/workbench/RuntimeWorkspace.vue'), meta: { area: 'runtime' } },
  { path: 'memory', name: 'MemoryWorkspace', component: () => import('../views/workbench/MemoryWorkspace.vue'), meta: { area: 'memory' } },
  { path: 'workflow-studio', name: 'WorkflowWorkspace', component: () => import('../views/workbench/WorkflowWorkspace.vue'), meta: { area: 'workflow' } },
  { path: 'knowledge-base', name: 'KnowledgeWorkspace', component: () => import('../views/workbench/KnowledgeWorkspace.vue'), meta: { area: 'knowledge' } },
  { path: 'observability', name: 'ObservabilityWorkspace', component: () => import('../views/workbench/ObservabilityWorkspace.vue'), meta: { area: 'observability' } }
]

const routes = [
  { path: '/', component: WorkbenchLayout, children: workbenchChildren },
  { path: '/assistant-app', redirect: '/' },
  { path: '/agent-platform', redirect: '/tools' },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
