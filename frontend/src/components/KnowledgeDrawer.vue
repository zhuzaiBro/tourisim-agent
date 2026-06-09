<template>
  <Transition name="drawer">
    <div v-if="open" class="drawer-overlay" @click.self="$emit('close')">
      <div class="drawer">

        <!-- ─── Header ─── -->
        <div class="drawer-header">
          <div class="dh-left">
            <span class="dh-gem">◆</span>
            <span class="dh-title">知识库管理</span>
          </div>
          <div class="dh-actions">
            <button class="dh-refresh" @click="loadCities" title="刷新">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M12 7A5 5 0 1 1 9.5 2.5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
                <path d="M9.5 1v2h2" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </button>
            <button class="dh-close" @click="$emit('close')">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M1 1l12 12M13 1L1 13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
            </button>
          </div>
        </div>

        <div class="drawer-body">

          <!-- ─── Add City Section ─── -->
          <div class="section">
            <button class="section-toggle" @click="showAddCity = !showAddCity">
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                <path d="M6.5 1v11M1 6.5h11" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
              新增城市
              <span class="toggle-arrow" :class="{ open: showAddCity }">›</span>
            </button>

            <Transition name="expand">
              <div v-if="showAddCity" class="add-city-form">
                <div class="form-row">
                  <div class="f-group">
                    <label>城市编码 <span class="req">*</span></label>
                    <input v-model="newCity.code" placeholder="如：beijing" class="f-input"
                      pattern="[a-z_]+" title="仅允许小写字母和下划线" />
                  </div>
                  <div class="f-group">
                    <label>中文名 <span class="req">*</span></label>
                    <input v-model="newCity.nameCn" placeholder="如：北京" class="f-input" />
                  </div>
                </div>
                <div class="form-row">
                  <div class="f-group">
                    <label>英文名</label>
                    <input v-model="newCity.nameEn" placeholder="如：Beijing" class="f-input" />
                  </div>
                  <div class="f-group">
                    <label>省份</label>
                    <input v-model="newCity.province" placeholder="如：北京市" class="f-input" />
                  </div>
                </div>
                <div class="f-group">
                  <label>城市简介</label>
                  <input v-model="newCity.description" placeholder="一句话描述这座城市" class="f-input" />
                </div>
                <div class="form-actions">
                  <button class="btn-secondary" @click="showAddCity = false">取消</button>
                  <button
                    class="btn-primary"
                    :disabled="!newCity.code || !newCity.nameCn || addingCity"
                    @click="handleAddCity"
                  >
                    <span v-if="addingCity" class="btn-loading"><i></i><i></i><i></i></span>
                    <span v-else>创建城市</span>
                  </button>
                </div>
              </div>
            </Transition>
          </div>

          <!-- ─── Init Qingdao shortcut ─── -->
          <div class="section init-section" v-if="!hasQingdao">
            <div class="init-banner">
              <div class="init-icon">🏖</div>
              <div class="init-text">
                <strong>系统初始化</strong>
                <p>首次使用？一键初始化青岛知识库（内置数据）开始体验</p>
              </div>
              <button class="btn-primary btn-sm" :disabled="initing" @click="handleInit">
                {{ initing ? '初始化中...' : '立即初始化' }}
              </button>
            </div>
          </div>

          <!-- ─── City List ─── -->
          <div class="section">
            <div class="section-title">
              已注册城市
              <span class="city-count">{{ cities.length }}</span>
            </div>

            <div v-if="loadingCities" class="cities-loading">
              <div class="skeleton" v-for="i in 3" :key="i"></div>
            </div>

            <div v-else-if="cities.length === 0" class="cities-empty">
              <p>尚未注册任何城市</p>
              <p>点击上方「新增城市」添加第一座城市</p>
            </div>

            <div v-else class="city-list">
              <div
                v-for="city in cities"
                :key="city.code"
                class="city-card"
                :class="{ expanded: expandedCity === city.code }"
              >
                <!-- City header row -->
                <div class="city-row" @click="toggleCity(city.code)">
                  <div class="city-info">
                    <div class="city-name">
                      {{ city.nameCn }}
                      <span class="city-code">{{ city.code }}</span>
                    </div>
                    <div class="city-province">{{ city.province }}</div>
                  </div>
                  <div class="city-badges">
                    <span class="badge" :class="city.knowledgeIngested ? 'badge-ok' : 'badge-pending'">
                      {{ city.knowledgeIngested ? '已摄入' : '未摄入' }}
                    </span>
                    <span class="badge" :class="city.enabled ? 'badge-enabled' : 'badge-off'">
                      {{ city.enabled ? '已启用' : '未启用' }}
                    </span>
                  </div>
                  <span class="expand-icon" :class="{ open: expandedCity === city.code }">›</span>
                </div>

                <!-- Expanded ingest panel -->
                <Transition name="expand">
                  <div v-if="expandedCity === city.code" class="ingest-panel">

                    <!-- Tab selector -->
                    <div class="ingest-tabs">
                      <button
                        v-for="tab in getTabs(city.code)"
                        :key="tab.id"
                        class="ingest-tab"
                        :class="{ active: activeTab[city.code] === tab.id }"
                        @click="activeTab[city.code] = tab.id"
                      >
                        {{ tab.label }}
                      </button>
                    </div>

                    <!-- Tab: Built-in (qingdao only) -->
                    <div v-if="activeTab[city.code] === 'builtin'" class="tab-content">
                      <p class="tab-desc">使用系统内置的青岛旅游知识数据（景点、美食、交通、住宿等）</p>
                      <button
                        class="btn-primary btn-wide"
                        :disabled="!!taskState[city.code]"
                        @click="handleBuiltin(city)"
                      >
                        <TaskIndicator :state="taskState[city.code]" label="摄入内置数据" />
                      </button>
                    </div>

                    <!-- Tab: File Upload -->
                    <div v-if="activeTab[city.code] === 'upload'" class="tab-content">
                      <p class="tab-desc">上传本地知识库文件，支持 .md / .txt / .pdf 格式</p>

                      <!-- Category selector -->
                      <div class="f-group">
                        <label>知识类别</label>
                        <select v-model="uploadCategory[city.code]" class="f-select">
                          <option value="knowledge">通用知识</option>
                          <option value="attraction">景点</option>
                          <option value="food">美食</option>
                          <option value="transport">交通</option>
                          <option value="accommodation">住宿</option>
                          <option value="festival">节庆</option>
                        </select>
                      </div>

                      <!-- Drop zone -->
                      <div
                        class="drop-zone"
                        :class="{ 'drag-over': dragOver[city.code], 'has-file': uploadFiles[city.code] }"
                        @dragenter.prevent="dragOver[city.code] = true"
                        @dragover.prevent="dragOver[city.code] = true"
                        @dragleave="dragOver[city.code] = false"
                        @drop.prevent="handleDrop($event, city.code)"
                        @click="triggerFileInput(city.code)"
                      >
                        <input
                          :ref="el => fileInputRefs[city.code] = el as HTMLInputElement"
                          type="file"
                          accept=".md,.txt,.pdf"
                          style="display:none"
                          @change="handleFileSelect($event, city.code)"
                        />
                        <div v-if="!uploadFiles[city.code]" class="dz-placeholder">
                          <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
                            <path d="M14 4v14M7 11l7-7 7 7" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                            <path d="M4 22h20" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                          </svg>
                          <p>点击选择文件或拖拽到此处</p>
                          <span>.md · .txt · .pdf</span>
                        </div>
                        <div v-else class="dz-file">
                          <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                            <path d="M4 2h9l5 5v11a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z"
                              stroke="currentColor" stroke-width="1.4"/>
                            <path d="M13 2v5h5" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/>
                          </svg>
                          <span class="file-name">{{ uploadFiles[city.code]?.name }}</span>
                          <span class="file-size">{{ formatSize(uploadFiles[city.code]?.size ?? 0) }}</span>
                          <button class="file-remove" @click.stop="uploadFiles[city.code] = null">×</button>
                        </div>
                      </div>

                      <button
                        class="btn-primary btn-wide"
                        :disabled="!uploadFiles[city.code] || !!taskState[city.code]"
                        @click="handleUpload(city)"
                      >
                        <TaskIndicator :state="taskState[city.code]" label="上传并摄入" />
                      </button>
                    </div>

                    <!-- Tab: Server path -->
                    <div v-if="activeTab[city.code] === 'path'" class="tab-content">
                      <p class="tab-desc">指定服务器上已存在的知识库文件路径</p>
                      <div class="f-group">
                        <label>文件路径</label>
                        <input
                          v-model="serverPath[city.code]"
                          class="f-input mono"
                          placeholder="/app/knowledge/beijing.md"
                        />
                      </div>
                      <div class="f-group">
                        <label>知识类别</label>
                        <select v-model="uploadCategory[city.code]" class="f-select">
                          <option value="knowledge">通用知识</option>
                          <option value="attraction">景点</option>
                          <option value="food">美食</option>
                          <option value="transport">交通</option>
                          <option value="accommodation">住宿</option>
                          <option value="festival">节庆</option>
                        </select>
                      </div>
                      <button
                        class="btn-primary btn-wide"
                        :disabled="!serverPath[city.code] || !!taskState[city.code]"
                        @click="handleServerPath(city)"
                      >
                        <TaskIndicator :state="taskState[city.code]" label="触发摄入" />
                      </button>
                    </div>

                    <!-- Tab: Database -->
                    <div v-if="activeTab[city.code] === 'db'" class="tab-content">
                      <p class="tab-desc">从 MySQL attraction 表中读取该城市的景点数据并向量化摄入</p>
                      <div class="db-info">
                        <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                          <circle cx="7" cy="7" r="5.5" stroke="currentColor" stroke-width="1.2"/>
                          <path d="M7 4.5v.5M7 7v3" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
                        </svg>
                        需要先在数据库 attraction 表中存有该城市的景点数据
                      </div>
                      <button
                        class="btn-primary btn-wide"
                        :disabled="!!taskState[city.code]"
                        @click="handleDatabase(city)"
                      >
                        <TaskIndicator :state="taskState[city.code]" label="从数据库摄入" />
                      </button>
                    </div>

                    <!-- Task result message -->
                    <div v-if="taskMsg[city.code]" class="task-msg" :class="taskState[city.code]">
                      {{ taskMsg[city.code] }}
                    </div>

                  </div>
                </Transition>
              </div>
            </div>
          </div>

        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, watch, reactive, computed } from 'vue'
