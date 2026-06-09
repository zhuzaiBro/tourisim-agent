<template>
  <div class="admin-shell">

    <!-- ─── Sidebar ─── -->
    <nav class="a-sidebar">
      <div class="a-logo">
        <span class="a-logo-mark">◆</span>
        <span class="a-logo-text">Voyage<em>OS</em></span>
      </div>

      <div class="a-nav">
        <button
          v-for="item in navItems"
          :key="item.id"
          class="a-nav-item"
          :class="{ active: activeSection === item.id }"
          @click="activeSection = item.id"
        >
          <component :is="item.icon" class="a-nav-icon" />
          <span>{{ item.label }}</span>
        </button>
      </div>

      <div class="a-sidebar-footer">
        <router-link to="/app" class="a-back-link">
          <IconArrowLeft />
          返回主界面
        </router-link>
      </div>
    </nav>

    <!-- ─── Main ─── -->
    <main class="a-main">

      <!-- ══ OVERVIEW ══ -->
      <section v-show="activeSection === 'overview'" class="a-section">
        <div class="a-section-head">
          <h1 class="a-title">数据大盘</h1>
          <div class="a-subtitle">实时运营指标概览</div>
        </div>

        <!-- Stat Cards -->
        <div class="stat-grid" v-if="stats">
          <div class="stat-card" v-for="card in statCards" :key="card.key">
            <div class="sc-icon-wrap" :style="{ '--hue': card.hue }">
              <component :is="card.icon" class="sc-icon" />
            </div>
            <div class="sc-body">
              <div class="sc-value">
                <AnimatedNumber :target="stats[card.key] ?? 0" />
              </div>
              <div class="sc-label">{{ card.label }}</div>
            </div>
            <div class="sc-trend" v-if="card.todayKey">
              <span class="sc-today">今日 +{{ stats[card.todayKey] ?? 0 }}</span>
            </div>
          </div>
        </div>

        <!-- Skeleton while loading -->
        <div class="stat-grid" v-else>
          <div class="stat-card skeleton" v-for="i in 4" :key="i">
            <div class="sk-icon"></div>
            <div class="sk-body">
              <div class="sk-num"></div>
              <div class="sk-label"></div>
            </div>
          </div>
        </div>

        <!-- Chart -->
        <div class="chart-card">
          <div class="chart-header">
            <span class="chart-title">近 7 日消息量趋势</span>
            <span class="chart-total" v-if="stats">本周 {{ stats.weekMessages ?? 0 }} 条</span>
          </div>
          <div class="chart-area" ref="chartRef">
            <svg
              v-if="dailyData.length"
              class="sparkline-svg"
              :viewBox="`0 0 ${chartW} 80`"
              preserveAspectRatio="none"
            >
              <defs>
                <linearGradient id="grad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#F59E0B" stop-opacity="0.25"/>
                  <stop offset="100%" stop-color="#F59E0B" stop-opacity="0"/>
                </linearGradient>
              </defs>
              <path :d="areaPath" fill="url(#grad)" />
              <path :d="linePath" fill="none" stroke="#F59E0B" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
              <circle
                v-for="(pt, i) in chartPoints"
                :key="i"
                :cx="pt.x"
                :cy="pt.y"
                r="2.5"
                fill="#F59E0B"
                class="chart-dot"
              />
            </svg>
            <div v-if="dailyData.length" class="chart-labels">
              <span v-for="d in dailyData" :key="d.date">{{ d.date }}</span>
            </div>
            <div v-if="!dailyData.length && !loadingStats" class="chart-empty">暂无数据</div>
          </div>
        </div>
      </section>

      <!-- ══ KNOWLEDGE BASE ══ -->
      <section v-show="activeSection === 'knowledge'" class="a-section">
        <div class="a-section-head">
          <h1 class="a-title">知识库管理</h1>
          <div class="a-subtitle">上传并管理各城市知识文档</div>
        </div>

        <!-- City cards with ingest status -->
        <div class="kb-cities">
          <div class="kb-city-card" v-for="city in adminCities" :key="city.code">
            <div class="kbc-status" :class="{ ingested: city.knowledgeIngested }"></div>
            <div class="kbc-info">
              <div class="kbc-name">{{ city.nameCn }}</div>
              <div class="kbc-meta">{{ city.nameEn }} · {{ city.province }}</div>
            </div>
            <div class="kbc-badge" :class="city.knowledgeIngested ? 'ok' : 'pending'">
              {{ city.knowledgeIngested ? '已摄入' : '未摄入' }}
            </div>
            <button
              class="kbc-action"
              @click="selectUploadCity(city.code)"
              :class="{ active: uploadCityCode === city.code }"
            >
              上传文件
            </button>
          </div>
          <div v-if="!adminCities.length" class="kb-no-cities">暂无城市，请先在城市管理中添加</div>
        </div>

        <!-- Upload zone -->
        <div class="upload-zone" :class="{ 'has-city': uploadCityCode, dragging: isDragging }">
          <div class="uz-inner"
            @dragover.prevent="isDragging = true"
            @dragleave="isDragging = false"
            @drop.prevent="handleDrop"
          >
            <div class="uz-icon">
              <IconUpload />
            </div>
            <div class="uz-title">
              <span v-if="uploadCityCode">上传至 <em>{{ getCityName(uploadCityCode) }}</em></span>
              <span v-else>选择城市后上传文档</span>
            </div>
            <div class="uz-desc">支持 .md .txt .pdf 文件，拖拽或点击选择</div>
            <input
              ref="fileInputRef"
              type="file"
              accept=".md,.txt,.pdf"
              multiple
              class="uz-file-input"
              @change="handleFileSelect"
            />
            <button class="uz-btn" :disabled="!uploadCityCode" @click="fileInputRef?.click()">
              选择文件
            </button>
          </div>
        </div>

        <!-- Category selector -->
        <div class="upload-options" v-if="uploadCityCode">
          <label class="uo-label">知识分类</label>
          <div class="uo-cats">
            <button
              v-for="cat in categories"
              :key="cat.value"
              class="uo-cat"
              :class="{ active: uploadCategory === cat.value }"
              @click="uploadCategory = cat.value"
            >{{ cat.label }}</button>
          </div>
        </div>

        <!-- Upload queue -->
        <div class="upload-queue" v-if="uploadQueue.length">
          <div class="uq-header">待上传文件</div>
          <div class="uq-item" v-for="(item, i) in uploadQueue" :key="i">
            <IconFile class="uq-icon" />
            <span class="uq-name">{{ item.file.name }}</span>
            <span class="uq-size">{{ formatSize(item.file.size) }}</span>
            <div class="uq-progress" v-if="item.status === 'uploading'">
              <div class="uq-bar" :style="{ width: item.progress + '%' }"></div>
            </div>
            <span class="uq-status" :class="item.status" v-else>
              {{ statusLabel(item.status) }}
            </span>
            <button class="uq-remove" @click="uploadQueue.splice(i, 1)" v-if="item.status !== 'uploading'">×</button>
          </div>
          <button class="upload-all-btn" :disabled="isUploading" @click="uploadAll">
            {{ isUploading ? '上传中...' : `上传全部 (${uploadQueue.filter(u => u.status === 'pending').length})` }}
          </button>
        </div>
      </section>

      <!-- ══ USERS ══ -->
      <section v-show="activeSection === 'users'" class="a-section">
        <div class="a-section-head">
          <h1 class="a-title">用户管理</h1>
          <div class="a-subtitle">共 {{ totalUsers }} 名注册用户</div>
        </div>

        <div class="user-table-wrap">
          <table class="user-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>用户名</th>
                <th>邮箱</th>
                <th>注册时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="u in userList" :key="u.id" class="user-row">
                <td class="mono">{{ u.id }}</td>
                <td>
                  <div class="user-cell">
                    <div class="user-avatar">{{ u.username.charAt(0).toUpperCase() }}</div>
                    <span>{{ u.username }}</span>
                  </div>
                </td>
                <td class="text-muted">{{ u.email }}</td>
                <td class="mono text-muted">{{ formatDate(u.createdAt) }}</td>
              </tr>
              <tr v-if="!userList.length">
                <td colspan="4" class="empty-row">暂无用户数据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- ══ CITIES ══ -->
      <section v-show="activeSection === 'cities'" class="a-section">
        <div class="a-section-head">
          <h1 class="a-title">城市管理</h1>
          <div class="a-subtitle">管理知识库覆盖城市</div>
        </div>

        <!-- Add City Form -->
        <div class="add-city-card">
          <div class="acc-header" @click="showAddCity = !showAddCity">
            <div class="acc-title">
              <span class="acc-plus">{{ showAddCity ? '−' : '+' }}</span>
              新增城市
            </div>
            <span class="acc-hint" v-if="!showAddCity">点击展开</span>
          </div>

          <Transition name="expand">
            <div v-if="showAddCity" class="acc-body">
              <div class="acc-grid">
                <div class="acc-field">
                  <label class="acc-label">城市编码 <span class="acc-req">*</span></label>
                  <input v-model="newCity.code" class="acc-input" placeholder="如：beijing（英文小写）" />
                  <span class="acc-tip">用于内部标识，创建后不可修改</span>
                </div>
                <div class="acc-field">
                  <label class="acc-label">中文名 <span class="acc-req">*</span></label>
                  <input v-model="newCity.nameCn" class="acc-input" placeholder="如：北京" />
                </div>
                <div class="acc-field">
                  <label class="acc-label">英文名</label>
                  <input v-model="newCity.nameEn" class="acc-input" placeholder="如：Beijing" />
                </div>
                <div class="acc-field">
                  <label class="acc-label">省份 / 直辖市</label>
                  <input v-model="newCity.province" class="acc-input" placeholder="如：北京市" />
                </div>
                <div class="acc-field acc-field-full">
                  <label class="acc-label">城市描述</label>
                  <input v-model="newCity.description" class="acc-input" placeholder="简短介绍这座城市" />
                </div>
              </div>
              <div class="acc-footer">
                <span class="acc-error" v-if="addCityError">{{ addCityError }}</span>
                <button class="acc-cancel" @click="resetAddCity">重置</button>
                <button class="acc-submit" :disabled="addingCity" @click="handleAddCity">
                  <span v-if="addingCity" class="acc-spinner"></span>
                  <span v-else>创建城市</span>
                </button>
              </div>
            </div>
          </Transition>
        </div>

        <!-- Cities Table -->
        <div class="city-table-wrap">
          <table class="user-table">
            <thead>
              <tr>
                <th>编码</th>
                <th>城市名</th>
                <th>省份</th>
                <th>知识库</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="city in allCities" :key="city.code" class="user-row">
                <td class="mono">{{ city.code }}</td>
                <td>
                  <div class="user-cell">
                    <div class="city-dot-sm" :class="{ ingested: city.knowledgeIngested }"></div>
                    {{ city.nameCn }}
                  </div>
                </td>
                <td class="text-muted">{{ city.province }}</td>
                <td>
                  <span class="kb-tag" :class="city.knowledgeIngested ? 'ok' : 'no'">
                    {{ city.knowledgeIngested ? '✓ 已摄入' : '— 未摄入' }}
                  </span>
                </td>
                <td>
                  <span class="status-tag" :class="city.enabled ? 'on' : 'off'">
                    {{ city.enabled ? '启用' : '停用' }}
                  </span>
                </td>
                <td>
                  <button class="action-btn" @click="toggleCity(city.code)">
                    {{ city.enabled ? '停用' : '启用' }}
                  </button>
                </td>
              </tr>
              <tr v-if="!allCities.length">
                <td colspan="6" class="empty-row">暂无城市数据，点击上方新增</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, h, defineComponent } from 'vue'
