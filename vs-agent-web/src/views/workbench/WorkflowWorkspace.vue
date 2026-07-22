<template>
  <section class="workspace">
    <header class="workspace-header">
      <div><h1>工作流</h1><p class="muted">把一句需求转成可导入 Dify 的流程，并在工作台内运行和观测。</p></div>
    </header>
    <p v-if="error" class="status-error">{{ error }}</p>
    <div class="workspace-grid">
      <WorkbenchSection title="生成工作流" class="panel">
        <label for="workflow-requirement">需求描述</label>
        <textarea id="workflow-requirement" v-model="requirement" rows="7" placeholder="描述目标、输入、需要用到的工具和期望输出。" />
        <div class="options"><label><span>执行方式</span><select v-model="mode"><option value="http">固定 HTTP 步骤</option><option value="agent">Agent 自主编排</option></select></label><label><span>应用类型</span><select v-model="appKind"><option value="chatflow">多轮对话</option><option value="workflow">单次流程</option></select></label></div>
        <button type="button" :disabled="generating || !requirement.trim()" @click="generate">{{ generating ? '生成中…' : '生成工作流' }}</button>
      </WorkbenchSection>
      <WorkbenchSection title="生成结果" class="panel">
        <p v-if="!generated" class="muted">生成后的节点、校验结果和导入入口会显示在这里。</p>
        <template v-else>
          <div class="result-heading"><strong>{{ generated.workflowName || '未命名工作流' }}</strong><span :class="generated.valid === false ? 'status-error' : 'status-success'">{{ generated.valid === false ? '校验失败' : '可导出' }}</span></div>
          <p v-if="generated.warnings?.length" class="muted">{{ generated.warnings.join(' · ') }}</p>
          <div class="nodes"><span v-for="node in nodes" :key="node.id || node.type" class="node">{{ node.title || node.label || node.type }}</span></div>
          <div class="actions"><a :href="exportUrl" target="_blank" rel="noopener">下载 Dify DSL</a><button type="button" :disabled="runningGenerated" @click="runGenerated">{{ runningGenerated ? '运行中…' : '试运行' }}</button><button type="button" :disabled="importing || generated.valid === false" @click="importToDify">{{ importing ? '导入中…' : '导入 Dify' }}</button></div>
          <p v-if="importResult" :class="importResult.success === false ? 'status-error' : 'status-success'">{{ importResult.success === false ? importResult.errorMessage || 'Dify 导入失败。' : '已导入 Dify。' }}</p>
          <a v-if="difyAppUrl" :href="difyAppUrl" target="_blank" rel="noopener">在 Dify 打开</a>
        </template>
      </WorkbenchSection>
    </div>
    <WorkbenchSection v-if="generatedRun || observation || importResult?.appId" title="运行结果" class="panel result-panel">
      <div v-if="generatedRun"><h3>本地试运行</h3><pre>{{ pretty(generatedRun) }}</pre></div>
      <div v-if="importResult?.appId"><h3>Dify 运行观测</h3><button type="button" :disabled="observing" @click="observeRun">{{ observing ? '观测中…' : '在 Dify 运行并观测' }}</button><pre v-if="observation">{{ pretty(observation) }}</pre></div>
    </WorkbenchSection>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import WorkbenchSection from '../../components/workbench/WorkbenchSection.vue'
import { exportWorkflowDslUrl, generateWorkflowFromRequirement, importGeneratedWorkflowToDify, runGeneratedWorkflow, runImportedDifyApp } from '../../services/api'

const requirement = ref('')
const mode = ref('http')
const appKind = ref('chatflow')
const generatedAppKind = ref(null)
const generated = ref(null)
const generatedRun = ref(null)
const importResult = ref(null)
const observation = ref(null)
const error = ref('')
const generating = ref(false)
const runningGenerated = ref(false)
const importing = ref(false)
const observing = ref(false)
const nodes = computed(() => generated.value?.ir?.nodes || [])
const exportUrl = computed(() => generated.value?.workflowId ? exportWorkflowDslUrl(generated.value.workflowId) : '#')
const difyAppUrl = computed(() => importResult.value?.appId ? `${import.meta.env.VITE_DIFY_CONSOLE_URL || 'http://localhost:3001'}/app/${importResult.value.appId}/workflow` : '')
const pretty = (value) => JSON.stringify(value, null, 2)
const capture = async (operation, fallback) => { error.value = ''; try { return await operation() } catch (cause) { error.value = cause?.message || fallback; return null } }
const generate = async () => { generating.value = true; generated.value = null; generatedAppKind.value = null; generatedRun.value = null; importResult.value = null; observation.value = null; try { const result = await capture(() => generateWorkflowFromRequirement(requirement.value.trim(), mode.value, appKind.value), '工作流生成失败。'); generated.value = result; if (result) generatedAppKind.value = appKind.value } finally { generating.value = false } }
const runGenerated = async () => { runningGenerated.value = true; try { generatedRun.value = await capture(() => runGeneratedWorkflow(generated.value.ir, requirement.value.trim()), '试运行失败。') } finally { runningGenerated.value = false } }
const importToDify = async () => { importing.value = true; observation.value = null; try { importResult.value = await capture(() => importGeneratedWorkflowToDify(generated.value.workflowId), 'Dify 导入失败。') } finally { importing.value = false } }
const observeRun = async () => { observing.value = true; try { observation.value = await capture(() => runImportedDifyApp(importResult.value.appId, generatedAppKind.value, requirement.value.trim()), 'Dify 运行观测失败。') } finally { observing.value = false } }
</script>

<style scoped>
.workspace { display: grid; gap: 16px; padding: 20px; }.workspace-header h1 { margin: 0 0 4px; font-size: 1.2rem; }.workspace-header p { margin: 0; }.workspace-grid { display: grid; grid-template-columns: minmax(280px, .9fr) minmax(0, 1.1fr); gap: 16px; }.panel { display: grid; align-content: start; gap: 10px; }.panel label { display: grid; gap: 5px; color: var(--color-text-muted); font-size: .8rem; }.panel textarea, .panel select { width: 100%; padding: 9px; color: var(--color-text); background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }.options { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }.result-heading, .actions { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }.nodes { display: flex; flex-wrap: wrap; gap: 8px; }.node { padding: 5px 8px; border-radius: var(--radius-sm); background: var(--color-bg); border: 1px solid var(--color-border); font-size: .8rem; }.actions a, .panel > a { color: var(--color-primary); font-weight: 600; }.result-panel h3 { margin: 0 0 8px; font-size: .9rem; }.result-panel pre { max-height: 280px; margin: 0; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; padding: 10px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); font-size: .78rem; }.status-error { margin: 0; color: var(--color-danger); }.status-success { margin: 0; color: var(--color-success); }button { cursor: pointer; }button:disabled { opacity: .55; cursor: not-allowed; }@media (max-width: 800px) { .workspace { padding: 14px; }.workspace-grid, .options { grid-template-columns: 1fr; } }
</style>
