<template>
  <div class="workbench-layout">
    <AppRail :areas="areas" :active-area="workbench.activeArea" :runtime-status="runtimeStatus" :runtime-label="runtimeLabel" />
    <main class="workbench-main" aria-label="Agent workbench">
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
const runtimeLabel = computed(() => 'Runtime')

watch(
  () => route.meta.area,
  (area) => workbench.setActiveArea(area || 'chat'),
  { immediate: true }
)

const runCommand = () => {
  const query = commandQuery.value.trim().toLowerCase()
  if (query.includes('skill')) router.push('/skills')
  else if (query.includes('tool')) router.push('/tools')
  else if (query.includes('memory')) router.push('/memory')
}
</script>
