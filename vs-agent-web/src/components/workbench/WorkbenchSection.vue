<template>
  <section class="workbench-section">
    <header v-if="title || $slots.actions">
      <h2 v-if="title">{{ title }}</h2>
      <div class="section-actions"><slot name="actions" /></div>
    </header>
    <slot />
  </section>
</template>

<script setup>
defineProps({ title: { type: String, default: '' } })
</script>
