<template>
  <div class="segmented-control">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      :class="{ active: modelValue === option.value }"
      @click="$emit('update:modelValue', option.value)"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<script setup>
defineProps({
  modelValue: { type: String, required: true },
  options: { type: Array, required: true }
})
defineEmits(['update:modelValue'])
</script>
