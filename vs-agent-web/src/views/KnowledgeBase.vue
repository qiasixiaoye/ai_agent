<template>
  <div class="kb-container">
    <div class="kb-header">
      <router-link to="/" class="back-link"><span>←</span> 返回首页</router-link>
      <h1>知识库管理</h1>
      <div class="header-actions">
        <button class="btn ghost" @click="refresh" :disabled="loadingList">⟳ 刷新</button>
        <button class="btn warn" @click="rebuildAll" :disabled="rebuilding">
          {{ rebuilding ? '重建中…' : '重建全量索引' }}
        </button>
      </div>
    </div>

    <!-- 上传 -->
    <section class="upload-card"
             @dragover.prevent="dragOver = true"
             @dragleave.prevent="dragOver = false"
             @drop.prevent="onDrop"
             :class="{ dragging: dragOver }">
      <div class="upload-inner">
        <div class="upload-icon">📄</div>
        <div class="upload-text">
          <strong>拖拽文件到此处</strong>，或
          <label class="upload-link">
            点此选择文件
            <input type="file" hidden @change="onFileChosen" />
          </label>
        </div>
        <div class="upload-meta">
          <input v-model="uploadSource" placeholder="来源（可选）" />
          <input v-model="uploadTags" placeholder="标签，逗号分隔（可选）" />
        </div>
        <div v-if="uploading" class="upload-status">上传并解析中…</div>
        <div v-if="lastUpload" class="upload-status success">
          已收到：{{ lastUpload.documentId }}（{{ lastUpload.status }}{{ lastUpload.duplicated ? ' / 已存在' : '' }}）
        </div>
        <div v-if="uploadError" class="upload-status fail">{{ uploadError }}</div>
      </div>
    </section>

    <!-- 列表 -->
    <section class="list-card">
      <div class="card-title">
        最近文档 ({{ docs.length }})
        <span v-if="loadingList" class="hint">加载中…</span>
      </div>
      <table v-if="docs.length > 0" class="kb-table">
        <thead>
          <tr>
            <th>文件名</th>
            <th>类型</th>
            <th>状态</th>
            <th>切片</th>
            <th>来源</th>
            <th>标签</th>
            <th>上传时间</th>
            <th class="actions-col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="d in docs" :key="d.documentId">
            <td class="file-cell" :title="d.documentId">{{ d.fileName }}</td>
            <td>{{ d.fileType || '-' }}</td>
            <td>
              <span :class="['status-badge', statusClass(d.status)]">{{ d.status }}</span>
              <div v-if="d.errorMessage" class="status-error" :title="d.errorMessage">⚠ {{ truncate(d.errorMessage, 32) }}</div>
            </td>
            <td>{{ d.chunkCount ?? 0 }}</td>
            <td>{{ d.source || '-' }}</td>
            <td>{{ d.tags || '-' }}</td>
            <td>{{ formatTime(d.uploadedAt) }}</td>
            <td class="actions-cell">
              <button class="btn-mini" @click="reprocess(d)">重新处理</button>
              <button class="btn-mini danger" @click="confirmDelete(d)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else-if="!loadingList" class="empty-hint">还没有文档，先用上面的上传区上传一份吧。</div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import {
  listKbDocuments,
  uploadKbDocument,
  deleteKbDocument,
  reprocessKbDocument,
  rebuildKbIndex
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

