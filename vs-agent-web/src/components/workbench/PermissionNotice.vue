<template>
  <div class="permission-notice" :class="risk">
    <div>
      <strong>{{ label }}</strong>
      <p v-if="reason">{{ reason }}</p>
    </div>
    <button v-if="risk === 'confirm' && !confirmed" type="button" @click="$emit('confirm')">确认</button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { riskLabel } from '../../utils/productText'

const props = defineProps({
  risk: { type: String, default: 'unknown' },
  reason: { type: String, default: '' },
  confirmed: { type: Boolean, default: false }
})
defineEmits(['confirm'])

const label = computed(() => riskLabel(props.risk))
</script>

<style scoped>
.permission-notice { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; padding: 8px 10px; border: 1px solid var(--color-border); border-radius: var(--radius-sm); font-size: 0.78rem; }
.permission-notice p { margin: 3px 0 0; color: var(--color-text-muted); }
.permission-notice button { min-height: 30px; padding: 4px 8px; color: #0f1115; background: var(--color-warning); border: 0; border-radius: var(--radius-sm); }
.permission-notice.safe strong { color: var(--color-success); }
.permission-notice.confirm strong { color: var(--color-warning); }
.permission-notice.blocked strong { color: var(--color-danger); }
</style>
