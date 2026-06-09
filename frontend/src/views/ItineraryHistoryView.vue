<template>
  <div class="history-layout layout-with-sidebar">
    <aside class="sidebar">
      <div class="sb-brand">
        <div class="term-window-dots">
          <span class="dot red"></span>
          <span class="dot yellow"></span>
          <span class="dot green"></span>
        </div>
        <span class="sb-prompt">~/voyage/history</span>
        <span class="term-prompt">$</span>
      </div>
      <div class="sb-nav">
        <div class="sb-nav-item" @click="router.push('/itinerary')">← 返回行程规划</div>
        <div class="sb-nav-item active">历史行程</div>
      </div>
    </aside>

    <main class="main">
      <header class="page-hd">
        <h1>我的行程</h1>
        <p class="page-sub">登录后自动保存的 AI 生成记录</p>
      </header>

      <div v-if="loading" class="state-msg">加载中…</div>
      <div v-else-if="!authStore.isLoggedIn" class="state-msg">
        <p>请先登录查看历史行程</p>
        <button class="btn-primary" @click="router.push('/login')">去登录</button>
      </div>
      <div v-else-if="items.length === 0" class="state-msg">
        <p>暂无历史行程，去规划一次吧</p>
        <button class="btn-primary" @click="router.push('/itinerary')">开始规划</button>
      </div>

      <div v-else class="history-list">
        <article
          v-for="item in items"
          :key="item.id"
          class="history-card"
          @click="openItinerary(item.id)"
        >
          <div class="hc-city">{{ item.cityName }}</div>
          <div class="hc-dates">{{ item.startDate }} — {{ item.endDate }} · {{ item.totalDays }} 天</div>
          <p class="hc-summary">{{ item.tripSummary || '（无摘要）' }}</p>
          <div class="hc-foot">
            <span>{{ formatDate(item.createdAt) }}</span>
            <span class="hc-link">查看详情 →</span>
          </div>
        </article>
      </div>

      <div v-if="totalPages > 1" class="pager">
        <button :disabled="page === 0" @click="loadPage(page - 1)">上一页</button>
        <span>{{ page + 1 }} / {{ totalPages }}</span>
        <button :disabled="page >= totalPages - 1" @click="loadPage(page + 1)">下一页</button>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { agentApi, type ItinerarySummary } from '@/api/agent'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const items = ref<ItinerarySummary[]>([])
const loading = ref(false)
const page = ref(0)
const totalPages = ref(0)

async function loadPage(p: number) {
  if (!authStore.isLoggedIn) return
  loading.value = true
  try {
    const res = await agentApi.listItineraries(p, 20)
    items.value = res.items
    page.value = res.page
    totalPages.value = res.totalPages
  } finally {
    loading.value = false
  }
}

function formatDate(iso: string) {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleString('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })
  } catch {
    return iso
  }
}

function openItinerary(id: string) {
  router.push({ path: '/itinerary', query: { id } })
}

onMounted(() => loadPage(0))
</script>

<style scoped>
.history-layout {
  display: flex;
  min-height: 100vh;
  background: var(--cream);
}
.sidebar {
  width: 220px;
  border-right: 1px solid var(--cream-300);
  padding: 24px 16px;
  flex-shrink: 0;
}
.sb-brand { margin-bottom: 28px; }
.sb-gem { color: var(--gold); }
.sb-name { font-family: 'Cormorant Garamond', serif; font-size: 22px; margin-left: 6px; }
.sb-name-en { display: block; font-size: 10px; color: var(--text-3); letter-spacing: 0.1em; }
.sb-nav-item {
  padding: 8px 10px;
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
  border-radius: 4px;
}
.sb-nav-item:hover { background: rgba(0,0,0,0.04); }
.sb-nav-item.active { color: var(--text); font-weight: 600; }

.main { flex: 1; padding: 32px 40px; max-width: 800px; }
.page-hd h1 {
  font-family: 'Cormorant Garamond', serif;
  font-size: 32px;
  font-weight: 500;
  margin: 0 0 6px;
  color: var(--forest);
}
.page-sub { font-size: 13px; color: var(--text-3); margin: 0 0 28px; }

.state-msg {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-2);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.history-list { display: flex; flex-direction: column; gap: 12px; }
.history-card {
  background: #fff;
  border: 1px solid var(--cream-300);
  border-radius: 8px;
  padding: 18px 20px;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s;
}
.history-card:hover {
  border-color: var(--gold);
  box-shadow: 0 4px 16px rgba(0,0,0,0.06);
}
.hc-city { font-size: 18px; font-weight: 600; margin-bottom: 4px; }
.hc-dates { font-size: 12px; color: var(--text-3); margin-bottom: 8px; }
.hc-summary {
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.6;
  margin: 0 0 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.hc-foot {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--text-3);
}
.hc-link { color: var(--gold); }

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 24px;
  font-size: 13px;
}
.pager button {
  padding: 6px 14px;
  border: 1px solid var(--cream-300);
  background: transparent;
  cursor: pointer;
  border-radius: 4px;
}
.pager button:disabled { opacity: 0.4; cursor: not-allowed; }

.btn-primary {
  padding: 8px 20px;
  background: var(--forest);
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
</style>