import { ingestApi, cityManageApi, type CityAdmin } from '@/api'
import { ElMessage } from 'element-plus'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; refresh: [] }>()

// ── State ──────────────────────────────────────────────
const cities = ref<CityAdmin[]>([])
const loadingCities = ref(false)
const expandedCity = ref<string | null>(null)
const activeTab = reactive<Record<string, string>>({})
const taskState = reactive<Record<string, 'loading' | 'ok' | 'error' | null>>({})
const taskMsg = reactive<Record<string, string>>({})

// Add city form
const showAddCity = ref(false)
const addingCity = ref(false)
const newCity = ref({ code: '', nameCn: '', nameEn: '', province: '', description: '' })

// Init
const initing = ref(false)

// Upload
const uploadFiles = reactive<Record<string, File | null>>({})
const uploadCategory = reactive<Record<string, string>>({})
const serverPath = reactive<Record<string, string>>({})
const dragOver = reactive<Record<string, boolean>>({})
const fileInputRefs = reactive<Record<string, HTMLInputElement | null>>({})

const hasQingdao = computed(() => cities.value.some(c => c.code === 'qingdao'))

// ── Helpers ──────────────────────────────────────────
function getTabs(cityCode: string) {
  const tabs = [
    { id: 'upload', label: '📁 上传文件' },
    { id: 'path',   label: '🗂 服务器路径' },
    { id: 'db',     label: '🗄 数据库' },
  ]
  if (cityCode === 'qingdao') {
    tabs.unshift({ id: 'builtin', label: '⭐ 内置数据' })
  }
  return tabs
}

