<template>
  <section class="context-workspace">
    <header><h1>Context</h1><p class="muted">Inspect and manage the conversation memory that supplies runtime context.</p></header>
    <MemoryWorkspace />
  </section>
</template>

<script setup>
import MemoryWorkspace from './MemoryWorkspace.vue'
</script>

<style scoped>
.context-workspace { display: grid; gap: 0; }.context-workspace > header { padding: 20px 20px 0; }.context-workspace h1, .context-workspace p { margin: 0 0 4px; }.context-workspace h1 { font-size: 1.2rem; }.context-workspace :deep(.workspace-header) { display: none; }
</style>
