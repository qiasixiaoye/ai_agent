import { readFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('..', import.meta.url))
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
    if (!router.includes(`path: '${path}'`) && !router.includes(`path: \"${path}\"`)) {
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
