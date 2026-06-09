<template>
  <div class="chat-layout layout-with-sidebar" :class="{ 'sidebar-open': sidebarOpen }">
    <div v-if="sidebarOpen" class="sidebar-backdrop" @click="closeSidebar" />

    <!-- ─── Sidebar ──────────────────────────────────────── -->
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <!-- Brand -->
      <div class="sb-brand">
        <div class="term-window-dots">
          <span class="dot red"></span>
          <span class="dot yellow"></span>
          <span class="dot green"></span>
        </div>
        <span class="sb-prompt">~/voyage</span>
        <span class="term-prompt">$</span>
      </div>

      <!-- City Selector -->
      <div class="sb-section">
        <div class="sb-section-label"><span class="term-comment">dest</span></div>
        <CitySelector />
      </div>

      <!-- Knowledge category filter -->
      <div class="sb-section">
        <div class="sb-section-label"><span class="term-comment">rag.filter</span></div>
        <div class="sb-category-filters">
          <button
            v-for="opt in categoryOptions"
            :key="opt.value || 'all'"
            class="sb-cat-btn"
            :class="{ active: chatStore.categoryFilter === opt.value }"
            @click="chatStore.categoryFilter = opt.value"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>

      <!-- Weather Panel -->
      <WeatherPanel :cities="cityStore.selectedCities" />

      <!-- 智能行程入口 -->
      <div class="sb-planner-entry" @click="$router.push('/itinerary')">
        <div class="spe-icon">
          <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
            <circle cx="6.5" cy="6.5" r="5.5" stroke="currentColor" stroke-width="1.2"/>
            <path d="M6.5 1v2M6.5 10v2M1 6.5h2M10 6.5h2" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
            <circle cx="6.5" cy="6.5" r="1.5" fill="currentColor"/>
          </svg>
        </div>
        <div class="spe-text">
          <span class="spe-label">./plan --agent</span>
          <span class="spe-sub">weather · route · food</span>
        </div>
        <svg class="spe-arrow" width="10" height="10" viewBox="0 0 10 10" fill="none">
          <path d="M3 2l4 3-4 3" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>

      <!-- Sessions -->
      <div class="sb-sessions">
        <div class="sb-section-header">
          <span class="sb-section-label"><span class="term-comment">sessions</span></span>
          <button class="sb-new-btn" @click="handleNewSession" title="新建对话">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M7 1v12M1 7h12" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
            </svg>
          </button>
        </div>

        <div class="sb-session-list">
          <div
            v-for="session in chatStore.sessions"
            :key="session.id"
            class="sb-session-item"
            :class="{ active: session.id === chatStore.currentSessionId }"
            @click="chatStore.switchSession(session.id)"
          >
            <svg class="sb-session-icon" width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M2 2h10a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H8l-2 2-2-2H2a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z"
                stroke="currentColor" stroke-width="1.2"/>
            </svg>
            <span class="sb-session-title">{{ session.title }}</span>
            <button
              class="sb-delete-btn"
              @click.stop="chatStore.deleteSession(session.id)"
              title="删除"
            >
              <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                <path d="M2 2l8 8M10 2l-8 8" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
              </svg>
            </button>
          </div>

          <div v-if="!chatStore.initialized" class="sb-empty">
            <p>加载中...</p>
          </div>
          <div v-else-if="chatStore.sessions.length === 0" class="sb-empty">
            <p>暂无对话记录</p>
            <p>选择城市后开始提问</p>
          </div>
        </div>
      </div>

      <!-- Admin entry: only shown for admin role -->
      <div class="sb-admin-entry" v-if="authStore.isAdmin">
        <router-link to="/admin" class="sb-admin-link">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <rect x="1" y="1" width="4" height="4" rx="1" stroke="currentColor" stroke-width="1.2"/>
            <rect x="7" y="1" width="4" height="4" rx="1" stroke="currentColor" stroke-width="1.2"/>
            <rect x="1" y="7" width="4" height="4" rx="1" stroke="currentColor" stroke-width="1.2"/>
            <rect x="7" y="7" width="4" height="4" rx="1" stroke="currentColor" stroke-width="1.2"/>
          </svg>
          管理后台
        </router-link>
      </div>

      <!-- Footer: user info + logout -->
      <div class="sb-footer">
        <div class="sb-user">
          <div class="sb-user-avatar">{{ authStore.user?.username?.charAt(0)?.toUpperCase() || '?' }}</div>
          <div class="sb-user-info">
            <div class="sb-user-name">{{ authStore.user?.username }}</div>
            <div class="sb-user-email">{{ authStore.user?.email }}</div>
          </div>
        </div>
        <button class="sb-logout-btn" @click="handleLogout" title="退出登录">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M5 2H2a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h3M9 10l3-3-3-3M13 7H5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
      </div>
    </aside>

    <!-- ─── Main Content ──────────────────────────────────── -->
    <div class="main">
      <!-- Top bar -->
      <header class="main-header">
        <button class="mobile-menu-btn" aria-label="打开菜单" @click="toggleSidebar">
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
            <path d="M2 4.5h14M2 9h14M2 13.5h10" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
          </svg>
        </button>
        <div class="mh-left">
          <div v-if="cityStore.selectedCities.length > 0" class="mh-cities">
            <span
              v-for="code in cityStore.selectedCities"
              :key="code"
              class="mh-city-tag"
            >
              {{ cityStore.displayName(code) }}
              <button @click="cityStore.toggleCity(code)" class="mh-city-remove">×</button>
            </span>
            <span v-if="cityStore.selectedCities.length > 1" class="mh-multi-badge">联游</span>
          </div>
          <span v-else class="mh-hint"><span class="term-green">$</span> select --city &lt;code&gt;</span>
        </div>

        <div class="mh-right">
          <!-- Stream toggle -->
          <label class="stream-toggle" title="流式输出（打字机效果）">
            <input type="checkbox" v-model="chatStore.useStream" />
            <span class="toggle-track"><span class="toggle-thumb"></span></span>
            <span class="toggle-label">流式</span>
          </label>

          <!-- Map toggle -->
          <button
            class="mh-icon-btn"
            :class="{ active: showMap }"
            @click="showMap = !showMap"
            title="地图"
          >
            <svg width="15" height="15" viewBox="0 0 15 15" fill="none">
              <path d="M1 3l4-1.5 5 1.5 4-1.5v10l-4 1.5-5-1.5-4 1.5V3z"
                stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/>
              <path d="M5 1.5v10M10 3v10" stroke="currentColor" stroke-width="1.3"/>
            </svg>
            <span class="btn-label">地图</span>
          </button>

          <!-- Knowledge base -->
          <button
            class="mh-icon-btn"
            :class="{ active: showKnowledge }"
            @click="showKnowledge = true"
            title="知识库管理"
          >
            <svg width="15" height="15" viewBox="0 0 15 15" fill="none">
              <rect x="1" y="1" width="13" height="13" rx="2" stroke="currentColor" stroke-width="1.3"/>
              <path d="M4 5h7M4 7.5h7M4 10h4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
            </svg>
            <span class="btn-label">知识库</span>
          </button>

          <!-- Notes -->
          <button
            class="mh-icon-btn"
            :class="{ active: showNotes }"
            @click="showNotes = true"
            title="笔记本"
          >
            <svg width="15" height="15" viewBox="0 0 15 15" fill="none">
              <path d="M3 1.5h9a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1v-10a1 1 0 0 1 1-1z" stroke="currentColor" stroke-width="1.3"/>
              <path d="M5 5h5M5 7.5h5M5 10h3" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
            </svg>
            <span class="btn-label">笔记</span>
            <span v-if="notesStore.notes.length" class="mh-badge">{{ notesStore.notes.length }}</span>
          </button>

          <!-- Notebook -->
          <button
            class="mh-icon-btn"
            :class="{ active: showNotebook }"
            @click="showNotebook = true"
            title="收藏夹"
          >
            <svg width="15" height="15" viewBox="0 0 15 15" fill="none">
              <path d="M3 1h9a1 1 0 0 1 1 1v12l-4.5-2.5L4 14V2a1 1 0 0 1 1-1z"
                stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/>
            </svg>
            <span class="btn-label">收藏</span>
            <span v-if="favStore.items.length" class="mh-badge">{{ favStore.items.length }}</span>
          </button>

          <!-- Export -->
          <div class="export-menu" v-if="chatStore.currentSession">
            <button class="mh-icon-btn" @click="showExport = !showExport">
              <svg width="15" height="15" viewBox="0 0 15 15" fill="none">
                <path d="M7.5 1v9M4 6.5l3.5 3.5L11 6.5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M1 11.5v1a1 1 0 0 0 1 1h11a1 1 0 0 0 1-1v-1" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
              </svg>
              导出
            </button>
            <div class="export-dropdown" v-if="showExport" v-click-outside="() => showExport = false">
              <button @click="doExportMd">📄 Markdown 文件</button>
              <button @click="doExportPrint">🖨 打印 / PDF</button>
            </div>
          </div>

          <!-- Clear -->
          <button class="mh-clear-btn" @click="chatStore.clearCurrentSession()" title="清空当前对话">
            <svg width="15" height="15" viewBox="0 0 15 15" fill="none">
              <path d="M2 4h11M5 4V2.5a.5.5 0 0 1 .5-.5h4a.5.5 0 0 1 .5.5V4m2 0-.8 8.5a.5.5 0 0 1-.5.5H4.3a.5.5 0 0 1-.5-.5L3 4"
                stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            清空
          </button>
        </div>
      </header>

      <!-- Content row: chat + optional map -->
      <div class="content-row">
        <!-- Chat area -->
        <main class="main-chat">
          <ChatWindow
            @quick-question="handleQuickSend"
            @regenerate="chatStore.regenerateMessage($event)"
          />
        </main>

        <!-- Map panel -->
        <Transition name="map-slide">
          <div v-if="showMap" class="map-wrapper">
            <MapPanel :cities="cityStore.selectedCities" @close="showMap = false" />
          </div>
        </Transition>
      </div>

      <!-- Input area -->
      <footer class="main-footer">
        <TripPlannerForm @send="handleSend" />
      </footer>
    </div>

    <!-- Notes panel -->
    <NotesPanel :open="showNotes" @close="showNotes = false" />

    <!-- Notebook drawer -->
    <NotebookDrawer :open="showNotebook" @close="showNotebook = false" />

    <!-- Knowledge base drawer -->
    <KnowledgeDrawer
      :open="showKnowledge"
      @close="showKnowledge = false"
      @refresh="cityStore.fetchCities()"
    />

  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useMobileSidebar } from '@/composables/useMobileSidebar'
