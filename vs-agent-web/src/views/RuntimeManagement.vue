<template>
  <div class="runtime-page">
    <header class="runtime-header">
      <router-link to="/" class="back">← 返回首页</router-link>
      <h1>Agent Runtime</h1>
      <p>MCP 工具治理 · 分层记忆 · 上下文窗口预算</p>
    </header>

    <main class="runtime-main">
      <section class="panel">
        <div class="panel-head">
          <div><h2>MCP 工具目录</h2><p>发现、策略、风险确认、超时和熔断状态</p></div>
          <button @click="loadMcp(true)" :disabled="loadingMcp">刷新目录</button>
        </div>
        <div v-if="mcpHealth" class="metrics">
          <span>Provider {{ mcpHealth.providerAvailable ? 'UP' : 'DOWN' }}</span>
          <span>{{ mcpHealth.discoveredTools }} discovered</span>
          <span>{{ mcpHealth.enabledTools }} enabled</span>
          <span>{{ mcpHealth.openCircuits }} open circuits</span>
        </div>
        <div class="tool-grid">
          <button v-for="tool in tools" :key="tool.name" class="tool-card"
                  :class="{ active: selectedTool?.name === tool.name, disabled: !tool.enabled }"
                  @click="selectedTool = tool">
            <strong>{{ tool.name }}</strong>
            <span>{{ tool.riskLevel }} · {{ tool.circuitState }}</span>
            <small>{{ tool.description }}</small>
          </button>
        </div>
        <div v-if="selectedTool" class="invoke-box">
          <textarea v-model="toolArgs" rows="4" placeholder='{"query":"hello"}'></textarea>
          <label><input v-model="confirmed" type="checkbox" /> 已人工确认高风险操作</label>
          <button @click="invokeTool" :disabled="invoking || !selectedTool.enabled">确定性调用</button>
          <pre v-if="toolResult">{{ JSON.stringify(toolResult, null, 2) }}</pre>
        </div>
      </section>

      <section class="panel">
        <div class="panel-head">
          <div><h2>分层记忆与窗口</h2><p>工作记忆 → 滚动摘要 → 语义记忆 / 情景记忆 → 预算装配</p></div>
        </div>
        <div class="memory-controls">
          <input v-model="conversationId" placeholder="conversationId" />
          <button @click="loadMemory">读取记忆</button>
          <button class="danger" @click="clearMemory">清空会话</button>
        </div>
        <div class="memory-controls">
          <input v-model="semanticContent" placeholder="手动写入一条用户事实或偏好" />
          <button @click="rememberSemantic">写入语义记忆</button>
        </div>
        <div class="memory-controls">
          <input v-model="contextQuery" placeholder="输入当前问题，预览会调入哪些记忆" />
          <input v-model.number="tokenBudget" class="budget" type="number" min="128" />
          <button @click="previewContext">预览上下文</button>
        </div>
        <div class="memory-controls">
          <select v-model="diagnosticMode">
            <option value="plain">Plain Chat</option>
            <option value="skills">Skills</option>
            <option value="mcp">MCP</option>
          </select>
          <button @click="loadDiagnostics">分析完整 Token 预算</button>
        </div>

        <div v-if="memory" class="memory-grid">
          <article><h3>Working</h3><strong>{{ memory.workingMessageCount }}</strong><span>messages</span></article>
          <article><h3>Summary</h3><pre>{{ memory.rollingSummary || '暂无摘要' }}</pre></article>
          <article><h3>Semantic</h3><ul><li v-for="m in memory.semanticMemories" :key="m.id">{{ m.content }}</li></ul></article>
          <article><h3>Episodic</h3><ul><li v-for="m in memory.episodicMemories" :key="m.id">{{ m.content }}</li></ul></article>
        </div>
        <pre v-if="contextResult" class="context-preview">{{ JSON.stringify(contextResult, null, 2) }}</pre>
        <pre v-if="diagnostics" class="context-preview">{{ JSON.stringify(diagnostics, null, 2) }}</pre>
      </section>

      <div v-if="error" class="error">{{ error }}</div>
    </main>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import {
  getMcpRuntimeHealth, listManagedMcpTools, refreshManagedMcpTools, invokeManagedMcpTool,
  getConversationMemory, previewConversationContext, getContextDiagnostics,
  addSemanticMemory, clearConversationMemory
} from '../services/api'