function toggleCity(code: string) {
  if (expandedCity.value === code) {
    expandedCity.value = null
  } else {
    expandedCity.value = code
    if (!activeTab[code]) {
      activeTab[code] = code === 'qingdao' ? 'builtin' : 'upload'
    }
    if (!uploadCategory[code]) uploadCategory[code] = 'knowledge'
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

// ── Load cities ───────────────────────────────────────
async function loadCities() {
  loadingCities.value = true
  try {
    cities.value = await cityManageApi.getAllCities()
  } catch {
    // If /all not available, fall back to enabled list
    try {
      const enabled = await cityManageApi.getAllCities()
      cities.value = enabled
    } catch {
      ElMessage.error('加载城市列表失败')
    }
  } finally {
    loadingCities.value = false
  }
}

watch(() => props.open, (val) => {
  if (val) loadCities()
})

// ── Task helpers ──────────────────────────────────────
function startTask(code: string) {
  taskState[code] = 'loading'
  taskMsg[code] = ''
}

function endTask(code: string, msg: string, ok = true) {
  taskState[code] = ok ? 'ok' : 'error'
  taskMsg[code] = msg
  setTimeout(() => { taskState[code] = null }, 8000)
  if (ok) {
    loadCities()
    emit('refresh')
  }
}

// ── Actions ───────────────────────────────────────────
async function handleInit() {
  initing.value = true
  try {
    await cityManageApi.initDefault()
    await ingestApi.ingestQingdao()
    ElMessage.success('青岛初始化已启动，知识库摄入进行中...')
    await loadCities()
    emit('refresh')
  } catch {
    // error already handled by interceptor
  } finally {
    initing.value = false
  }
}

async function handleAddCity() {
  addingCity.value = true
  try {
    await cityManageApi.addCity({ ...newCity.value })
    ElMessage.success(`城市 「${newCity.value.nameCn}」 添加成功`)
    newCity.value = { code: '', nameCn: '', nameEn: '', province: '', description: '' }
    showAddCity.value = false
    await loadCities()
  } catch {
    // handled by interceptor
  } finally {
    addingCity.value = false
  }
}

async function handleBuiltin(city: CityAdmin) {
  startTask(city.code)
  try {
    const res = await ingestApi.ingestQingdao()
    endTask(city.code, res.message)
  } catch {
    endTask(city.code, '摄入失败，请查看后端日志', false)
  }
}

async function handleUpload(city: CityAdmin) {
  const file = uploadFiles[city.code]
  if (!file) return
  startTask(city.code)
  try {
    const res = await ingestApi.uploadFile(file, city.code, uploadCategory[city.code] || 'knowledge')
    endTask(city.code, res.message)
    uploadFiles[city.code] = null
  } catch {
    endTask(city.code, '上传失败，请检查文件格式', false)
  }
}

async function handleServerPath(city: CityAdmin) {
  const path = serverPath[city.code]
  if (!path) return
  startTask(city.code)
  try {
    const res = await ingestApi.ingestCity({
      cityCode: city.code,
      sourceType: 'FILE',
      filePath: path,
    })
    endTask(city.code, res.message)
  } catch {
    endTask(city.code, '摄入失败，请确认路径存在且后端有读取权限', false)
  }
}

async function handleDatabase(city: CityAdmin) {
  startTask(city.code)
  try {
    const res = await ingestApi.ingestFromDb(city.code)
    endTask(city.code, res.message)
  } catch {
    endTask(city.code, '数据库摄入失败，请确认 attraction 表有数据', false)
  }
}

// File input
function triggerFileInput(code: string) {
  fileInputRefs[code]?.click()
}

function handleFileSelect(e: Event, code: string) {
  const input = e.target as HTMLInputElement
  if (input.files?.[0]) uploadFiles[code] = input.files[0]
}

function handleDrop(e: DragEvent, code: string) {
  dragOver[code] = false
  const file = e.dataTransfer?.files[0]
  if (!file) return
  const ok = ['.md', '.txt', '.pdf'].some(ext => file.name.toLowerCase().endsWith(ext))
  if (!ok) {
    ElMessage.warning('仅支持 .md / .txt / .pdf 格式')
    return
  }
  uploadFiles[code] = file
}
</script>

<!-- TaskIndicator: inline sub-component rendered via template ref -->
<script lang="ts">
import { defineComponent, h } from 'vue'
const TaskIndicator = defineComponent({
  props: { state: String, label: String },
  setup(props) {
    return () => {
      if (props.state === 'loading') {
        return h('span', { class: 'task-indicator loading' }, [
          h('span', { class: 'spin' }),
          '处理中...',
        ])
      }
      if (props.state === 'ok') return h('span', { class: 'task-indicator ok' }, '✓ 已启动')
      if (props.state === 'error') return h('span', { class: 'task-indicator error' }, '✗ 失败')
      return h('span', props.label)
    }
  },
})
export { TaskIndicator }
</script>

<style scoped>
/* ── Overlay + Drawer ─── */
.drawer-overlay {
  position: fixed;
  inset: 0;
  background: rgba(var(--forest-rgb),0.3);
  backdrop-filter: blur(2px);
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
}

.drawer {
  width: 560px;
  max-width: 92vw;
  height: 100%;
  background: var(--cream);
  display: flex;
  flex-direction: column;
  box-shadow: -8px 0 48px rgba(var(--forest-rgb),0.18);
}

.drawer-enter-active, .drawer-leave-active { transition: opacity 0.25s ease; }
.drawer-enter-active .drawer, .drawer-leave-active .drawer { transition: transform 0.25s ease; }
.drawer-enter-from, .drawer-leave-to { opacity: 0; }
.drawer-enter-from .drawer, .drawer-leave-to .drawer { transform: translateX(100%); }

/* ── Header ─── */
.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--cream-300);
  flex-shrink: 0;
  background: var(--forest);
}

