<template>
  <div class="skills-container">
    <div class="skills-header">
      <router-link to="/" class="back-link"><span>←</span> 返回首页</router-link>
      <h1>Skill 平台</h1>
      <button class="reload-btn" @click="refresh" :disabled="loadingList">⟳ 刷新</button>
    </div>

    <div class="skills-body">
      <!-- 左侧：Skill 列表 -->
      <aside class="skills-list">
        <div class="list-title">已注册 Skill ({{ skills.length }})</div>
        <div v-if="loadingList" class="hint">加载中...</div>
        <div v-else-if="skills.length === 0" class="hint">暂无已注册 Skill。请确认后端启动并扫描到 SKILL.md。</div>
        <ul v-else>
          <li
            v-for="s in skills"
            :key="s.name"
            :class="['skill-item', { active: current && current.name === s.name }]"
            @click="select(s.name)"
          >
            <div class="skill-item-title">{{ s.displayName || s.name }}</div>
            <div class="skill-item-desc">{{ s.description }}</div>
            <div class="skill-item-meta">
              <span class="badge">{{ s.sourceType }}</span>
              <span class="badge" v-if="s.version">v{{ s.version }}</span>
              <span class="badge tag" v-for="t in (s.tags || [])" :key="t">#{{ t }}</span>
            </div>
          </li>
        </ul>
      </aside>

      <!-- 右侧：详情 + 执行 -->
      <section class="skills-detail">
        <div v-if="!current" class="empty">
          <div class="empty-icon">🧩</div>
          <p>从左侧选一个 Skill 开始</p>
        </div>

        <template v-else>
          <div class="detail-head">
            <h2>{{ current.displayName || current.name }}</h2>
            <code class="skill-name-code">{{ current.name }}</code>
            <p class="detail-desc">{{ current.description }}</p>
            <div class="detail-meta">
              <span class="badge">{{ current.sourceType }}</span>
              <span class="badge" v-if="current.version">v{{ current.version }}</span>
              <span class="badge" v-if="current.timeoutMs">timeout {{ current.timeoutMs }}ms</span>
              <span class="badge tag" v-for="t in (current.tags || [])" :key="t">#{{ t }}</span>
            </div>
          </div>

          <!-- 输入参数表单 -->
          <div class="form-card">
            <div class="card-title">输入参数</div>
            <div v-if="!current.inputs || current.inputs.length === 0" class="hint">此 Skill 不需要参数。</div>
            <div v-else class="form-grid">
              <div v-for="p in current.inputs" :key="p.name" class="form-row">
                <label>
                  <span class="param-name">{{ p.name }}</span>
                  <span class="param-required" v-if="p.required">*</span>
                  <span class="param-type">{{ p.type }}</span>
                </label>
                <textarea
                  v-if="p.type === 'string'"
                  v-model="formData[p.name]"
                  :placeholder="p.description"
                  rows="2"
                ></textarea>
                <input
                  v-else
                  v-model="formData[p.name]"
                  :placeholder="p.description"
                />
                <div class="param-desc">{{ p.description }}</div>
              </div>
            </div>
            <div class="actions">
              <button class="execute-btn" :disabled="executing" @click="execute">
                {{ executing ? '执行中...' : '执行 Skill' }}
              </button>
            </div>
          </div>

          <!-- 执行结果 -->
          <div class="result-card" v-if="result || error">
            <div class="card-title">执行结果</div>
            <div v-if="error" class="result-fail">
              <strong>调用失败：</strong>{{ error }}
            </div>
            <div v-else>
              <div class="result-summary">
                <span :class="['result-badge', result.success ? 'ok' : 'fail']">
                  {{ result.success ? '成功' : '失败' }}
                </span>
                <span class="result-elapsed">耗时 {{ result.elapsedMs }} ms</span>
              </div>
              <div v-if="!result.success" class="result-fail">
                <strong>错误：</strong>{{ result.errorMessage }}
              </div>
              <pre v-else class="result-data">{{ JSON.stringify(result.data, null, 2) }}</pre>
            </div>
          </div>

          <!-- 示例 -->
          <div class="examples-card" v-if="current.examples && current.examples.length > 0">
            <div class="card-title">调用示例</div>
            <ul>
              <li v-for="(ex, i) in current.examples" :key="i">{{ ex }}</li>
            </ul>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listSkills, getSkill, executeSkill } from '../services/api'

const skills = ref([])
const current = ref(null)
const formData = reactive({})
const result = ref(null)
const error = ref(null)
const loadingList = ref(false)
const executing = ref(false)

const refresh = async () => {
  loadingList.value = true
  try {
    skills.value = await listSkills()
  } catch (e) {
    error.value = e.message
  } finally {
    loadingList.value = false
  }
}

const select = async (name) => {
  result.value = null
  error.value = null
  Object.keys(formData).forEach(k => delete formData[k])
  try {
    const detail = await getSkill(name)
    current.value = detail
    // 初始化表单值
    for (const p of (detail.inputs || [])) {
      formData[p.name] = p.defaultValue ?? ''
    }
  } catch (e) {
    error.value = e.message
  }
}