import { useRouter } from 'vue-router'
import { useCityStore } from '@/stores/city'
import { useChatStore } from '@/stores/chat'
import { useFavoritesStore } from '@/stores/favorites'
import { useNotesStore } from '@/stores/notes'
import { useAuthStore } from '@/stores/auth'
import { exportAsMarkdown, printChat } from '@/utils/exportChat'
import CitySelector from '@/components/CitySelector.vue'
import ChatWindow from '@/components/ChatWindow.vue'
import TripPlannerForm from '@/components/TripPlannerForm.vue'
import WeatherPanel from '@/components/WeatherPanel.vue'
import MapPanel from '@/components/MapPanel.vue'
import NotebookDrawer from '@/components/NotebookDrawer.vue'
import KnowledgeDrawer from '@/components/KnowledgeDrawer.vue'
import NotesPanel from '@/components/NotesPanel.vue'
import type { TravelPreferences } from '@/types'

const router = useRouter()
const cityStore = useCityStore()
const chatStore = useChatStore()
const favStore = useFavoritesStore()
const notesStore = useNotesStore()
const authStore = useAuthStore()
const { sidebarOpen, toggleSidebar, closeSidebar } = useMobileSidebar()

const showMap = ref(false)
const showNotebook = ref(false)
const showKnowledge = ref(false)
const showNotes = ref(false)
const showExport = ref(false)

