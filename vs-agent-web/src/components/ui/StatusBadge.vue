<template>
  <span class="status-badge" :class="variantClass">
    <span class="dot" />
    {{ label }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  // success | error | warning | info | running | skipped, or a boolean
  status: { type: [String, Boolean], default: 'info' },
  text: { type: String, default: '' },
})

const normalized = computed(() => {
  if (typeof props.status === 'boolean') {
    return props.status ? 'success' : 'error'
  }
  return props.status
})

const variantClass = computed(() => `status-${normalized.value}`)

const defaultLabels = {
  success: '成功',
  error: '失败',
  warning: '警告',
  info: '信息',
  running: '执行中',
  skipped: '已跳过',
}

const label = computed(() => props.text || defaultLabels[normalized.value] || normalized.value)
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: 2px 10px;
  border-radius: var(--radius-pill);
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1.6;
  white-space: nowrap;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.status-success {
  color: var(--color-success);
  background: var(--color-success-bg);
}

.status-error {
  color: var(--color-error);
  background: var(--color-error-bg);
}

.status-warning {
  color: var(--color-warning);
  background: var(--color-warning-bg);
}

.status-info {
  color: var(--color-info);
  background: var(--color-info-bg);
}

.status-running {
  color: var(--color-primary);
  background: var(--color-primary-light);
}

.status-running .dot {
  animation: pulse 1.2s ease-in-out infinite;
}

.status-skipped {
  color: var(--color-skipped);
  background: var(--color-skipped-bg);
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
</style>
