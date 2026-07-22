import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/',                name: 'Home',          component: () => import('../views/Home.vue') },
  // ① 用 Agent（普通 / RAG / 智能体三模式合并在对话页）
  { path: '/assistant-app',   name: 'AssistantApp',  component: () => import('../views/AssistantApp.vue') },
  // ② 配能力
  { path: '/agent-platform',  name: 'AgentPlatform', component: () => import('../views/AgentPlatform.vue') },
  { path: '/skills',          name: 'Skills',        component: () => import('../views/Skills.vue') },
  { path: '/runtime',         name: 'RuntimeManagement', component: () => import('../views/RuntimeManagement.vue') },
  { path: '/knowledge-base',  name: 'KnowledgeBase', component: () => import('../views/KnowledgeBase.vue') },
  // ③ 产工作流（一句话 → Dify 画布，交给 dify-builder）
  { path: '/workflow-studio', name: 'WorkflowStudio', component: () => import('../views/WorkflowStudio.vue') },
  // 横切：可观测
  { path: '/observability',   name: 'Observability', component: () => import('../views/Observability.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
