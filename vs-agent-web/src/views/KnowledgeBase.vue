<template>
  <main class="kb-page">
    <header class="page-header">
      <router-link to="/" class="back-link">返回首页</router-link>
      <div>
        <p class="eyebrow">RAG Source</p>
        <h1>知识资料库</h1>
        <p>为 AI 助手的 RAG 问答准备资料；上传后的文档会解析、切块并写入 pgvector。</p>
      </div>
      <div class="header-actions">
        <button class="ghost-btn" @click="refresh" :disabled="loadingList">刷新</button>
        <button class="warn-btn" @click="rebuildAll" :disabled="rebuilding">
          {{ rebuilding ? '重建中' : '重建索引' }}
        </button>
      </div>
    </header>

    <section class="summary-grid">
      <article class="summary-card">
        <span>文档总数</span>
        <strong>{{ docs.length }}</strong>
      </article>
      <article class="summary-card">
        <span>已完成</span>
        <strong>{{ doneCount }}</strong>
      </article>
      <article class="summary-card">
        <span>处理中</span>
        <strong>{{ pendingCount }}</strong>
      </article>
      <article class="summary-card">
        <span>失败</span>
        <strong>{{ failedCount }}</strong>
      </article>
    </section>

    <section class="content-grid">
      <aside class="upload-panel">
        <div class="panel-title">上传资料</div>
        <div
          :class="['drop-zone', { active: dragOver }]"
          @dragover.prevent="dragOver = true"
          @dragleave.prevent="dragOver = false"
          @drop.prevent="onDrop"
        >
          <strong>拖入文件</strong>
          <span>支持 PDF、Word、文本等 Tika 可解析格式</span>
          <label class="file-button">
            选择文件
            <input type="file" hidden @change="onFileChosen" />
          </label>
        </div>

        <label>来源</label>
        <input v-model="uploadSource" placeholder="例如：产品手册 / 项目文档" />
        <label>标签</label>
        <input v-model="uploadTags" placeholder="逗号分隔，例如：rag,internal" />

        <div v-if="uploading" class="notice">正在上传并解析...</div>
        <div v-if="lastUpload" class="notice ok">
          {{ lastUpload.documentId }} 已接收，状态 {{ lastUpload.status }}
        </div>
        <div v-if="uploadError" class="notice fail">{{ uploadError }}</div>
      </aside>

      <section class="table-panel">
        <div class="panel-head">
          <div>
            <div class="panel-title">资料列表</div>
            <p>这些资料只在调用助手 RAG 入口时参与检索，不影响普通对话和工作流生成。</p>
          </div>
        </div>

        <div v-if="loadingList" class="empty-state">正在加载资料...</div>
        <div v-else-if="docs.length === 0" class="empty-state">还没有资料，先上传一份用于 RAG 验证。</div>
        <div v-else class="doc-list">
          <article v-for="d in docs" :key="d.documentId" class="doc-row">
            <div class="doc-main">
              <strong :title="d.documentId">{{ d.fileName }}</strong>
              <span>{{ d.source || '未设置来源' }} · {{ d.tags || '无标签' }}</span>
            </div>
            <div class="doc-meta">
              <span :class="['status-badge', statusClass(d.status)]">{{ d.status }}</span>
              <span>{{ d.chunkCount ?? 0 }} chunks</span>
              <span>{{ formatTime(d.uploadedAt) }}</span>
            </div>
            <div class="doc-actions">
              <button @click="reprocess(d)">重新处理</button>
              <button class="danger" @click="confirmDelete(d)">删除</button>
            </div>
            <p v-if="d.errorMessage" class="row-error">{{ d.errorMessage }}</p>
          </article>
        </div>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  deleteKbDocument,
  listKbDocuments,
  rebuildKbIndex,
  reprocessKbDocument,
  uploadKbDocument
} from '../services/api'

const docs = ref([])
const loadingList = ref(false)
const rebuilding = ref(false)
const uploading = ref(false)
const dragOver = ref(false)
const uploadSource = ref('')
const uploadTags = ref('')
const lastUpload = ref(null)
const uploadError = ref(null)

const doneCount = computed(() => docs.value.filter((d) => statusClass(d.status) === 'ok').length)
const pendingCount = computed(() => docs.value.filter((d) => statusClass(d.status) === 'pending').length)
const failedCount = computed(() => docs.value.filter((d) => statusClass(d.status) === 'fail').length)

const refresh = async () => {
  loadingList.value = true
  try {
    docs.value = await listKbDocuments(80)
  } finally {
    loadingList.value = false
  }
}

const doUpload = async (file) => {
  uploading.value = true
  uploadError.value = null
  lastUpload.value = null
  try {
    lastUpload.value = await uploadKbDocument(file, {
      source: uploadSource.value || undefined,
      tags: uploadTags.value || undefined
    })
    await refresh()
  } catch (e) {
    uploadError.value = e.message || '上传失败'
  } finally {
    uploading.value = false
  }
}

const onFileChosen = (event) => {
  const file = event.target.files && event.target.files[0]
  if (file) doUpload(file)
  event.target.value = ''
}

const onDrop = (event) => {
  dragOver.value = false
  const file = event.dataTransfer.files && event.dataTransfer.files[0]
  if (file) doUpload(file)
}

