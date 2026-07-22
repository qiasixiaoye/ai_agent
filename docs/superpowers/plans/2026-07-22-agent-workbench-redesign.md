# Agent Workbench Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current demo-page frontend with a unified Codex/Claude-Code-style Agent Workbench that centers chat and exposes tools, Skills, runtime governance, memory, context, workflow, knowledge, and observability in one operator shell.

**Architecture:** Build a shared Vue workbench shell around the existing routes and APIs, then migrate each major surface into consistent workbench panels. Keep backend contracts stable and add only frontend orchestration stores, shared components, and a lightweight validation script.

**Tech Stack:** Vue 3.5, Vite 7, Vue Router 4, Pinia 3, axios, existing local CSS tokens, Node.js scripts without new dependencies.

## Global Constraints

- Project root: `C:\Users\lmh\Desktop\ai_agent\ai_agent`.
- Frontend root: `C:\Users\lmh\Desktop\ai_agent\ai_agent\vs-agent-web`.
- Do not replace Spring AI, MCP governance, memory algorithms, vector search, Dify, workflow execution, or observability backend behavior.
- Do not add a UI component library unless implementation proves the current stack cannot support the required interface.
- Default first screen is the usable workbench chat, not a marketing home page.
- Keep Tool, Agent, Skill, MCP, memory, context, workflow, and observability as separate visible surfaces.
- Use existing APIs in `vs-agent-web/src/services/api.js` as the transport boundary.
- Do not silently auto-write memory in the first implementation; use suggestion-first memory writes unless the user explicitly enables auto-write.
- UI style should be dense, calm, professional, and repeat-use friendly; avoid large glowing gradients, decorative orbs, nested cards, and oversized hero sections.
- Existing unrelated dirty worktree changes are user-owned. Do not revert them. Stage and commit only files touched by the active task.

---

## File Structure

Create:

- `vs-agent-web/scripts/validate-workbench.mjs`: dependency-free structural checks for workbench files, route names, store exports, and forbidden visual patterns.
- `vs-agent-web/src/layouts/WorkbenchLayout.vue`: shell frame with left rail, main route slot, and right inspector.
- `vs-agent-web/src/components/workbench/AppRail.vue`: navigation rail and runtime indicator.
- `vs-agent-web/src/components/workbench/InspectorPanel.vue`: right-side context, memory, tools, permissions, and trace inspector.
- `vs-agent-web/src/components/workbench/CommandBar.vue`: compact global search/command entry for tools and Skills.
- `vs-agent-web/src/components/workbench/SegmentedControl.vue`: reusable mode switch.
- `vs-agent-web/src/components/workbench/WorkbenchSection.vue`: unframed section wrapper with consistent heading/action layout.
- `vs-agent-web/src/components/workbench/PermissionNotice.vue`: risk/confirmation display for tool and MCP invocation.
- `vs-agent-web/src/stores/workbench.js`: current conversation, active area, inspector state, recent invocations, permission display preferences.
- `vs-agent-web/src/stores/runtime.js`: MCP health, catalog, invoke state, risk normalization.
- `vs-agent-web/src/stores/skills.js`: Skill catalog, selected Skill, routing preview, execution state.
- `vs-agent-web/src/stores/memory.js`: memory, context preview, diagnostics, manual write, suggestion-first auto-write state.
- `vs-agent-web/src/views/workbench/ChatWorkspace.vue`: central chat experience inside the workbench.
- `vs-agent-web/src/views/workbench/ToolsWorkspace.vue`: unified local/platform/MCP tool browser and runner.
- `vs-agent-web/src/views/workbench/SkillsWorkspace.vue`: Skill browser, router preview, evaluation, and execution.
- `vs-agent-web/src/views/workbench/RuntimeWorkspace.vue`: MCP runtime health, catalog refresh, managed invocation.
- `vs-agent-web/src/views/workbench/MemoryWorkspace.vue`: conversation memory, context preview, diagnostics, and semantic writes.
- `vs-agent-web/src/views/workbench/WorkflowWorkspace.vue`: wrapper/adaptation for workflow builder.
- `vs-agent-web/src/views/workbench/KnowledgeWorkspace.vue`: wrapper/adaptation for knowledge base.
- `vs-agent-web/src/views/workbench/ObservabilityWorkspace.vue`: wrapper/adaptation for trace lookup and failure lists.

Modify:

- `vs-agent-web/package.json`: add `validate:workbench` script.
- `vs-agent-web/src/App.vue`: remove conflicting global light styles and keep the router outlet only.
- `vs-agent-web/src/router/index.js`: route `/` to workbench chat; route old paths to workbench-compatible views.
- `vs-agent-web/src/style.css`: normalize base app background, text, scrollbars, and responsive shell behavior.
- `vs-agent-web/src/styles/tokens.css`: replace neon-heavy tokens with restrained workbench tokens.
- `vs-agent-web/src/stores/chat.js`: add message metadata, mode, status updates, and memory suggestion hooks.
- `vs-agent-web/src/components/ChatInput.vue`: make input work cleanly inside workbench footer.
- `vs-agent-web/src/components/ChatMessage.vue`: support compact metadata, incomplete/error state, and optional detail blocks.
- `vs-agent-web/src/components/LoadingIndicator.vue`: align loading style with workbench.
- Existing views under `vs-agent-web/src/views/*.vue`: either wrap, simplify, or replace imports with workbench views while preserving endpoint behavior.

---

### Task 1: Add Workbench Validation Harness

**Files:**
- Create: `C:\Users\lmh\Desktop\ai_agent\ai_agent\vs-agent-web\scripts\validate-workbench.mjs`
- Modify: `C:\Users\lmh\Desktop\ai_agent\ai_agent\vs-agent-web\package.json`

**Interfaces:**
- Consumes: frontend file tree.
- Produces: `npm run validate:workbench`, used by every later task.

- [ ] **Step 1: Create the failing validation script**

Create `vs-agent-web/scripts/validate-workbench.mjs` with checks for files that do not exist yet:

