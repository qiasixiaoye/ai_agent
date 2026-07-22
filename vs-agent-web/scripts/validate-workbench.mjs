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
  'src/views/workbench/AgentWorkspace.vue',
  'src/views/workbench/ToolsWorkspace.vue',
  'src/views/workbench/SkillsWorkspace.vue',
  'src/views/workbench/McpWorkspace.vue',
  'src/views/workbench/RuntimeWorkspace.vue',
  'src/views/workbench/MemoryWorkspace.vue',
  'src/views/workbench/ContextWorkspace.vue',
  'src/views/workbench/WorkflowWorkspace.vue',
  'src/views/workbench/KnowledgeWorkspace.vue',
  'src/views/workbench/ObservabilityWorkspace.vue'
]

const failures = []

for (const file of requiredFiles) {
  if (!existsSync(resolve(root, file))) failures.push(`missing ${file}`)
}

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

const routerPath = resolve(root, 'src/router/index.js')
if (existsSync(routerPath)) {
  const router = readFileSync(routerPath, 'utf8')
  for (const path of ['/', '/agent', '/tools', '/skills', '/mcp', '/runtime', '/memory', '/context', '/workflow-studio', '/knowledge-base', '/observability']) {
    const childPath = path === '/' ? null : path.slice(1)
    const hasAbsolutePath = router.includes(`path: '${path}'`) || router.includes(`path: \"${path}\"`)
    const hasChildPath = childPath && (router.includes(`path: '${childPath}'`) || router.includes(`path: \"${childPath}\"`))
    if (!hasAbsolutePath && !hasChildPath) {
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