const execute = async () => {
  if (!current.value) return
  executing.value = true
  error.value = null
  result.value = null
  try {
    const args = {}
    for (const p of (current.value.inputs || [])) {
      const v = formData[p.name]
      if (v !== undefined && v !== '') {
        args[p.name] = p.type === 'int' || p.type === 'number'
          ? Number(v)
          : (p.type === 'boolean' ? Boolean(v) : v)
      }
    }
    result.value = await executeSkill(current.value.name, args)
  } catch (e) {
    error.value = e.message
  } finally {
    executing.value = false
  }
}

onMounted(refresh)
</script>

<style scoped>
.skills-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f5f7fb;
  color: #222;
}

.skills-header {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  background-color: #4a6fa5;
  color: white;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.08);
}

.skills-header h1 {
  margin: 0 auto;
  font-size: 1.4rem;
}

.back-link {
  color: white;
  text-decoration: none;
  display: flex;
  align-items: center;
}

.back-link span { font-size: 1.2rem; margin-right: 5px; }

.reload-btn {
  background: rgba(255, 255, 255, 0.2);
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 4px;
  padding: 4px 10px;
  cursor: pointer;
}
.reload-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.skills-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.skills-list {
  width: 320px;
  min-width: 280px;
  background: white;
  border-right: 1px solid #e5e8f0;
  overflow-y: auto;
  padding: 12px;
}

.list-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: #4a6fa5;
}

.skills-list ul { list-style: none; padding: 0; margin: 0; }

.skill-item {
  padding: 10px 12px;
  border-radius: 6px;
  margin-bottom: 6px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: background 0.15s, border-color 0.15s;
}
.skill-item:hover { background: #f0f4fb; }
.skill-item.active {
  background: #e7f0ff;
  border-color: #4a6fa5;
}

.skill-item-title {
  font-weight: 600;
  color: #2a3a55;
}
.skill-item-desc {
  font-size: 12px;
  color: #6c7a99;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.skill-item-meta {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.badge {
  display: inline-block;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  background: #e5e8f0;
  color: #4a6fa5;
}
.badge.tag { background: #fff0e0; color: #b06000; }

.skills-detail {
  flex: 1;
  overflow-y: auto;
  padding: 20px 28px;
}

.empty {
  text-align: center;
  padding-top: 120px;
  color: #8a96b3;
}
.empty-icon { font-size: 56px; }

.detail-head { margin-bottom: 18px; }
.detail-head h2 { margin: 0; }
.skill-name-code {
  display: inline-block;
  background: #f0f2f7;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  color: #4a6fa5;
  margin-top: 4px;
}
.detail-desc { margin: 8px 0; color: #4a4a4a; }
.detail-meta { display: flex; gap: 6px; flex-wrap: wrap; }

.form-card, .result-card, .examples-card {
  background: white;
  border: 1px solid #e5e8f0;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}

.card-title {
  font-weight: 600;
  margin-bottom: 12px;
  color: #2a3a55;
}

.form-grid { display: flex; flex-direction: column; gap: 14px; }
.form-row label {
  display: flex;
  gap: 6px;
  align-items: baseline;
  font-size: 13px;
  color: #444;
  margin-bottom: 4px;
}
.param-name { font-weight: 600; color: #2a3a55; }
.param-required { color: #d33; }
.param-type {
  font-family: monospace;
  font-size: 11px;
  background: #f0f2f7;
  padding: 1px 5px;
  border-radius: 3px;
  color: #4a6fa5;
}
.param-desc {
  font-size: 12px;
  color: #8a96b3;
  margin-top: 4px;
}
.form-row input,
.form-row textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 10px;
  border: 1px solid #d0d7e2;
  border-radius: 6px;
  font-size: 14px;
  font-family: inherit;
}
.form-row textarea { resize: vertical; min-height: 50px; }

.actions { margin-top: 14px; text-align: right; }
.execute-btn {
  background: #4a6fa5;
  color: white;
  border: none;
  border-radius: 6px;
  padding: 8px 18px;
  font-size: 14px;
  cursor: pointer;
}
.execute-btn:hover:not(:disabled) { background: #3a5a85; }
.execute-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.result-summary { margin-bottom: 8px; display: flex; gap: 10px; align-items: center; }
.result-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
.result-badge.ok { background: #e0f5e0; color: #1a7a1a; }
.result-badge.fail { background: #fde0e0; color: #b01a1a; }
.result-elapsed { color: #8a96b3; font-size: 12px; }
.result-fail { color: #b01a1a; font-size: 13px; }
.result-data {
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 6px;
  padding: 12px;
  overflow-x: auto;
  font-size: 12.5px;
  line-height: 1.5;
  max-height: 360px;
  overflow-y: auto;
}

.examples-card ul { padding-left: 18px; margin: 0; }
.examples-card li { margin: 4px 0; color: #4a4a4a; }

.hint { color: #8a96b3; font-size: 13px; }
</style>
