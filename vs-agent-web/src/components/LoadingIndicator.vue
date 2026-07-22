<template>
  <div class="loading-indicator" role="status" aria-live="polite">
    <span class="loading-label">正在生成回复</span>
    <span class="dot"></span>
    <span class="dot"></span>
    <span class="dot"></span>
  </div>
</template>

<style scoped>
.loading-indicator {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 4px;
  padding: 6px 0;
  color: var(--color-text-muted);
  font-size: 0.75rem;
  margin-right: auto;
  margin-bottom: 12px;
}

.loading-label {
  margin-right: 4px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--color-primary);
  margin: 0;
  opacity: 0.6;
  animation: pulse 1.5s infinite;
}

.dot:nth-child(2) {
  animation-delay: 0.5s;
}

.dot:nth-child(3) {
  animation-delay: 1s;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(0.8);
    opacity: 0.6;
  }
  50% {
    transform: scale(1.2);
    opacity: 1;
  }
}
</style>