const tools = ref([])
const mcpHealth = ref(null)
const selectedTool = ref(null)
const toolArgs = ref('{}')
const confirmed = ref(false)
const toolResult = ref(null)
const loadingMcp = ref(false)
const invoking = ref(false)
const conversationId = ref('demo')
const semanticContent = ref('')
const contextQuery = ref('')
const tokenBudget = ref(2500)
const memory = ref(null)
const contextResult = ref(null)
const diagnostics = ref(null)
const diagnosticMode = ref('plain')
const error = ref('')

const loadMcp = async (refresh = false) => {
  loadingMcp.value = true
  error.value = ''
  try {
    tools.value = refresh ? await refreshManagedMcpTools() : await listManagedMcpTools()
    mcpHealth.value = await getMcpRuntimeHealth()
  } catch (e) { error.value = e.message } finally { loadingMcp.value = false }
}

const invokeTool = async () => {
  invoking.value = true
  try {
    JSON.parse(toolArgs.value)
    toolResult.value = await invokeManagedMcpTool({ toolName: selectedTool.value.name, argumentsJson: toolArgs.value, confirmed: confirmed.value })
    await loadMcp()
  } catch (e) { error.value = e.message } finally { invoking.value = false }
}

const loadMemory = async () => { try { memory.value = await getConversationMemory(conversationId.value) } catch (e) { error.value = e.message } }
const rememberSemantic = async () => {
  if (!semanticContent.value.trim()) return
  try { memory.value = await addSemanticMemory(conversationId.value, semanticContent.value.trim()); semanticContent.value = '' } catch (e) { error.value = e.message }
}
const previewContext = async () => { try { contextResult.value = await previewConversationContext(conversationId.value, contextQuery.value, tokenBudget.value) } catch (e) { error.value = e.message } }
const loadDiagnostics = async () => { try { diagnostics.value = await getContextDiagnostics(conversationId.value, contextQuery.value, diagnosticMode.value) } catch (e) { error.value = e.message } }
const clearMemory = async () => { try { await clearConversationMemory(conversationId.value); await loadMemory(); contextResult.value = null } catch (e) { error.value = e.message } }

onMounted(() => { loadMcp(); loadMemory() })
</script>

<style scoped>
.runtime-page{min-height:100vh;background:var(--color-bg)}
.runtime-header{padding:28px 40px;background:linear-gradient(135deg,#16213e,#3457d5);color:#fff}.runtime-header h1{margin:12px 0 4px}.runtime-header p{margin:0;opacity:.8}.back{color:#fff;text-decoration:none}
.runtime-main{max-width:1200px;margin:auto;padding:28px;display:grid;gap:20px}.panel{background:var(--color-surface);border:1px solid var(--color-border);border-radius:12px;padding:20px}.panel-head{display:flex;justify-content:space-between;gap:16px}.panel h2{margin:0}.panel p{margin:4px 0;color:var(--color-text-muted)}button{cursor:pointer;border:0;border-radius:7px;padding:9px 14px;background:var(--color-primary);color:white}button:disabled{opacity:.45}
.metrics{display:flex;gap:10px;flex-wrap:wrap;margin:16px 0}.metrics span{padding:5px 9px;background:var(--color-surface-alt);border-radius:20px;font-size:12px}.tool-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:10px}.tool-card{text-align:left;background:var(--color-surface-alt);color:var(--color-text);border:1px solid var(--color-border);display:flex;flex-direction:column;gap:5px}.tool-card.active{border-color:var(--color-primary)}.tool-card.disabled{opacity:.55}.tool-card span,.tool-card small{color:var(--color-text-muted)}
.invoke-box{margin-top:16px;display:grid;gap:10px}.invoke-box textarea,.memory-controls input,.memory-controls select{border:1px solid var(--color-border);border-radius:7px;padding:9px;background:var(--color-bg);color:var(--color-text)}pre{white-space:pre-wrap;word-break:break-word;background:var(--color-surface-alt);padding:12px;border-radius:8px;max-height:280px;overflow:auto}.memory-controls{display:flex;gap:9px;margin-top:14px}.memory-controls input{flex:1}.memory-controls .budget{max-width:100px}.danger{background:#b42318}.memory-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px;margin-top:18px}.memory-grid article{border:1px solid var(--color-border);border-radius:9px;padding:14px;min-height:110px}.memory-grid h3{margin:0 0 8px}.memory-grid ul{padding-left:18px}.context-preview{margin-top:14px}.error{color:#b42318;background:#fee4e2;padding:12px;border-radius:8px}@media(max-width:700px){.memory-grid{grid-template-columns:1fr}.memory-controls{flex-direction:column}.runtime-main{padding:14px}}
</style>