```js
import { readFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(new URL('..', import.meta.url).pathname)
const requiredFiles = [
  'src/layouts/WorkbenchLayout.vue',
  'src/components/workbench/AppRail.vue',
  'src/components/workbench/InspectorPanel.vue',
  'src/components/workbench/CommandBar.vue',
  'src/components/workbench/SegmentedControl.vue',
  'src/components/workbench/WorkbenchSection.vue',
  'src/components/workbench/PermissionNotice.vue',
  'src/stores/workbench.js',
  'src/stores/runtime.js',
  'src/stores/skills.js',
  'src/stores/memory.js',
  'src/views/workbench/ChatWorkspace.vue',
  'src/views/workbench/ToolsWorkspace.vue',
  'src/views/workbench/SkillsWorkspace.vue',
  'src/views/workbench/RuntimeWorkspace.vue',
  'src/views/workbench/MemoryWorkspace.vue',
  'src/views/workbench/WorkflowWorkspace.vue',
  'src/views/workbench/KnowledgeWorkspace.vue',
  'src/views/workbench/ObservabilityWorkspace.vue'
]

const failures = []

for (const file of requiredFiles) {
  if (!existsSync(resolve(root, file))) failures.push(`missing ${file}`)
}

const routerPath = resolve(root, 'src/router/index.js')
if (existsSync(routerPath)) {
  const router = readFileSync(routerPath, 'utf8')
  for (const path of ['/', '/tools', '/skills', '/runtime', '/memory', '/workflow-studio', '/knowledge-base', '/observability']) {
    if (!router.includes(`path: '${path}'`) && !router.includes(`path: "${path}"`)) {
      failures.push(`router missing ${path}`)
    }
  }
}

const tokenPath = resolve(root, 'src/styles/tokens.css')
if (existsSync(tokenPath)) {
  const tokens = readFileSync(tokenPath, 'utf8')
  for (const forbidden of ['glow-cyan', 'nebula', 'bokeh', 'scanline']) {
    if (tokens.toLowerCase().includes(forbidden)) failures.push(`token file still contains ${forbidden}`)
  }
}

if (failures.length) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('WORKBENCH_STRUCTURE_OK')
```

- [ ] **Step 2: Add validation script to package.json**

Modify `vs-agent-web/package.json` scripts:

```json
{
  "scripts": {
    "dev": "vite",
    "pure-build": "vite build",
    "build": "vite build",
    "preview": "vite preview",
    "validate:workbench": "node scripts/validate-workbench.mjs"
  }
}
```

- [ ] **Step 3: Run validation and verify it fails for the right reason**

Run:

```powershell
cd C:\Users\lmh\Desktop\ai_agent\ai_agent\vs-agent-web
npm run validate:workbench
```

Expected: FAIL with messages beginning `missing src/layouts/WorkbenchLayout.vue`.

- [ ] **Step 4: Commit**

```powershell
git -C C:\Users\lmh\Desktop\ai_agent\ai_agent add -- vs-agent-web/scripts/validate-workbench.mjs vs-agent-web/package.json
git -C C:\Users\lmh\Desktop\ai_agent\ai_agent commit -m "test: add workbench validation harness"
```

---

### Task 2: Build Shell, Tokens, and Routes

**Files:**
- Create: `vs-agent-web/src/layouts/WorkbenchLayout.vue`
- Create: `vs-agent-web/src/components/workbench/AppRail.vue`
- Create: `vs-agent-web/src/components/workbench/CommandBar.vue`
- Create: `vs-agent-web/src/components/workbench/SegmentedControl.vue`
- Create: `vs-agent-web/src/components/workbench/WorkbenchSection.vue`
- Create: placeholder workspace views under `vs-agent-web/src/views/workbench/*.vue`
- Create: `vs-agent-web/src/stores/workbench.js`
- Modify: `vs-agent-web/src/App.vue`
- Modify: `vs-agent-web/src/router/index.js`
- Modify: `vs-agent-web/src/style.css`
- Modify: `vs-agent-web/src/styles/tokens.css`

**Interfaces:**
- Consumes: `npm run validate:workbench` from Task 1.
- Produces:
  - `useWorkbenchStore()` with state `{ currentConversationId, activeArea, inspectorOpen, recentInvocations }`.
  - Routes `/`, `/chat`, `/tools`, `/skills`, `/runtime`, `/memory`, `/workflow-studio`, `/knowledge-base`, `/observability`.
  - `WorkbenchLayout` wraps all workbench routes.

- [ ] **Step 1: Run validation before implementation**

Run:

```powershell
cd C:\Users\lmh\Desktop\ai_agent\ai_agent\vs-agent-web
npm run validate:workbench
```

Expected: FAIL because shell files are missing.

- [ ] **Step 2: Implement `useWorkbenchStore`**

Create `vs-agent-web/src/stores/workbench.js`:

```js
import { defineStore } from 'pinia'

export const WORKBENCH_AREAS = [
  { key: 'chat', path: '/', label: 'Chat' },
  { key: 'tools', path: '/tools', label: 'Tools' },
  { key: 'skills', path: '/skills', label: 'Skills' },
  { key: 'runtime', path: '/runtime', label: 'Runtime' },
  { key: 'memory', path: '/memory', label: 'Memory' },
  { key: 'workflow', path: '/workflow-studio', label: 'Workflow' },
  { key: 'knowledge', path: '/knowledge-base', label: 'Knowledge' },
  { key: 'observability', path: '/observability', label: 'Observability' }
]

export const useWorkbenchStore = defineStore('workbench', {
  state: () => ({
    currentConversationId: '',
    activeArea: 'chat',
    inspectorOpen: true,
    recentInvocations: [],
    permissionDetailOpen: true
  }),
  actions: {
    setConversation(id) {
      this.currentConversationId = id
    },
    setActiveArea(area) {
      this.activeArea = area
    },
    toggleInspector() {
      this.inspectorOpen = !this.inspectorOpen
    },
    addInvocation(invocation) {
      this.recentInvocations.unshift({
        id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
        createdAt: new Date().toISOString(),
        ...invocation
      })
      this.recentInvocations = this.recentInvocations.slice(0, 12)
    }
  }
})
```

- [ ] **Step 3: Implement shell components**

Create these components with the exact prop contracts:

`AppRail.vue`:

