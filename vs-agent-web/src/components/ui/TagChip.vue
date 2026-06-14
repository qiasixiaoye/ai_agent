<template>
  <span class="tag-chip" :class="{ accent }">{{ label }}</span>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  accent: { type: Boolean, default: false },
})
</script>

<style scoped>
.tag-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: 0.72rem;
  font-weight: 500;
  color: var(--color-text-muted);
  background: var(--color-surface-alt);
  border: 1px solid var(--color-border);
  white-space: nowrap;
}

.tag-chip.accent {
  color: var(--color-primary);
  background: var(--color-primary-light);
  border-color: var(--color-primary-soft);
}
</style>
