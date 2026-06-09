<template>
  <div class="day-card">

    <!-- ─── Weather ──────────────────────────────────────── -->
    <div class="weather" :class="weatherClass">
      <div class="w-body">

        <!-- Custom SVG weather icons (no emoji) -->
        <div class="w-icon">
          <!-- Sunny -->
          <svg v-if="weatherClass === 'sunny'" width="36" height="36" viewBox="0 0 36 36" fill="none">
            <circle cx="18" cy="18" r="6" stroke="currentColor" stroke-width="1.5"/>
            <path d="M18 4v4M18 28v4M4 18h4M28 18h4M8.1 8.1l2.83 2.83M25.07 25.07l2.83 2.83M8.1 27.9l2.83-2.83M25.07 10.93l2.83-2.83" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          <!-- Rainy -->
          <svg v-else-if="weatherClass === 'rainy'" width="36" height="36" viewBox="0 0 36 36" fill="none">
            <path d="M28 21H9a6 6 0 010-12 6 6 0 01.5.04A8 8 0 0124 12a6 6 0 014 9z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
            <path d="M12 27l-1 4M18 26v4M24 27l1 4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          <!-- Snowy -->
          <svg v-else-if="weatherClass === 'snowy'" width="36" height="36" viewBox="0 0 36 36" fill="none">
            <path d="M28 21H9a6 6 0 010-12 6 6 0 01.5.04A8 8 0 0124 12a6 6 0 014 9z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
            <circle cx="12" cy="28" r="1.5" fill="currentColor"/>
            <circle cx="18" cy="30" r="1.5" fill="currentColor"/>
            <circle cx="24" cy="28" r="1.5" fill="currentColor"/>
            <circle cx="15" cy="26" r="1" fill="currentColor" opacity="0.5"/>
            <circle cx="21" cy="26" r="1" fill="currentColor" opacity="0.5"/>
          </svg>
          <!-- Foggy -->
          <svg v-else-if="weatherClass === 'foggy'" width="36" height="36" viewBox="0 0 36 36" fill="none">
            <path d="M6 13h24M5 19h26M8 25h20" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          <!-- Cloudy / default -->
          <svg v-else width="36" height="36" viewBox="0 0 36 36" fill="none">
            <path d="M27 23.5H10a5 5 0 010-10 5 5 0 010 .5A7 7 0 0122.5 15a5 5 0 014.5 8.5z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
            <path d="M20 11A6.5 6.5 0 0130 17" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </div>

        <div class="w-temps">
          <span class="w-hi">{{ day.weather.tempHigh }}</span><span class="w-deg">°</span>
          <span class="w-sep"> / </span>
          <span class="w-lo">{{ day.weather.tempLow }}°</span>
        </div>

        <div class="w-right">
          <span class="w-cond">{{ day.weather.conditionText }}</span>
          <span class="w-badge" :class="day.weather.outdoorFriendly ? 'badge-good' : 'badge-warn'">
            {{ day.weather.outdoorFriendly ? '适合户外' : '注意天气' }}
          </span>
          <DataSourceBadge :source="day.weather.dataSource" kind="weather" />
        </div>
      </div>

      <div class="w-stats">
        <span>{{ day.weather.windDir }}风 {{ day.weather.windScale }} 级</span>
        <span class="w-dot">·</span>
        <span>湿度 {{ day.weather.humidity }}%</span>
        <span class="w-dot">·</span>
        <span>UV&thinsp;{{ day.weather.uvIndex }}</span>
      </div>
    </div>

    <!-- ─── Narrative ─────────────────────────────────────── -->
    <p v-if="day.narrative" class="narrative">{{ day.narrative }}</p>

    <!-- ─── Plan tabs ─────────────────────────────────────── -->
    <div v-if="hasAlternatePlan" class="tabs">
      <button class="tab" :class="{ active: activeTab === 'main' }" @click="activeTab = 'main'">
        {{ shortTitle(day.mainPlanTitle) }}
      </button>
      <button class="tab" :class="{ active: activeTab === 'alt' }" @click="activeTab = 'alt'">
        {{ shortTitle(day.alternatePlanTitle) }}
        <span v-if="alternatePoiCount" class="tab-badge">{{ alternatePoiCount }}站</span>
      </button>
    </div>

    <!-- ─── Timeline ──────────────────────────────────────── -->
    <div class="timeline">
      <div v-if="activeTab === 'alt' && !currentActivities.length" class="tl-empty">
        暂无室内备选行程，请查看晴天方案或重新生成行程。
      </div>
      <div
        v-for="(act, i) in currentActivities" :key="i"
        class="tl-row" :class="`t-${act.type}`"
      >
        <!-- Marker + stem -->
        <div class="tl-track-col">
          <div class="tl-marker" :class="`m-${act.type}`"></div>
          <div v-if="i < currentActivities.length - 1" class="tl-stem"></div>
        </div>

        <!-- Time -->
        <div class="tl-time">{{ act.timeSlot.split('-')[0] }}</div>

        <!-- Content -->
        <div class="tl-content">
          <div class="tl-name-row">
            <span class="tl-name">{{ act.activity }}</span>
            <DataSourceBadge
              v-if="act.poi?.dataSource && act.type === 'attraction'"
              :source="act.poi.dataSource"
              kind="poi"
            />
          </div>
          <div v-if="(i > 0 && act.transportFromPrev) || act.notes" class="tl-sub">
            <template v-if="i > 0 && act.transportFromPrev">
              <span>{{ act.transportFromPrev }}</span>
              <template v-if="act.transportMinutes > 0">
                <span class="tl-sep">·</span>
                <span>{{ act.transportMinutes }}分钟</span>
              </template>
            </template>
            <template v-if="act.notes">
              <span v-if="i > 0 && act.transportFromPrev" class="tl-sep">·</span>
              <span>{{ act.notes }}</span>
            </template>
          </div>
          <div v-if="displayTags(act).length" class="tl-tags">
            <span v-for="tag in displayTags(act)" :key="tag">{{ tag }}</span>
          </div>
        </div>

        <!-- Price -->
        <div class="tl-price">
          <span v-if="act.estimatedCost > 0">¥&thinsp;{{ Math.round(act.estimatedCost) }}</span>
          <span v-else-if="act.type === 'attraction'" class="price-free">免票</span>
        </div>
      </div>
    </div>

    <!-- ─── Info grid ─────────────────────────────────────── -->
    <div class="info-grid">

      <!-- Accommodation (trip-level, day 1 only, flows with scroll) -->
      <section v-if="showAccommodation" class="info-card info-card-full">
        <div class="card-hd">
          <div class="hd-icon hd-stay">
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
              <path d="M2 11V4.5a2 2 0 012-2h5a2 2 0 012 2V11M2 11h9M4.5 6h4" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M5 11V8.5h3V11" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/>
            </svg>
          </div>
          <span class="hd-title">住宿安排</span>
          <DataSourceBadge
            v-if="accommodations![0]"
            :source="accommodations![0].dataSource"
            :real="hasRealAccommodationData"
            kind="accommodation"
          />
        </div>
        <div v-if="primaryAccommodation" class="stay-primary">
          <span class="stay-badge">首推</span>
          <div class="stay-main">
            <span class="stay-name">{{ primaryAccommodation.name }}</span>
            <div class="stay-meta">
              <span v-if="primaryAccommodation.starLevel">{{ primaryAccommodation.starLevel }}</span>
              <span v-if="primaryAccommodation.starLevel" class="stay-sep">·</span>
              <span>{{ primaryAccommodation.category }}</span>
              <span class="stay-sep">·</span>
              <span>{{ primaryAccommodation.district || primaryAccommodation.address }}</span>
            </div>
            <div v-if="primaryAccommodation.recommendReason" class="stay-reason">
              {{ primaryAccommodation.recommendReason }}
            </div>
          </div>
          <div class="stay-right">
            <span class="stay-score">{{ primaryAccommodation.rating.toFixed(1) }}</span>
            <span class="stay-price">{{ primaryAccommodation.priceRange }}</span>
          </div>
        </div>
        <div class="stay-list">
          <div
            v-for="acc in altAccommodations"
            :key="acc.name"
            class="stay-item"
          >
            <span class="stay-item-name">{{ acc.name }}</span>
            <span class="stay-item-meta">{{ acc.priceRange }} · {{ acc.distanceKm }}km</span>
          </div>
        </div>
        <ul v-if="accommodationTips?.length" class="stay-tips">
          <li v-for="tip in accommodationTips" :key="tip">{{ tip }}</li>
        </ul>
      </section>

      <!-- Food -->
      <section v-if="day.foods?.length" class="info-card">
        <div class="card-hd">
          <div class="hd-icon hd-dining">
            <!-- Fork & knife icon -->
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
              <path d="M4 1v3.5a1.8 1.8 0 001.5 1.77V12M6.5 1v11M9.5 1c0 1.5 1.2 2.5 1.2 4V12" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <span class="hd-title">今日餐饮</span>
          <DataSourceBadge v-if="foodSourceSummary" :source="foodSourceSummary" kind="food" />
        </div>
        <div class="food-list">
          <div v-for="food in day.foods.slice(0, 5)" :key="food.name" class="food-item">
            <div class="fi-main">
              <span class="fi-name">{{ food.name }}</span>
              <div class="fi-meta">
                <span>{{ simplifyCategory(food.category) }}</span>
                <span class="fi-sep">·</span>
                <span>{{ food.distanceKm }}&thinsp;km</span>
                <span v-if="food.businessStatus" class="fi-sep">·</span>
                <span v-if="food.businessStatus">{{ food.businessStatus.split(' ')[0] }}</span>
              </div>
              <div v-if="food.recommendReason" class="fi-reason">{{ food.recommendReason }}</div>
            </div>
            <div class="fi-right">
              <DataSourceBadge
                v-if="food.dataSource"
                :source="food.dataSource"
                kind="food"
                class="fi-ds"
              />
              <span class="fi-score">{{ food.rating.toFixed(1) }}</span>
              <span class="fi-price">{{ food.priceRange }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- Right column stack -->
      <div class="info-right">

        <!-- Route -->
        <section v-if="currentRoute?.legs?.length" class="info-card">
          <div class="card-hd">
            <div class="hd-icon hd-route">
              <!-- Route path icon -->
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                <circle cx="3" cy="10.5" r="1.5" stroke="currentColor" stroke-width="1.2"/>
                <circle cx="10" cy="2.5" r="1.5" stroke="currentColor" stroke-width="1.2"/>
                <path d="M3 9V6.5A3.5 3.5 0 016.5 3h0A3.5 3.5 0 0110 6.5v0A3.5 3.5 0 016.5 10H4" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
              </svg>
            </div>
            <span class="hd-title">{{ activeTab === 'alt' ? '备选路线' : '游览路线' }}</span>
            <DataSourceBadge :source="currentRoute.dataSource" kind="route" />
            <span class="hd-sub">{{ currentRoute.totalDistanceKm }}&thinsp;km</span>
          </div>
          <ol class="route-list">
            <li v-for="(leg, i) in currentRoute.legs" :key="leg.toName">
              <span class="rl-n">{{ i + 1 }}</span>
              <span class="rl-name">{{ leg.toName }}</span>
              <span class="rl-mode">{{ leg.transportSuggestion }}</span>
            </li>
          </ol>
        </section>

        <!-- Tips -->
        <section v-if="day.tips?.length" class="info-card">
          <div class="card-hd">
            <div class="hd-icon hd-tips">
              <!-- Attention diamond icon -->
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                <path d="M6.5 1.5l5 5-5 5-5-5 5-5z" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/>
                <path d="M6.5 4.5v3M6.5 8.5v.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
              </svg>
            </div>
            <span class="hd-title">出行提示</span>
          </div>
          <ul class="tips-list">
            <li v-for="tip in day.tips" :key="tip">{{ tip }}</li>
          </ul>
        </section>

        <!-- Budget -->
        <section v-if="day.budget" class="info-card">
          <div class="card-hd">
            <div class="hd-icon hd-budget">
              <!-- Coin icon -->
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                <circle cx="6.5" cy="6.5" r="5" stroke="currentColor" stroke-width="1.2"/>
                <path d="M6.5 3.5v.5M6.5 9v.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                <path d="M4.5 8a2 2 0 004 0c0-1-2-1.5-2-2.5a1.5 1.5 0 00-3 0" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
              </svg>
            </div>
            <span class="hd-title">当日预算</span>
          </div>
          <div class="budget-list">
            <div
              v-for="(val, key) in day.budget" :key="key"
              class="budget-row" :class="{ 'b-total': key === 'total' }"
            >
              <span class="b-label">{{ budgetLabel(String(key)) }}</span>
              <span class="b-val">{{ val }}</span>
            </div>
          </div>
        </section>

      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { AccommodationRecommendation, DayPlan, TimeSlotActivity } from '@/api/agent'
import DataSourceBadge from '@/components/DataSourceBadge.vue'
import { aggregateDataSource, formatPoiDayTag } from '@/utils/dataSource'

const props = defineProps<{
  day: DayPlan
  accommodations?: AccommodationRecommendation[]
  primaryAccommodation?: AccommodationRecommendation
  accommodationTips?: string[]
  hasRealAccommodationData?: boolean
}>()
const activeTab = ref<'main' | 'alt'>('main')

const showAccommodation = computed(
  () => props.day.dayNumber === 1 && (props.accommodations?.length ?? 0) > 0,
)

const altAccommodations = computed(
  () => (props.accommodations ?? []).filter(a => !a.primaryPick).slice(0, 4),
)

const foodSourceSummary = computed(() =>
  aggregateDataSource((props.day.foods ?? []).map(f => f.dataSource)),
)

const mainActivities = computed(() => props.day.mainActivities ?? [])
const alternateActivities = computed(() => props.day.alternateActivities ?? [])

const hasAlternatePlan = computed(() =>
  alternateActivities.value.length > 0
  || (props.day.alternateRoute?.optimizedPois?.length ?? 0) > 0,
)

const alternatePoiCount = computed(() =>
  props.day.alternateRoute?.optimizedPois?.length
  ?? alternateActivities.value.filter(a => a.type === 'attraction').length,
)

const currentActivities = computed(() =>
  activeTab.value === 'main' ? mainActivities.value : alternateActivities.value,
)

const currentRoute = computed(() =>
  activeTab.value === 'alt' && props.day.alternateRoute?.legs?.length
    ? props.day.alternateRoute
    : props.day.route,
)

watch(() => props.day.dayNumber, () => {
  activeTab.value = 'main'
})

watch(hasAlternatePlan, (ok) => {
  if (!ok && activeTab.value === 'alt') activeTab.value = 'main'
})

const weatherClass = computed(() => {
  const c = props.day.weather.condition
  if (c.includes('rain') || c.includes('thunder')) return 'rainy'
  if (c.includes('snow')) return 'snowy'
  if (c === 'cloudy' || c.includes('cloud')) return 'cloudy'
  if (c.includes('fog')) return 'foggy'
  return 'sunny'
})

function budgetLabel(key: string) {
  const m: Record<string, string> = {
    attraction: '景点门票',
    food: '餐饮消费',
    transport: '交通出行',
    accommodation: '住宿参考',
    total: '当日合计',
  }
  return m[key] || key
}

function shortTitle(title?: string) {
  if (!title) return '备选方案'
  const colonIdx = title.indexOf('：')
  if (colonIdx > 0) return title.slice(0, colonIdx).replace(/^[\s\S]{0,3}(?=[^\s\S]|[\u4e00-\u9fa5a-zA-Z])/, t => t.replace(/[^\u4e00-\u9fa5a-zA-Z]/g, ''))
  return title.replace(/^[^\u4e00-\u9fa5a-zA-Z]+/, '')
}

function simplifyCategory(cat: string) {
  const parts = cat.split(';')
  const last = parts[parts.length - 1]?.trim()
  if (last && last !== '餐饮相关' && last !== '餐饮服务') return last
  return parts[1]?.trim() || parts[0]
}

function displayTags(act: TimeSlotActivity): string[] {
  if (!act.poi?.tags?.length) return []
  return act.poi.tags
    .slice(0, 3)
    .map(t => formatPoiDayTag(t) ?? t)
}
</script>

<style scoped>
/* ── Root ──────────────────────────────────────────────────── */
.day-card {
  padding: 0 32px 44px;
}

/* ── Weather ────────────────────────────────────────────────── */
.weather {
  padding: 22px 4px 18px 18px;
  margin-bottom: 22px;
  border-bottom: 1px solid var(--cream-300);
  border-left: 2px solid transparent;
  transition: border-left-color 0.3s;
}
.weather.sunny  { border-left-color: #C8920A; }
.weather.rainy  { border-left-color: #4E86B5; }
.weather.cloudy { border-left-color: #8A8A9A; }
.weather.snowy  { border-left-color: #70A8CC; }
.weather.foggy  { border-left-color: #A09080; }

.w-body {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 14px;
}

.w-icon { opacity: 0.75; flex-shrink: 0; }
.weather.sunny .w-icon  { color: #C8920A; }
.weather.rainy .w-icon  { color: #4E86B5; }
.weather.cloudy .w-icon { color: #6A6A7A; }
.weather.snowy .w-icon  { color: #5090B8; }
.weather.foggy .w-icon  { color: #806858; }

.w-temps {
  font-family: 'Cormorant Garamond', serif;
  font-size: 50px;
  font-weight: 300;
  color: var(--text);
  line-height: 1;
  letter-spacing: -0.04em;
  flex: 1;
}
.w-deg { font-size: 26px; font-weight: 300; opacity: 0.4; }
.w-sep { font-size: 26px; color: var(--cream-300); }
.w-lo  { font-size: 34px; color: var(--text-3); font-weight: 300; }

.w-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 7px;
  flex-shrink: 0;
}
.w-cond {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  letter-spacing: 0.01em;
}
.w-badge {
  font-size: 9.5px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  padding: 3px 8px;
  border: 1px solid currentColor;
}
.badge-good { color: var(--forest); }
.badge-warn { color: var(--gold); }
.w-src { font-size: 10px; color: var(--text-3); font-style: italic; }

.w-stats {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0 8px;
  font-size: 11.5px;
  color: var(--text-3);
  letter-spacing: 0.01em;
}
.w-dot { opacity: 0.3; margin: 0 2px; }

/* ── Narrative ─────────────────────────────────────────────── */
.narrative {
  font-size: 13.5px;
  line-height: 1.85;
  color: var(--text);
  font-style: normal;
  padding: 0 0 22px;
  border-bottom: 1px solid var(--cream-300);
  letter-spacing: 0.01em;
}

/* ── Tabs ──────────────────────────────────────────────────── */
.tabs {
  display: flex;
  border-bottom: 1px solid var(--cream-300);
  gap: 0;
}
.tab {
  padding: 12px 0;
  margin-right: 28px;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--text-3);
  background: none;
  border: none;
  cursor: pointer;
  position: relative;
  transition: color 0.18s;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  white-space: nowrap;
}
.tab::after {
  content: '';
  position: absolute;
  bottom: -1px; left: 0; right: 0;
  height: 1.5px;
  background: var(--forest);
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 0.22s cubic-bezier(0.4, 0, 0.2, 1);
}
.tab:hover { color: var(--text); }
.tab.active { color: var(--term-green); font-weight: 600; }
.tab.active::after { transform: scaleX(1); }
.tab-badge {
  margin-left: 6px;
  font-size: 9px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 3px;
  background: rgba(var(--forest-rgb), 0.12);
  color: var(--forest);
  vertical-align: middle;
}
.tl-empty {
  padding: 20px 4px 28px;
  font-size: 12px;
  color: var(--text-3);
  border-bottom: 1px solid var(--cream-300);
  margin-bottom: 8px;
}

/* ── Timeline ──────────────────────────────────────────────── */
.timeline {
  padding: 6px 0;
  margin-bottom: 24px;
}

.tl-row {
  display: grid;
  grid-template-columns: 20px 62px 1fr auto;
  gap: 0 14px;
  align-items: start;
}

/* Vertical track: marker + connecting stem */
.tl-track-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 20px;
}

/* ── Geometric timeline markers ───────────────────────────── */
/* Attraction: rotated square (diamond) */
.m-attraction {
  width: 9px; height: 9px;
  border: 1.5px solid var(--forest);
  transform: rotate(45deg);
  background: var(--cream);
  margin-top: 6px;
  flex-shrink: 0;
}
/* Food/Dining: horizontal bar */
.m-food {
  width: 13px; height: 2px;
  background: var(--gold);
  border-radius: 1px;
  margin-top: 9px;
  flex-shrink: 0;
}
/* Rest/Leisure: hollow circle */
.m-rest {
  width: 8px; height: 8px;
  border-radius: 50%;
  border: 1.5px solid var(--cream-300);
  background: transparent;
  margin-top: 6px;
  flex-shrink: 0;
}
/* Transport: small arrow chevron */
.m-transport {
  width: 8px; height: 8px;
  border-right: 1.5px solid var(--text-3);
  border-top: 1.5px solid var(--text-3);
  transform: rotate(45deg);
  margin-top: 7px;
  flex-shrink: 0;
}
/* Shopping */
.m-shopping {
  width: 8px; height: 8px;
  border: 1.5px solid var(--earth);
  margin-top: 6px;
  flex-shrink: 0;
}

/* Connecting stem between items */
.tl-stem {
  flex: 1;
  width: 1px;
  min-height: 14px;
  background: var(--cream-300);
  margin: 4px 0;
}

/* Time label */
.tl-time {
  font-family: 'SF Mono', 'Fira Code', 'Courier New', monospace;
  font-size: 10.5px;
  color: var(--text-3);
  padding-top: 5px;
  letter-spacing: 0.03em;
  text-align: right;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/* Activity content */
.tl-content {
  padding: 3px 0 18px;
  min-width: 0;
}

.tl-name-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 4px;
}

.tl-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text);
  letter-spacing: 0.005em;
  line-height: 1.3;
}
.t-food .tl-name,
.t-rest .tl-name {
  font-weight: 400;
  color: var(--text-3);
  font-size: 12.5px;
  font-style: italic;
}

.tl-sub {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  font-size: 11px;
  color: var(--text-3);
  line-height: 1.7;
  gap: 0;
}
.tl-sep { margin: 0 5px; opacity: 0.3; font-size: 10px; }

.tl-tags {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 5px;
}
.tl-tags span {
  font-size: 9.5px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-3);
  padding-bottom: 1px;
  border-bottom: 1px solid var(--cream-300);
}

/* Price */
.tl-price {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-2);
  white-space: nowrap;
  padding-top: 4px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.price-free {
  font-size: 10px;
  font-weight: 700;
  color: var(--forest);
  letter-spacing: 0.06em;
}

/* ── Info grid ──────────────────────────────────────────────── */
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  align-items: start;
}
@media (max-width: 680px) {
  .info-grid { grid-template-columns: 1fr; }
}

.info-card-full {
  grid-column: 1 / -1;
}

.info-right {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ── Card base ──────────────────────────────────────────────── */
.info-card {
  background: var(--white);
  border: 1px solid var(--cream-300);
  border-radius: var(--radius-lg);
  padding: 14px 16px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

/* Card header with icon */
.card-hd {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 13px;
  padding-bottom: 11px;
  border-bottom: 1px solid var(--cream-300);
}

.hd-icon {
  width: 26px; height: 26px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.hd-dining { background: rgba(110, 207, 111, 0.15); color: var(--term-green); }
.hd-stay   { background: rgba(137, 57, 77, 0.12); color: var(--earth); }
.hd-route  { background: rgba(var(--forest-rgb), 0.2); color: var(--forest-400); }
.hd-tips   { background: rgba(232, 184, 109, 0.15); color: var(--term-amber); }
.hd-budget { background: rgba(var(--forest-rgb), 0.15); color: var(--forest-400); }

.hd-title {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text);
  flex: 1;
}
.hd-sub {
  font-size: 10px;
  color: var(--text-2);
  font-style: normal;
}

/* ── Accommodation ─────────────────────────────────────────── */
.stay-primary {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  margin-bottom: 10px;
  background: rgba(var(--forest-rgb), 0.05);
  border: 1px solid rgba(var(--forest-rgb), 0.14);
  border-radius: 6px;
}
.stay-badge {
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--forest);
  background: rgba(var(--forest-rgb), 0.12);
  padding: 3px 7px;
  border-radius: 4px;
  flex-shrink: 0;
}
.stay-main { flex: 1; min-width: 0; }
.stay-name {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 3px;
}
.stay-meta, .stay-item-meta {
  font-size: 11px;
  color: var(--text-2);
  line-height: 1.45;
}
.stay-sep { margin: 0 4px; opacity: 0.45; }
.stay-reason {
  margin-top: 5px;
  font-size: 10.5px;
  color: var(--text-3);
  line-height: 1.45;
}
.stay-right { flex-shrink: 0; text-align: right; }
.stay-score {
  display: block;
  font-size: 17px;
  font-weight: 700;
  color: var(--forest);
  line-height: 1;
}
.stay-price {
  display: block;
  font-size: 11px;
  color: var(--text-2);
  margin-top: 3px;
}
.stay-list { display: flex; flex-direction: column; }
.stay-item {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--cream-300);
  font-size: 12px;
}
.stay-item:last-child { border-bottom: none; padding-bottom: 0; }
.stay-item-name { color: var(--text); font-weight: 500; }
.stay-tips {
  margin: 10px 0 0;
  padding-left: 16px;
  font-size: 11px;
  color: var(--text-2);
  line-height: 1.55;
}
.stay-tips li { margin-bottom: 4px; }

/* ── Food list ──────────────────────────────────────────────── */
.food-list { display: flex; flex-direction: column; }

.food-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--cream-300);
}
.food-item:first-child { padding-top: 0; }
.food-item:last-child { border-bottom: none; padding-bottom: 0; }

.fi-main { flex: 1; min-width: 0; }
.fi-name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text);
  display: block;
  margin-bottom: 3px;
  letter-spacing: 0.005em;
}
.fi-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  font-size: 11px;
  color: var(--text-2);
  line-height: 1.5;
}
.fi-sep { margin: 0 4px; opacity: 0.5; color: var(--text-3); }