const reprocess = async (doc) => {
  await reprocessKbDocument(doc.documentId)
  await refresh()
}

const confirmDelete = async (doc) => {
  if (!confirm(`确认删除 ${doc.fileName}？`)) return
  await deleteKbDocument(doc.documentId)
  await refresh()
}

const rebuildAll = async () => {
  if (!confirm('确认重建所有文档的向量索引？')) return
  rebuilding.value = true
  try {
    await rebuildKbIndex()
    await refresh()
  } finally {
    rebuilding.value = false
  }
}

const statusClass = (status) => {
  const value = String(status || '').toUpperCase()
  if (value.includes('SUCCESS') || value.includes('READY') || value.includes('PROCESSED')) return 'ok'
  if (value.includes('FAIL') || value.includes('ERROR')) return 'fail'
  if (value.includes('PROCESS') || value.includes('PENDING')) return 'pending'
  return ''
}

const formatTime = (value) => {
  if (!value) return '-'
  try {
    return new Date(value).toLocaleString()
  } catch {
    return value
  }
}

onMounted(refresh)
</script>

<style scoped>
.kb-page {
  min-height: 100vh;
  background: #f6f8fb;
  color: #172033;
  padding: 24px;
}

.page-header,
.summary-grid,
.content-grid {
  max-width: 1240px;
  margin: 0 auto;
}

.page-header {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) auto;
  gap: 18px;
  align-items: start;
  padding-bottom: 18px;
  border-bottom: 1px solid #dbe3ef;
}

.back-link {
  color: #2563eb;
  text-decoration: none;
  font-weight: 700;
}

.eyebrow {
  margin: 0 0 6px;
  color: #15803d;
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  font-size: 32px;
}

.page-header p,
.panel-head p {
  margin: 8px 0 0;
  color: #64748b;
}

.header-actions {
  display: flex;
  gap: 8px;
}

button,
.file-button {
  min-height: 34px;
  border: 1px solid #cbd5e1;
  border-radius: 7px;
  background: #ffffff;
  color: #172033;
  padding: 0 12px;
  cursor: pointer;
}

.ghost-btn:hover,
.doc-actions button:hover {
  background: #f1f5f9;
}

.warn-btn {
  background: #1f7a4d;
  border-color: #1f7a4d;
  color: #ffffff;
}

button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 10px;
  margin-top: 18px;
}

.summary-card,
.upload-panel,
.table-panel {
  background: #ffffff;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  box-shadow: 0 8px 26px rgba(31, 45, 61, 0.05);
}

.summary-card {
  padding: 14px;
}

.summary-card span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.summary-card strong {
  display: block;
  margin-top: 8px;
  font-size: 24px;
}

.content-grid {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.upload-panel,
.table-panel {
  padding: 18px;
}

.panel-title {
  color: #172033;
  font-weight: 800;
  font-size: 17px;
}

.drop-zone {
  min-height: 164px;
  display: grid;
  place-items: center;
  gap: 7px;
  border: 1px dashed #94a3b8;
  border-radius: 8px;
  background: #f8fafc;
  margin: 14px 0;
  text-align: center;
  color: #64748b;
}

.drop-zone.active {
  border-color: #15803d;
  background: #ecfdf3;
}

.drop-zone strong {
  color: #172033;
}

.file-button {
  display: inline-flex;
  align-items: center;
  background: #15803d;
  border-color: #15803d;
  color: #ffffff;
  font-weight: 700;
}

label {
  display: block;
  margin: 10px 0 5px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

input {
  width: 100%;
  min-height: 36px;
  border: 1px solid #cbd5e1;
  border-radius: 7px;
  padding: 0 10px;
}

.notice {
  margin-top: 12px;
  color: #475569;
  font-size: 13px;
}

.notice.ok {
  color: #15803d;
}

.notice.fail,
.row-error {
  color: #b91c1c;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid #e2e8f0;
}

.empty-state {
  color: #64748b;
  text-align: center;
  padding: 72px 0;
}

.doc-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.doc-row {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto auto;
  gap: 14px;
  align-items: center;
  border: 1px solid #e2e8f0;
  border-left: 4px solid #15803d;
  border-radius: 8px;
  padding: 13px;
}

.doc-main strong,
.doc-main span {
  display: block;
}

.doc-main span {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}

.doc-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  color: #64748b;
  font-size: 12px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  border-radius: 6px;
  padding: 0 8px;
  background: #f1f5f9;
  color: #475569;
  font-weight: 700;
}

.status-badge.ok {
  background: #dcfce7;
  color: #166534;
}

.status-badge.pending {
  background: #fef3c7;
  color: #92400e;
}

.status-badge.fail {
  background: #fee2e2;
  color: #991b1b;
}

.doc-actions {
  display: flex;
  gap: 6px;
}

.doc-actions .danger {
  color: #b91c1c;
  border-color: #fecaca;
}

.row-error {
  grid-column: 1 / -1;
  margin: 0;
  font-size: 12px;
}

@media (max-width: 920px) {
  .page-header,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }

  .doc-row {
    grid-template-columns: 1fr;
  }

  .doc-meta,
  .doc-actions {
    flex-wrap: wrap;
  }
}
</style>