.dh-left { display: flex; align-items: center; gap: 10px; }
.dh-gem { color: var(--gold); font-size: 10px; }

.dh-title {
  font-family: 'Cormorant Garamond', serif;
  font-size: 20px;
  font-weight: 500;
  color: #fff;
}

.dh-actions { display: flex; align-items: center; gap: 8px; }

.dh-refresh, .dh-close {
  width: 30px;
  height: 30px;
  background: rgba(255,255,255,0.1);
  border: 1px solid rgba(255,255,255,0.2);
  border-radius: 7px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255,255,255,0.7);
  transition: all 0.15s;
}

.dh-refresh:hover, .dh-close:hover {
  background: rgba(255,255,255,0.2);
  color: #fff;
}

/* ── Body ─── */
.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ── Section ─── */
.section {
  background: var(--white);
  border: 1.5px solid var(--cream-300);
  border-radius: 12px;
  overflow: hidden;
}

.section-toggle {
  width: 100%;
  padding: 14px 16px;
  background: none;
  border: none;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-2);
  cursor: pointer;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  transition: background 0.15s;
}

.section-toggle:hover { background: var(--cream); }

.toggle-arrow {
  margin-left: auto;
  font-size: 18px;
  color: var(--text-3);
  transition: transform 0.2s;
}