```vue
<template>
  <aside class="app-rail">
    <div class="brand">Agent</div>
    <nav>
      <RouterLink
        v-for="item in areas"
        :key="item.key"
        :to="item.path"
        class="rail-link"
        :class="{ active: activeArea === item.key }"
      >
        <span class="rail-dot" />
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>
    <div class="rail-status" :class="runtimeStatus">{{ runtimeLabel }}</div>
  </aside>
</template>

<script setup>
import { RouterLink } from 'vue-router'

defineProps({
  areas: { type: Array, required: true },
  activeArea: { type: String, required: true },
  runtimeStatus: { type: String, default: 'unknown' },
  runtimeLabel: { type: String, default: 'Runtime unknown' }
})
</script>
```

`CommandBar.vue`:

```vue
<template>
  <div class="command-bar">
    <input :value="modelValue" type="search" placeholder="Search tools, Skills, memory" @input="$emit('update:modelValue', $event.target.value)" />
    <button type="button" @click="$emit('run')">Run</button>
  </div>
</template>

<script setup>
defineProps({ modelValue: { type: String, default: '' } })
defineEmits(['update:modelValue', 'run'])
</script>
```

`SegmentedControl.vue`:

```vue
<template>
  <div class="segmented-control">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      :class="{ active: modelValue === option.value }"
      @click="$emit('update:modelValue', option.value)"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<script setup>
defineProps({
  modelValue: { type: String, required: true },
  options: { type: Array, required: true }
})
defineEmits(['update:modelValue'])
</script>
```

`WorkbenchSection.vue`:

```vue
<template>
  <section class="workbench-section">
    <header v-if="title || $slots.actions">
      <h2 v-if="title">{{ title }}</h2>
      <div class="section-actions"><slot name="actions" /></div>
    </header>
    <slot />
  </section>
</template>

<script setup>
defineProps({ title: { type: String, default: '' } })
</script>
```

- [ ] **Step 4: Implement `WorkbenchLayout.vue`**

Create `vs-agent-web/src/layouts/WorkbenchLayout.vue`:

```vue
<template>
  <div class="workbench-layout">
    <AppRail :areas="areas" :active-area="workbench.activeArea" :runtime-status="runtimeStatus" :runtime-label="runtimeLabel" />
    <main class="workbench-main">
      <CommandBar v-model="commandQuery" @run="runCommand" />
      <RouterView />
    </main>
    <InspectorPanel v-if="workbench.inspectorOpen" />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter, RouterView } from 'vue-router'
import AppRail from '../components/workbench/AppRail.vue'
import CommandBar from '../components/workbench/CommandBar.vue'
import InspectorPanel from '../components/workbench/InspectorPanel.vue'
import { WORKBENCH_AREAS, useWorkbenchStore } from '../stores/workbench'

const route = useRoute()
const router = useRouter()
const workbench = useWorkbenchStore()
const commandQuery = ref('')
const areas = WORKBENCH_AREAS

const runtimeStatus = computed(() => 'unknown')
const runtimeLabel = computed(() => 'Runtime')

watch(
  () => route.meta.area,
  (area) => workbench.setActiveArea(area || 'chat'),
  { immediate: true }
)

const runCommand = () => {
  const query = commandQuery.value.trim().toLowerCase()
  if (query.includes('skill')) router.push('/skills')
  else if (query.includes('tool')) router.push('/tools')
  else if (query.includes('memory')) router.push('/memory')
}
</script>
```

Create a temporary `InspectorPanel.vue`:

```vue
<template>
  <aside class="inspector-panel">
    <h2>Context</h2>
    <p class="muted">No active run yet.</p>
  </aside>
</template>
```

- [ ] **Step 5: Create placeholder workbench views**

Create each file under `vs-agent-web/src/views/workbench/` with a small section so routing and layout can compile. Example for `ToolsWorkspace.vue`:

```vue
<template>
  <WorkbenchSection title="Tools">
    <p class="muted">Tool catalog will load here.</p>
  </WorkbenchSection>
</template>

<script setup>
import WorkbenchSection from '../../components/workbench/WorkbenchSection.vue'
</script>
```

Use the same pattern for `ChatWorkspace.vue`, `SkillsWorkspace.vue`, `RuntimeWorkspace.vue`, `MemoryWorkspace.vue`, `WorkflowWorkspace.vue`, `KnowledgeWorkspace.vue`, and `ObservabilityWorkspace.vue`, changing only the title and message.

- [ ] **Step 6: Replace router with shell routes**

Modify `vs-agent-web/src/router/index.js`:

```js
import { createRouter, createWebHistory } from 'vue-router'
import WorkbenchLayout from '../layouts/WorkbenchLayout.vue'

const workbenchChildren = [
  { path: '', name: 'ChatWorkspace', component: () => import('../views/workbench/ChatWorkspace.vue'), meta: { area: 'chat' } },
  { path: 'chat', redirect: '/' },
  { path: 'tools', name: 'ToolsWorkspace', component: () => import('../views/workbench/ToolsWorkspace.vue'), meta: { area: 'tools' } },
  { path: 'skills', name: 'SkillsWorkspace', component: () => import('../views/workbench/SkillsWorkspace.vue'), meta: { area: 'skills' } },
  { path: 'runtime', name: 'RuntimeWorkspace', component: () => import('../views/workbench/RuntimeWorkspace.vue'), meta: { area: 'runtime' } },
  { path: 'memory', name: 'MemoryWorkspace', component: () => import('../views/workbench/MemoryWorkspace.vue'), meta: { area: 'memory' } },
  { path: 'workflow-studio', name: 'WorkflowWorkspace', component: () => import('../views/workbench/WorkflowWorkspace.vue'), meta: { area: 'workflow' } },
  { path: 'knowledge-base', name: 'KnowledgeWorkspace', component: () => import('../views/workbench/KnowledgeWorkspace.vue'), meta: { area: 'knowledge' } },
  { path: 'observability', name: 'ObservabilityWorkspace', component: () => import('../views/workbench/ObservabilityWorkspace.vue'), meta: { area: 'observability' } }
]

const routes = [
  { path: '/', component: WorkbenchLayout, children: workbenchChildren },
  { path: '/assistant-app', redirect: '/' },
  { path: '/agent-platform', redirect: '/tools' },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
```

- [ ] **Step 7: Normalize App and style tokens**

Modify `App.vue` to remove the old light global style:

```vue
<template>
  <router-view />
</template>
```

Replace `tokens.css` with restrained variables including:

