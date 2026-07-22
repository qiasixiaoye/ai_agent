<template>
  <div class="workbench-layout">
    <AppRail :areas="areas" :active-area="workbench.activeArea" :runtime-status="runtimeStatus" :runtime-label="runtimeLabel" />
    <main class="workbench-main" aria-label="智能体工作台">
      <CommandBar v-model="commandQuery" @run="runCommand" />
      <RouterView />
    </main>
    <InspectorPanel v-if="workbench.inspectorOpen" />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter, RouterView } from 'vue-router'
import AppRail from '../components/workbench/AppRail.vue'
import CommandBar from '../components/workbench/CommandBar.vue'
import InspectorPanel from '../components/workbench/InspectorPanel.vue'
import { WORKBENCH_AREAS, useWorkbenchStore } from '../stores/workbench'

const route = useRoute()
const router = useRouter()
const workbench = useWorkbenchStore()
const commandQuery = ref('')
const areas = WORKBENCH_AREAS

const runtimeStatus = computed(() => 'unknown')
const runtimeLabel = computed(() => '运行环境待检测')

watch(
  () => route.meta.area,
  (area) => workbench.setActiveArea(area || 'chat'),
  { immediate: true }
)

const runCommand = () => {
  const query = commandQuery.value.trim().toLowerCase()
  if (/(skill|tool|mcp|runtime|技能|工具|能力|权限|运行)/.test(query)) router.push('/capabilities')
  else if (/(memory|context|knowledge|知识|记忆|上下文|文档)/.test(query)) router.push('/knowledge-base')
  else if (/(workflow|dify|工作流|编排)/.test(query)) router.push('/workflow-studio')
  else if (/(trace|observ|监控|观测|链路|失败)/.test(query)) router.push('/observability')
  else if (/(agent|任务|执行|演示)/.test(query)) router.push('/agent')
}
</script>
