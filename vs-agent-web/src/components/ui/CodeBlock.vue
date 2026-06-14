<template>
  <div class="code-block" :class="{ collapsed: collapsible && !expanded }">
    <div v-if="collapsible || label" class="code-block-bar">
      <span v-if="label" class="code-block-label">{{ label }}</span>
      <div class="code-block-actions">
        <button v-if="copyable" type="button" class="code-block-btn" @click="copy">
          {{ copied ? '已复制' : '复制' }}
        </button>
        <button v-if="collapsible" type="button" class="code-block-btn" @click="expanded = !expanded">
          {{ expanded ? '收起' : '展开' }}
        </button>
      </div>
    </div>
    <pre v-show="!collapsible || expanded" class="code-block-pre"><code>{{ display }}</code></pre>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  content: { type: [String, Object, Array, Number, Boolean], default: '' },
  label: { type: String, default: '' },
  collapsible: { type: Boolean, default: false },
  defaultExpanded: { type: Boolean, default: true },
  copyable: { type: Boolean, default: true },
})

const expanded = ref(props.defaultExpanded)
const copied = ref(false)

const display = computed(() => {
  if (typeof props.content === 'string') return props.content
  try {
    return JSON.stringify(props.content, null, 2)
  } catch {
    return String(props.content)
  }
})

async function copy() {
  try {
    await navigator.clipboard.writeText(display.value)
    copied.value = true
    setTimeout(() => (copied.value = false), 1200)
  } catch {
    // clipboard unavailable, ignore
  }
}
</script>

<style scoped>
.code-block {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: #0f172a;
  overflow: hidden;
}

.code-block-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-1) var(--space-3);
  background: #1e293b;
  border-bottom: 1px solid #334155;
}

.code-block.collapsed .code-block-bar {
  border-bottom: none;
}

.code-block-label {
  font-size: 0.75rem;
  color: #94a3b8;
  font-family: var(--font-mono);
}

.code-block-actions {
  display: flex;
  gap: var(--space-1);
}

.code-block-btn {
  font-size: 0.72rem;
  padding: 2px 8px;
  min-height: auto;
  border: 1px solid #334155;
  border-radius: var(--radius-sm);
  background: transparent;
  color: #cbd5e1;
  cursor: pointer;
}

.code-block-btn:hover {
  background: #334155;
}

.code-block-pre {
  margin: 0;
  padding: var(--space-3);
  overflow-x: auto;
  max-height: 360px;
  overflow-y: auto;
}

.code-block-pre code {
  font-family: var(--font-mono);
  font-size: 0.8rem;
  color: #e2e8f0;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