```css
:root {
  --color-bg: #0f1115;
  --color-panel: #161a22;
  --color-panel-muted: #1d222c;
  --color-border: #2a303b;
  --color-text: #eef1f5;
  --color-text-muted: #a7afbd;
  --color-text-subtle: #747d8c;
  --color-primary: #5aa7ff;
  --color-success: #4fbf7f;
  --color-warning: #d8a84f;
  --color-danger: #df6b6b;
  --radius-sm: 4px;
  --radius-md: 8px;
  --shadow-panel: 0 10px 30px rgba(0, 0, 0, 0.28);
}
```

Update `style.css` with shell classes `.workbench-layout`, `.app-rail`, `.workbench-main`, `.inspector-panel`, `.command-bar`, `.segmented-control`, `.workbench-section`, `.muted`, and responsive behavior below `900px`.

- [ ] **Step 8: Run checks**

Run:

```powershell
cd C:\Users\lmh\Desktop\ai_agent\ai_agent\vs-agent-web
npm run validate:workbench
npm run build
```

Expected:

```text
WORKBENCH_STRUCTURE_OK
vite build exits with code 0
```

- [ ] **Step 9: Commit**

```powershell
git -C C:\Users\lmh\Desktop\ai_agent\ai_agent add -- vs-agent-web/src/layouts vs-agent-web/src/components/workbench vs-agent-web/src/views/workbench vs-agent-web/src/stores/workbench.js vs-agent-web/src/App.vue vs-agent-web/src/router/index.js vs-agent-web/src/style.css vs-agent-web/src/styles/tokens.css
git -C C:\Users\lmh\Desktop\ai_agent\ai_agent commit -m "feat: add agent workbench shell"
```

---

### Task 3: Add Runtime, Skill, and Memory Stores

**Files:**
- Create: `vs-agent-web/src/stores/runtime.js`
- Create: `vs-agent-web/src/stores/skills.js`
- Create: `vs-agent-web/src/stores/memory.js`
- Modify: `vs-agent-web/src/stores/workbench.js`
- Modify: `vs-agent-web/scripts/validate-workbench.mjs`

**Interfaces:**
- Consumes: API functions from `vs-agent-web/src/services/api.js`.
- Produces:
  - `useRuntimeStore().loadHealth()`, `loadTools()`, `refreshTools()`, `invokeTool(payload)`, `riskForTool(tool)`.
  - `useSkillsStore().loadSkills()`, `selectSkill(name)`, `previewRoute(query)`, `executeSelected(args)`.
  - `useMemoryStore().loadConversation(conversationId)`, `previewContext(conversationId, query, tokenBudget)`, `loadDiagnostics(conversationId, query, mode)`, `suggestMemory({ userMessage, assistantMessage })`, `writeSuggestion(conversationId)`.

- [ ] **Step 1: Extend validation for store exports**

Add these checks to `validate-workbench.mjs`:

```js
const exportChecks = {
  'src/stores/runtime.js': ['useRuntimeStore'],
  'src/stores/skills.js': ['useSkillsStore'],
  'src/stores/memory.js': ['useMemoryStore'],
  'src/stores/workbench.js': ['useWorkbenchStore', 'WORKBENCH_AREAS']
}

for (const [file, exports] of Object.entries(exportChecks)) {
  const full = resolve(root, file)
  if (!existsSync(full)) continue
  const text = readFileSync(full, 'utf8')
  for (const name of exports) {
    if (!text.includes(`export const ${name}`)) failures.push(`${file} missing export ${name}`)
  }
}
```

- [ ] **Step 2: Implement `runtime.js`**

Create `vs-agent-web/src/stores/runtime.js`:

```js
import { defineStore } from 'pinia'
import {
  getMcpRuntimeHealth,
  listManagedMcpTools,
  refreshManagedMcpTools,
  invokeManagedMcpTool
} from '../services/api'

const messageOf = (error) => error?.message || 'Request failed'

export const useRuntimeStore = defineStore('runtime', {
  state: () => ({
    health: null,
    tools: [],
    loading: false,
    invoking: false,
    error: '',
    lastResult: null
  }),
  getters: {
    status: (state) => state.health?.status || (state.error ? 'error' : 'unknown')
  },
  actions: {
    async loadHealth() {
      try {
        this.health = await getMcpRuntimeHealth()
        this.error = ''
      } catch (error) {
        this.error = messageOf(error)
      }
    },
    async loadTools() {
      this.loading = true
      try {
        this.tools = await listManagedMcpTools()
        this.error = ''
      } catch (error) {
        this.error = messageOf(error)
      } finally {
        this.loading = false
      }
    },
    async refreshTools() {
      this.loading = true
      try {
        this.tools = await refreshManagedMcpTools()
        this.error = ''
      } catch (error) {
        this.error = messageOf(error)
      } finally {
        this.loading = false
      }
    },
    riskForTool(tool) {
      const risk = String(tool?.riskLevel || tool?.risk || '').toLowerCase()
      if (risk.includes('high') || tool?.requiresConfirmation) return 'confirm'
      if (risk.includes('block')) return 'blocked'
      if (risk.includes('safe') || risk.includes('low')) return 'safe'
      return 'unknown'
    },
    async invokeTool({ toolName, argumentsJson, confirmed = false }) {
      this.invoking = true
      try {
        this.lastResult = await invokeManagedMcpTool({ toolName, argumentsJson, confirmed })
        this.error = ''
        return this.lastResult
      } catch (error) {
        this.error = messageOf(error)
        throw error
      } finally {
        this.invoking = false
      }
    }
  }
})
```

- [ ] **Step 3: Implement `skills.js`**

Create `vs-agent-web/src/stores/skills.js`:

```js
import { defineStore } from 'pinia'
import { listSkills, getSkill, executeSkill, previewSkillRoute, evaluateSkillRouting } from '../services/api'

const messageOf = (error) => error?.message || 'Request failed'

export const useSkillsStore = defineStore('skills', {
  state: () => ({
    catalog: [],
    selected: null,
    routePreview: null,
    evaluation: null,
    loading: false,
    executing: false,
    error: '',
    lastResult: null
  }),
  actions: {
    async loadSkills() {
      this.loading = true
      try {
        this.catalog = await listSkills()
        this.error = ''
      } catch (error) {
        this.error = messageOf(error)
      } finally {
        this.loading = false
      }
    },
    async selectSkill(name) {
      this.loading = true
      try {
        this.selected = await getSkill(name)
        this.error = ''
      } catch (error) {
        this.error = messageOf(error)
      } finally {
        this.loading = false
      }
    },
    async previewRoute(query, topK = 3, threshold = 0.24) {
      this.routePreview = await previewSkillRoute(query, topK, threshold)
      return this.routePreview
    },
    async evaluateRouting() {
      this.evaluation = await evaluateSkillRouting()
      return this.evaluation
    },
    async executeSelected(args) {
      if (!this.selected?.name) throw new Error('No Skill selected')
      this.executing = true
      try {
        this.lastResult = await executeSkill(this.selected.name, args)
        this.error = ''
        return this.lastResult
      } catch (error) {
        this.error = messageOf(error)
        throw error
      } finally {
        this.executing = false
      }
    }
  }
})
```

- [ ] **Step 4: Implement `memory.js`**

Create `vs-agent-web/src/stores/memory.js`:

```js
import { defineStore } from 'pinia'
import {
  getConversationMemory,
  previewConversationContext,
  getContextDiagnostics,
  addSemanticMemory,
  clearConversationMemory
} from '../services/api'

const messageOf = (error) => error?.message || 'Request failed'

export const useMemoryStore = defineStore('memory', {
  state: () => ({
    conversation: null,
    contextPreview: null,
    diagnostics: null,
    suggestion: null,
    autoWriteEnabled: false,
    loading: false,
    writing: false,
    error: ''
  }),
  actions: {
    async loadConversation(conversationId) {
      if (!conversationId) return
      this.loading = true
      try {
        this.conversation = await getConversationMemory(conversationId)
        this.error = ''
      } catch (error) {
        this.error = messageOf(error)
      } finally {
        this.loading = false
      }
    },
    async previewContext(conversationId, query, tokenBudget = 2500) {
      this.contextPreview = await previewConversationContext(conversationId, query, tokenBudget)
      return this.contextPreview
    },
    async loadDiagnostics(conversationId, query, mode = 'plain') {
      this.diagnostics = await getContextDiagnostics(conversationId, query, mode)
      return this.diagnostics
    },
    suggestMemory({ userMessage, assistantMessage }) {
      const user = String(userMessage || '').trim()
      const assistant = String(assistantMessage || '').trim()
      if (!user || !assistant || assistant.length < 80) return
      this.suggestion = {
        content: `User asked: ${user}\nUseful answer summary: ${assistant.slice(0, 600)}`,
        importance: 0.8,
        status: 'pending'
      }
    },
    async writeSuggestion(conversationId) {
      if (!this.suggestion?.content) return
      this.writing = true
      try {
        const result = await addSemanticMemory(conversationId, this.suggestion.content, this.suggestion.importance)
        this.suggestion.status = 'written'
        this.error = ''
        await this.loadConversation(conversationId)
        return result
      } catch (error) {
        this.suggestion.status = 'failed'
        this.error = messageOf(error)
        throw error
      } finally {
        this.writing = false
      }
    },
    async clearConversation(conversationId) {
      await clearConversationMemory(conversationId)
      this.conversation = null
      this.contextPreview = null
      this.diagnostics = null
      this.suggestion = null
    }
  }
})
```

- [ ] **Step 5: Run checks**

Run:

```powershell
cd C:\Users\lmh\Desktop\ai_agent\ai_agent\vs-agent-web
npm run validate:workbench
npm run build
```

Expected: validation prints `WORKBENCH_STRUCTURE_OK`; build exits with code 0.

- [ ] **Step 6: Commit**

```powershell
git -C C:\Users\lmh\Desktop\ai_agent\ai_agent add -- vs-agent-web/src/stores/runtime.js vs-agent-web/src/stores/skills.js vs-agent-web/src/stores/memory.js vs-agent-web/src/stores/workbench.js vs-agent-web/scripts/validate-workbench.mjs
git -C C:\Users\lmh\Desktop\ai_agent\ai_agent commit -m "feat: add workbench orchestration stores"
```

---

### Task 4: Move Chat Into the Workbench

**Files:**
- Modify: `vs-agent-web/src/stores/chat.js`
- Modify: `vs-agent-web/src/views/workbench/ChatWorkspace.vue`
- Modify: `vs-agent-web/src/components/ChatInput.vue`
- Modify: `vs-agent-web/src/components/ChatMessage.vue`
- Modify: `vs-agent-web/src/components/LoadingIndicator.vue`
- Modify: `vs-agent-web/src/layouts/WorkbenchLayout.vue`

**Interfaces:**
- Consumes:
  - `useWorkbenchStore().setConversation(id)`.
  - `useMemoryStore().suggestMemory(payload)`.
  - existing SSE functions `connectToAssistantAppChat`, `connectToAssistantAppRagChat`, `connectToManusChat`.
- Produces:
  - `useChatStore().createConversation(mode)`.
  - `useChatStore().addMessage(chatId, payload)`.
  - `useChatStore().updateLastAssistantMessage(chatId, patch)`.

- [ ] **Step 1: Add richer chat store actions**

Modify `chat.js` to preserve existing methods and add:

```js
createConversation(mode = 'normal') {
  const chatId = uuidv4()
  this.assistantAppChats[chatId] = {
    id: chatId,
    mode,
    messages: [],
    createdAt: new Date().toISOString()
  }
  return chatId
},

addMessage(chatId, payload) {
  if (!this.assistantAppChats[chatId]) {
    this.assistantAppChats[chatId] = { id: chatId, mode: payload.mode || 'normal', messages: [] }
  }
  const message = {
    id: uuidv4(),
    content: payload.content || '',
    isUser: Boolean(payload.isUser),
    role: payload.isUser ? 'user' : 'assistant',
    mode: payload.mode || this.assistantAppChats[chatId].mode || 'normal',
    status: payload.status || 'complete',
    details: payload.details || null,
    timestamp: new Date()
  }
  this.assistantAppChats[chatId].messages.push(message)
  return message.id
},

updateLastAssistantMessage(chatId, patch) {
  const chat = this.assistantAppChats[chatId]
  if (!chat) return
  const message = [...chat.messages].reverse().find((item) => !item.isUser)
  if (message) Object.assign(message, patch)
}
```

