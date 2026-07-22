<template>
  <section class="workspace">
    <header class="workspace-header"><div><h1>Skills</h1><p class="muted">Inspect skill routes, then execute selected skills with JSON arguments.</p></div><button type="button" :disabled="skills.loading" @click="loadSkills">{{ skills.loading ? 'Loading...' : 'Reload catalog' }}</button></header>
    <p v-if="skills.error || localError" class="status-error">{{ localError || skills.error }}</p>
    <div class="workspace-grid">
      <WorkbenchSection title="Skill catalog" class="catalog-panel"><input v-model="search" class="control" type="search" placeholder="Search skills" /><button v-for="skill in filteredCatalog" :key="skillKey(skill)" type="button" class="catalog-item" :class="{ active: skillKey(skill) === skillKey(skills.selected) }" @click="selectSkill(skill)"><strong>{{ skillLabel(skill) }}</strong><span>{{ skill.description || skill.summary || 'No description supplied.' }}</span></button><p v-if="!filteredCatalog.length" class="muted">No skills match this search.</p></WorkbenchSection>
      <div class="detail-stack">
        <WorkbenchSection title="Selected skill" class="detail-panel"><p v-if="!skills.selected" class="muted">Choose a skill from the catalog.</p><template v-else><div class="metadata"><strong>{{ skillLabel(skills.selected) }}</strong><span>{{ skills.selected.description || skills.selected.summary || 'No description supplied.' }}</span></div><label for="skill-args">Arguments (JSON)</label><textarea id="skill-args" v-model="argsJson" rows="8" spellcheck="false" /><p v-if="jsonError" class="status-error">{{ jsonError }}</p><button type="button" :disabled="skills.executing" @click="execute">{{ skills.executing ? 'Executing...' : 'Execute skill' }}</button><pre v-if="skills.lastResult">{{ pretty(skills.lastResult) }}</pre></template></WorkbenchSection>
        <WorkbenchSection title="Route preview" class="detail-panel"><div class="inline-control"><input v-model="routeQuery" class="control" placeholder="Describe the task to route" /><button type="button" @click="previewRoute">Preview route</button></div><pre v-if="skills.routePreview">{{ pretty(skills.routePreview) }}</pre><button type="button" @click="evaluateRouting">Evaluate routing</button><pre v-if="skills.evaluation">{{ pretty(skills.evaluation) }}</pre></WorkbenchSection>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import WorkbenchSection from '../../components/workbench/WorkbenchSection.vue'
import { useSkillsStore } from '../../stores/skills'
import { useWorkbenchStore } from '../../stores/workbench'

const skills = useSkillsStore()
const workbench = useWorkbenchStore()
const search = ref('')
const argsJson = ref('{}')
const routeQuery = ref('')
const jsonError = ref('')
const localError = ref('')
const skillKey = (skill) => skill?.name || skill?.id || ''
const skillLabel = (skill) => skill?.displayName || skillKey(skill) || 'Unnamed skill'
const filteredCatalog = computed(() => { const query = search.value.trim().toLowerCase(); return skills.catalog.filter((skill) => !query || `${skillLabel(skill)} ${skill.description || skill.summary || ''}`.toLowerCase().includes(query)) })
const pretty = (value) => JSON.stringify(value, null, 2)
const selectSkill = async (skill) => { localError.value = ''; jsonError.value = ''; argsJson.value = '{}'; try { await skills.selectSkill(skillKey(skill)) } catch { localError.value = skills.error || 'Unable to load skill details.' } }
const loadSkills = async () => { await skills.loadSkills(); if (!skills.selected && skills.catalog.length) await selectSkill(skills.catalog[0]) }
const previewRoute = async () => { localError.value = ''; if (!routeQuery.value.trim()) { localError.value = 'Enter a task description to preview a route.'; return }; try { await skills.previewRoute(routeQuery.value.trim()) } catch { localError.value = skills.error || 'Route preview failed.' } }
const evaluateRouting = async () => { localError.value = ''; try { await skills.evaluateRouting() } catch { localError.value = skills.error || 'Routing evaluation failed.' } }
const execute = async () => { jsonError.value = ''; localError.value = ''; let args; try { args = JSON.parse(argsJson.value || '{}') } catch { jsonError.value = 'Arguments must be valid JSON before the skill can run.'; return }; try { await skills.executeSelected(args); workbench.addInvocation({ source: 'skills', name: skillLabel(skills.selected), status: 'complete', summary: 'Skill execution completed.' }) } catch { localError.value = skills.error || 'Skill execution failed.' } }
onMounted(loadSkills)
</script>

<style scoped>
.workspace { display: grid; gap: 16px; padding: 20px; }.workspace-header { display: flex; justify-content: space-between; align-items: center; gap: 14px; }.workspace-header h1 { margin: 0 0 4px; font-size: 1.2rem; }.workspace-header p { margin: 0; }.workspace-grid { display: grid; grid-template-columns: minmax(220px, .75fr) minmax(0, 1.5fr); gap: 16px; }.detail-stack, .catalog-panel, .detail-panel { display: grid; align-content: start; gap: 10px; }.detail-stack { gap: 16px; }.control, textarea { width: 100%; padding: 9px; color: var(--color-text); background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }.catalog-item { display: grid; gap: 3px; padding: 10px; text-align: left; color: var(--color-text); background: var(--color-panel-muted); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }.catalog-item.active { border-color: var(--color-primary); }.catalog-item span, .metadata span { color: var(--color-text-muted); font-size: .8rem; }.metadata { display: grid; gap: 5px; }.detail-panel label { color: var(--color-text-muted); font-size: .8rem; }.inline-control { display: flex; gap: 8px; }.status-error { margin: 0; color: var(--color-danger); }.detail-panel pre { max-height: 260px; margin: 0; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; padding: 10px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); font-size: .78rem; }button { cursor: pointer; }button:disabled { opacity: .55; cursor: not-allowed; }@media (max-width: 800px) { .workspace { padding: 14px; }.workspace-grid { grid-template-columns: 1fr; }.workspace-header { align-items: flex-start; flex-direction: column; }.inline-control { flex-direction: column; } }
</style>
