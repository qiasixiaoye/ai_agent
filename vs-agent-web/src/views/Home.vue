<template>
  <div class="home-container fade-in">
    <div class="cyber-header slide-in-top">
      <div class="glitch-container">
        <h1 class="glitch-text" data-text="AI AGENT PLATFORM">AI AGENT PLATFORM</h1>
      </div>
      <p class="tagline">// AI 助手 + 智能体 + 知识库 + Skill + 评测 + 可观测性</p>
    </div>

    <div class="matrix-bg"></div>

    <div class="card-grid">
      <router-link to="/assistant-app" class="cyber-card assistant-card slide-in-left">
        <div class="card-content">
          <div class="card-icon">🤖</div>
          <div class="card-header"><div class="card-title">AI 助手</div><div class="card-tag">v1.0.0</div></div>
          <div class="card-description"><code>通用对话 + RAG + 工具调用 + MCP</code></div>
          <div class="card-action"><span class="action-text">$ run assistant.exe</span><span class="action-arrow">⟩</span></div>
        </div>
      </router-link>

      <router-link to="/manus-app" class="cyber-card manus-card slide-in-right">
        <div class="card-content">
          <div class="card-icon">🧠</div>
          <div class="card-header"><div class="card-title">AI 超级智能体</div><div class="card-tag">v1.0.0</div></div>
          <div class="card-description"><code>ReAct 多步推理 + 工具自动选择</code></div>
          <div class="card-action"><span class="action-text">$ run quantum_brain.exe</span><span class="action-arrow">⟩</span></div>
        </div>
      </router-link>

      <router-link to="/knowledge-base" class="cyber-card kb-card slide-in-left">
        <div class="card-content">
          <div class="card-icon">📚</div>
          <div class="card-header"><div class="card-title">知识库管理</div><div class="card-tag">v1.0.0</div></div>
          <div class="card-description"><code>上传 / 解析 / 切块 / 向量化 / 重建索引</code></div>
          <div class="card-action"><span class="action-text">$ run kb_console.exe</span><span class="action-arrow">⟩</span></div>
        </div>
      </router-link>

      <router-link to="/skills" class="cyber-card skill-card slide-in-right">
        <div class="card-content">
          <div class="card-icon">🧩</div>
          <div class="card-header"><div class="card-title">Skill 平台</div><div class="card-tag">v1.0.0</div></div>
          <div class="card-description"><code>列出 / 查看 / 直接执行已注册 Skill</code></div>
          <div class="card-action"><span class="action-text">$ run skill_console.exe</span><span class="action-arrow">⟩</span></div>
        </div>
      </router-link>

      <router-link to="/eval" class="cyber-card eval-card slide-in-left">
        <div class="card-content">
          <div class="card-icon">📊</div>
          <div class="card-header"><div class="card-title">Eval 评测</div><div class="card-tag">v1.0.0</div></div>
          <div class="card-description"><code>YAML 数据集 + Runner + Judge + 通过率矩阵</code></div>
          <div class="card-action"><span class="action-text">$ run eval_harness.exe</span><span class="action-arrow">⟩</span></div>
        </div>
      </router-link>

      <router-link to="/observability" class="cyber-card obs-card slide-in-right">
        <div class="card-content">
          <div class="card-icon">📈</div>
          <div class="card-header"><div class="card-title">执行日志面板</div><div class="card-tag">v1.0.0</div></div>
          <div class="card-description"><code>链路追踪、会话历史、失败排查</code></div>
          <div class="card-action"><span class="action-text">$ run observability_console.exe</span><span class="action-arrow">⟩</span></div>
        </div>
      </router-link>
    </div>

    <footer class="cyber-footer slide-in-bottom">
      <div class="terminal-line">
        <span class="terminal-prompt">root@ai-platform:~#</span>
        <span class="terminal-command">deploy_intelligence --version=latest</span>
        <span class="terminal-cursor"></span>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount } from 'vue';
import { useHead } from '@vueuse/head';

