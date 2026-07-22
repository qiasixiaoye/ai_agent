<template>
  <div :class="['message', isUser ? 'user-message' : 'ai-message']">
    <div class="avatar">
      <span v-if="isUser">👤</span>
      <span v-else>🤖</span>
    </div>
    <div class="message-content">
      <div class="message-text" v-html="formattedMessage"></div>
      <div class="message-time">{{ formattedTime }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'

const props = defineProps({
  content: {
    type: String,
    required: true
  },
  isUser: {
    type: Boolean,
    default: false
  },
  timestamp: {
    type: Date,
    default: () => new Date()
  }
})

const formattedTime = computed(() => {
  return props.timestamp.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
})

const escapeHtml = (value) => value
  .replaceAll('&', '&amp;')
  .replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;')
  .replaceAll('"', '&quot;')
  .replaceAll("'", '&#39;')

const allowedTags = new Set([
  'p', 'br', 'strong', 'em', 'b', 'i', 'code', 'pre', 'ul', 'ol', 'li',
  'blockquote', 'a', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'hr', 'del',
  'table', 'thead', 'tbody', 'tr', 'th', 'td'
])
const allowedAttributes = { a: new Set(['href', 'title']) }
const discardTags = new Set(['script', 'style', 'iframe', 'object', 'embed'])

const isSafeHref = (value) => {
  const normalized = value.trim().replace(/[\u0000-\u001f\u007f\s]/g, '')
  if (!normalized || normalized.startsWith('#') || normalized.startsWith('?')) return true
  if (normalized.startsWith('/') && !normalized.startsWith('//')) return true
  if (normalized.startsWith('./') || normalized.startsWith('../')) return true
  if (/^(?![a-z][a-z0-9+.-]*:)(?!\/\/)/i.test(normalized)) return true

  try {
    return ['http:', 'https:', 'mailto:'].includes(new URL(normalized).protocol)
  } catch {
    return false
  }
}

const sanitizeHtml = (html) => {
  const document = new DOMParser().parseFromString(html, 'text/html')

  for (const element of Array.from(document.body.querySelectorAll('*'))) {
    if (!element.parentNode) continue

    const tag = element.tagName.toLowerCase()
    if (!allowedTags.has(tag)) {
      if (discardTags.has(tag)) element.remove()
      else element.replaceWith(...element.childNodes)
      continue
    }

    const allowed = allowedAttributes[tag] || new Set()
    for (const attribute of Array.from(element.attributes)) {
      if (!allowed.has(attribute.name.toLowerCase())) element.removeAttribute(attribute.name)
    }

    if (tag === 'a' && !isSafeHref(element.getAttribute('href') || '')) {
      element.removeAttribute('href')
    }
  }

  return document.body.innerHTML
}

const formattedMessage = computed(() => {
  return props.isUser ? escapeHtml(props.content) : sanitizeHtml(marked(props.content))
})
</script>

<style scoped>
.message {
  display: flex;
  margin-bottom: 16px;
  max-width: 80%;
}

.user-message {
  margin-left: auto;
  flex-direction: row-reverse;
}

.ai-message {
  margin-right: auto;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 8px;
  font-size: 20px;
}

.message-content {
  padding: 10px 14px;
  border-radius: 18px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  color: var(--color-text);
}

.user-message .message-content {
  background: linear-gradient(160deg, rgba(34, 211, 238, 0.18), rgba(99, 102, 241, 0.14));
  border-color: var(--color-primary-soft);
  border-top-right-radius: 0;
}

.ai-message .message-content {
  background: var(--color-surface);
  border-top-left-radius: 0;
}

.message-text {
  /*white-space: pre-wrap;*/
  word-break: break-word;
  text-align: left;
}

.message-text :deep(p) {
  margin: 0;
  text-align: left;
}

.message-time {
  font-size: 12px;
  opacity: 0.7;
  text-align: right;
  margin-top: 4px;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .message {
    max-width: 85%;
  }
  
  .message-content {
    padding: 8px 12px;
  }
  
  .avatar {
    width: 32px;
    height: 32px;
    font-size: 18px;
  }
}

/* 小屏幕移动设备适配 */
@media (max-width: 480px) {
  .message {
    max-width: 90%;
    margin-bottom: 12px;
  }
  
  .avatar {
    width: 28px;
    height: 28px;
    margin: 0 5px;
    font-size: 16px;
  }
  
  .message-content {
    padding: 6px 10px;
    font-size: 14px;
  }
  
  .message-time {
    font-size: 10px;
    margin-top: 2px;
  }
}
</style>