import api, { ingestApi } from '@/api'
import { ElMessage } from 'element-plus'

// ── Inline SVG icon components ──────────────────────────────
const IconGrid = defineComponent({ render: () => h('svg', { viewBox: '0 0 16 16', fill: 'none', 'stroke-width': 1.4 }, [
  h('rect', { x: 1, y: 1, width: 6, height: 6, rx: 1, stroke: 'currentColor' }),
  h('rect', { x: 9, y: 1, width: 6, height: 6, rx: 1, stroke: 'currentColor' }),
  h('rect', { x: 1, y: 9, width: 6, height: 6, rx: 1, stroke: 'currentColor' }),
  h('rect', { x: 9, y: 9, width: 6, height: 6, rx: 1, stroke: 'currentColor' }),
]) })

const IconBook = defineComponent({ render: () => h('svg', { viewBox: '0 0 16 16', fill: 'none', 'stroke-width': 1.4 }, [
  h('path', { d: 'M3 2h8a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z', stroke: 'currentColor' }),
  h('path', { d: 'M5 6h6M5 9h4', stroke: 'currentColor', 'stroke-linecap': 'round' }),
]) })

const IconUsers = defineComponent({ render: () => h('svg', { viewBox: '0 0 16 16', fill: 'none', 'stroke-width': 1.4 }, [
  h('circle', { cx: 6, cy: 5, r: 3, stroke: 'currentColor' }),
  h('path', { d: 'M1 14c0-2.761 2.239-4 5-4s5 1.239 5 4', stroke: 'currentColor', 'stroke-linecap': 'round' }),
  h('path', { d: 'M11 3c1.657 0 3 1.343 3 3s-1.343 3-3 3', stroke: 'currentColor', 'stroke-linecap': 'round' }),
  h('path', { d: 'M13 14c0-2-1-3.5-3-4', stroke: 'currentColor', 'stroke-linecap': 'round' }),
]) })

