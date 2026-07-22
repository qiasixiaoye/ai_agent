<template>
  <section class="workspace">
    <header class="workspace-header">
      <div><h1>Workflow</h1><p class="muted">Generate a Dify-ready workflow from a requirement, then run and inspect it without leaving the workbench.</p></div>
    </header>
    <p v-if="error" class="status-error">{{ error }}</p>
    <div class="workspace-grid">
      <WorkbenchSection title="Generate workflow" class="panel">
        <label for="workflow-requirement">Requirement</label>
        <textarea id="workflow-requirement" v-model="requirement" rows="7" placeholder="Describe the workflow outcome, inputs, and tools it should use." />
        <div class="options"><label><span>Execution style</span><select v-model="mode"><option value="http">HTTP steps</option><option value="agent">Agent orchestration</option></select></label><label><span>Application type</span><select v-model="appKind"><option value="chatflow">Chatflow</option><option value="workflow">Workflow</option></select></label></div>
        <button type="button" :disabled="generating || !requirement.trim()" @click="generate">{{ generating ? 'Generating...' : 'Generate workflow' }}</button>
      </WorkbenchSection>
      <WorkbenchSection title="Generated workflow" class="panel">
        <p v-if="!generated" class="muted">Generated workflow details will appear here.</p>
        <template v-else>
          <div class="result-heading"><strong>{{ generated.workflowName || 'Untitled workflow' }}</strong><span :class="generated.valid === false ? 'status-error' : 'status-success'">{{ generated.valid === false ? 'Validation failed' : 'Ready for export' }}</span></div>
          <p v-if="generated.warnings?.length" class="muted">{{ generated.warnings.join(' · ') }}</p>
          <div class="nodes"><span v-for="node in nodes" :key="node.id || node.type" class="node">{{ node.title || node.label || node.type }}</span></div>
          <div class="actions"><a :href="exportUrl" target="_blank" rel="noopener">Download Dify DSL</a><button type="button" :disabled="runningGenerated" @click="runGenerated">{{ runningGenerated ? 'Running...' : 'Run generated workflow' }}</button><button type="button" :disabled="importing || generated.valid === false" @click="importToDify">{{ importing ? 'Importing...' : 'Import to Dify' }}</button></div>
          <p v-if="importResult" :class="importResult.success === false ? 'status-error' : 'status-success'">{{ importResult.success === false ? importResult.errorMessage || 'Dify import failed.' : 'Imported to Dify.' }}</p>
          <a v-if="difyAppUrl" :href="difyAppUrl" target="_blank" rel="noopener">Open imported workflow in Dify</a>
        </template>
      </WorkbenchSection>
    </div>
    <WorkbenchSection v-if="generatedRun || observation" title="Run results" class="panel result-panel">
      <div v-if="generatedRun"><h3>Generated workflow run</h3><pre>{{ pretty(generatedRun) }}</pre></div>
      <div v-if="importResult?.appId"><h3>Dify run observation</h3><button type="button" :disabled="observing" @click="observeRun">{{ observing ? 'Observing...' : 'Run and observe in Dify' }}</button><pre v-if="observation">{{ pretty(observation) }}</pre></div>
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
const generate = async () => { generating.value = true; generated.value = null; generatedRun.value = null; importResult.value = null; observation.value = null; try { generated.value = await capture(() => generateWorkflowFromRequirement(requirement.value.trim(), mode.value, appKind.value), 'Workflow generation failed.') } finally { generating.value = false } }
const runGenerated = async () => { runningGenerated.value = true; try { generatedRun.value = await capture(() => runGeneratedWorkflow(generated.value.ir, requirement.value.trim()), 'Generated workflow run failed.') } finally { runningGenerated.value = false } }
const importToDify = async () => { importing.value = true; observation.value = null; try { importResult.value = await capture(() => importGeneratedWorkflowToDify(generated.value.workflowId), 'Dify import failed.') } finally { importing.value = false } }
const observeRun = async () => { observing.value = true; try { observation.value = await capture(() => runImportedDifyApp(importResult.value.appId, appKind.value, requirement.value.trim()), 'Dify run observation failed.') } finally { observing.value = false } }
</script>

<style scoped>
.workspace { display: grid; gap: 16px; padding: 20px; }.workspace-header h1 { margin: 0 0 4px; font-size: 1.2rem; }.workspace-header p { margin: 0; }.workspace-grid { display: grid; grid-template-columns: minmax(280px, .9fr) minmax(0, 1.1fr); gap: 16px; }.panel { display: grid; align-content: start; gap: 10px; }.panel label { display: grid; gap: 5px; color: var(--color-text-muted); font-size: .8rem; }.panel textarea, .panel select { width: 100%; padding: 9px; color: var(--color-text); background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }.options { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }.result-heading, .actions { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }.nodes { display: flex; flex-wrap: wrap; gap: 8px; }.node { padding: 5px 8px; border-radius: var(--radius-sm); background: var(--color-bg); border: 1px solid var(--color-border); font-size: .8rem; }.actions a, .panel > a { color: var(--color-primary); font-weight: 600; }.result-panel h3 { margin: 0 0 8px; font-size: .9rem; }.result-panel pre { max-height: 280px; margin: 0; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; padding: 10px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); font-size: .78rem; }.status-error { margin: 0; color: var(--color-danger); }.status-success { margin: 0; color: var(--color-success); }button { cursor: pointer; }button:disabled { opacity: .55; cursor: not-allowed; }@media (max-width: 800px) { .workspace { padding: 14px; }.workspace-grid, .options { grid-template-columns: 1fr; } }
</style>
