import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/',                name: 'Home',          component: () => import('../views/Home.vue') },
  { path: '/assistant-app',   name: 'AssistantApp',  component: () => import('../views/AssistantApp.vue') },
  { path: '/manus-app',       name: 'ManusApp',      component: () => import('../views/ManusApp.vue') },
  { path: '/observability',   name: 'Observability', component: () => import('../views/Observability.vue') },
  { path: '/skills',          name: 'Skills',        component: () => import('../views/Skills.vue') },
  { path: '/knowledge-base',  name: 'KnowledgeBase', component: () => import('../views/KnowledgeBase.vue') },
  { path: '/eval',            name: 'Eval',          component: () => import('../views/Eval.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