const IconMap = defineComponent({ render: () => h('svg', { viewBox: '0 0 16 16', fill: 'none', 'stroke-width': 1.4 }, [
  h('path', { d: 'M1 3.5l4.5-1.5 5 1.5 4.5-1.5v10l-4.5 1.5-5-1.5L1 13.5V3.5z', stroke: 'currentColor', 'stroke-linejoin': 'round' }),
  h('path', { d: 'M5.5 2v10M10.5 4v10', stroke: 'currentColor' }),
]) })

const IconArrowLeft = defineComponent({ render: () => h('svg', { viewBox: '0 0 16 16', fill: 'none', 'stroke-width': 1.5 }, [
  h('path', { d: 'M10 3L5 8l5 5', stroke: 'currentColor', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }),
]) })

const IconUpload = defineComponent({ render: () => h('svg', { viewBox: '0 0 32 32', fill: 'none', 'stroke-width': 1.5 }, [
  h('path', { d: 'M16 6v16M9 13l7-7 7 7', stroke: 'currentColor', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }),
  h('path', { d: 'M4 24v2a2 2 0 0 0 2 2h20a2 2 0 0 0 2-2v-2', stroke: 'currentColor', 'stroke-linecap': 'round' }),
]) })

const IconFile = defineComponent({ render: () => h('svg', { viewBox: '0 0 16 16', fill: 'none', 'stroke-width': 1.3 }, [
  h('path', { d: 'M4 1h5l4 4v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V2a1 1 0 0 1 1-1z', stroke: 'currentColor' }),
  h('path', { d: 'M9 1v4h4', stroke: 'currentColor' }),
]) })