useHead({
  title: 'AI Agent Platform - 通用 AI 对话与智能助手服务',
  meta: [
    { name: 'description', content: '基于 Spring AI 的通用 AI 应用平台：AI 助手、Manus 智能体、知识库、Skill 平台、Eval 评测、可观测性。' },
    { name: 'keywords', content: 'AI对话,Spring AI,智能体,RAG,知识库,工具调用,MCP,ReAct,可观测性,Eval' },
    { property: 'og:title', content: 'AI Agent Platform' },
    { property: 'og:description', content: '基于 Spring AI 的通用 AI 应用平台' },
    { property: 'og:type', content: 'website' },
    { property: 'og:url', content: window.location.origin },
    { property: 'og:site_name', content: 'AI Agent Platform' },
    { property: 'og:locale', content: 'zh_CN' },
    { name: 'robots', content: 'index, follow' },
    { name: 'canonical', content: window.location.origin }
  ]
});

onMounted(() => {
  const canvas = document.createElement('canvas');
  const matrixBg = document.querySelector('.matrix-bg');
  if (!matrixBg) return;
  matrixBg.appendChild(canvas);

  const ctx = canvas.getContext('2d');
  const setCanvasSize = () => { canvas.width = window.innerWidth; canvas.height = window.innerHeight; };
  setCanvasSize();
  window.addEventListener('resize', setCanvasSize);

  const characters = 'asdasfasfsafsdfdsfdswqernionvmbmirteihbgn1249237589wnen';
  let columns = Math.floor(canvas.width / 20);
  let drops = Array(columns).fill(1);
  const updateColumns = () => { columns = Math.floor(canvas.width / 20); drops = Array(columns).fill(1); };
  window.addEventListener('resize', updateColumns);

  function drawMatrix() {
    ctx.fillStyle = 'rgba(0, 0, 0, 0.05)';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#0F0';
    ctx.font = '15px monospace';
    for (let i = 0; i < drops.length; i++) {
      const text = characters[Math.floor(Math.random() * characters.length)];
      ctx.fillText(text, i * 20, drops[i] * 20);
      if (drops[i] * 20 > canvas.height && Math.random() > 0.98) drops[i] = 0;
      drops[i]++;
    }
  }

  const matrixInterval = setInterval(drawMatrix, 50);

  onBeforeUnmount(() => {
    clearInterval(matrixInterval);
    window.removeEventListener('resize', setCanvasSize);
    window.removeEventListener('resize', updateColumns);
  });
});
</script>

