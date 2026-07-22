<template>
  <aside class="app-rail">
    <div class="brand">Agent</div>
    <nav>
      <RouterLink
        v-for="item in areas"
        :key="item.key"
        :to="item.path"
        class="rail-link"
        :class="{ active: activeArea === item.key }"
      >
        <span class="rail-dot" />
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>
    <div class="rail-status" :class="runtimeStatus">{{ runtimeLabel }}</div>
  </aside>
</template>

<script setup>
import { RouterLink } from 'vue-router'

defineProps({
  areas: { type: Array, required: true },
  activeArea: { type: String, required: true },
  runtimeStatus: { type: String, default: 'unknown' },
  runtimeLabel: { type: String, default: 'Runtime unknown' }
})
</script>