// Stat icons
const IconMessageSq = defineComponent({ render: () => h('svg', { viewBox: '0 0 16 16', fill: 'none', 'stroke-width': 1.4 }, [
  h('path', { d: 'M2 2h12a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H9l-3 2-3-2H2a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z', stroke: 'currentColor' }),
]) })

const IconConvo = defineComponent({ render: () => h('svg', { viewBox: '0 0 16 16', fill: 'none', 'stroke-width': 1.4 }, [
  h('path', { d: 'M1 4h9a1 1 0 0 1 1 1v5H4l-3 2V5a1 1 0 0 1 1-1z', stroke: 'currentColor' }),
  h('path', { d: 'M11 7h3a1 1 0 0 1 1 1v4l-2-1h-4V8a1 1 0 0 1 1-1z', stroke: 'currentColor' }),
]) })

// ── Animated number component ────────────────────────────────
const AnimatedNumber = defineComponent({
  props: { target: Number },
  setup(props) {
    const display = ref(0)
    onMounted(() => {
      const end = props.target ?? 0
      if (end === 0) return
      const step = Math.ceil(end / 30)
      const timer = setInterval(() => {
        display.value = Math.min(display.value + step, end)
        if (display.value >= end) clearInterval(timer)
      }, 30)
    })
    return () => h('span', display.value.toLocaleString())
  },
})

// ── State ────────────────────────────────────────────────────
const activeSection = ref('overview')
const loadingStats = ref(false)
const stats = ref<any>(null)
const dailyData = ref<{ date: string; count: number }[]>([])
const userList = ref<any[]>([])
const totalUsers = ref(0)
const adminCities = ref<any[]>([])
const allCities = ref<any[]>([])

// Add city state
const showAddCity = ref(false)
const addingCity = ref(false)
const addCityError = ref('')
const newCity = ref({ code: '', nameCn: '', nameEn: '', province: '', description: '' })

// Upload state
const uploadCityCode = ref('')
const uploadCategory = ref('knowledge')
const isDragging = ref(false)
const isUploading = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
const uploadQueue = ref<{ file: File; status: 'pending' | 'uploading' | 'done' | 'error'; progress: number }[]>([])

// Chart
const chartW = 600
const chartRef = ref<HTMLElement | null>(null)
const chartPoints = computed(() => {
  if (!dailyData.value.length) return []
  const vals = dailyData.value.map(d => d.count)
  const maxV = Math.max(...vals, 1)
  return vals.map((v, i) => ({
    x: (i / (vals.length - 1)) * (chartW - 20) + 10,
    y: 70 - (v / maxV) * 60,
  }))
})

const linePath = computed(() => {
  const pts = chartPoints.value
  if (!pts.length) return ''
  return pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x},${p.y}`).join(' ')
})

const areaPath = computed(() => {
  const pts = chartPoints.value
  if (!pts.length) return ''
  const line = pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x},${p.y}`).join(' ')
  const lastX = pts[pts.length - 1].x
  const firstX = pts[0].x
  return `${line} L${lastX},80 L${firstX},80 Z`
})

// ── Nav ──────────────────────────────────────────────────────
const navItems = [
  { id: 'overview', label: '数据概览', icon: IconGrid },
  { id: 'knowledge', label: '知识库', icon: IconBook },
  { id: 'users', label: '用户', icon: IconUsers },
  { id: 'cities', label: '城市', icon: IconMap },
]

const statCards = [
  { key: 'totalUsers', label: '注册用户', icon: IconUsers, hue: '220', todayKey: null },
  { key: 'totalConversations', label: '累计对话', icon: IconConvo, hue: '160', todayKey: 'todayConversations' },
  { key: 'totalMessages', label: '消息总量', icon: IconMessageSq, hue: '40', todayKey: 'todayMessages' },
  { key: 'weekMessages', label: '本周消息', icon: IconMessageSq, hue: '280', todayKey: null },
]

const categories = [
  { value: 'knowledge', label: '综合知识' },
  { value: 'attraction', label: '景点' },
  { value: 'food', label: '美食' },
  { value: 'transport', label: '交通' },
  { value: 'accommodation', label: '住宿' },
  { value: 'festival', label: '节庆' },
]

// ── Lifecycle ────────────────────────────────────────────────
onMounted(async () => {
  await Promise.all([loadStats(), loadUsers(), loadCities()])
})

