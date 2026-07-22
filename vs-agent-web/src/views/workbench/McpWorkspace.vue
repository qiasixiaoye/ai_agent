<template>
  <section class="mcp-workspace">
    <header><h1>MCP</h1><p class="muted">Review managed MCP availability and invoke the governed runtime catalog.</p></header>
    <RuntimeWorkspace />
  </section>
</template>

<script setup>
import RuntimeWorkspace from './RuntimeWorkspace.vue'
</script>

<style scoped>
.mcp-workspace { display: grid; gap: 0; }.mcp-workspace > header { padding: 20px 20px 0; }.mcp-workspace h1, .mcp-workspace p { margin: 0 0 4px; }.mcp-workspace h1 { font-size: 1.2rem; }.mcp-workspace :deep(.workspace-header) { display: none; }
</style>