.toggle-arrow.open { transform: rotate(90deg); }

/* ── Init banner ─── */
.init-section { background: rgba(var(--accent-rgb),0.06); border-color: rgba(var(--accent-rgb),0.3); }

.init-banner {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
}

.init-icon { font-size: 28px; flex-shrink: 0; }

.init-text { flex: 1; }
.init-text strong { font-size: 14px; color: var(--text); }
.init-text p { font-size: 12px; color: var(--text-3); margin-top: 2px; line-height: 1.4; }

/* ── Section title ─── */
.section-title {
  padding: 14px 16px 10px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-3);
  display: flex;
  align-items: center;
  gap: 8px;
}

.city-count {
  background: var(--forest);
  color: #fff;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 8px;
}

/* ── Loading / empty ─── */
.cities-loading { padding: 8px 16px 16px; display: flex; flex-direction: column; gap: 8px; }

.skeleton {
  height: 52px;
  background: var(--cream);
  border-radius: 8px;
  animation: shimmer 1.4s infinite;
}

@keyframes shimmer {
  0%, 100% { opacity: 0.7; }
  50% { opacity: 1; }
}

.cities-empty {
  padding: 32px 16px;
  text-align: center;
  color: var(--text-3);
  font-size: 13px;
  line-height: 1.8;
}

