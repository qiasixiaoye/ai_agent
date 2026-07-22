<template>
  <aside class="app-rail">
    <div class="brand">
      <span class="brand-mark">A</span>
      <div>
        <strong>AgentHub</strong>
        <small>智能体工作台</small>
      </div>
    </div>
    <nav>
      <RouterLink
        v-for="item in areas"
        :key="item.key"
        :to="item.path"
        class="rail-link"
        :class="{ active: activeArea === item.key }"
      >
        <span>
          <strong>{{ item.label }}</strong>
          <small>{{ item.description }}</small>
        </span>
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
  runtimeLabel: { type: String, default: '运行环境待检测' }
})
</script>
