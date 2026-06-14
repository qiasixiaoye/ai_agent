<template>
  <div class="pipeline-step">
    <div class="pipeline-step-marker">
      <div class="pipeline-step-index">{{ index }}</div>
      <div v-if="!last" class="pipeline-step-connector" />
    </div>
    <div class="pipeline-step-body">
      <AppCard>
        <template #header>
          <div class="pipeline-step-title">
            <span class="pipeline-step-name">{{ title }}</span>
            <TagChip v-if="toolName" :label="toolName" accent />
          </div>
        </template>
        <template #actions>
          <StatusBadge :status="status" />
          <span v-if="costMs !== undefined && costMs !== null" class="pipeline-step-cost">{{ costMs }}ms</span>
        </template>

        <div v-if="errorMessage" class="pipeline-step-error">{{ errorMessage }}</div>

        <div v-if="markdown" class="pipeline-step-markdown" v-html="renderedMarkdown" />
        <CodeBlock v-else-if="output !== undefined && output !== null" :content="output" collapsible :default-expanded="defaultExpanded" />
      </AppCard>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
import AppCard from './AppCard.vue'
import StatusBadge from './StatusBadge.vue'
import TagChip from './TagChip.vue'
import CodeBlock from './CodeBlock.vue'

const props = defineProps({
  index: { type: [Number, String], required: true },
  title: { type: String, required: true },
  toolName: { type: String, default: '' },
  status: { type: [String, Boolean], default: 'info' },
  costMs: { type: Number, default: null },
  output: { type: [String, Object, Array, Number, Boolean], default: undefined },
  errorMessage: { type: String, default: '' },
  markdown: { type: Boolean, default: false },
  last: { type: Boolean, default: false },
  defaultExpanded: { type: Boolean, default: false },
})

const renderedMarkdown = computed(() => {
  if (!props.markdown || typeof props.output !== 'string') return ''
  return marked.parse(props.output)
})
</script>

<style scoped>
.pipeline-step {
  display: flex;
  gap: var(--space-4);
}

.pipeline-step-marker {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}

.pipeline-step-index {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.8rem;
  font-weight: 700;
}

.pipeline-step-connector {
  flex: 1;
  width: 2px;
  background: var(--color-border-strong);
  margin: var(--space-1) 0;
  min-height: 24px;
}

.pipeline-step-body {
  flex: 1;
  padding-bottom: var(--space-4);
  min-width: 0;
}

.pipeline-step-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.pipeline-step-name {
  font-weight: 600;
  color: var(--color-text);
}

.pipeline-step-cost {
  font-size: 0.75rem;
  color: var(--color-text-subtle);
  font-family: var(--font-mono);
}

.pipeline-step-error {
  color: var(--color-error);
  background: var(--color-error-bg);
  border-radius: var(--radius-sm);
  padding: var(--space-2) var(--space-3);
  font-size: 0.85rem;
  margin-bottom: var(--space-2);
}

.pipeline-step-markdown {
  font-size: 0.9rem;
  line-height: 1.7;
  color: var(--color-text);
}

.pipeline-step-markdown :deep(h1),
.pipeline-step-markdown :deep(h2),
.pipeline-step-markdown :deep(h3) {
  margin-top: var(--space-3);
  margin-bottom: var(--space-2);
}

.pipeline-step-markdown :deep(p) {
  margin: var(--space-2) 0;
}

.pipeline-step-markdown :deep(ul),
.pipeline-step-markdown :deep(ol) {
  padding-left: 1.4em;
}

.pipeline-step-markdown :deep(code) {
  background: var(--color-surface-alt);
  padding: 1px 4px;
  border-radius: 4px;
}
</style>
