<template>
  <div class="planbook-layout layout-with-sidebar" :class="{ 'sidebar-open': sidebarOpen }">
    <div v-if="sidebarOpen" class="sidebar-backdrop" @click="closeSidebar" />

    <!-- ─── Sidebar ──────────────────────────────────────────── -->
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <div class="sb-brand">
        <div class="term-window-dots">
          <span class="dot red"></span>
          <span class="dot yellow"></span>
          <span class="dot green"></span>
        </div>
        <span class="sb-prompt">~/voyage/planbook</span>
        <span class="term-prompt">$</span>
      </div>

      <div class="sb-nav">
        <div class="sb-nav-item" @click="router.push('/itinerary')">
          <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
            <path d="M8 2.5L4.5 6.5l3.5 4" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          行程规划
        </div>
        <div class="sb-nav-item active">
          <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
            <rect x="2" y="1.5" width="9" height="10" rx="1.5" stroke="currentColor" stroke-width="1.3"/>
            <path d="M4.5 5h4M4.5 7.5h2.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
          </svg>
          我的规划册
          <span v-if="store.items.length" class="sb-count">{{ store.items.length }}</span>
        </div>
      </div>

      <!-- Plan list -->
      <div class="sb-list">
        <div v-if="store.items.length === 0" class="sb-empty">
          <span>暂无保存的行程</span>
          <span class="sb-empty-hint">生成行程后点击「加入规划册」</span>
        </div>

        <div
          v-for="item in store.items"
          :key="item.id"
          class="sb-plan-item"
          :class="{ active: selectedId === item.id }"
          @click="selectPlan(item.id)"
        >
          <div class="spi-city">{{ item.cityName }}</div>
          <div class="spi-dates">{{ item.startDate }} — {{ item.endDate }}</div>
          <div class="spi-days">{{ item.totalDays }} 天</div>
          <div v-if="item.customTitle" class="spi-custom-title">{{ item.customTitle }}</div>
        </div>
      </div>
    </aside>

    <!-- ─── Main ──────────────────────────────────────────────── -->
    <div class="main">
      <div class="mobile-top-bar">
        <button class="mobile-menu-btn" aria-label="打开菜单" @click="toggleSidebar">
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
            <path d="M2 4.5h14M2 9h14M2 13.5h10" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
          </svg>
        </button>
        <span class="mobile-top-title">{{ selected?.cityName || '我的规划册' }}</span>
      </div>

      <!-- Empty state (no plans) -->
      <div v-if="store.items.length === 0" class="empty-state">
        <div class="es-icon">
          <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
            <rect x="8" y="6" width="32" height="36" rx="3" stroke="var(--cream-300)" stroke-width="1.5"/>
            <path d="M16 18h16M16 24h16M16 30h10" stroke="var(--cream-300)" stroke-width="1.5" stroke-linecap="round"/>
            <circle cx="36" cy="36" r="8" fill="var(--cream)" stroke="var(--gold)" stroke-width="1.5"/>
            <path d="M33 36h6M36 33v6" stroke="var(--gold)" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </div>
        <h2 class="es-title">规划册还是空的</h2>
        <p class="es-desc">前往行程规划，生成专属行程后加入规划册</p>
        <button class="es-btn" @click="router.push('/itinerary')">去规划行程</button>
      </div>

      <!-- Plan viewer -->
      <template v-else-if="selected">

        <!-- Plan header -->
        <div class="plan-header">
          <div class="ph-left">
            <div class="ph-city">{{ selected.cityName }}</div>
            <div class="ph-dates">{{ selected.startDate }} — {{ selected.endDate }} · {{ selected.totalDays }}天</div>
            <div v-if="!editingTitle" class="ph-title-row">
              <span class="ph-title">{{ selected.customTitle || selected.tripSummary }}</span>
              <button class="ph-edit-btn" @click="startEditTitle" title="编辑标题">
                <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                  <path d="M9.5 2.5l1 1-7 7H2.5v-1l7-7z" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/>
                </svg>
              </button>
            </div>
            <div v-else class="ph-title-edit">
              <input
                ref="titleInputRef"
                v-model="titleDraft"
                class="ph-title-input"
                @keydown.enter="saveTitle"
                @keydown.escape="cancelTitle"
                placeholder="输入自定义标题…"
              />
              <button class="ph-save-btn" @click="saveTitle">保存</button>
              <button class="ph-cancel-btn" @click="cancelTitle">取消</button>
            </div>
          </div>

          <div class="ph-actions">
            <button class="ph-action-btn export" @click="exportPlan">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M7 2v7M4.5 6.5L7 9l2.5-2.5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M2 10v1.5A0.5 0.5 0 002.5 12h9a0.5 0.5 0 00.5-.5V10" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
              </svg>
              导出行程
            </button>
            <button class="ph-action-btn delete" @click="confirmDelete">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M2.5 4h9M5.5 4V2.5h3V4M6 6.5v4M8 6.5v4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
                <path d="M3.5 4l.5 7.5h6L11 4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              删除
            </button>
          </div>
        </div>

        <!-- Day navigator -->
        <div class="day-nav">
          <button class="dn-arrow" :disabled="currentDay === 0" @click="goDay(currentDay - 1, 'prev')">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path d="M10 3L5 8l5 5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
          <div class="dn-center">
            <span class="dn-label">第 {{ currentDay + 1 }} 天</span>
            <span class="dn-date">{{ selected.itinerary.days[currentDay]?.date }} {{ selected.itinerary.days[currentDay]?.dayOfWeek }}</span>
            <div class="dn-dots">
              <button
                v-for="(_, i) in selected.itinerary.days" :key="i"
                class="dn-dot" :class="{ active: i === currentDay }"
                @click="goDay(i, i > currentDay ? 'next' : 'prev')"
              />
            </div>
          </div>
          <button class="dn-arrow" :disabled="currentDay === selected.itinerary.days.length - 1" @click="goDay(currentDay + 1, 'next')">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path d="M6 3l5 5-5 5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
        </div>

        <!-- Day content -->
        <div class="day-stage">
          <Transition :name="'slide-' + slideDir" mode="out-in">
            <div class="day-content" :key="currentDay">
              <DayPlanCard :day="selected.itinerary.days[currentDay]" />
            </div>
          </Transition>
        </div>

      </template>

      <!-- Placeholder when plan selected but empty days -->
      <div v-else-if="store.items.length > 0 && !selected" class="select-hint">
        <p>从左侧选择一个行程查看详情</p>
      </div>

    </div>

    <!-- Delete confirm dialog -->
    <Teleport to="body">
      <div v-if="showDeleteConfirm" class="overlay" @click.self="showDeleteConfirm = false">
        <div class="dialog">
          <div class="dialog-title">删除行程</div>
          <div class="dialog-body">确定要从规划册中删除「{{ selected?.cityName }}」行程吗？</div>
          <div class="dialog-actions">
            <button class="dialog-btn cancel" @click="showDeleteConfirm = false">取消</button>
            <button class="dialog-btn confirm" @click="doDelete">删除</button>
          </div>
        </div>
      </div>
    </Teleport>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import { useMobileSidebar } from '@/composables/useMobileSidebar'