.fi-reason {
  margin-top: 3px;
  font-size: 10.5px;
  color: var(--text-3);
  line-height: 1.45;
}

.fi-right { flex-shrink: 0; text-align: right; display: flex; flex-direction: column; align-items: flex-end; gap: 3px; }
.fi-ds { transform: scale(0.92); transform-origin: right top; }
.fi-score {
  font-size: 18px;
  font-weight: 700;
  color: var(--term-green);
  line-height: 1;
  display: block;
  letter-spacing: 0;
}
.fi-price { font-size: 11px; color: var(--text-2); display: block; margin-top: 3px; }

/* ── Route list ─────────────────────────────────────────────── */
.route-list { list-style: none; display: flex; flex-direction: column; }
.route-list li {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 7px 0;
  border-bottom: 1px solid var(--cream-300);
  font-size: 12px;
}
.route-list li:first-child { padding-top: 0; }
.route-list li:last-child { border-bottom: none; padding-bottom: 0; }

.rl-n {
  width: 17px; height: 17px;
  border-radius: 4px;
  background: rgba(var(--forest-rgb),0.08);
  color: var(--forest);
  font-size: 9.5px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  letter-spacing: 0;
}
.rl-name { flex: 1; font-weight: 600; color: var(--text); }
.rl-mode { font-size: 10.5px; color: var(--text-2); white-space: nowrap; }

