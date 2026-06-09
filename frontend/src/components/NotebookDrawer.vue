<template>
  <Transition name="drawer">
    <div v-if="open" class="drawer-overlay" @click.self="$emit('close')">
      <div class="drawer">
        <div class="drawer-header">
          <div class="dh-left">
            <span class="dh-gem">◆</span>
            <span class="dh-title">我的收藏</span>
            <span class="dh-count">{{ favStore.items.length }}</span>
          </div>
          <button class="dh-close" @click="$emit('close')">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M1 1l12 12M13 1L1 13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
          </button>
        </div>

        <!-- Filter by city -->
        <div class="drawer-filters" v-if="allCities.length > 1">
          <button
            class="filter-chip"
            :class="{ active: !filterCity }"
            @click="filterCity = ''"
          >全部</button>
          <button
            v-for="city in allCities"
            :key="city"
            class="filter-chip"
            :class="{ active: filterCity === city }"
            @click="filterCity = city"
          >{{ city }}</button>
        </div>

        <!-- Items -->
        <div class="drawer-body">
          <div v-if="filteredItems.length === 0" class="drawer-empty">
            <div class="empty-icon">🔖</div>
            <p>还没有收藏内容</p>
            <p class="empty-sub">在 AI 回复上点击收藏按钮</p>
          </div>

          <div
            v-for="item in filteredItems"
            :key="item.id"
            class="nb-item"
          >
            <div class="nb-meta">
              <div class="nb-cities">
                <span v-for="city in item.cities" :key="city" class="nb-city">{{ city }}</span>
              </div>
              <div class="nb-right">
                <span class="nb-date">{{ formatDate(item.savedAt) }}</span>
                <button class="nb-delete" @click="favStore.remove(item.id)" title="删除">
                  <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                    <path d="M1 1l10 10M11 1L1 11" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
                  </svg>
                </button>
              </div>
            </div>
            <div class="nb-question">
              <svg width="11" height="11" viewBox="0 0 11 11" fill="none">
                <circle cx="5.5" cy="5.5" r="4.5" stroke="currentColor" stroke-width="1.2"/>
                <path d="M5.5 3.5v.5M5.5 6v2" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
              </svg>
              {{ item.question }}
            </div>
            <div class="nb-content" v-html="renderMd(item.content)"></div>
            <div class="nb-actions">
              <button class="nb-copy" @click="copyContent(item.content)">
                <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                  <rect x="3.5" y="3.5" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.2"/>
                  <path d="M8.5 3.5V2A.5.5 0 0 0 8 1.5H2A.5.5 0 0 0 1.5 2v6a.5.5 0 0 0 .5.5h1.5"
                    stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                </svg>
                复制
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { useFavoritesStore } from '@/stores/favorites'

defineProps<{ open: boolean }>()
defineEmits<{ close: [] }>()

const favStore = useFavoritesStore()
const filterCity = ref('')

const allCities = computed(() => {
  const s = new Set<string>()
  favStore.items.forEach(item => item.cities.forEach(c => s.add(c)))
  return [...s]
})

const filteredItems = computed(() => {
  if (!filterCity.value) return favStore.items
  return favStore.items.filter(i => i.cities.includes(filterCity.value))
})

function renderMd(content: string): string {
  marked.setOptions({ breaks: true, gfm: true })
  const raw = marked.parse(content) as string
  return DOMPurify.sanitize(raw, { ADD_TAGS: ['table', 'thead', 'tbody', 'tr', 'th', 'td'] })
}

function formatDate(date: Date): string {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(date))
}

async function copyContent(content: string) {
  await navigator.clipboard.writeText(content)
}
</script>

<style scoped>
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
  width: 480px;
  max-width: 90vw;
  height: 100%;
  background: var(--cream);
  display: flex;
  flex-direction: column;
  box-shadow: -8px 0 40px rgba(var(--forest-rgb),0.15);
}