import { useRouter } from 'vue-router'
import { usePlanBookStore, type PlanBookItem } from '@/stores/planBook'
import DayPlanCard from '@/components/DayPlanCard.vue'

const router = useRouter()
const store = usePlanBookStore()
const { sidebarOpen, toggleSidebar, closeSidebar } = useMobileSidebar()

const selectedId = ref<string | null>(store.items[0]?.id ?? null)
const currentDay = ref(0)
const slideDir = ref<'next' | 'prev'>('next')
const showDeleteConfirm = ref(false)
const editingTitle = ref(false)
const titleDraft = ref('')
const titleInputRef = ref<HTMLInputElement | null>(null)

const selected = computed<PlanBookItem | null>(() =>
  store.items.find(i => i.id === selectedId.value) ?? null
)

function selectPlan(id: string) {
  if (selectedId.value === id) return
  selectedId.value = id
  currentDay.value = 0
  slideDir.value = 'next'
  editingTitle.value = false
}

function goDay(index: number, dir: 'next' | 'prev') {
  slideDir.value = dir
  currentDay.value = index
}

function startEditTitle() {
  titleDraft.value = selected.value?.customTitle || selected.value?.tripSummary || ''
  editingTitle.value = true
  nextTick(() => titleInputRef.value?.focus())
}