<style scoped>
.home-container { min-height: 100vh; width: 100%; background-color: #0c0c0c; color: #33ff00; font-family: 'JetBrains Mono', monospace; padding: 0; margin: 0; overflow-x: hidden; position: relative; display: flex; flex-direction: column; justify-content: space-between; }
.fade-in { animation: fadeIn 1.2s ease forwards; opacity: 0; }
.slide-in-top { animation: slideInTop 1.2s ease forwards 0.3s; opacity: 0; }
.slide-in-left { animation: slideInLeft 1s ease forwards 0.8s; opacity: 0; }
.slide-in-right { animation: slideInRight 1s ease forwards 1s; opacity: 0; }
.kb-card { animation-delay: 1.0s; }
.skill-card { animation-delay: 1.1s; }
.eval-card { animation-delay: 1.15s; }
.obs-card { animation-delay: 1.2s; }
.slide-in-bottom { animation: slideInBottom 1s ease forwards 1.5s; opacity: 0; }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes slideInTop { from { opacity: 0; transform: translateY(-50px); } to { opacity: 1; transform: translateY(0); } }
@keyframes slideInLeft { from { opacity: 0; transform: translateX(-100px); } to { opacity: 1; transform: translateX(0); } }
@keyframes slideInRight { from { opacity: 0; transform: translateX(100px); } to { opacity: 1; transform: translateX(0); } }
@keyframes slideInBottom { from { opacity: 0; transform: translateY(50px); } to { opacity: 1; transform: translateY(0); } }
.cyber-header { text-align: center; padding: 3rem 1rem; position: relative; z-index: 10; width: 100%; }
.glitch-container { margin-bottom: 1rem; }
.glitch-text { font-size: 3.5rem; font-weight: bold; text-transform: uppercase; position: relative; display: inline-block; color: #33ff00; text-shadow: 0 0 5px rgba(51, 255, 0, 0.8); }
.tagline { font-size: 1.2rem; color: #cccccc; letter-spacing: 1px; }
.matrix-bg { position: fixed; top: 0; left: 0; width: 100%; height: 100%; z-index: 1; opacity: 0.2; }
.card-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 2rem; width: 100%; max-width: 1200px; margin: 2rem auto; padding: 0 2rem; position: relative; z-index: 10; box-sizing: border-box; }
.cyber-card { background: linear-gradient(145deg, #0f0f0f, #1a1a1a); border: 1px solid rgba(51, 255, 0, 0.3); border-radius: 8px; box-shadow: 0 0 15px rgba(51, 255, 0, 0.2), inset 0 0 15px rgba(51, 255, 0, 0.1); padding: 0; overflow: hidden; transition: all 0.3s ease; text-decoration: none; color: inherit; position: relative; height: 280px; }
.card-content { padding: 1.5rem; height: 100%; display: flex; flex-direction: column; }
.cyber-card:hover { transform: translateY(-8px); box-shadow: 0 10px 20px rgba(51, 255, 0, 0.3), inset 0 0 20px rgba(51, 255, 0, 0.2); }
.assistant-card:hover { border-color: rgba(74, 111, 165, 0.6); }
.manus-card:hover { border-color: rgba(155, 89, 182, 0.6); }
.kb-card:hover { border-color: rgba(47, 138, 76, 0.6); }
.skill-card:hover { border-color: rgba(255, 200, 80, 0.6); }
.eval-card:hover { border-color: rgba(109, 74, 165, 0.6); box-shadow: 0 10px 20px rgba(109, 74, 165, 0.3), inset 0 0 20px rgba(109, 74, 165, 0.2); }
.obs-card:hover { border-color: rgba(80, 180, 220, 0.6); }
.card-icon { font-size: 2.5rem; margin-bottom: 1rem; display: inline-block; }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
.card-title { font-size: 1.5rem; font-weight: bold; color: #ffffff; }
.card-tag { font-size: 0.8rem; background-color: rgba(51, 255, 0, 0.2); color: #33ff00; padding: 0.2rem 0.5rem; border-radius: 4px; }
.assistant-card .card-tag { background-color: rgba(74, 111, 165, 0.2); color: #6da0e0; }
.manus-card .card-tag { background-color: rgba(155, 89, 182, 0.2); color: #b07cc7; }
.kb-card .card-tag { background-color: rgba(47, 138, 76, 0.2); color: #2f8a4c; }
.skill-card .card-tag { background-color: rgba(255, 200, 80, 0.2); color: #ffc850; }
.eval-card .card-tag { background-color: rgba(109, 74, 165, 0.2); color: #b07cc7; }
.card-description { flex-grow: 1; margin-bottom: 1.5rem; }
.card-description code { color: #cccccc; font-size: 0.9rem; display: block; line-height: 1.4; }
.card-action { background-color: rgba(0, 0, 0, 0.5); padding: 0.75rem 1rem; border-radius: 4px; border-left: 3px solid #33ff00; display: flex; justify-content: space-between; align-items: center; }
.assistant-card .card-action { border-left-color: #6da0e0; }
.manus-card .card-action { border-left-color: #b07cc7; }
.kb-card .card-action { border-left-color: #2f8a4c; }
.skill-card .card-action { border-left-color: #ffc850; }
.eval-card .card-action { border-left-color: #b07cc7; }
.action-text { color: #bbbbbb; font-size: 0.85rem; }
.action-arrow { color: #33ff00; font-size: 1.2rem; font-weight: bold; transition: transform 0.2s; }
.cyber-card:hover .action-arrow { transform: translateX(5px); }
.cyber-footer { width: 100%; text-align: center; padding: 2rem; position: relative; z-index: 10; margin-top: 2rem; box-sizing: border-box; }
.terminal-line { display: inline-block; background-color: rgba(0, 0, 0, 0.7); padding: 0.75rem 1rem; border-radius: 4px; border: 1px solid rgba(51, 255, 0, 0.3); }
.terminal-prompt { color: #33ff00; margin-right: 0.5rem; }
.terminal-command { color: #ffffff; }
.terminal-cursor { display: inline-block; width: 8px; height: 1rem; background-color: #33ff00; margin-left: 0.25rem; vertical-align: middle; animation: blink 1s step-end infinite; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }
@media (max-width: 768px) { .glitch-text { font-size: 2rem; } .card-grid { grid-template-columns: 1fr; } }
</style>
