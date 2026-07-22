<template>
  <div class="command-bar">
    <input :value="modelValue" type="search" placeholder="搜索能力、知识、工作流，或输入要执行的任务" @input="$emit('update:modelValue', $event.target.value)" />
    <button type="button" @click="$emit('run')">前往</button>
  </div>
</template>

<script setup>
defineProps({ modelValue: { type: String, default: '' } })
defineEmits(['update:modelValue', 'run'])
</script>
