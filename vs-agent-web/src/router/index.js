import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue')
  },
  {
    path: '/assistant-app',
    name: 'AssistantApp',
    component: () => import('../views/AssistantApp.vue')
  },
  {
    path: '/manus-app',
    name: 'ManusApp',
    component: () => import('../views/ManusApp.vue')
  },
  {
    path: '/observability',
    name: 'Observability',
    component: () => import('../views/Observability.vue')
  },
  {
    path: '/skills',
    name: 'Skills',
    component: () => import('../views/S