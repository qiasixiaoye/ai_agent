<template>
  <section class="app-card" :class="{ clickable: clickable }">
    <header v-if="title || $slots.actions || $slots.header" class="app-card-header">
      <div class="app-card-header-main">
        <slot name="header">
          <h3 v-if="title" class="app-card-title">{{ title }}</h3>
          <p v-if="subtitle" class="app-card-subtitle">{{ subtitle }}</p>
        </slot>
      </div>
      <div v-if="$slots.actions" class="app-card-actions">
        <slot name="actions" />
      </div>
    </header>
    <div class="app-card-body">
      <slot />
    </div>
    <footer v-if="$slots.footer" class="app-card-footer">
      <slot name="footer" />
    </footer>
  </section>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  clickable: { type: Boolean, default: false },
})
</script>

<style scoped>
.app-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  transition: box-shadow 0.15s ease, transform 0.15s ease, border-color 0.15s ease;
}

.app-card.clickable {
  cursor: pointer;
}

.app-card.clickable:hover {
  box-shadow: var(--shadow-md);
  border-color: var(--color-primary-soft);
  transform: translateY(-2px);
}

.app-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.app-card-title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 600;
  color: var(--color-text);
}

.app-card-subtitle {
  margin: var(--space-1) 0 0;
  font-size: 0.85rem;
  color: var(--color-text-muted);
}

.app-card-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}

.app-card-body {
  flex: 1;
  min-width: 0;
}

.app-card-footer {
  border-top: 1px solid var(--color-border);
  padding-top: var(--space-3);
  font-size: 0.85rem;
  color: var(--color-text-muted);
}
</style>