/* ── City card ─── */
.city-list { padding: 0 8px 12px; display: flex; flex-direction: column; gap: 6px; }

.city-card {
  border: 1.5px solid var(--cream-300);
  border-radius: 10px;
  overflow: hidden;
  background: var(--cream);
  transition: border-color 0.15s;
}

.city-card.expanded { border-color: var(--forest); }

.city-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  cursor: pointer;
  transition: background 0.15s;
}

.city-row:hover { background: var(--cream-200); }

.city-info { flex: 1; min-width: 0; }

.city-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 6px;
}

.city-code {
  font-size: 11px;
  color: var(--text-3);
  font-family: 'SF Mono', monospace;
  background: var(--cream-200);
  padding: 1px 5px;
  border-radius: 4px;
}

.city-province { font-size: 11px; color: var(--text-3); margin-top: 1px; }

.city-badges { display: flex; gap: 4px; flex-shrink: 0; }

.badge {
  font-size: 10px;
  font-weight: 500;
  padding: 2px 7px;
  border-radius: 10px;
  border: 1px solid;
}

.badge-ok    { color: #2d7a4f; border-color: #a3d9b7; background: #f0faf4; }
.badge-pending { color: var(--text-3); border-color: var(--cream-300); background: var(--cream); }
.badge-enabled { color: var(--forest); border-color: rgba(var(--forest-rgb),0.25); background: rgba(var(--forest-rgb),0.06); }
.badge-off   { color: var(--text-3); border-color: var(--cream-300); background: var(--cream); }

.expand-icon {
  font-size: 18px;
  color: var(--text-3);
  flex-shrink: 0;
  transition: transform 0.2s;
}

.expand-icon.open { transform: rotate(90deg); }

/* ── Ingest panel ─── */
.ingest-panel { padding: 0 14px 16px; border-top: 1px solid var(--cream-300); }

.ingest-tabs {
  display: flex;
  gap: 4px;
  padding: 12px 0 10px;
  flex-wrap: wrap;
}

.ingest-tab {
  padding: 5px 12px;
  border: 1.5px solid var(--cream-300);
  border-radius: 8px;
  background: none;
  font-size: 12px;
  color: var(--text-2);
  cursor: pointer;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  transition: all 0.15s;
}

.ingest-tab.active {
  background: var(--forest);
  border-color: var(--forest);
  color: #fff;
}

.ingest-tab:not(.active):hover {
  border-color: var(--forest);
  color: var(--forest);
}

.tab-content { display: flex; flex-direction: column; gap: 12px; }

.tab-desc { font-size: 12.5px; color: var(--text-3); line-height: 1.5; }

.db-info {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-3);
  background: var(--cream);
  border: 1px solid var(--cream-300);
  border-radius: 7px;
  padding: 9px 12px;
}

/* ── Form elements ─── */
.add-city-form {
  padding: 0 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-top: 1px solid var(--cream-200);
}

.form-row { display: flex; gap: 10px; }
.form-row .f-group { flex: 1; }

.f-group { display: flex; flex-direction: column; gap: 5px; }

.f-group label {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--text-3);
}

.req { color: var(--earth-light); }

.f-input, .f-select {
  height: 38px;
  padding: 0 12px;
  border: 1.5px solid var(--cream-300);
  border-radius: 8px;
  background: var(--white);
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  font-size: 13.5px;
  color: var(--text);
  outline: none;
  transition: border-color 0.2s;
  -webkit-appearance: none;
}