const categoryOptions = [
  { label: '全部', value: undefined },
  { label: '景点', value: 'attraction' },
  { label: '美食', value: 'food' },
  { label: '交通', value: 'transport' },
] as const

onMounted(async () => {
  await cityStore.fetchCities()
  await chatStore.init()
  await Promise.all([favStore.fetchAll(), notesStore.fetchAll()])
})

function handleNewSession() {
  chatStore.createSession(cityStore.selectedCities)
}

function handleLogout() {
  authStore.logout()
  chatStore.reset()
  router.push('/login')
}

async function handleSend(payload: { question: string; preferences?: TravelPreferences }) {
  await chatStore.sendMessage(
    payload.question,
    cityStore.selectedCities,
    payload.preferences,
    chatStore.categoryFilter
  )
}

async function handleQuickSend(question: string) {
  await chatStore.sendMessage(question, cityStore.selectedCities, undefined, chatStore.categoryFilter)
}

function doExportMd() {
  if (chatStore.currentSession) {
    exportAsMarkdown(chatStore.currentSession)
    showExport.value = false
  }
}

function doExportPrint() {
  if (chatStore.currentSession) {
    printChat(chatStore.currentSession)
    showExport.value = false
  }
}

// v-click-outside directive
const vClickOutside = {
  mounted(el: HTMLElement, binding: any) {
    el._clickOutside = (e: Event) => {
      if (!el.contains(e.target as Node)) binding.value(e)
    }
    document.addEventListener('click', el._clickOutside)
  },
  unmounted(el: HTMLElement) {
    document.removeEventListener('click', el._clickOutside)
  },
}
</script>