/* Transition */
.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 0.25s ease;
}

.drawer-enter-active .drawer,
.drawer-leave-active .drawer {
  transition: transform 0.25s ease;
}

.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}

.drawer-enter-from .drawer,
.drawer-leave-to .drawer {
  transform: translateX(100%);
}

/* Header */
.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--cream-300);
  flex-shrink: 0;
}

.dh-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dh-gem { color: var(--gold); font-size: 9px; }

.dh-title {
  font-family: 'Cormorant Garamond', serif;
  font-size: 20px;
  font-weight: 500;
  color: var(--text);
}

.dh-count {
  background: var(--forest);
  color: #fff;
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 10px;
  font-weight: 500;
}

.dh-close {
  width: 28px;
  height: 28px;
  background: none;
  border: 1px solid var(--cream-300);
  border-radius: 7px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-3);
  transition: all 0.15s;
}

.dh-close:hover { border-color: var(--text-2); color: var(--text); }

/* Filters */
.drawer-filters {
  display: flex;
  gap: 6px;
  padding: 12px 24px;
  border-bottom: 1px solid var(--cream-300);
  flex-wrap: wrap;
  flex-shrink: 0;
}

.filter-chip {
  padding: 4px 12px;
  border: 1.5px solid var(--cream-300);
  border-radius: 14px;
  background: transparent;
  font-size: 12px;
  color: var(--text-2);
  cursor: pointer;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  transition: all 0.15s;
}

.filter-chip.active,
.filter-chip:hover {
  border-color: var(--forest);
  color: var(--forest);
  background: rgba(var(--forest-rgb),0.06);
}

/* Body */
.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.drawer-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 60px 0;
  color: var(--text-3);
}

.empty-icon { font-size: 36px; }

.drawer-empty p {
  font-size: 14px;
  font-family: 'Cormorant Garamond', serif;
  font-size: 18px;
}

.empty-sub {
  font-size: 12px !important;
  font-family: 'DM Sans', 'PingFang SC', sans-serif !important;
  color: var(--text-3);
}

/* Notebook item */
.nb-item {
  background: var(--white);
  border: 1.5px solid var(--cream-300);
  border-radius: 12px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nb-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.nb-cities {
  display: flex;
  gap: 4px;
}

.nb-city {
  font-size: 11px;
  padding: 2px 8px;
  background: var(--forest);
  color: rgba(255,255,255,0.9);
  border-radius: 10px;
  font-weight: 500;
}

.nb-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nb-date {
  font-size: 11px;
  color: var(--text-3);
}

.nb-delete {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--text-3);
  padding: 2px;
  display: flex;
  align-items: center;
  transition: color 0.15s;
}

.nb-delete:hover { color: #dc2626; }

.nb-question {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 12px;
  color: var(--text-3);
  font-style: italic;
  line-height: 1.4;
}

.nb-question svg { flex-shrink: 0; margin-top: 1px; }

.nb-content {
  font-size: 13px;
  line-height: 1.65;
  color: var(--text-2);
  max-height: 180px;
  overflow: hidden;
  mask-image: linear-gradient(to bottom, black 70%, transparent 100%);
}

.nb-content :deep(h1), .nb-content :deep(h2), .nb-content :deep(h3) {
  font-size: 14px;
  font-weight: 600;
  margin: 6px 0 3px;
}

.nb-content :deep(ul), .nb-content :deep(ol) {
  padding-left: 16px;
}

.nb-content :deep(p) { margin: 3px 0; }

.nb-actions {
  display: flex;
  gap: 8px;
  padding-top: 4px;
  border-top: 1px solid var(--cream-200);
}

.nb-copy {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: none;
  border: 1px solid var(--cream-300);
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 11px;
  color: var(--text-3);
  cursor: pointer;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  transition: all 0.15s;
}

.nb-copy:hover { border-color: var(--forest); color: var(--forest); }
</style>