Update legacy `createAssistantAppChat()` to call `createConversation('normal')`, and legacy `addAssistantAppMessage()` to call `addMessage()`.

- [ ] **Step 2: Implement chat workspace**

Replace `ChatWorkspace.vue` placeholder with:

```vue
<template>
  <section class="chat-workspace">
    <header class="workspace-header">
      <div>
        <h1>Chat</h1>
        <p class="muted">Normal, RAG, and Agent modes share this conversation context.</p>
      </div>
      <SegmentedControl v-model="mode" :options="modeOptions" />
    </header>

    <div ref="messagesContainer" class="chat-transcript">
      <ChatMessage
        v-for="message in messages"
        :key="message.id"
        :content="message.content"
        :is-user="message.isUser"
        :timestamp="message.timestamp"
        :status="message.status"
        :details="message.details"
      />
      <LoadingIndicator v-if="loading" />
    </div>

    <ChatInput :loading="loading" @send="sendMessage" />
  </section>
</template>
```

In the `<script setup>`, copy the existing SSE selection logic from `AssistantApp.vue`, but call the new chat store actions and `memoryStore.suggestMemory()` inside finalize:

```js
const finalize = () => {
  eventSource.value?.close()
  chatStore.updateLastAssistantMessage(chatId.value, { status: aiResponse ? 'complete' : 'incomplete' })
  memoryStore.suggestMemory({ userMessage: lastUserMessage.value, assistantMessage: aiResponse })
  loading.value = false
}
```

- [ ] **Step 3: Update message and loading components**

`ChatMessage.vue` must accept props:

```js
defineProps({
  content: { type: String, required: true },
  isUser: { type: Boolean, default: false },
  timestamp: { type: [String, Date], default: '' },
  status: { type: String, default: 'complete' },
  details: { type: [Object, Array, String], default: null }
})
```

Render status text only for `streaming`, `incomplete`, or `error`. Keep Markdown rendering if already present. Use compact styling and no nested card layout.

- [ ] **Step 4: Run checks**

Run:

```powershell
cd C:\Users\lmh\Desktop\ai_agent\ai_agent\vs-agent-web
npm run validate:workbench
npm run build
```

Expected: both pass. If backend is running, manually open `http://localhost:5173/` and send a short Normal mode message. If backend is not running, verify the chat shows a concrete connection error and keeps the typed message.

- [ ] **Step 5: Commit**

```powershell
git -C C:\Users\lmh\Desktop\ai_agent\ai_agent add -- vs-agent-web/src/stores/chat.js vs-agent-web/src/views/workbench/ChatWorkspace.vue vs-agent-web/src/components/ChatInput.vue vs-agent-web/src/components/ChatMessage.vue vs-agent-web/src/components/LoadingIndicator.vue vs-agent-web/src/layouts/WorkbenchLayout.vue
git -C C:\Users\lmh\Desktop\ai_agent\ai_agent commit -m "feat: move chat into workbench"
```

---

### Task 5: Implement the Context Inspector

**Files:**
- Modify: `vs-agent-web/src/components/workbench/InspectorPanel.vue`
- Create: `vs-agent-web/src/components/workbench/PermissionNotice.vue`
- Modify: `vs-agent-web/src/stores/memory.js`
- Modify: `vs-agent-web/src/stores/runtime.js`
- Modify: `vs-agent-web/src/stores/workbench.js`
- Modify: `vs-agent-web/src/views/workbench/ChatWorkspace.vue`

**Interfaces:**
- Consumes:
  - `useWorkbenchStore().currentConversationId`.
  - `useMemoryStore().suggestion`, `loadConversation()`, `writeSuggestion()`, `previewContext()`, `loadDiagnostics()`.
  - `useRuntimeStore().status`, `tools`, `riskForTool()`.
- Produces:
  - Inspector sections for context, memory suggestion, tools, permissions, and recent calls.
  - `PermissionNotice` props `{ risk, reason, confirmed }` and emits `confirm`.

- [ ] **Step 1: Implement `PermissionNotice.vue`**

```vue
<template>
  <div class="permission-notice" :class="risk">
    <div>
      <strong>{{ label }}</strong>
      <p v-if="reason">{{ reason }}</p>
    </div>
    <button v-if="risk === 'confirm' && !confirmed" type="button" @click="$emit('confirm')">Confirm</button>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  risk: { type: String, default: 'unknown' },
  reason: { type: String, default: '' },
  confirmed: { type: Boolean, default: false }
})
defineEmits(['confirm'])

const label = computed(() => ({
  safe: 'Safe',
  confirm: 'Confirmation required',
  blocked: 'Blocked',
  unknown: 'Unknown risk'
}[props.risk] || 'Unknown risk')
)
</script>
```

- [ ] **Step 2: Implement inspector sections**

Replace `InspectorPanel.vue` with sections for:

- Conversation id and mode.
- Memory suggestion editor with content textarea, importance input, `Write memory` button, and failure status.
- Context preview button using the latest chat input or last user message.
- Diagnostics summary rendering known buckets when present.
- Recent invocations list from `workbench.recentInvocations`.
- Runtime status from `runtime.status`.

Use `WorkbenchSection` for each section and avoid nested cards.

- [ ] **Step 3: Wire chat completion to inspector**

In `ChatWorkspace.vue`, after SSE finalize:

```js
workbench.addInvocation({
  source: mode.value,
  name: 'chat',
  status: aiResponse ? 'complete' : 'incomplete',
  summary: lastUserMessage.value.slice(0, 120)
})
```

Also expose the current conversation id through `workbench.setConversation(chatId.value)` during mount.

- [ ] **Step 4: Run checks**

Run:

```powershell
cd C:\Users\lmh\Desktop\ai_agent\ai_agent\vs-agent-web
npm run validate:workbench
npm run build
```

Expected: both pass. Manual check: inspector renders on desktop and collapses below `900px` without covering the chat input.

- [ ] **Step 5: Commit**

