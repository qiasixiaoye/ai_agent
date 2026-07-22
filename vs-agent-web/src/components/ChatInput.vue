<template>
  <div class="chat-input-container">
    <textarea 
      ref="inputRef"
      class="chat-input" 
      v-model="message" 
      placeholder="请输入您的消息..." 
      @keydown.enter.prevent="onSubmit"
    ></textarea>
    <button class="send-button" @click="onSubmit" :disabled="!message.trim()">
      发送
    </button>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['send'])

const message = ref('')
const inputRef = ref(null)

const onSubmit = () => {
  if (!message.value.trim() || props.loading) return
  
  emit('send', message.value)
  message.value = ''
  
  // 自动聚焦输入框
  setTimeout(() => {
    inputRef.value?.focus()
  }, 0)
}
</script>

<style scoped>
.chat-input-container {
  display: flex;
  padding: 12px;
  background: rgba(12, 18, 34, 0.65);
  backdrop-filter: blur(8px);
  border-top: 1px solid var(--color-border);
  position: sticky;
  bottom: 0;
}

.chat-input {
  flex: 1;
  height: 44px;
  padding: 12px 16px;
  border: 1px solid var(--color-border);
  border-radius: 22px;
  outline: none;
  resize: none;
  font-family: inherit;
  font-size: 15px;
  background: var(--color-surface);
  color: var(--color-text);
}
.chat-input::placeholder { color: var(--color-text-subtle); }
.chat-input:focus { border-color: var(--color-primary); box-shadow: var(--shadow-focus); }

.send-button {
  margin-left: 10px;
  padding: 0 22px;
  height: 44px;
  background: var(--gradient-brand);
  color: #04121a;
  border: none;
  border-radius: 22px;
  cursor: pointer;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.send-button:hover {
  filter: brightness(1.1);
  box-shadow: var(--shadow-focus);
}

.send-button:disabled {
  background: var(--color-surface-alt);
  color: var(--color-text-subtle);
  cursor: not-allowed;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .chat-input-container {
    padding: 8px;
  }
  
  .chat-input {
    font-size: 15px;
    padding: 10px;
  }
}

/* 小屏幕移动设备适配 */
@media (max-width: 480px) {
  .chat-input-container {
    padding: 6px;
  }
  
  .chat-input {
    height: 40px;
    font-size: 14px;
    padding: 8px 12px;
  }
  
  .send-button {
    padding: 0 15px;
    height: 40px;
    font-size: 14px;
  }
}
</style>