<style scoped>
/* ── Layout ─────────────────────────────────────────── */
.chat-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--cream);
}

/* ── Sidebar ─────────────────────────────────────────── */
.sidebar {
  width: 260px;
  flex-shrink: 0;
  background: linear-gradient(180deg, #1a1014 0%, #120c0f 100%);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-right: 2px solid var(--forest);
  box-shadow: inset -1px 0 0 rgba(137, 57, 77, 0.3);
}

.sb-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(137, 57, 77, 0.4);
  flex-shrink: 0;
  background: rgba(0, 0, 0, 0.25);
}

.sb-prompt {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-on-theme);
  letter-spacing: 0.02em;
}

.sb-section {
  padding: 14px 20px 12px;
  border-bottom: 1px solid rgba(255,255,255,0.07);
  flex-shrink: 0;
}

.sb-section-label {
  font-size: 11px;
  letter-spacing: 0.04em;
  color: var(--forest-400);
  margin-bottom: 10px;
}

.sb-category-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.sb-cat-btn {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: var(--radius);
  border: 1px solid rgba(137, 57, 77, 0.5);
  background: rgba(0, 0, 0, 0.2);
  color: rgba(250, 246, 240, 0.55);
  cursor: pointer;
  transition: all 0.15s;
}

.sb-cat-btn:hover {
  border-color: var(--forest-400);
  color: var(--term-green);
}

.sb-cat-btn.active {
  background: rgba(137, 57, 77, 0.35);
  border-color: var(--term-green);
  color: var(--term-green);
  font-weight: 600;
}

.sb-sessions {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 14px 0 0;
  min-height: 0;
}

.sb-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px 10px;
  flex-shrink: 0;
}

.sb-new-btn {
  width: 24px;
  height: 24px;
  background: rgba(255,255,255,0.08);
  border: none;
  border-radius: 6px;
  color: rgba(255,255,255,0.6);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s, color 0.2s;
}

