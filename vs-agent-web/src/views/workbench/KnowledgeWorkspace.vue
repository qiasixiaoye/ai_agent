<template>
  <section class="workspace">
    <header class="workspace-header">
      <div>
        <p class="eyebrow">知识资产</p>
        <h1>管理 Agent 可检索的资料</h1>
        <p class="muted">上传文档、维护索引，并作为知识库对话的检索来源。</p>
      </div>
      <div class="header-actions">
        <button type="button" :disabled="loading" @click="refresh">{{ loading ? '刷新中…' : '刷新文档' }}</button>
        <button type="button" :disabled="rebuilding" @click="rebuild">{{ rebuilding ? '重建中…' : '重建索引' }}</button>
      </div>
    </header>

    <p v-if="error" class="soft-notice">{{ error }}</p>
    <p v-if="uploadError" class="status-error">上传失败：{{ uploadError }}</p>

    <div class="workspace-grid">
      <div class="stack">
        <WorkbenchSection title="上传资料" class="panel">
          <input ref="fileInput" type="file" @change="chooseFile" />
          <input v-model="source" placeholder="来源，可选" />
          <input v-model="tags" placeholder="标签，逗号分隔，可选" />
          <button type="button" :disabled="uploading || !pendingFile" @click="upload">{{ uploading ? '上传中…' : '上传选中文件' }}</button>
          <p v-if="pendingFile" class="muted">{{ pendingFile.name }}</p>
        </WorkbenchSection>

        <WorkbenchSection title="文档列表" class="panel list-panel">
          <p v-if="loading" class="muted">正在加载文档…</p>
          <p v-else-if="!documents.length" class="muted">暂无文档。上传资料后，知识库问答会优先使用这些内容。</p>
          <button v-for="document in documents" v-else :key="document.documentId" type="button" :class="['document-row', { selected: selectedId === document.documentId }]" @click="selectDocument(document.documentId)">
            <strong>{{ document.fileName || document.documentId }}</strong>
            <span>{{ document.status || '未知状态' }} · {{ document.chunkCount ?? 0 }} 个片段</span>
          </button>
        </WorkbenchSection>
      </div>

      <WorkbenchSection title="文档详情" class="panel detail-panel">
        <p v-if="detailLoading" class="muted">正在加载详情…</p>
        <p v-else-if="!selected" class="muted">从左侧选择文档后查看元数据和处理动作。</p>
        <template v-else>
          <dl>
            <div><dt>名称</dt><dd>{{ selected.fileName || '-' }}</dd></div>
            <div><dt>状态</dt><dd>{{ selected.status || '-' }}</dd></div>
            <div><dt>来源</dt><dd>{{ selected.source || '-' }}</dd></div>
            <div><dt>标签</dt><dd>{{ selected.tags || '-' }}</dd></div>
            <div><dt>片段</dt><dd>{{ selected.chunkCount ?? 0 }}</dd></div>
            <div><dt>上传时间</dt><dd>{{ formatTime(selected.uploadedAt) }}</dd></div>
          </dl>
          <p v-if="selected.errorMessage" class="status-error">{{ selected.errorMessage }}</p>
          <div class="header-actions">
            <button type="button" :disabled="acting" @click="reprocess">{{ acting ? '处理中…' : '重新处理' }}</button>
            <button type="button" class="danger" :disabled="acting" @click="remove">删除文档</button>
          </div>
          <pre v-if="selected.content || selected.text">{{ selected.content || selected.text }}</pre>
        </template>
      </WorkbenchSection>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import WorkbenchSection from '../../components/workbench/WorkbenchSection.vue'
import { deleteKbDocument, getKbDocument, listKbDocuments, rebuildKbIndex, reprocessKbDocument, uploadKbDocument } from '../../services/api'
import { productErrorMessage } from '../../utils/productText'

const documents = ref([])
const selected = ref(null)
const selectedId = ref('')
const pendingFile = ref(null)
const source = ref('')
const tags = ref('')
const error = ref('')
const uploadError = ref('')
const loading = ref(false)
const detailLoading = ref(false)
const uploading = ref(false)
const rebuilding = ref(false)
const acting = ref(false)