```powershell
git -C C:\Users\lmh\Desktop\ai_agent\ai_agent add -- vs-agent-web/src/components/workbench/InspectorPanel.vue vs-agent-web/src/components/workbench/PermissionNotice.vue vs-agent-web/src/stores/memory.js vs-agent-web/src/stores/runtime.js vs-agent-web/src/stores/workbench.js vs-agent-web/src/views/workbench/ChatWorkspace.vue
git -C C:\Users\lmh\Desktop\ai_agent\ai_agent commit -m "feat: add workbench context inspector"
```

---

### Task 6: Convert Tools, Skills, Runtime, and Memory Panels

**Files:**
- Modify: `vs-agent-web/src/views/workbench/ToolsWorkspace.vue`
- Modify: `vs-agent-web/src/views/workbench/SkillsWorkspace.vue`
- Modify: `vs-agent-web/src/views/workbench/RuntimeWorkspace.vue`
- Modify: `vs-agent-web/src/views/workbench/MemoryWorkspace.vue`
- Modify: `vs-agent-web/src/stores/runtime.js`
- Modify: `vs-agent-web/src/stores/skills.js`
- Modify: `vs-agent-web/src/stores/memory.js`
- Modify: `vs-agent-web/src/stores/workbench.js`

**Interfaces:**
- Consumes:
  - `useRuntimeStore` functions from Task 3.
  - `useSkillsStore` functions from Task 3.
  - `useMemoryStore` functions from Task 3.
- Produces:
  - Tool runner with JSON validation and confirmation state.
  - Skill runner with route preview and execution state.
  - Runtime panel with health, refresh, catalog, and managed invoke.
  - Memory panel with manual write, suggestion write, clear, preview, and diagnostics.

- [ ] **Step 1: Implement JSON helper in runtime store**

Add to `runtime.js`:

```js
normalizeArgumentsJson(value) {
  if (!value || !value.trim()) return '{}'
  JSON.parse(value)
  return value
}
```

Use it before `invokeTool`.

- [ ] **Step 2: Implement Tools workspace**

`ToolsWorkspace.vue` should:

- Load MCP tools on mount through `runtime.loadTools()`.
- Render a searchable list of tools.
- Render selected tool metadata and a JSON textarea initialized to `{}`.
- Use `PermissionNotice` with `runtime.riskForTool(selectedTool)`.
- On safe invoke, call `runtime.invokeTool({ toolName, argumentsJson, confirmed: false })`.
- On confirm invoke, first set local `confirmed = true`, then call with `confirmed: true`.
- Add invocation summary to `workbench.addInvocation()`.
- Show invalid JSON before calling the backend.

- [ ] **Step 3: Implement Skills workspace**

`SkillsWorkspace.vue` should:

- Load Skills on mount through `skills.loadSkills()`.
- Render catalog and selected Skill detail.
- Provide route preview input calling `skills.previewRoute(query)`.
- Provide JSON args textarea initialized to `{}`.
- Execute with `skills.executeSelected(JSON.parse(argsJson))`.
- Add invocation summary to `workbench.addInvocation()`.
- Show route evaluation results via `skills.evaluateRouting()`.

- [ ] **Step 4: Implement Runtime workspace**

`RuntimeWorkspace.vue` should:

- Call `runtime.loadHealth()` and `runtime.loadTools()` on mount.
- Render health status, refresh button, catalog count, and last invocation result.
- Provide managed invocation controls for selected MCP tool.
- Show backend errors and circuit/timeout text if returned in error messages.

- [ ] **Step 5: Implement Memory workspace**

`MemoryWorkspace.vue` should:

- Read conversation id from `workbench.currentConversationId`.
- Load memory with `memory.loadConversation(conversationId)`.
- Provide manual semantic memory textarea and importance input.
- Use `addSemanticMemory` through a store action if the current store does not expose manual writes directly.
- Provide preview context input and diagnostics input.
- Provide clear-memory button requiring a typed confirmation string `CLEAR`.

- [ ] **Step 6: Run checks**

Run:

```powershell
cd C:\Users\lmh\Desktop\ai_agent\ai_agent\vs-agent-web
npm run validate:workbench
npm run build
```

Expected: both pass. Manual check with backend available: Skills list loads, Runtime health returns, invalid JSON is blocked client-side, and confirmed MCP invocation sends `confirmed: true`.

- [ ] **Step 7: Commit**

```powershell
git -C C:\Users\lmh\Desktop\ai_agent\ai_agent add -- vs-agent-web/src/views/workbench/ToolsWorkspace.vue vs-agent-web/src/views/workbench/SkillsWorkspace.vue vs-agent-web/src/views/workbench/RuntimeWorkspace.vue vs-agent-web/src/views/workbench/MemoryWorkspace.vue vs-agent-web/src/stores/runtime.js vs-agent-web/src/stores/skills.js vs-agent-web/src/stores/memory.js vs-agent-web/src/stores/workbench.js
git -C C:\Users\lmh\Desktop\ai_agent\ai_agent commit -m "feat: add workbench runtime skill memory panels"
```

---

### Task 7: Convert Workflow, Knowledge, and Observability Panels

**Files:**
- Modify: `vs-agent-web/src/views/workbench/WorkflowWorkspace.vue`
- Modify: `vs-agent-web/src/views/workbench/KnowledgeWorkspace.vue`
- Modify: `vs-agent-web/src/views/workbench/ObservabilityWorkspace.vue`
- Optionally Modify: `vs-agent-web/src/views/WorkflowStudio.vue`
- Optionally Modify: `vs-agent-web/src/views/KnowledgeBase.vue`
- Optionally Modify: `vs-agent-web/src/views/Observability.vue`

**Interfaces:**
- Consumes: existing API functions in `api.js` for workflow builder, knowledge base, and observability.
- Produces: workbench-native wrappers or migrated panels that avoid old gradient headers and duplicate back links.

- [ ] **Step 1: Implement Workflow workspace**

Either import and restyle the existing `WorkflowStudio.vue` content into `WorkflowWorkspace.vue`, or move its API logic into the new view. Preserve these capabilities:

- requirement-to-workflow generation,
- generated workflow run,
- Dify export/import links,
- run observation result.

Remove standalone page header/back-link UI from the workbench version.

- [ ] **Step 2: Implement Knowledge workspace**

Preserve:

- document list,
- upload,
- delete,
- reprocess,
- rebuild index,
- detail view.