/* ── Tips list ──────────────────────────────────────────────── */
.tips-list { list-style: none; display: flex; flex-direction: column; gap: 8px; }
.tips-list li {
  font-size: 12.5px;
  color: var(--text);
  line-height: 1.65;
  padding-left: 14px;
  position: relative;
}
.tips-list li::before {
  content: '';
  position: absolute;
  left: 0; top: 8px;
  width: 6px; height: 1.5px;
  background: var(--term-amber);
  border-radius: 1px;
}

/* ── Budget list ────────────────────────────────────────────── */
.budget-list { display: flex; flex-direction: column; }
.budget-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 7px 0;
  border-bottom: 1px solid var(--cream-300);
  font-size: 12.5px;
}
.budget-row:first-child { padding-top: 0; }
.budget-row:last-child { border-bottom: none; padding-bottom: 0; }
.b-total {
  border-top: 1px solid var(--cream-300);
  border-bottom: none !important;
  padding-top: 9px;
  margin-top: 2px;
}
.b-label { color: var(--text-2); font-size: 12px; }
.b-val { font-weight: 600; color: var(--text); font-variant-numeric: tabular-nums; }
.b-total .b-label { color: var(--text); font-weight: 600; font-size: 12.5px; }
.b-total .b-val   { color: var(--forest-400); font-weight: 700; font-size: 14px; }
</style>