async function loadStats() {
  loadingStats.value = true
  try {
    const [statsRes, dailyRes] = await Promise.all([
      api.get('/admin/stats').then(r => r.data),
      api.get('/admin/stats/daily').then(r => r.data),
    ])
    stats.value = statsRes
    dailyData.value = dailyRes
  } catch { /* backend may not be running */ } finally {
    loadingStats.value = false
  }
}

async function loadUsers() {
  try {
    const res = await api.get('/admin/users').then(r => r.data)
    userList.value = res.items
    totalUsers.value = res.total
  } catch {}
}

async function loadCities() {
  try {
    const cities = await api.get('/admin/cities').then(r => r.data)
    allCities.value = cities
    adminCities.value = cities
  } catch {}
}

// ── Knowledge Upload ─────────────────────────────────────────
function selectUploadCity(code: string) {
  uploadCityCode.value = uploadCityCode.value === code ? '' : code
}

function getCityName(code: string) {
  return adminCities.value.find(c => c.code === code)?.nameCn ?? code
}

function handleDrop(e: DragEvent) {
  isDragging.value = false
  if (!uploadCityCode.value) { ElMessage.warning('请先选择城市'); return }
  const files = Array.from(e.dataTransfer?.files ?? [])
  addToQueue(files)
}

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  addToQueue(files)
  input.value = ''
}

function addToQueue(files: File[]) {
  const allowed = ['.md', '.txt', '.pdf']
  files.forEach(f => {
    if (!allowed.some(ext => f.name.toLowerCase().endsWith(ext))) {
      ElMessage.warning(`不支持的文件格式: ${f.name}`)
      return
    }
    uploadQueue.value.push({ file: f, status: 'pending', progress: 0 })
  })
}

async function uploadAll() {
  if (!uploadCityCode.value) { ElMessage.warning('请先选择城市'); return }
  const pending = uploadQueue.value.filter(u => u.status === 'pending')
  if (!pending.length) return
  isUploading.value = true
  for (const item of pending) {
    item.status = 'uploading'
    item.progress = 30
    try {
      await ingestApi.uploadFile(item.file, uploadCityCode.value, uploadCategory.value)
      item.progress = 100
      item.status = 'done'
    } catch {
      item.status = 'error'
    }
  }
  isUploading.value = false
  await loadCities()
  ElMessage.success('上传完成，正在处理知识库...')
}

async function handleAddCity() {
  addCityError.value = ''
  if (!newCity.value.code.trim()) { addCityError.value = '城市编码不能为空'; return }
  if (!/^[a-z0-9_]+$/.test(newCity.value.code)) { addCityError.value = '编码只能包含小写字母、数字和下划线'; return }
  if (!newCity.value.nameCn.trim()) { addCityError.value = '中文名不能为空'; return }
  addingCity.value = true
  try {
    await api.post('/cities', {
      code: newCity.value.code.trim(),
      nameCn: newCity.value.nameCn.trim(),
      nameEn: newCity.value.nameEn.trim() || undefined,
      province: newCity.value.province.trim() || undefined,
      description: newCity.value.description.trim() || undefined,
    })
    ElMessage.success(`城市「${newCity.value.nameCn}」创建成功`)
    resetAddCity()
    showAddCity.value = false
    await loadCities()
  } catch (e: any) {
    addCityError.value = e.response?.data?.message || '创建失败，请检查编码是否重复'
  } finally {
    addingCity.value = false
  }
}

function resetAddCity() {
  newCity.value = { code: '', nameCn: '', nameEn: '', province: '', description: '' }
  addCityError.value = ''
}

async function toggleCity(code: string) {
  try {
    await api.patch(`/admin/cities/${code}/toggle`)
    await loadCities()
  } catch {
    ElMessage.error('操作失败')
  }
}

// ── Helpers ──────────────────────────────────────────────────
function formatSize(bytes: number) {
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / 1024 / 1024).toFixed(1) + 'MB'
}

function formatDate(iso: string) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

function statusLabel(s: string) {
  return { pending: '待上传', done: '✓ 完成', error: '✗ 失败', uploading: '' }[s] ?? s
}
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Instrument+Serif:ital@0;1&family=JetBrains+Mono:wght@400;500&display=swap');

/* ── Shell ─────────────────────────────────────────────────── */
.admin-shell {
  display: flex;
  height: 100vh;
  background: #09090B;
  color: #E4E4E7;
  font-family: 'DM Sans', 'PingFang SC', system-ui, sans-serif;
  overflow: hidden;
}

/* ── Sidebar ─────────────────────────────────────────────────── */
.a-sidebar {
  width: 220px;
  flex-shrink: 0;
  background: #0D0D10;
  border-right: 1px solid #1C1C21;
  display: flex;
  flex-direction: column;
  padding: 24px 0 20px;
}

.a-logo {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 20px 28px;
  border-bottom: 1px solid #1C1C21;
  margin-bottom: 16px;
}

.a-logo-mark {
  color: #F59E0B;
  font-size: 11px;
}

.a-logo-text {
  font-family: 'Instrument Serif', serif;
  font-size: 18px;
  color: #FAFAFA;
  letter-spacing: 0.01em;
}

