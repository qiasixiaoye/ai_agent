<template>
  <div class="command-bar">
    <input :value="modelValue" type="search" placeholder="Search tools, Skills, memory" @input="$emit('update:modelValue', $event.target.value)" />
    <button type="button" @click="$emit('run')">Run</button>
  </div>
</template>

<script setup>
defineProps({ modelValue: { type: String, default: '' } })
defineEmits(['update:modelValue', 'run'])
</script>