function saveTitle() {
  if (selectedId.value && titleDraft.value.trim()) {
    store.updateTitle(selectedId.value, titleDraft.value.trim())
  }
  editingTitle.value = false
}

function cancelTitle() {
  editingTitle.value = false
}

function confirmDelete() {
  showDeleteConfirm.value = true
}

async function doDelete() {
  if (!selectedId.value) return
  const nextItem = store.items.find(i => i.id !== selectedId.value)
  await store.remove(selectedId.value)
  selectedId.value = nextItem?.id ?? null
  currentDay.value = 0
  showDeleteConfirm.value = false
}

onMounted(async () => {
  await store.fetchAll()
  if (!selectedId.value && store.items.length > 0) {
    selectedId.value = store.items[0].id
  }
})

function exportPlan() {
  if (!selected.value) return
  const item = selected.value
  const html = buildExportHtml(item)
  const win = window.open('', '_blank', 'noopener')
  if (!win) return
  win.document.write(html)
  win.document.close()
  setTimeout(() => win.print(), 300)
}

function buildExportHtml(item: PlanBookItem): string {
  const days = item.itinerary.days
  const title = item.customTitle || `${item.cityName} ${item.totalDays}日行程`

  const daysHtml = days.map(day => {
    const activities = day.mainActivities.map(act => `
      <tr>
        <td class="time">${act.timeSlot}</td>
        <td class="act">${act.activity}</td>
        <td class="note">${act.notes || ''}</td>
      </tr>`).join('')

    const foods = day.foods.slice(0, 3).map(f =>
      `<li>${f.name}（${f.rating.toFixed(1)}★ · ${f.priceRange}）</li>`).join('')

    const tips = day.tips.map(t => `<li>${t}</li>`).join('')

    return `
      <div class="day">
        <h2>第 ${day.dayNumber} 天 · ${day.date} ${day.dayOfWeek}</h2>
        <div class="weather">天气：${day.weather.conditionText} ${day.weather.tempLow}~${day.weather.tempHigh}℃ · ${day.weather.windDir}风${day.weather.windScale}级</div>
        <p class="narrative">${day.narrative}</p>
        <h3>行程安排</h3>
        <table><thead><tr><th>时间</th><th>活动</th><th>备注</th></tr></thead><tbody>${activities}</tbody></table>
        <div class="two-col">
          <div>
            <h3>今日美食</h3>
            <ul>${foods}</ul>
          </div>
          <div>
            <h3>出行贴士</h3>
            <ul>${tips}</ul>
          </div>
        </div>
        <div class="budget">
          预算参考：门票 ${day.budget?.attraction ?? '-'} · 餐饮 ${day.budget?.food ?? '-'} · 交通 ${day.budget?.transport ?? '-'} · <strong>合计 ${day.budget?.total ?? '-'}</strong>
        </div>
      </div>`
  }).join('')

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>${title}</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif; color: #1A1614; background: #fff; padding: 32px; max-width: 800px; margin: 0 auto; }
  h1 { font-size: 28px; font-weight: 700; margin-bottom: 6px; }
  .meta { font-size: 13px; color: #888; margin-bottom: 8px; }
  .summary { font-size: 14px; color: #555; line-height: 1.7; margin-bottom: 32px; border-left: 3px solid var(--gold); padding-left: 14px; }
  .day { margin-bottom: 40px; padding-bottom: 32px; border-bottom: 1px solid #eee; }
  .day:last-child { border-bottom: none; }
  h2 { font-size: 18px; font-weight: 700; color: var(--forest); margin-bottom: 6px; }
  .weather { font-size: 12px; color: #888; margin-bottom: 8px; }
  .narrative { font-size: 13px; color: #666; line-height: 1.7; margin-bottom: 16px; font-style: italic; }
  h3 { font-size: 13px; font-weight: 700; color: #555; text-transform: uppercase; letter-spacing: 0.05em; margin: 14px 0 8px; }
  table { width: 100%; border-collapse: collapse; font-size: 13px; margin-bottom: 12px; }
  th { background: #f5efe7; text-align: left; padding: 6px 10px; font-size: 11px; color: #888; }
  td { padding: 7px 10px; border-bottom: 1px solid #f0eae2; vertical-align: top; }
  td.time { width: 120px; font-variant-numeric: tabular-nums; color: #888; white-space: nowrap; }
  td.act { font-weight: 600; }
  td.note { color: #888; font-size: 12px; }
  .two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin: 12px 0; }
  ul { list-style: none; padding: 0; }
  ul li { font-size: 13px; color: #555; padding: 4px 0; border-bottom: 1px solid #f5efe7; }
  ul li::before { content: '· '; color: var(--gold); }
  .budget { font-size: 12px; color: #888; background: #f5efe7; padding: 8px 14px; border-radius: 6px; margin-top: 12px; }
  .budget strong { color: var(--forest); }
  @media print {
    body { padding: 16px; }
    .day { page-break-inside: avoid; }
  }
</style>
</head>
<body>
  <h1>${title}</h1>
  <div class="meta">${item.startDate} — ${item.endDate} · ${item.totalDays}天 · 生成于 ${new Date(item.savedAt).toLocaleDateString('zh-CN')}</div>
  <div class="summary">${item.tripSummary}</div>
  ${daysHtml}
  <p style="font-size:11px;color:#ccc;margin-top:24px;text-align:center;">由 旅途 Voyage 生成 · 仅供参考</p>
</body>
</html>`
}
</script>

<style scoped>
/* ─── Layout ────────────────────────────────────────────── */
.planbook-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--cream);
}

/* ─── Sidebar ─────────────────────────────────────────── */
.sidebar {
  width: 240px;
  flex-shrink: 0;
  background: var(--forest);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-right: 1px solid rgba(255,255,255,0.06);
}

.sb-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 22px 18px 18px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
  flex-shrink: 0;
}
.sb-gem { color: var(--gold); font-size: 9px; }
.sb-name {
  font-family: 'Cormorant Garamond', serif;
  font-size: 20px;
  font-weight: 500;
  color: var(--text-on-theme);
  letter-spacing: 0.02em;
}
.sb-name-en {
  font-family: 'Cormorant Garamond', serif;
  font-size: 13px;
  font-style: italic;
  color: rgba(250, 246, 240, 0.55);
  letter-spacing: 0.05em;
  margin-left: 2px;
}

.sb-nav {
  padding: 10px 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  border-bottom: 1px solid rgba(255,255,255,0.07);
  flex-shrink: 0;
}

.sb-nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 7px;
  font-size: 12px;
  color: rgba(255,255,255,0.4);
  cursor: pointer;
  transition: all 0.18s;
  letter-spacing: 0.02em;
}
.sb-nav-item:hover { color: rgba(255,255,255,0.7); background: rgba(255,255,255,0.05); }
.sb-nav-item.active { color: var(--gold); background: rgba(var(--accent-rgb),0.12); }

.sb-count {
  margin-left: auto;
  background: rgba(var(--accent-rgb),0.2);
  color: var(--gold);
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 10px;
  min-width: 18px;
  text-align: center;
}

.sb-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sb-empty {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 20px 10px;
  font-size: 12px;
  color: rgba(255,255,255,0.3);
  text-align: center;
}
.sb-empty-hint { font-size: 10.5px; color: rgba(255,255,255,0.18); line-height: 1.5; }

.sb-plan-item {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid rgba(255,255,255,0.07);
  transition: all 0.18s;
  background: rgba(255,255,255,0.03);
}
.sb-plan-item:hover { background: rgba(255,255,255,0.06); border-color: rgba(255,255,255,0.12); }
.sb-plan-item.active { background: rgba(var(--accent-rgb),0.1); border-color: rgba(var(--accent-rgb),0.3); }

.spi-city {
  font-size: 14px;
  font-weight: 600;
  color: rgba(255,255,255,0.85);
  font-family: 'Cormorant Garamond', serif;
  letter-spacing: 0.02em;
  margin-bottom: 3px;
}
.sb-plan-item.active .spi-city { color: var(--gold); }
.spi-dates { font-size: 10.5px; color: rgba(255,255,255,0.3); letter-spacing: 0.02em; }
.spi-days { font-size: 10px; color: rgba(255,255,255,0.2); margin-top: 1px; }
.spi-custom-title {
  font-size: 10.5px;
  color: rgba(var(--accent-rgb),0.7);
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ─── Main ────────────────────────────────────────────── */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

/* Empty state */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 40px;
}
.es-icon { opacity: 0.4; }
.es-title {
  font-family: 'Cormorant Garamond', serif;
  font-size: 24px;
  font-weight: 600;
  color: var(--text);
}
.es-desc { font-size: 13.5px; color: var(--text-3); }
.es-btn {
  margin-top: 4px;
  padding: 9px 24px;
  background: rgba(var(--forest-rgb),0.08);
  border: 1px solid rgba(var(--forest-rgb),0.15);
  border-radius: 8px;
  color: var(--forest);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
}
.es-btn:hover { background: rgba(var(--forest-rgb),0.14); }

.select-hint {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: var(--text-3);
}

/* ─── Plan header ─────────────────────────────────────── */
.plan-header {
  padding: 18px 28px 14px;
  border-bottom: 1px solid var(--cream-300);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-shrink: 0;
  background: var(--cream);
}

.ph-left { display: flex; flex-direction: column; gap: 3px; min-width: 0; flex: 1; }

.ph-city {
  font-family: 'Cormorant Garamond', serif;
  font-size: 26px;
  font-weight: 600;
  color: var(--text);
  letter-spacing: 0.01em;
  line-height: 1.1;
}
.ph-dates { font-size: 12px; color: var(--text-3); letter-spacing: 0.04em; }

.ph-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
}
.ph-title {
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.6;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ph-edit-btn {
  width: 22px;
  height: 22px;
  border: 1px solid var(--cream-300);
  border-radius: 5px;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-3);
  transition: all 0.18s;
  flex-shrink: 0;
}
.ph-edit-btn:hover { border-color: var(--forest); color: var(--forest); }

.ph-title-edit { display: flex; align-items: center; gap: 6px; margin-top: 3px; }
.ph-title-input {
  flex: 1;
  padding: 5px 10px;
  border: 1px solid var(--gold);
  border-radius: 6px;
  background: var(--cream);
  font-size: 13px;
  color: var(--text);
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  outline: none;
}
.ph-save-btn, .ph-cancel-btn {
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  transition: all 0.15s;
}
.ph-save-btn { background: var(--forest); border: none; color: #fff; }
.ph-save-btn:hover { opacity: 0.85; }
.ph-cancel-btn { background: transparent; border: 1px solid var(--cream-300); color: var(--text-3); }
.ph-cancel-btn:hover { border-color: var(--text-3); }

.ph-actions { display: flex; gap: 8px; flex-shrink: 0; align-items: center; }

.ph-action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.18s;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  border: 1px solid transparent;
}
.ph-action-btn.export {
  background: rgba(var(--forest-rgb),0.07);
  border-color: rgba(var(--forest-rgb),0.12);
  color: var(--forest);
}
.ph-action-btn.export:hover { background: rgba(var(--forest-rgb),0.13); }
.ph-action-btn.delete {
  background: rgba(200,60,60,0.06);
  border-color: rgba(200,60,60,0.15);
  color: #c03c3c;
}
.ph-action-btn.delete:hover { background: rgba(200,60,60,0.12); }

/* ─── Day navigator ──────────────────────────────────── */
.day-nav {
  display: flex;
  align-items: center;
  padding: 10px 28px;
  border-bottom: 1px solid var(--cream-300);
  flex-shrink: 0;
  background: var(--cream);
  gap: 20px;
}
.dn-arrow {
  width: 32px;
  height: 32px;
  border: 1px solid var(--cream-300);
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-2);
  transition: all 0.2s;
  flex-shrink: 0;
}
.dn-arrow:hover:not(:disabled) { border-color: var(--forest); color: var(--forest); background: rgba(var(--forest-rgb),0.05); }
.dn-arrow:disabled { opacity: 0.25; cursor: not-allowed; }

.dn-center { flex: 1; display: flex; align-items: center; gap: 12px; }
.dn-label { font-family: 'Cormorant Garamond', serif; font-size: 17px; font-weight: 600; color: var(--text); white-space: nowrap; }
.dn-date { font-size: 12px; color: var(--text-3); white-space: nowrap; }
.dn-dots { display: flex; gap: 6px; margin-left: auto; }
.dn-dot {
  width: 7px; height: 7px;
  border-radius: 50%;
  border: 1.5px solid var(--cream-300);
  background: transparent;
  cursor: pointer;
  padding: 0;
  transition: all 0.2s;
}
.dn-dot:hover { border-color: var(--text-3); }
.dn-dot.active { background: var(--forest); border-color: var(--forest); }

/* ─── Day stage ──────────────────────────────────────── */
.day-stage {
  flex: 1;
  overflow: hidden;
  position: relative;
}
.day-content {
  height: 100%;
  overflow-y: auto;
}

/* Slide transitions */
.slide-next-enter-active, .slide-next-leave-active,
.slide-prev-enter-active, .slide-prev-leave-active {
  transition: transform 0.32s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.25s ease;
}
.slide-next-enter-from  { transform: translateX(48px); opacity: 0; }
.slide-next-leave-to    { transform: translateX(-48px); opacity: 0; }
.slide-prev-enter-from  { transform: translateX(-48px); opacity: 0; }
.slide-prev-leave-to    { transform: translateX(48px); opacity: 0; }

/* ─── Delete dialog ──────────────────────────────────── */
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(2px);
}
.dialog {
  background: var(--cream);
  border-radius: 12px;
  padding: 24px 28px;
  width: 340px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.18);
}
.dialog-title {
  font-family: 'Cormorant Garamond', serif;
  font-size: 18px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 10px;
}
.dialog-body {
  font-size: 13.5px;
  color: var(--text-2);
  line-height: 1.6;
  margin-bottom: 20px;
}
.dialog-actions { display: flex; gap: 8px; justify-content: flex-end; }
.dialog-btn {
  padding: 7px 18px;
  border-radius: 7px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.18s;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
}
.dialog-btn.cancel { background: transparent; border: 1px solid var(--cream-300); color: var(--text-2); }
.dialog-btn.cancel:hover { border-color: var(--text-3); }
.dialog-btn.confirm { background: #c03c3c; border: none; color: #fff; }
.dialog-btn.confirm:hover { opacity: 0.85; }
</style>
