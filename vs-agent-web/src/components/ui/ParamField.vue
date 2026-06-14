<template>
  <div class="param-field">
    <label class="param-label">
      <span class="param-name">{{ param.name }}</span>
      <span v-if="param.required" class="param-required">必填</span>
      <span v-if="param.type" class="param-type">{{ param.type }}</span>
    </label>
    <p v-if="param.description" class="param-desc">{{ param.description }}</p>

    <textarea
      v-if="param.type === 'object' || param.type === 'array'"
      class="param-input param-textarea"
      :value="modelValue"
      :placeholder="placeholderFor()"
      rows="4"
      @input="onInput($event.target.value)"
    />

    <label v-else-if="param.type === 'boolean'" class="param-checkbox">
      <input
        type="checkbox"
        :checked="modelValue === true || modelValue === 'true'"
        @change="$emit('update:modelValue', $event.target.checked)"
      />
      <span>{{ modelValue ? '是' : '否' }}</span>
    </label>

    <input
      v-else-if="param.type === 'int' || param.type === 'number'"
      type="number"
      class="param-input"
      :value="modelValue"
      :placeholder="placeholderFor()"
      @input="$emit('update:modelValue', $event.target.value)"
    />

    <input
      v-else
      type="text"
      class="param-input"
      :value="modelValue"
      :placeholder="placeholderFor()"
      @input="$emit('update:modelValue', $event.target.value)"
    />
  </div>
</template>

<script setup>
const props = defineProps({
  param: { type: Object, required: true },
  modelValue: { type: [String, Number, Boolean, Object, Array], default: '' },
})

const emit = defineEmits(['update:modelValue'])

function placeholderFor() {
  if (props.param.defaultValue !== undefined && props.param.defaultValue !== null) {
    return `默认值: ${typeof props.param.defaultValue === 'object' ? JSON.stringify(props.param.defaultValue) : props.param.defaultValue}`
  }
  return ''
}

function onInput(value) {
  emit('update:modelValue', value)
}
</script>

<style scoped>
.param-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: var(--space-3);
}

.param-label {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-text);
}

.param-name {
  font-family: var(--font-mono);
}

.param-required {
  font-size: 0.68rem;
  color: var(--color-error);
  background: var(--color-error-bg);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  font-weight: 500;
}

.param-type {
  font-size: 0.68rem;
  color: var(--color-text-muted);
  background: var(--color-surface-alt);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  font-weight: 500;
}

.param-desc {
  margin: 0;
  font-size: 0.78rem;
  color: var(--color-text-muted);
}

.param-input {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 0.88rem;
  background: var(--color-surface);
  color: var(--color-text);
}

.param-input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.param-textarea {
  font-family: var(--font-mono);
  resize: vertical;
}

.param-checkbox {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 0.85rem;
  cursor: pointer;
}
</style>