const refresh = async () => {
  loadingList.value = true
  try {
    docs.value = await listKbDocuments(50)
  } catch (e) {
    console.error(e)
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

const onFileChosen = (e) => {
  const f = e.target.files && e.target.files[0]
  if (f) doUpload(f)
  e.target.value = ''
}

const onDrop = (e) => {
  dragOver.value = false
  const f = e.dataTransfer.files && e.dataTransfer.files[0]
  if (f) doUpload(f)
}

const reprocess = async (d) => {
  try {
    await reprocessKbDocument(d.documentId)
    await refresh()
  } catch (e) {
    alert(e.message || '重处理失败')
  }
}

const confirmDelete = async (d) => {
  if (!confirm(`确认删除 ${d.fileName}？`)) return
  try {
    await deleteKbDocument(d.documentId)
    await refresh()
  } catch (e) {
    alert(e.message || '删除失败')
  }
}

const rebuildAll = async () => {
  if (!confirm('重建全量向量索引可能耗时较久，确认？')) return
  rebuilding.value = true
  try {
    await rebuildKbIndex()
    await refresh()
  } catch (e) {
    alert(e.message || '重建失败')
  } finally {
    rebuilding.value = false
  }
}

const formatTime = (t) => {
  if (!t) return '-'
  try { return new Date(t).toLocaleString() } catch { return t }
}

const truncate = (s, n) => (s && s.length > n ? s.slice(0, n) + '…' : s)

const statusClass = (s) => {
  if (!s) return ''
  const up = String(s).toUpperCase()
  if (up.includes('SUCCESS') || up.includes('READY') || up === 'PROCESSED') return 'ok'
  if (up.includes('FAIL') || up.includes('ERROR')) return 'fail'
  if (up.includes('PROCESS') || up.includes('PENDING')) return 'pending'
  return ''
}

onMounted(refresh)
</script>

<style scoped>
.kb-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fb;
  color: #222;
}
.kb-header {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  background: #2f8a4c;
  color: white;
}
.kb-header h1 { margin: 0 auto; font-size: 1.4rem; }
.back-link {
  color: white;
  text-decoration: none;
  display: flex;
  align-items: center;
}
.back-link span { font-size: 1.2rem; margin-right: 5px; }

.header-actions { display: flex; gap: 8px; }
.btn {
  border: none;
  border-radius: 4px;
  padding: 6px 12px;
  cursor: pointer;
  font-size: 13px;
}
.btn.ghost { background: rgba(255,255,255,0.18); color: white; border: 1px solid rgba(255,255,255,0.4); }
.btn.warn { background: #f0ad4e; color: white; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }

.upload-card {
  margin: 16px 20px 0;
  background: white;
  border: 2px dashed #cdd6e0;
  border-radius: 8px;
  padding: 18px;
  transition: border-color 0.15s, background 0.15s;
}
.upload-card.dragging { border-color: #2f8a4c; background: #f0fbf4; }
.upload-inner { display: flex; flex-direction: column; gap: 8px; align-items: center; }
.upload-icon { font-size: 30px; }
.upload-text strong { color: #2a3a55; }
.upload-link { color: #2f8a4c; cursor: pointer; text-decoration: underline; }
.upload-meta { display: flex; gap: 8px; margin-top: 6px; width: 100%; max-width: 540px; }
.upload-meta input {
  flex: 1; padding: 6px 8px; border: 1px solid #d0d7e2; border-radius: 4px; font-size: 13px;
}
.upload-status { font-size: 13px; color: #555; }
.upload-status.success { color: #1a7a1a; }
.upload-status.fail { color: #b01a1a; }

.list-card {
  flex: 1;
  margin: 16px 20px 20px;
  background: white;
  border: 1px solid #e5e8f0;
  border-radius: 8px;
  padding: 16px;
  overflow-y: auto;
}
.card-title { font-weight: 600; color: #2a3a55; margin-bottom: 12px; }
.hint { color: #8a96b3; font-size: 12px; margin-left: 6px; }

.kb-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.kb-table th, .kb-table td {
  padding: 8px 10px;
  text-align: left;
  border-bottom: 1px solid #eef0f5;
}
.kb-table th { background: #f7f9fc; color: #4a6fa5; font-weight: 600; }
.kb-table tbody tr:hover { background: #fafbfd; }
.file-cell { font-weight: 500; max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.actions-col { width: 160px; }
.actions-cell { display: flex; gap: 6px; }

.btn-mini {
  border: 1px solid #d0d7e2;
  background: white;
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 12px;
  cursor: pointer;
}
.btn-mini:hover { background: #f0f4fb; }
.btn-mini.danger { color: #b01a1a; border-color: #f3d2d2; }
.btn-mini.danger:hover { background: #fff5f5; }

.status-badge {
  display: inline-block;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  background: #e5e8f0;
  color: #4a6fa5;
}
.status-badge.ok { background: #e0f5e0; color: #1a7a1a; }
.status-badge.fail { background: #fde0e0; color: #b01a1a; }
.status-badge.pending { background: #fff3d0; color: #a07000; }
.status-error { color: #b01a1a; font-size: 11px; margin-top: 2px; }

.empty-hint { color: #8a96b3; text-align: center; padding: 40px 0; }
</style>
