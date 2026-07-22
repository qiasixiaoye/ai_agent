<template>
  <div :class="['message', isUser ? 'user-message' : 'ai-message']">
    <div class="avatar"><span>{{ isUser ? '你' : 'AI' }}</span></div>
    <div class="message-content">
      <div class="message-text" v-html="formattedMessage"></div>
      <div class="message-meta">
        <span v-if="statusText" class="message-status" :class="`status-${status}`">{{ statusText }}</span>
        <span v-if="formattedTime" class="message-time">{{ formattedTime }}</span>
      </div>
      <details v-if="detailsText" class="message-details">
        <summary>详情</summary>
        <pre>{{ detailsText }}</pre>
      </details>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'

const props = defineProps({
  content: { type: String, required: true },
  isUser: { type: Boolean, default: false },
  timestamp: { type: [String, Date], default: '' },
  status: { type: String, default: 'complete' },
  details: { type: [Object, Array, String], default: null }
})

const formattedTime = computed(() => {
  if (!props.timestamp) return ''
  const value = props.timestamp instanceof Date ? props.timestamp : new Date(props.timestamp)
  if (Number.isNaN(value.getTime())) return ''
  return value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
})

const statusText = computed(() => ({
  streaming: '生成中',
  incomplete: '响应未完成',
  error: '连接异常'
}[props.status] || ''))

const detailsText = computed(() => {
  if (!props.details) return ''
  return typeof props.details === 'string' ? props.details : JSON.stringify(props.details, null, 2)
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
.message { display: flex; margin-bottom: 12px; max-width: min(80%, 760px); }
.user-message { margin-left: auto; flex-direction: row-reverse; }
.ai-message { margin-right: auto; }

.avatar {
  width: 30px;
  height: 30px;
  margin: 0 8px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-panel-muted);
  color: var(--color-text-muted);
  font-size: 0.68rem;
  font-weight: 700;
}

.message-content {
  padding: 9px 12px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: var(--color-panel-muted);
  color: var(--color-text);
}
.user-message .message-content {
  background: color-mix(in srgb, var(--color-primary) 16%, var(--color-panel-muted));
  border-color: color-mix(in srgb, var(--color-primary) 42%, var(--color-border));
}

.message-text { word-break: break-word; text-align: left; }
.message-text :deep(p) { margin: 0; text-align: left; }
.message-meta { display: flex; align-items: center; gap: 8px; margin-top: 5px; font-size: 0.72rem; opacity: 0.7; }
.message-time { margin-left: auto; }
.message-status { font-weight: 600; }
.status-streaming { color: var(--color-primary); }
.status-incomplete, .status-error { color: #d0804c; }
.message-details { margin-top: 7px; font-size: 0.75rem; }
.message-details summary { cursor: pointer; color: var(--color-text-muted); }
.message-details pre { margin: 5px 0 0; overflow-x: auto; white-space: pre-wrap; font: inherit; color: var(--color-text-muted); }

@media (max-width: 768px) {
  .message { max-width: 88%; }
  .avatar { width: 28px; height: 28px; margin: 0 6px; }
}
</style>