const report = (cause, fallback) => {
  error.value = productErrorMessage(cause, fallback)
}
const refresh = async () => {
  loading.value = true
  error.value = ''
  try {
    documents.value = await listKbDocuments(80)
    if (selectedId.value && !documents.value.some((item) => item.documentId === selectedId.value)) {
      selectedId.value = ''
      selected.value = null
    }
  } catch (cause) {
    report(cause, '知识库')
  } finally {
    loading.value = false
  }
}
const selectDocument = async (id) => {
  selectedId.value = id
  selected.value = null
  detailLoading.value = true
  error.value = ''
  try {
    selected.value = await getKbDocument(id)
  } catch (cause) {
    report(cause, '文档详情')
  } finally {
    detailLoading.value = false
  }
}
const chooseFile = (event) => {
  pendingFile.value = event.target.files?.[0] || null
  event.target.value = ''
}
const upload = async () => {
  if (!pendingFile.value) return
  uploading.value = true
  uploadError.value = ''
  try {
    const result = await uploadKbDocument(pendingFile.value, { source: source.value || undefined, tags: tags.value || undefined })
    pendingFile.value = null
    await refresh()
    if (result?.documentId) await selectDocument(result.documentId)
  } catch (cause) {
    uploadError.value = productErrorMessage(cause, '文档上传')
  } finally {
    uploading.value = false
  }
}
const reprocess = async () => {
  if (!selectedId.value) return
  acting.value = true
  error.value = ''
  try {
    await reprocessKbDocument(selectedId.value)
    await refresh()
    await selectDocument(selectedId.value)
  } catch (cause) {
    report(cause, '文档处理')
  } finally {
    acting.value = false
  }
}
const remove = async () => {
  if (!selectedId.value || !window.confirm(`删除 ${selected.value?.fileName || '这个文档'}？`)) return
  acting.value = true
  error.value = ''
  try {
    await deleteKbDocument(selectedId.value)
    selectedId.value = ''
    selected.value = null
    await refresh()
  } catch (cause) {
    report(cause, '文档删除')
  } finally {
    acting.value = false
  }
}
const rebuild = async () => {
  rebuilding.value = true
  error.value = ''
  try {
    await rebuildKbIndex()
    await refresh()
  } catch (cause) {
    report(cause, '索引重建')
  } finally {
    rebuilding.value = false
  }
}
const formatTime = (value) => value ? new Date(value).toLocaleString() : '-'
onMounted(refresh)
</script>

<style scoped>
.workspace { display: grid; gap: 16px; padding: 20px; }
.workspace-header, .header-actions { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.workspace-header { padding: 22px; background: var(--color-panel); border: 1px solid var(--color-border); border-radius: var(--radius-lg); }
.workspace-header h1 { margin: 4px 0 6px; font-size: 1.7rem; }
.workspace-header p { margin: 0; }
.eyebrow { color: var(--color-primary); font-size: .75rem; font-weight: 800; letter-spacing: .16em; }
.workspace-grid { display: grid; grid-template-columns: minmax(260px, .8fr) minmax(0, 1.2fr); gap: 16px; }
.stack, .panel { display: grid; align-content: start; gap: 10px; }
.stack { gap: 16px; }
.panel input { width: 100%; padding: 10px; color: var(--color-text); background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }
.document-row { display: grid; gap: 4px; width: 100%; padding: 11px; color: var(--color-text); text-align: left; background: var(--color-bg-elevated); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }
.document-row.selected { border-color: var(--color-primary); background: rgba(90, 167, 255, .12); }
.document-row span { color: var(--color-text-muted); font-size: .78rem; }
.detail-panel dl { display: grid; gap: 8px; margin: 0; }
.detail-panel dl div { display: grid; grid-template-columns: 100px 1fr; gap: 10px; }
.detail-panel dt { color: var(--color-text-muted); }
.detail-panel dd { margin: 0; overflow-wrap: anywhere; }
.detail-panel pre { max-height: 300px; margin: 0; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; padding: 10px; background: var(--color-bg); border: 1px solid var(--color-border); border-radius: var(--radius-sm); font-size: .78rem; }
.soft-notice { margin: 0; padding: 10px 12px; color: var(--color-warning); background: rgba(216, 168, 79, .08); border: 1px solid rgba(216, 168, 79, .24); border-radius: var(--radius-sm); }
.status-error { margin: 0; color: var(--color-danger); }
.danger { color: var(--color-danger); }
button { cursor: pointer; }
button:disabled { opacity: .55; cursor: not-allowed; }
@media (max-width: 800px) {
  .workspace { padding: 14px; }
  .workspace-header, .workspace-grid { align-items: flex-start; grid-template-columns: 1fr; }
  .workspace-header { flex-direction: column; }
}
</style>
