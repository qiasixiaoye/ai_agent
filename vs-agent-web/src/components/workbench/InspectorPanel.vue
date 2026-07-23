<template>
  <aside class="inspector-panel" aria-label="本轮运行概览">
    <WorkbenchSection title="本轮执行">
      <p class="summary-line">{{ executionText }}</p>
      <ul v-if="recent.length" class="execution-list">
        <li v-for="item in recent" :key="item.id">
          <span class="status-dot" :class="item.status"></span>
          <span>{{ item.name || item.operation || '自动编排' }}</span>
          <small>{{ statusLabel(item.status) }}</small>
        </li>
      </ul>
      <p v-else class="muted">发送问题后，这里会显示本轮实际使用的能力。</p>
    </WorkbenchSection>

    <WorkbenchSection title="记忆">
      <p class="summary-line">{{ memoryText }}</p>
      <p class="muted">只有完成轮次中的稳定、非敏感信息才会进入记忆候选。</p>
      <button type="button" class="outline-button" @click="openMemory">管理 Memory</button>
    </WorkbenchSection>

    <WorkbenchSection title="可追溯性">
      <p class="muted">Tool、Skill、知识检索和最终回答共享同一条 Trace。</p>
      <button type="button" class="outline-button" @click="openObservability">查看 Trace</button>
    </WorkbenchSection>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import WorkbenchSection from './WorkbenchSection.vue'
import { useMemoryStore } from '../../stores/memory'
import { useWorkbenchStore } from '../../stores/workbench'
import { statusLabel } from '../../utils/productText'

const router = useRouter()
const memory = useMemoryStore()
const workbench = useWorkbenchStore()
const recent = computed(() => workbench.recentInvocations.slice(0, 4))
const executionText = computed(() => {
  const latest = recent.value[0]
  if (!latest) return '等待下一次请求'
  if (latest.status === 'complete') return latest.summary || '本轮已完成'
  return statusLabel(latest.status)
})
const memoryText = computed(() => {
  if (memory.memoryStatus === 'written') return '本轮已自动保存 1 条记忆，可在 Memory 中管理。'
  if (memory.memoryStatus === 'pending') return '本轮有 1 条候选记忆等待确认。'
  if (memory.memoryStatus === 'failed') return '记忆写入失败，不影响本轮回答。'
  if (memory.memoryStatus === 'none') return '本轮未产生可写入的长期记忆。'
  return '等待本轮回答完成后评估。'
})
const openMemory = () => router.push('/knowledge-base')
const openObservability = () => router.push('/observability')
</script>

<style scoped>
.inspector-panel { display: grid; align-content: start; gap: 12px; overflow-y: auto; }
.inspector-panel :deep(.workbench-section) { padding: 14px; box-shadow: none; }
.summary-line { margin: 0; color: var(--color-text); line-height: 1.55; }
.execution-list { display: grid; gap: 8px; margin: 12px 0 0; padding: 0; list-style: none; }
.execution-list li { display: grid; grid-template-columns: 8px minmax(0, 1fr) auto; gap: 8px; align-items: center; font-size: .78rem; }
.execution-list small { color: var(--color-text-subtle); }
.status-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--color-primary); }
.status-dot.error, .status-dot.failed { background: var(--color-danger); }
.status-dot.incomplete { background: #e5a44f; }
.outline-button { width: 100%; margin-top: 10px; padding: 8px 10px; color: var(--color-primary); background: transparent; border: 1px solid color-mix(in srgb, var(--color-primary) 45%, var(--color-border)); border-radius: var(--radius-sm); cursor: pointer; }
</style>