.f-input.mono { font-family: 'SF Mono', 'Fira Code', monospace; font-size: 12.5px; }
.f-input::placeholder { color: var(--text-3); }
.f-input:focus, .f-select:focus { border-color: var(--forest); }
.f-select { cursor: pointer; }

/* ── Drop zone ─── */
.drop-zone {
  border: 2px dashed var(--cream-300);
  border-radius: 10px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.2s;
  background: var(--cream);
  text-align: center;
}

.drop-zone:hover, .drop-zone.drag-over {
  border-color: var(--forest);
  background: rgba(var(--forest-rgb),0.03);
}

.drop-zone.has-file { border-style: solid; border-color: var(--forest); }

.dz-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: var(--text-3);
}

.dz-placeholder svg { color: var(--text-3); opacity: 0.5; }
.dz-placeholder p { font-size: 13px; }
.dz-placeholder span { font-size: 11px; background: var(--cream-200); padding: 2px 8px; border-radius: 6px; }

.dz-file {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--forest);
}

.file-name { flex: 1; font-size: 13px; font-weight: 500; min-width: 0; text-overflow: ellipsis; overflow: hidden; white-space: nowrap; }
.file-size { font-size: 11px; color: var(--text-3); flex-shrink: 0; }

.file-remove {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  background: var(--cream-200);
  border: none;
  border-radius: 50%;
  font-size: 14px;
  line-height: 1;
  color: var(--text-3);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, color 0.15s;
}

.file-remove:hover { background: #fca5a5; color: #dc2626; }

/* ── Buttons ─── */
.btn-primary {
  height: 40px;
  background: var(--forest);
  color: #fff;
  border: none;
  border-radius: 9px;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 18px;
  transition: background 0.2s, opacity 0.2s;
}

.btn-primary:hover:not(:disabled) { background: var(--forest-600); }
.btn-primary:disabled { opacity: 0.45; cursor: not-allowed; }
.btn-primary.btn-wide { width: 100%; }
.btn-primary.btn-sm { height: 34px; font-size: 12.5px; padding: 0 14px; white-space: nowrap; }

.btn-secondary {
  height: 38px;
  background: transparent;
  color: var(--text-2);
  border: 1.5px solid var(--cream-300);
  border-radius: 9px;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  font-size: 13px;
  cursor: pointer;
  padding: 0 16px;
  transition: all 0.15s;
}

.btn-secondary:hover { border-color: var(--forest); color: var(--forest); }

.form-actions { display: flex; gap: 8px; justify-content: flex-end; padding-top: 4px; }

/* ── Task state ─── */
.task-msg {
  font-size: 12px;
  padding: 8px 12px;
  border-radius: 7px;
  line-height: 1.5;
}

.task-msg.ok    { background: #f0faf4; color: #2d7a4f; border: 1px solid #a3d9b7; }
.task-msg.error { background: #fef2f2; color: #dc2626; border: 1px solid #fca5a5; }

.btn-loading {
  display: flex;
  gap: 3px;
  align-items: center;
}

.btn-loading i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #fff;
  animation: ldot 1.2s infinite ease-in-out both;
}

.btn-loading i:nth-child(1) { animation-delay: -0.24s; }
.btn-loading i:nth-child(2) { animation-delay: -0.12s; }

@keyframes ldot {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

/* ── Expand transition ─── */
.expand-enter-active, .expand-leave-active { transition: max-height 0.3s ease, opacity 0.2s ease; overflow: hidden; }
.expand-enter-from, .expand-leave-to { max-height: 0; opacity: 0; }
.expand-enter-to, .expand-leave-from { max-height: 600px; opacity: 1; }
</style>

<style>
/* Task indicator global (used in defineComponent above) */
.task-indicator { display: inline-flex; align-items: center; gap: 6px; }
.task-indicator.ok { color: #a3d9b7; }
.task-indicator.error { color: #fca5a5; }
.task-indicator .spin {
  width: 12px; height: 12px;
  border: 1.5px solid rgba(255,255,255,0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  flex-shrink: 0;
}
@keyframes spin { to { transform: rotate(360deg); } }
</style>