.sb-new-btn:hover { background: rgba(255,255,255,0.14); color: #fff; }

.sb-session-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 10px 10px;
}

.sb-session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: 8px;
  cursor: pointer;
  color: rgba(255,255,255,0.5);
  transition: background 0.15s, color 0.15s;
  position: relative;
}

.sb-session-item:hover,
.sb-session-item.active {
  background: rgba(255,255,255,0.09);
  color: rgba(255,255,255,0.9);
}

.sb-session-icon { flex-shrink: 0; opacity: 0.6; }

.sb-session-title {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sb-delete-btn {
  opacity: 0;
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  background: rgba(255,255,255,0.1);
  border: none;
  border-radius: 4px;
  color: rgba(255,255,255,0.5);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.15s, background 0.15s;
}

.sb-session-item:hover .sb-delete-btn { opacity: 1; }
.sb-delete-btn:hover { background: rgba(220,80,80,0.3); color: #ff8a8a; }

.sb-empty {
  padding: 24px 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  text-align: center;
}

.sb-empty p { font-size: 12px; color: rgba(255,255,255,0.25); line-height: 1.5; }

.sb-footer {
  padding: 12px 16px;
  border-top: 1px solid rgba(255,255,255,0.07);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.sb-user {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.sb-user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(255,255,255,0.15);
  color: rgba(255,255,255,0.9);
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.sb-user-info {
  min-width: 0;
  flex: 1;
}

.sb-user-name {
  font-size: 13px;
  font-weight: 500;
  color: rgba(255,255,255,0.85);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sb-user-email {
  font-size: 11px;
  color: rgba(255,255,255,0.35);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sb-logout-btn {
  width: 30px;
  height: 30px;
  background: rgba(255,255,255,0.07);
  border: none;
  border-radius: 6px;
  color: rgba(255,255,255,0.4);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.2s, color 0.2s;
}

.sb-logout-btn:hover {
  background: rgba(255,80,80,0.2);
  color: rgba(255,120,120,0.9);
}

.sb-admin-entry {
  padding: 0 10px 8px;
}

.sb-admin-link {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 10px;
  border-radius: 7px;
  text-decoration: none;
  font-size: 12px;
  color: rgba(255,255,255,0.28);
  transition: background 0.15s, color 0.15s;
  letter-spacing: 0.02em;
}

.sb-admin-link:hover {
  background: rgba(255,255,255,0.06);
  color: rgba(255,255,255,0.55);
}

/* ── Main ─────────────────────────────────────────────── */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

/* Header */
.main-header {
  height: 44px;
  flex-shrink: 0;
  background: var(--cream-200);
  border-bottom: 1px solid var(--cream-300);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  gap: 12px;
  box-shadow: inset 0 -1px 0 rgba(137, 57, 77, 0.15);
}

.mh-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.mh-cities {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.mh-city-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px 2px 10px;
  background: rgba(137, 57, 77, 0.25);
  color: var(--term-green);
  border: 1px solid var(--forest);
  border-radius: var(--radius);
  font-size: 11px;
  font-weight: 500;
}

.mh-city-remove {
  background: none;
  border: none;
  color: rgba(255,255,255,0.5);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 0;
  transition: color 0.15s;
}

.mh-city-remove:hover { color: rgba(255,255,255,0.9); }

.mh-multi-badge {
  font-size: 10px;
  letter-spacing: 0.08em;
  color: var(--gold);
  border: 1px solid var(--gold);
  padding: 2px 7px;
  border-radius: 10px;
}

.mh-hint { font-size: 12px; color: var(--text-3); }

.mh-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* Custom toggle */
.stream-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  user-select: none;
}

.stream-toggle input { display: none; }

.toggle-track {
  position: relative;
  width: 32px;
  height: 18px;
  background: var(--cream-300);
  border-radius: 9px;
  transition: background 0.2s;
}

.stream-toggle input:checked + .toggle-track { background: var(--forest); }

.toggle-thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 12px;
  height: 12px;
  background: #fff;
  border-radius: 50%;
  transition: transform 0.2s;
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
}

.stream-toggle input:checked + .toggle-track .toggle-thumb { transform: translateX(14px); }

.toggle-label { font-size: 12px; color: var(--text-3); }

/* Icon buttons (map, notebook, export) */
.mh-icon-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: 1px solid var(--cream-300);
  border-radius: 7px;
  padding: 5px 9px;
  font-size: 12px;
  color: var(--text-3);
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s, background 0.2s;
  position: relative;
}

.mh-icon-btn:hover,
.mh-icon-btn.active {
  border-color: var(--forest);
  color: var(--forest);
  background: rgba(var(--forest-rgb),0.05);
}

.mh-badge {
  position: absolute;
  top: -5px;
  right: -5px;
  background: var(--earth-light);
  color: #fff;
  font-size: 9px;
  font-weight: 600;
  width: 15px;
  height: 15px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Export dropdown */
.export-menu { position: relative; }

.export-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  background: var(--white);
  border: 1.5px solid var(--cream-300);
  border-radius: 10px;
  box-shadow: var(--shadow-md);
  overflow: hidden;
  z-index: 100;
  min-width: 160px;
}

.export-dropdown button {
  display: block;
  width: 100%;
  padding: 10px 16px;
  text-align: left;
  background: none;
  border: none;
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
  transition: background 0.15s;
}

.export-dropdown button:hover { background: var(--cream-200); }

.mh-clear-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  background: none;
  border: 1px solid var(--cream-300);
  border-radius: 7px;
  padding: 5px 10px;
  font-size: 12px;
  color: var(--text-3);
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s;
}

.mh-clear-btn:hover { border-color: var(--text-3); color: var(--text-2); }

/* Content row (chat + map) */
.content-row {
  flex: 1;
  display: flex;
  overflow: hidden;
  min-height: 0;
}

.main-chat {
  flex: 1;
  overflow: hidden;
  background: var(--cream);
  min-width: 0;
}

.map-wrapper {
  width: 380px;
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
}

/* Map slide transition */
.map-slide-enter-active,
.map-slide-leave-active {
  transition: width 0.3s ease, opacity 0.3s ease;
}

.map-slide-enter-from,
.map-slide-leave-to {
  width: 0 !important;
  opacity: 0;
}

/* Footer */
.main-footer {
  flex-shrink: 0;
  background: var(--cream);
  border-top: 1px solid var(--cream-300);
}

/* 智能行程入口 */
.sb-planner-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 20px;
  cursor: pointer;
  border-bottom: 1px solid rgba(255,255,255,0.07);
  transition: background 0.2s;
  flex-shrink: 0;
}
.sb-planner-entry:hover { background: rgba(255,255,255,0.06); }

.spe-icon {
  width: 28px;
  height: 28px;
  background: rgba(var(--accent-rgb),0.15);
  border: 1px solid rgba(var(--accent-rgb),0.25);
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--gold);
  flex-shrink: 0;
}

.spe-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.spe-label {
  font-size: 12.5px;
  font-weight: 500;
  color: rgba(255,255,255,0.75);
  letter-spacing: 0.01em;
}

.spe-sub {
  font-size: 10px;
  color: rgba(255,255,255,0.3);
  letter-spacing: 0.03em;
}

.spe-arrow {
  color: rgba(255,255,255,0.25);
  flex-shrink: 0;
  transition: transform 0.2s, color 0.2s;
}
.sb-planner-entry:hover .spe-arrow {
  transform: translateX(2px);
  color: var(--gold);
}

@media (max-width: 768px) {
  .main-header {
    padding: 0 12px;
    gap: 8px;
  }
  .mh-hint { font-size: 12px; }
}
</style>