.a-logo-text em {
  font-style: italic;
  color: #F59E0B;
}

.a-nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 10px;
}

.a-nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: #71717A;
  font-size: 13.5px;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  text-align: left;
}

.a-nav-item:hover { background: #18181B; color: #A1A1AA; }

.a-nav-item.active {
  background: rgba(245,158,11,0.1);
  color: #F59E0B;
}

.a-nav-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.a-sidebar-footer {
  padding: 16px 10px 0;
  border-top: 1px solid #1C1C21;
  margin-top: 12px;
}

.a-back-link {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 7px;
  text-decoration: none;
  color: #52525B;
  font-size: 12.5px;
  transition: color 0.15s, background 0.15s;
}

.a-back-link:hover { color: #A1A1AA; background: #18181B; }

.a-back-link svg { width: 14px; height: 14px; }

/* ── Main ─────────────────────────────────────────────────── */
.a-main {
  flex: 1;
  overflow-y: auto;
  padding: 40px 48px;
  scrollbar-width: thin;
  scrollbar-color: #27272A transparent;
}

/* ── Section ─────────────────────────────────────────────── */
.a-section-head {
  margin-bottom: 32px;
}

.a-title {
  font-family: 'Instrument Serif', serif;
  font-size: 28px;
  font-weight: 400;
  color: #FAFAFA;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.a-subtitle {
  font-size: 13px;
  color: #52525B;
  margin-top: 4px;
  letter-spacing: 0.01em;
}

/* ── Stat Grid ─────────────────────────────────────────── */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

@media (max-width: 1100px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } }

.stat-card {
  background: #111113;
  border: 1px solid #1C1C21;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: border-color 0.2s;
}

.stat-card:hover { border-color: #27272A; }

.sc-icon-wrap {
  width: 36px;
  height: 36px;
  border-radius: 9px;
  background: hsl(var(--hue), 70%, 15%);
  border: 1px solid hsl(var(--hue), 60%, 22%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: hsl(var(--hue), 80%, 65%);
}

.sc-icon { width: 16px; height: 16px; }

.sc-body { flex: 1; }

.sc-value {
  font-family: 'JetBrains Mono', monospace;
  font-size: 28px;
  font-weight: 500;
  color: #FAFAFA;
  line-height: 1;
  letter-spacing: -0.03em;
}

.sc-label {
  font-size: 12px;
  color: #52525B;
  margin-top: 5px;
  letter-spacing: 0.03em;
  text-transform: uppercase;
}

.sc-trend { }

.sc-today {
  font-size: 11px;
  color: #22C55E;
  background: rgba(34,197,94,0.1);
  border: 1px solid rgba(34,197,94,0.2);
  border-radius: 4px;
  padding: 2px 7px;
  font-family: 'JetBrains Mono', monospace;
}

/* Skeleton cards */
.stat-card.skeleton {
  animation: pulse-bg 1.6s ease-in-out infinite;
}

@keyframes pulse-bg {
  0%, 100% { border-color: #1C1C21; }
  50% { border-color: #27272A; }
}

.sk-icon {
  width: 36px; height: 36px;
  border-radius: 9px;
  background: #18181B;
}

.sk-body { display: flex; flex-direction: column; gap: 8px; }

.sk-num {
  width: 80px; height: 28px;
  border-radius: 5px;
  background: #18181B;
}

.sk-label {
  width: 60px; height: 12px;
  border-radius: 3px;
  background: #18181B;
}

/* ── Chart ─────────────────────────────────────────── */
.chart-card {
  background: #111113;
  border: 1px solid #1C1C21;
  border-radius: 12px;
  padding: 20px 24px;
  margin-bottom: 0;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.chart-title {
  font-size: 13px;
  font-weight: 500;
  color: #A1A1AA;
  letter-spacing: 0.03em;
  text-transform: uppercase;
}

.chart-total {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #F59E0B;
}

.chart-area {
  position: relative;
}

.sparkline-svg {
  width: 100%;
  height: 80px;
  display: block;
}

.chart-dot {
  transition: r 0.15s;
}

.chart-dot:hover { r: 4; }

.chart-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
}

.chart-labels span {
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
  color: #3F3F46;
}

.chart-empty {
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #3F3F46;
}

/* ── Knowledge Base ─────────────────────────────────── */
.kb-cities {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 10px;
  margin-bottom: 24px;
}

.kb-city-card {
  background: #111113;
  border: 1px solid #1C1C21;
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  transition: border-color 0.15s;
}

.kb-city-card:hover { border-color: #27272A; }

.kbc-status {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #27272A;
  flex-shrink: 0;
  transition: background 0.2s, box-shadow 0.2s;
}

.kbc-status.ingested {
  background: #22C55E;
  box-shadow: 0 0 6px rgba(34,197,94,0.4);
}

.kbc-info { flex: 1; min-width: 0; }

.kbc-name {
  font-size: 14px;
  font-weight: 500;
  color: #E4E4E7;
}

.kbc-meta {
  font-size: 11px;
  color: #52525B;
  margin-top: 2px;
}

.kbc-badge {
  font-size: 11px;
  border-radius: 4px;
  padding: 2px 7px;
  flex-shrink: 0;
}

.kbc-badge.ok { background: rgba(34,197,94,0.1); color: #22C55E; border: 1px solid rgba(34,197,94,0.2); }
.kbc-badge.pending { background: rgba(113,113,122,0.1); color: #71717A; border: 1px solid #27272A; }

.kbc-action {
  font-size: 11px;
  background: #18181B;
  border: 1px solid #27272A;
  border-radius: 5px;
  color: #71717A;
  padding: 4px 10px;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s;
  font-family: inherit;
}

.kbc-action:hover,
.kbc-action.active { background: rgba(245,158,11,0.1); border-color: rgba(245,158,11,0.3); color: #F59E0B; }

.kb-no-cities {
  font-size: 13px;
  color: #3F3F46;
  padding: 16px;
}

/* Upload zone */
.upload-zone {
  background: #111113;
  border: 1.5px dashed #27272A;
  border-radius: 12px;
  margin-bottom: 16px;
  transition: border-color 0.2s, background 0.2s;
}

.upload-zone.dragging,
.upload-zone.has-city:hover { border-color: rgba(245,158,11,0.4); background: rgba(245,158,11,0.03); }

.uz-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 36px 24px;
  gap: 8px;
  text-align: center;
}

.uz-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: #18181B;
  border: 1px solid #27272A;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #52525B;
  margin-bottom: 4px;
}

.uz-icon svg { width: 24px; height: 24px; }

.uz-title {
  font-size: 14px;
  font-weight: 500;
  color: #A1A1AA;
}

.uz-title em { font-style: normal; color: #F59E0B; }

.uz-desc {
  font-size: 12px;
  color: #3F3F46;
}

.uz-file-input { display: none; }

.uz-btn {
  margin-top: 8px;
  background: #18181B;
  border: 1px solid #27272A;
  border-radius: 7px;
  color: #A1A1AA;
  font-size: 13px;
  font-family: inherit;
  padding: 8px 20px;
  cursor: pointer;
  transition: all 0.15s;
}

.uz-btn:not(:disabled):hover { background: rgba(245,158,11,0.1); border-color: rgba(245,158,11,0.3); color: #F59E0B; }
.uz-btn:disabled { opacity: 0.35; cursor: not-allowed; }

/* Upload options */
.upload-options {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.uo-label {
  font-size: 12px;
  color: #52525B;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  white-space: nowrap;
}

.uo-cats { display: flex; gap: 6px; flex-wrap: wrap; }

.uo-cat {
  font-size: 12px;
  background: #18181B;
  border: 1px solid #27272A;
  border-radius: 5px;
  color: #71717A;
  padding: 4px 10px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.15s;
}

.uo-cat:hover { color: #A1A1AA; }
.uo-cat.active { background: rgba(245,158,11,0.1); border-color: rgba(245,158,11,0.3); color: #F59E0B; }

/* Queue */
.upload-queue {
  background: #111113;
  border: 1px solid #1C1C21;
  border-radius: 10px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.uq-header {
  font-size: 12px;
  color: #52525B;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding-bottom: 4px;
  border-bottom: 1px solid #1C1C21;
}

.uq-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.uq-icon { width: 14px; height: 14px; color: #52525B; flex-shrink: 0; }

.uq-name { flex: 1; color: #E4E4E7; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.uq-size { font-family: 'JetBrains Mono', monospace; font-size: 11px; color: #52525B; }

.uq-progress {
  flex: 1;
  max-width: 100px;
  height: 3px;
  background: #27272A;
  border-radius: 2px;
  overflow: hidden;
}

.uq-bar {
  height: 100%;
  background: #F59E0B;
  transition: width 0.3s;
  border-radius: 2px;
}

.uq-status {
  font-size: 11px;
  font-family: 'JetBrains Mono', monospace;
}

.uq-status.done { color: #22C55E; }
.uq-status.error { color: #EF4444; }
.uq-status.pending { color: #52525B; }

.uq-remove {
  width: 18px;
  height: 18px;
  background: none;
  border: none;
  color: #52525B;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 3px;
  transition: color 0.15s;
}

.uq-remove:hover { color: #EF4444; }

.upload-all-btn {
  align-self: flex-end;
  margin-top: 4px;
  background: rgba(245,158,11,0.1);
  border: 1px solid rgba(245,158,11,0.3);
  border-radius: 7px;
  color: #F59E0B;
  font-size: 13px;
  font-family: inherit;
  padding: 8px 18px;
  cursor: pointer;
  transition: all 0.15s;
}

.upload-all-btn:hover:not(:disabled) { background: rgba(245,158,11,0.18); }
.upload-all-btn:disabled { opacity: 0.45; cursor: not-allowed; }

/* ── User Table ─────────────────────────────────────── */
.user-table-wrap,
.city-table-wrap {
  background: #111113;
  border: 1px solid #1C1C21;
  border-radius: 12px;
  overflow: hidden;
}

.user-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.user-table th {
  text-align: left;
  padding: 12px 16px;
  font-size: 11px;
  font-weight: 500;
  color: #52525B;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid #1C1C21;
  background: #0D0D10;
}

.user-row td {
  padding: 12px 16px;
  color: #E4E4E7;
  border-bottom: 1px solid #141418;
}

.user-row:last-child td { border-bottom: none; }
.user-row:hover td { background: rgba(245,158,11,0.03); }

.user-cell {
  display: flex;
  align-items: center;
  gap: 9px;
}

.user-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: rgba(245,158,11,0.15);
  border: 1px solid rgba(245,158,11,0.2);
  color: #F59E0B;
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.mono {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #52525B !important;
}

.text-muted { color: #71717A !important; }

.empty-row {
  text-align: center;
  color: #3F3F46 !important;
  padding: 32px !important;
}

/* City table specific */
.city-dot-sm {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #27272A;
  flex-shrink: 0;
}

.city-dot-sm.ingested {
  background: #22C55E;
  box-shadow: 0 0 5px rgba(34,197,94,0.35);
}

.kb-tag {
  font-size: 11px;
  font-family: 'JetBrains Mono', monospace;
  border-radius: 4px;
  padding: 2px 7px;
}

.kb-tag.ok { background: rgba(34,197,94,0.1); color: #22C55E; }
.kb-tag.no { background: #18181B; color: #52525B; }

.status-tag {
  font-size: 11px;
  border-radius: 4px;
  padding: 2px 7px;
}

.status-tag.on { background: rgba(34,197,94,0.1); color: #22C55E; }
.status-tag.off { background: #18181B; color: #52525B; }

.action-btn {
  font-size: 12px;
  background: #18181B;
  border: 1px solid #27272A;
  border-radius: 5px;
  color: #71717A;
  padding: 4px 10px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.15s;
}

.action-btn:hover { border-color: rgba(245,158,11,0.3); color: #F59E0B; background: rgba(245,158,11,0.08); }

/* ── Add City Form ─────────────────────────────────── */
.add-city-card {
  background: #111113;
  border: 1px solid #1C1C21;
  border-radius: 12px;
  margin-bottom: 16px;
  overflow: hidden;
}

.acc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}

.acc-header:hover { background: #16161A; }

.acc-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13.5px;
  font-weight: 500;
  color: #A1A1AA;
}

.acc-plus {
  width: 20px;
  height: 20px;
  border-radius: 5px;
  background: rgba(245,158,11,0.12);
  border: 1px solid rgba(245,158,11,0.2);
  color: #F59E0B;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  flex-shrink: 0;
}

.acc-hint {
  font-size: 11px;
  color: #3F3F46;
}

.acc-body {
  padding: 0 18px 18px;
  border-top: 1px solid #1C1C21;
}

.acc-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-top: 16px;
}

.acc-field-full { grid-column: 1 / -1; }

.acc-field {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.acc-label {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: #52525B;
}

.acc-req { color: #F59E0B; }

.acc-input {
  width: 100%;
  height: 38px;
  background: #0D0D10;
  border: 1px solid #27272A;
  border-radius: 7px;
  padding: 0 11px;
  font-size: 13px;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  color: #E4E4E7;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.acc-input::placeholder { color: #3F3F46; }

.acc-input:focus {
  border-color: rgba(245,158,11,0.4);
  box-shadow: 0 0 0 3px rgba(245,158,11,0.05);
}

.acc-tip {
  font-size: 11px;
  color: #3F3F46;
  margin-top: 1px;
}

.acc-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #1C1C21;
}

.acc-error {
  flex: 1;
  font-size: 12px;
  color: #FCA5A5;
}

.acc-cancel {
  background: none;
  border: 1px solid #27272A;
  border-radius: 7px;
  color: #52525B;
  font-size: 12.5px;
  font-family: inherit;
  padding: 7px 14px;
  cursor: pointer;
  transition: all 0.15s;
}

.acc-cancel:hover { border-color: #3F3F46; color: #71717A; }

.acc-submit {
  background: rgba(245,158,11,0.12);
  border: 1px solid rgba(245,158,11,0.3);
  border-radius: 7px;
  color: #F59E0B;
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  padding: 7px 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 7px;
  transition: all 0.15s;
}

.acc-submit:hover:not(:disabled) { background: rgba(245,158,11,0.2); }
.acc-submit:disabled { opacity: 0.4; cursor: not-allowed; }

.acc-spinner {
  width: 13px;
  height: 13px;
  border: 1.5px solid rgba(245,158,11,0.3);
  border-top-color: #F59E0B;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

/* Expand transition */
.expand-enter-active,
.expand-leave-active {
  transition: max-height 0.25s ease, opacity 0.2s ease;
  max-height: 400px;
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  max-height: 0;
  opacity: 0;
}
</style>