Use a compact split layout: document list on the left, selected document/detail/actions on the right. Show upload errors and empty list states.

- [ ] **Step 3: Implement Observability workspace**

Preserve:

- request trace query,
- session requests query,
- failed request query.

Add a compact trace summary that can show request id, session id, status, elapsed time if present, and error text if present.

- [ ] **Step 4: Run checks**

Run:

```powershell
cd C:\Users\lmh\Desktop\ai_agent\ai_agent\vs-agent-web
npm run validate:workbench
npm run build
```

Expected: both pass. Manual check: navigating to `/workflow-studio`, `/knowledge-base`, and `/observability` stays inside the same workbench shell.

- [ ] **Step 5: Commit**

```powershell
git -C C:\Users\lmh\Desktop\ai_agent\ai_agent add -- vs-agent-web/src/views/workbench/WorkflowWorkspace.vue vs-agent-web/src/views/workbench/KnowledgeWorkspace.vue vs-agent-web/src/views/workbench/ObservabilityWorkspace.vue vs-agent-web/src/views/WorkflowStudio.vue vs-agent-web/src/views/KnowledgeBase.vue vs-agent-web/src/views/Observability.vue
git -C C:\Users\lmh\Desktop\ai_agent\ai_agent commit -m "feat: add workflow knowledge observability workbench panels"
```

---

### Task 8: Remove Legacy Demo Feel and Verify Visual Behavior

**Files:**
- Modify: `vs-agent-web/src/style.css`
- Modify: `vs-agent-web/src/styles/tokens.css`
- Modify: `vs-agent-web/src/views/Home.vue`
- Modify: `vs-agent-web/src/views/AssistantApp.vue`
- Modify: `vs-agent-web/src/views/AgentPlatform.vue`
- Modify: `vs-agent-web/src/views/Skills.vue`
- Modify: `vs-agent-web/src/views/RuntimeManagement.vue`
- Modify: `vs-agent-web/scripts/validate-workbench.mjs`

**Interfaces:**
- Consumes: all previous workbench routes and panels.
- Produces: no old marketing-style first screen, no duplicate standalone headers, no neon token leftovers, and a visual QA report in the final answer.

- [ ] **Step 1: Add validation checks for legacy route usage**

Extend `validate-workbench.mjs`:

```js
const appText = readFileSync(resolve(root, 'src/App.vue'), 'utf8')
if (appText.includes('background-color: #f0f2f5') || appText.includes('color: #333')) {
  failures.push('App.vue still contains old light global style')
}

const routerText = readFileSync(resolve(root, 'src/router/index.js'), 'utf8')
for (const legacyName of ['Home.vue', 'AssistantApp.vue', 'AgentPlatform.vue']) {
  if (routerText.includes(legacyName)) failures.push(`router still imports legacy view ${legacyName}`)
}
```

- [ ] **Step 2: Retire or thin legacy views**

Replace old standalone views with simple compatibility views only if they are still imported anywhere. For example, `Home.vue` may contain:

```vue
<template>
  <RouterView />
</template>

<script setup>
import { RouterView } from 'vue-router'
</script>
```

If router no longer imports a legacy file, leave the file untouched unless build warnings require cleanup. Do not delete files in this task unless the repo clearly no longer references them.

- [ ] **Step 3: Run build checks**

Run:

```powershell
cd C:\Users\lmh\Desktop\ai_agent\ai_agent\vs-agent-web
npm run validate:workbench
npm run build
```

Expected: validation prints `WORKBENCH_STRUCTURE_OK`; Vite build exits with code 0.

- [ ] **Step 4: Run local dev server for visual QA**

Run:

```powershell
cd C:\Users\lmh\Desktop\ai_agent\ai_agent\vs-agent-web
npm run dev -- --host 127.0.0.1
```

Expected: Vite prints a local URL, usually `http://127.0.0.1:5173/`. If port 5173 is taken, use the URL Vite prints.

Open the URL and check:

- desktop width: left rail, main panel, and right inspector fit without overlap;
- mobile width: rail and inspector collapse or stack without hiding chat input;
- `/`, `/tools`, `/skills`, `/runtime`, `/memory`, `/workflow-studio`, `/knowledge-base`, `/observability` all render inside the workbench;
- empty/error states are visible when backend data is unavailable;
- text does not overflow buttons, rail links, or compact panels.

- [ ] **Step 5: Commit**

```powershell
git -C C:\Users\lmh\Desktop\ai_agent\ai_agent add -- vs-agent-web/src/style.css vs-agent-web/src/styles/tokens.css vs-agent-web/src/views/Home.vue vs-agent-web/src/views/AssistantApp.vue vs-agent-web/src/views/AgentPlatform.vue vs-agent-web/src/views/Skills.vue vs-agent-web/src/views/RuntimeManagement.vue vs-agent-web/scripts/validate-workbench.mjs
git -C C:\Users\lmh\Desktop\ai_agent\ai_agent commit -m "refactor: finish workbench visual consolidation"
```

---

## Self-Review Notes

Spec coverage:

- Unified shell: Tasks 2, 8.
- Chat as central workspace: Task 4.
- Tool/Skill/runtime/memory workbench panels: Tasks 3, 5, 6.
- Workflow/knowledge/observability access: Task 7.
- Permission/risk display: Tasks 3, 5, 6.
- Context diagnostics and memory suggestion-first behavior: Tasks 3, 4, 5, 6.
- Existing backend compatibility: all tasks consume existing `api.js`; no backend task is included.
- Build and visual verification: Tasks 1, 2, 3, 4, 5, 6, 7, 8.

Placeholder scan:

- No unresolved marker or open-ended instruction is intentionally left.
- Conditional edits in Task 7 and Task 8 are bounded: only apply them when the files remain imported or build behavior requires cleanup.

Type consistency:

- Store names are consistent: `useWorkbenchStore`, `useRuntimeStore`, `useSkillsStore`, `useMemoryStore`.
- Route area keys match `WORKBENCH_AREAS`: `chat`, `tools`, `skills`, `runtime`, `memory`, `workflow`, `knowledge`, `observability`.
- Memory suggestion methods match across tasks: `suggestMemory()` and `writeSuggestion()`.
- Runtime invocation uses the existing API shape `{ toolName, argumentsJson, confirmed }`.
