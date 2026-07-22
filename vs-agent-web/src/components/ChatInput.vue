<template>
  <div class="chat-input-container">
    <textarea
      ref="inputRef"
      v-model="message"
      class="chat-input"
      aria-label="消息"
      placeholder="输入你的问题或任务，Enter 发送，Shift+Enter 换行"
      :disabled="loading"
      @keydown.enter.prevent="onSubmit"
    ></textarea>
    <button class="send-button" type="button" :disabled="loading || !message.trim()" @click="onSubmit">
      发送
    </button>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['send'])
const message = ref('')
const inputRef = ref(null)

const onSubmit = () => {
  if (!message.value.trim() || props.loading) return

  emit('send', message.value.trim())
  message.value = ''
  setTimeout(() => inputRef.value?.focus(), 0)
}
</script>

<style scoped>
.chat-input-container {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  background: var(--color-panel);
  border-top: 1px solid var(--color-border);
}

.chat-input {
  flex: 1;
  min-height: 42px;
  max-height: 128px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  outline: none;
  resize: vertical;
  font: inherit;
  font-size: 0.9rem;
  background: var(--color-bg-elevated);
  color: var(--color-text);
}
.chat-input::placeholder { color: var(--color-text-subtle); }
.chat-input:focus { border-color: var(--color-primary); box-shadow: var(--shadow-focus); }

.send-button {
  height: 42px;
  padding: 0 16px;
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-strong));
  color: #07111f;
  cursor: pointer;
  font-weight: 700;
}
.send-button:hover { filter: brightness(1.06); }
.send-button:disabled { background: var(--color-panel-muted); border-color: var(--color-border); color: var(--color-text-subtle); cursor: not-allowed; }

@media (max-width: 480px) {
  .chat-input-container { padding: 10px; }
  .send-button { padding: 0 12px; }
}
</style>
