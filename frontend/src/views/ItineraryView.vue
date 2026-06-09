<template>
  <div class="planner-layout layout-with-sidebar" :class="{ 'sidebar-open': sidebarOpen }">
    <div v-if="sidebarOpen" class="sidebar-backdrop" @click="closeSidebar" />

    <!-- ─── Sidebar ──────────────────────────────────────────── -->
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <!-- Brand -->
      <div class="sb-brand">
        <div class="term-window-dots">
          <span class="dot red"></span>
          <span class="dot yellow"></span>
          <span class="dot green"></span>
        </div>
        <span class="sb-prompt">~/voyage/plan</span>
        <span class="term-prompt">$</span>
      </div>

      <!-- Nav links -->
      <div class="sb-nav">
        <div class="sb-nav-item" @click="router.push('/app')">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <path d="M8 2L4 6l4 4" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          返回智能助手
        </div>
        <div class="sb-nav-item" @click="router.push('/planbook')">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <rect x="2" y="1" width="8" height="10" rx="1.5" stroke="currentColor" stroke-width="1.2"/>
            <path d="M4 4.5h4M4 6.5h2.5" stroke="currentColor" stroke-width="1.1" stroke-linecap="round"/>
          </svg>
          我的规划册
          <span v-if="planBookCount > 0" class="sb-badge">{{ planBookCount }}</span>
        </div>
        <div class="sb-nav-item" @click="router.push('/itinerary/history')">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <circle cx="6" cy="6" r="4.5" stroke="currentColor" stroke-width="1.2"/>
            <path d="M6 3.5V6l2 1.5" stroke="currentColor" stroke-width="1.1" stroke-linecap="round"/>
          </svg>
          历史行程
        </div>
      </div>

      <!-- Form: scrollable -->
      <div class="sb-form">
        <div class="sf-heading">
          <span class="sf-title">规划行程</span>
          <span class="sf-sub">Plan Your Journey</span>
        </div>

        <!-- City -->
        <div class="sf-group">
          <label class="sf-label">推荐目的地</label>
          <div class="sf-chips">
            <button
              v-for="c in recommendedCities" :key="c.code"
              class="sf-chip" :class="{ active: form.cityCode === c.code && !form.cityName }"
              @click="selectRecommendedCity(c.code)"
            >{{ c.name }}</button>
          </div>
          <label class="sf-label sf-label-sub">或自定义</label>
          <div class="sf-custom-row">
            <span class="sf-custom-prefix">$</span>
            <input
              v-model="customCityInput"
              class="sf-custom-input"
              type="text"
              placeholder="输入城市，如 丽江"
              maxlength="20"
              @keydown.enter.prevent="applyCustomCity"
            />
            <button class="sf-custom-btn" :disabled="!customCityInput.trim()" @click="applyCustomCity">确定</button>
          </div>
          <div v-if="form.cityName" class="sf-custom-active">
            已选：<span>{{ form.cityName }}</span>
            <button class="sf-custom-clear" @click="clearCustomCity">×</button>
          </div>
        </div>

        <!-- Dates -->
        <div class="sf-group">
          <label class="sf-label">日期</label>
          <div class="sf-dates">
            <input type="date" v-model="form.startDate" :min="today" class="sf-date" />
            <span class="sf-date-sep">→</span>
            <input type="date" v-model="form.endDate" :min="form.startDate || today" :max="maxEndDate" class="sf-date" />
          </div>
          <span class="sf-hint" :class="{ 'sf-hint-warn': tripDays > 7 }">
            最多 7 天<span v-if="tripDays > 7">（当前 {{ tripDays }} 天）</span>
          </span>
        </div>

        <!-- Preferences -->
        <div class="sf-group">
          <label class="sf-label">偏好</label>
          <div class="sf-chips flex-wrap">
            <button
              v-for="p in PREFERENCES" :key="p.code"
              class="sf-chip" :class="{ active: form.preferences.includes(p.code) }"
              @click="togglePref(p.code)"
            >{{ p.emoji }} {{ p.label }}</button>
          </div>
        </div>

        <!-- Food: dietary & taste -->
        <div class="sf-group sf-food-prefs">
          <label class="sf-label">美食 · 忌口</label>
          <div class="sf-chips flex-wrap">
            <button
              v-for="d in DIETARY_OPTIONS" :key="d.code"
              class="sf-chip sf-chip-diet" :class="{ active: form.dietaryRestrictions.includes(d.code) }"
              @click="toggleDietary(d.code)"
            >{{ d.label }}</button>
          </div>
          <label class="sf-label sf-label-sub">口味偏好</label>
          <div class="sf-chips flex-wrap">
            <button
              v-for="t in TASTE_OPTIONS" :key="t.code"
              class="sf-chip sf-chip-taste" :class="{ active: form.tastePreferences.includes(t.code) }"
              @click="toggleTaste(t.code)"
            >{{ t.emoji }} {{ t.label }}</button>
          </div>
        </div>

        <!-- Budget -->
        <div class="sf-group">
          <label class="sf-label">预算</label>
          <div class="sf-budget">
            <button
              v-for="b in BUDGETS" :key="b.code"
              class="sf-budget-btn" :class="{ active: form.budget === b.code }"
              @click="form.budget = b.code"
            >
              <span class="sfb-label">{{ b.label }}</span>
              <span class="sfb-desc">{{ b.desc }}</span>
            </button>
          </div>
        </div>

        <!-- Accommodation -->
        <div class="sf-group">
          <label class="sf-label">住宿偏好</label>
          <div class="sf-chips flex-wrap">
            <button
              v-for="a in ACCOMMODATION_TYPES" :key="a.code"
              class="sf-chip sf-chip-acc" :class="{ active: form.accommodationType === a.code }"
              @click="form.accommodationType = a.code"
            >{{ a.emoji }} {{ a.label }}</button>
          </div>
        </div>

        <!-- Transport -->
        <div class="sf-group">
          <label class="sf-label">出行方式</label>
          <div class="sf-chips">
            <button
              v-for="t in TRANSPORTS" :key="t.code"
              class="sf-chip" :class="{ active: form.transportMode === t.code }"
              @click="form.transportMode = t.code"
            >{{ t.emoji }} {{ t.label }}</button>
          </div>
        </div>

        <!-- People -->
        <div class="sf-group">
          <label class="sf-label">人数</label>
          <div class="sf-people">
            <div class="sf-counter">
              <span>成人</span>
              <div class="sfc-btns">
                <button @click="form.adults = Math.max(1, form.adults - 1)">−</button>
                <span>{{ form.adults }}</span>
                <button @click="form.adults++">+</button>
              </div>
            </div>
            <div class="sf-counter">
              <span>儿童</span>
              <div class="sfc-btns">
                <button @click="form.children = Math.max(0, form.children - 1)">−</button>
                <span>{{ form.children }}</span>
                <button @click="form.children++">+</button>
              </div>
            </div>
          </div>
        </div>

        <!-- Mode toggle -->
        <div class="sf-group">
          <label class="sf-label">引擎模式</label>
          <div class="sf-mode-toggle">
            <button
              class="sf-mode-btn"
              :class="{ active: !useMultiAgent }"
              @click="useMultiAgent = false"
            >
              <span class="sfm-icon">⚡</span>
              <span class="sfm-label">标准</span>
            </button>
            <button
              class="sf-mode-btn"
              :class="{ active: useMultiAgent }"
              @click="useMultiAgent = true"
            >
              <span class="sfm-icon">◈</span>
              <span class="sfm-label">多智能体</span>
              <span class="sfm-badge">NEW</span>
            </button>
          </div>
        </div>

        <!-- Generate -->
        <button class="sf-generate" :disabled="loading || !canGenerate" @click="generate">
          <span v-if="loading" class="sf-loading">
            <span class="sf-spinner"></span>
            {{ useMultiAgent ? '智能体协作中…' : '生成中…' }}
          </span>
          <span v-else>{{ useMultiAgent ? '启动多智能体' : '生成行程' }}</span>
        </button>

        <div v-if="error" class="sf-error">{{ error }}</div>
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
        <span class="mobile-top-title">{{ result?.cityName || '行程规划' }}</span>
      </div>

      <!-- Empty state -->
      <div v-if="!result && !loading" class="main-empty">
        <div class="empty-mark">
          <svg width="40" height="40" viewBox="0 0 40 40" fill="none">
            <path d="M20 4C11.163 4 4 11.163 4 20s7.163 16 16 16 16-7.163 16-16S28.837 4 20 4z" stroke="var(--cream-300)" stroke-width="1"/>
            <path d="M20 10v10l6 4" stroke="var(--cream-300)" stroke-width="1.2" stroke-linecap="round"/>
          </svg>
        </div>
        <h2 class="empty-title">规划您的专属旅程</h2>
        <p class="empty-desc">在左侧填写目的地与日期，生成每日精选行程</p>
        <div class="empty-features">
          <div class="ef-item">天气驱动，晴雨双方案</div>
          <div class="ef-item">最优路线，减少无效交通</div>
          <div class="ef-item">附近高分餐厅推荐</div>
        </div>
      </div>

      <!-- Multi-agent dashboard (replaces loading) -->
      <div v-else-if="loading && useMultiAgent" class="main-ma">
        <MultiAgentDashboard
          ref="maDashboard"
          @complete="onMultiAgentComplete"
          @error="onMultiAgentError"
        />
      </div>

      <!-- Loading state -->
      <div v-else-if="loading && !useMultiAgent" class="main-loading">
        <!-- Grid texture -->
        <div class="ml-grid"></div>
        <!-- Horizontal scan line -->
        <div class="ml-scan"></div>
        <!-- Corner decorations -->
        <div class="ml-corner ml-tl"></div>
        <div class="ml-corner ml-tr"></div>
        <div class="ml-corner ml-bl"></div>
        <div class="ml-corner ml-br"></div>

        <div class="ml-inner">
          <!-- Left: compass -->
          <div class="ml-left">
            <div class="ml-compass-wrap">
              <!-- Outer glow ring -->
              <div class="ml-glow"></div>
              <svg class="ml-compass-svg" viewBox="0 0 200 200" fill="none" xmlns="http://www.w3.org/2000/svg">
                <!-- Slowly rotating outer graduation ring -->
                <g class="c-spin-outer">
                  <circle cx="100" cy="100" r="91" stroke="rgba(var(--accent-rgb),0.18)" stroke-width="0.5"/>
                  <circle cx="100" cy="100" r="91" stroke="rgba(var(--accent-rgb),0.35)" stroke-width="0.8"
                    stroke-dasharray="2 12.8" stroke-linecap="round" class="c-grad-ring"/>
                </g>

                <!-- Main structural rings -->
                <circle cx="100" cy="100" r="80" stroke="rgba(var(--accent-rgb),0.45)" stroke-width="0.8" class="c-draw c-r1"/>
                <circle cx="100" cy="100" r="62" stroke="rgba(var(--accent-rgb),0.25)" stroke-width="0.5"
                  stroke-dasharray="5 6" class="c-draw c-r2"/>
                <circle cx="100" cy="100" r="44" stroke="rgba(var(--accent-rgb),0.5)" stroke-width="0.8" class="c-draw c-r3"/>
                <circle cx="100" cy="100" r="10" stroke="rgba(var(--accent-rgb),0.7)" stroke-width="1" class="c-draw c-core"/>

                <!-- Cardinal axes (cross) -->
                <line x1="100" y1="20" x2="100" y2="56" stroke="rgba(var(--accent-rgb),0.4)" stroke-width="0.6" class="c-draw c-ax1"/>
                <line x1="100" y1="144" x2="100" y2="180" stroke="rgba(var(--accent-rgb),0.4)" stroke-width="0.6" class="c-draw c-ax1"/>
                <line x1="20" y1="100" x2="56" y2="100" stroke="rgba(var(--accent-rgb),0.4)" stroke-width="0.6" class="c-draw c-ax1"/>
                <line x1="144" y1="100" x2="180" y2="100" stroke="rgba(var(--accent-rgb),0.4)" stroke-width="0.6" class="c-draw c-ax1"/>

                <!-- Diagonal lines (intercardinal) -->
                <line x1="43" y1="43" x2="63" y2="63" stroke="rgba(var(--accent-rgb),0.2)" stroke-width="0.5" class="c-draw c-diag"/>
                <line x1="157" y1="43" x2="137" y2="63" stroke="rgba(var(--accent-rgb),0.2)" stroke-width="0.5" class="c-draw c-diag"/>
                <line x1="43" y1="157" x2="63" y2="137" stroke="rgba(var(--accent-rgb),0.2)" stroke-width="0.5" class="c-draw c-diag"/>
                <line x1="157" y1="157" x2="137" y2="137" stroke="rgba(var(--accent-rgb),0.2)" stroke-width="0.5" class="c-draw c-diag"/>

                <!-- NORTH needle (gold) -->
                <path d="M100 44 L104.5 90 L100 100 L95.5 90 Z" fill="rgba(var(--accent-rgb),0.9)" class="c-needle"/>
                <!-- SOUTH needle (dim) -->
                <path d="M100 156 L104 110 L100 100 L96 110 Z" fill="rgba(255,255,255,0.12)" class="c-needle"/>
                <!-- EAST needle (dim) -->
                <path d="M156 100 L110 104 L100 100 L110 96 Z" fill="rgba(255,255,255,0.08)"/>
                <!-- WEST needle (dim) -->
                <path d="M44 100 L90 104 L100 100 L90 96 Z" fill="rgba(255,255,255,0.08)"/>

                <!-- Center dot -->
                <circle cx="100" cy="100" r="4" fill="rgba(var(--accent-rgb),0.95)" class="c-center-dot"/>
                <circle cx="100" cy="100" r="7" stroke="rgba(var(--accent-rgb),0.4)" stroke-width="1" fill="none" class="c-pulse-ring"/>

                <!-- Cardinal labels -->
                <text x="100" y="16" text-anchor="middle" class="c-cardinal">N</text>
                <text x="100" y="192" text-anchor="middle" class="c-cardinal">S</text>
                <text x="191" y="104" text-anchor="middle" class="c-cardinal">E</text>
                <text x="9" y="104" text-anchor="middle" class="c-cardinal">W</text>
              </svg>
            </div>

            <div class="ml-dest">
              <div class="ml-dest-label">DESTINATION</div>
              <div class="ml-dest-city">{{ loadingCityName }}</div>
              <div class="ml-dest-coords">{{ loadingCityCoords }}</div>
            </div>

            <!-- Total days display -->
            <div class="ml-days-badge">
              <span class="ml-days-num">{{ form.endDate && form.startDate ? Math.max(1, Math.round((new Date(form.endDate).getTime() - new Date(form.startDate).getTime()) / 86400000) + 1) : 1 }}</span>
              <span class="ml-days-unit">DAYS</span>
            </div>
          </div>

          <!-- Right: steps terminal -->
          <div class="ml-right">
            <div class="ml-terminal-header">
              <span class="ml-terminal-dot" style="background:#ff5f57"></span>
              <span class="ml-terminal-dot" style="background:#febc2e"></span>
              <span class="ml-terminal-dot" style="background:#28c840"></span>
              <span class="ml-terminal-title">VOYAGE — ITINERARY AGENT</span>
              <span class="ml-terminal-blink">█</span>
            </div>

            <div class="ml-steps-wrap">
              <div
                v-for="(step, i) in LOADING_STEPS" :key="i"
                class="ml-step-row"
                :class="{ 'step-done': loadingStep > i, 'step-active': loadingStep === i }"
                :style="{ '--delay': i * 0.12 + 's' }"
              >
                <div class="mlsr-left">
                  <!-- Done: small diamond check -->
                  <div v-if="loadingStep > i" class="mlsr-done">
                    <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                      <path d="M1.5 5l2.5 2.5L8.5 2" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                  </div>
                  <!-- Active: pulsing rings -->
                  <div v-else-if="loadingStep === i" class="mlsr-active">
                    <div class="mlsr-pulse-outer"></div>
                    <div class="mlsr-pulse-inner"></div>
                  </div>
                  <!-- Pending -->
                  <div v-else class="mlsr-pending"></div>
                </div>

                <div class="mlsr-body">
                  <span class="mlsr-idx">0{{ i + 1 }}</span>
                  <span class="mlsr-text">{{ step }}</span>
                </div>

                <div class="mlsr-status">
                  <span v-if="loadingStep > i" class="mlsr-tag done-tag">DONE</span>
                  <span v-else-if="loadingStep === i" class="mlsr-tag live-tag">LIVE</span>
                  <span v-else class="mlsr-tag pend-tag">WAIT</span>
                </div>
              </div>
            </div>

            <div class="ml-footer-note">
              <span class="ml-fn-bar"></span>
              正在整合天气、路线与美食数据，请稍候…
            </div>
          </div>
        </div>
      </div>

      <!-- Result -->
      <template v-else-if="result">

        <!-- Trip header -->
        <div class="trip-header">
          <div class="th-left">
            <div class="th-eyebrow">
              <span>{{ result.startDate }} — {{ result.endDate }}</span>
              <span class="th-sep">·</span>
              <span>{{ result.totalDays }} 天</span>
            </div>
            <div class="th-city">{{ result.cityName }}</div>
            <div class="th-summary">{{ result.tripSummary }}</div>
            <div class="th-sources">
              <DataSourceBadge
                :source="tripSources.weather"
                :real="result.hasRealWeatherData"
                kind="weather"
              />
              <DataSourceBadge
                :source="tripSources.poi"
                :real="result.hasRealPoiData"
                kind="poi"
              />
              <DataSourceBadge
                :source="tripSources.food"
                :real="result.hasRealFoodData"
                kind="food"
              />
              <DataSourceBadge
                v-if="tripSources.accommodation"
                :source="tripSources.accommodation"
                :real="result.hasRealAccommodationData"
                kind="accommodation"
              />
              <DataSourceBadge
                v-if="tripSources.route"
                :source="tripSources.route"
                kind="route"
              />
            </div>
          </div>
          <div class="th-right">
            <button class="th-map-btn" @click="showMap = true">查看地图</button>
            <button
              class="th-save-btn"
              :class="{ saved: planBookStore.isSaved(result.itineraryId) }"
              @click="toggleSave"
            >
              {{ planBookStore.isSaved(result.itineraryId) ? '已收藏' : '加入规划册' }}
            </button>
          </div>
        </div>

        <!-- Day navigator -->
        <div class="day-nav">
          <button class="dn-arrow" :disabled="currentDay === 0" @click="goDay(currentDay - 1, 'prev')">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M9 2.5L4.5 7 9 11.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>

          <div class="dn-center">
            <span class="dn-day">DAY {{ String(currentDay + 1).padStart(2, '0') }}</span>
            <span class="dn-date">{{ result.days[currentDay]?.date }} &nbsp;{{ result.days[currentDay]?.dayOfWeek }}</span>
            <div class="dn-ticks">
              <button
                v-for="(_, i) in result.days" :key="i"
                class="dn-tick" :class="{ active: i === currentDay }"
                @click="goDay(i, i > currentDay ? 'next' : 'prev')"
                :aria-label="`第 ${i+1} 天`"
              />
            </div>
          </div>

          <button class="dn-arrow" :disabled="currentDay === result.days.length - 1" @click="goDay(currentDay + 1, 'next')">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M5 2.5L9.5 7 5 11.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
        </div>

        <!-- Day content with slide transition -->
        <div class="day-stage">
          <Transition :name="'slide-' + slideDir" mode="out-in">
            <div class="day-content" :key="currentDay">
              <DayPlanCard
                :day="result.days[currentDay]"
                :accommodations="result.accommodations"
                :primary-accommodation="result.primaryAccommodation"
                :accommodation-tips="result.accommodationTips"
                :has-real-accommodation-data="result.hasRealAccommodationData"
              />
            </div>
          </Transition>
        </div>

      </template>
    </div>

    <ItineraryAttractionMap
      v-if="result"
      :open="showMap"
      :city-name="result.cityName"
      :day-number="currentDay + 1"
      :route="result.days[currentDay]?.route"
      :transport-mode="result.transportMode as 'walking' | 'driving' | 'transit'"
      @close="showMap = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useMobileSidebar } from '@/composables/useMobileSidebar'
import { useRouter, useRoute } from 'vue-router'
import { agentApi, type ItineraryResponse } from '@/api/agent'
import { usePlanBookStore } from '@/stores/planBook'
import DayPlanCard from '@/components/DayPlanCard.vue'
import MultiAgentDashboard from '@/components/MultiAgentDashboard.vue'
import ItineraryAttractionMap from '@/components/ItineraryAttractionMap.vue'
import DataSourceBadge from '@/components/DataSourceBadge.vue'
import { resolveTripDataSources } from '@/utils/dataSource'
import { CITY_GEO } from '@/utils/cityData'
import { useCityStore } from '@/stores/city'
import { toCustomCityCode } from '@/utils/destination'
import { ElMessage } from 'element-plus'

const router = useRouter()
const cityStore = useCityStore()
const route = useRoute()
const { sidebarOpen, toggleSidebar, closeSidebar } = useMobileSidebar()
const planBookStore = usePlanBookStore()
const planBookCount = computed(() => planBookStore.items.length)
const today = new Date().toISOString().split('T')[0]

const FALLBACK_CITIES = [
  { code: 'qingdao', name: '青岛' },
  { code: 'beijing',  name: '北京' },
  { code: 'shanghai', name: '上海' },
  { code: 'xian',     name: '西安' },
  { code: 'chengdu',  name: '成都' },
]

const recommendedCities = computed(() =>
  cityStore.cities.length > 0
    ? cityStore.cities.map(c => ({ code: c.code, name: c.nameCn }))
    : FALLBACK_CITIES
)

const customCityInput = ref('')

const PREFERENCES = [
  { code: 'family',      emoji: '👨‍👩‍👧', label: '亲子' },
  { code: 'couple',      emoji: '💑',    label: '情侣' },
  { code: 'food',        emoji: '🍜',    label: '美食' },
  { code: 'photography', emoji: '📸',    label: '摄影' },
  { code: 'culture',     emoji: '🏛️',    label: '文化' },
  { code: 'budget',      emoji: '💰',    label: '省钱' },
]

const BUDGETS = [
  { code: 'low',    label: '经济', desc: '<300/天' },
  { code: 'medium', label: '舒适', desc: '300-600' },
  { code: 'high',   label: '奢华', desc: '>600/天' },
]

const TRANSPORTS = [
  { code: 'transit',  emoji: '🚇', label: '公交' },
  { code: 'driving',  emoji: '🚗', label: '自驾' },
  { code: 'walking',  emoji: '🚶', label: '步行' },
]

const ACCOMMODATION_TYPES = [
  { code: 'hotel', emoji: '🏨', label: '酒店' },
  { code: 'homestay', emoji: '🏡', label: '民宿' },
  { code: 'hostel', emoji: '🛏️', label: '青旅' },
  { code: 'any', emoji: '✨', label: '不限' },
]

const DIETARY_OPTIONS = [
  { code: '不吃辣', label: '不吃辣' },
  { code: '清真', label: '清真' },
  { code: '素食', label: '素食' },
  { code: '不吃猪肉', label: '不吃猪肉' },
  { code: '海鲜过敏', label: '海鲜过敏' },
  { code: '乳糖不耐', label: '乳糖不耐' },
]

const TASTE_OPTIONS = [
  { code: '本地特色', emoji: '🍲', label: '本地特色' },
  { code: '辣味', emoji: '🌶️', label: '爱吃辣' },
  { code: '清淡', emoji: '🥗', label: '清淡' },
  { code: '甜食', emoji: '🍰', label: '甜食' },
  { code: '街边小吃', emoji: '🍢', label: '街边小吃' },
  { code: '精致料理', emoji: '🍽️', label: '精致料理' },
  { code: '夜市', emoji: '🌙', label: '夜市' },
]

const LOADING_STEPS = [
  '气象分析 + 景点发现',
  '路线优化 + 美食 + 住宿',
  '日程时间编排',
  '预算规划 + 旅行叙事',
  '质量审核与辩论',
]

const form = ref({
  cityCode: 'qingdao',
  cityName: undefined as string | undefined,
  startDate: today,
  endDate: today,
  preferences: [] as string[],
  dietaryRestrictions: [] as string[],
  tastePreferences: [] as string[],
  budget: 'medium',
  transportMode: 'transit',
  accommodationType: 'hotel',
  adults: 2,
  children: 0,
})

const loading = ref(false)
const error = ref('')
const result = ref<ItineraryResponse | null>(null)
const currentDay = ref(0)
const slideDir = ref<'next' | 'prev'>('next')
const loadingStep = ref(0)
const useMultiAgent = ref(true)
const maDashboard = ref<InstanceType<typeof MultiAgentDashboard> | null>(null)
const showMap = ref(false)

const tripSources = computed(() => resolveTripDataSources(result.value?.toolCallLogs))

const loadingCityName = computed(() =>
  form.value.cityName
    ?? recommendedCities.value.find(c => c.code === form.value.cityCode)?.name
    ?? cityStore.displayName(form.value.cityCode)
)

function selectRecommendedCity(code: string) {
  form.value.cityCode = code
  form.value.cityName = undefined
  customCityInput.value = ''
}

function applyCustomCity() {
  const name = customCityInput.value.trim()
  if (!name) return
  if (name.length > 20) {
    ElMessage.warning('目的地名称不超过 20 字')
    return
  }
  const matched = recommendedCities.value.find(c => c.name === name)
  if (matched) {
    selectRecommendedCity(matched.code)
    ElMessage.info(`「${name}」在推荐列表中，已自动选中`)
    return
  }
  form.value.cityCode = toCustomCityCode(name)
  form.value.cityName = name
  cityStore.addCustomDestination(name)
}

function clearCustomCity() {
  form.value.cityName = undefined
  customCityInput.value = ''
  if (!recommendedCities.value.some(c => c.code === form.value.cityCode)) {
    form.value.cityCode = recommendedCities.value[0]?.code ?? 'qingdao'
  }
}
const loadingCityCoords = computed(() => {
  const geo = CITY_GEO[form.value.cityCode]
  return geo ? `${geo.lat.toFixed(3)}°N  ${geo.lng.toFixed(3)}°E` : ''
})

const tripDays = computed(() => {
  if (!form.value.startDate || !form.value.endDate) return 0
  const start = new Date(form.value.startDate)
  const end = new Date(form.value.endDate)
  return Math.round((end.getTime() - start.getTime()) / 86400000) + 1
})

const maxEndDate = computed(() => {
  if (!form.value.startDate) return ''
  const d = new Date(form.value.startDate)
  d.setDate(d.getDate() + 6)
  return d.toISOString().slice(0, 10)
})

const canGenerate = computed(() =>
  !!form.value.cityCode && !!form.value.startDate && !!form.value.endDate
  && tripDays.value >= 1 && tripDays.value <= 7
)

function togglePref(code: string) {
  const idx = form.value.preferences.indexOf(code)
  if (idx >= 0) form.value.preferences.splice(idx, 1)
  else form.value.preferences.push(code)
}

function toggleListItem(list: string[], code: string, max: number) {
  const idx = list.indexOf(code)
  if (idx >= 0) list.splice(idx, 1)
  else if (list.length < max) list.push(code)
}

function toggleDietary(code: string) {
  toggleListItem(form.value.dietaryRestrictions, code, 6)
}

function toggleTaste(code: string) {
  toggleListItem(form.value.tastePreferences, code, 5)
}

function goDay(index: number, dir: 'next' | 'prev') {
  slideDir.value = dir
  currentDay.value = index
}

async function toggleSave() {
  if (!result.value) return
  if (planBookStore.isSaved(result.value.itineraryId)) {
    await planBookStore.remove(result.value.itineraryId)
  } else {
    await planBookStore.save(result.value)
  }
}

async function generate() {
  if (!form.value.cityCode || !form.value.startDate || !form.value.endDate) return
  if (tripDays.value > 7) {
    error.value = '行程最长支持 7 天，请调整日期范围'
    return
  }
  if (tripDays.value < 1) {
    error.value = '结束日期不能早于开始日期'
    return
  }
  if (!canGenerate.value) return
  loading.value = true
  error.value = ''
  result.value = null
  currentDay.value = 0
  loadingStep.value = 0

  if (useMultiAgent.value) {
    // Multi-agent mode: dashboard handles the streaming + results
    // Wait briefly for the dashboard to mount, then trigger
    await new Promise(r => setTimeout(r, 100))
    if (maDashboard.value) {
      maDashboard.value.generate({
        cityCode: form.value.cityCode,
        cityName: form.value.cityName,
        startDate: form.value.startDate,
        endDate: form.value.endDate,
        preferences: form.value.preferences,
        dietaryRestrictions: form.value.dietaryRestrictions.length
          ? form.value.dietaryRestrictions : undefined,
        tastePreferences: form.value.tastePreferences.length
          ? form.value.tastePreferences : undefined,
        budget: form.value.budget as 'low' | 'medium' | 'high',
        transportMode: form.value.transportMode as 'walking' | 'driving' | 'transit',
        accommodationType: form.value.accommodationType as 'hotel' | 'homestay' | 'hostel' | 'any',
        adults: form.value.adults,
        children: form.value.children,
      })
    }
    return // The dashboard will emit 'complete' or 'error'
  }

  // Standard mode: simulate step progress during API call
  const stepTimer = setInterval(() => {
    if (loadingStep.value < LOADING_STEPS.length - 1) loadingStep.value++
  }, 800)

  try {
    result.value = await agentApi.generateItinerary({
      cityCode: form.value.cityCode,
      cityName: form.value.cityName,
      startDate: form.value.startDate,
      endDate: form.value.endDate,
      preferences: form.value.preferences,
      dietaryRestrictions: form.value.dietaryRestrictions.length
        ? form.value.dietaryRestrictions : undefined,
      tastePreferences: form.value.tastePreferences.length
        ? form.value.tastePreferences : undefined,
      budget: form.value.budget as 'low' | 'medium' | 'high',
      transportMode: form.value.transportMode as 'walking' | 'driving' | 'transit',
      accommodationType: form.value.accommodationType as 'hotel' | 'homestay' | 'hostel' | 'any',
      adults: form.value.adults,
      children: form.value.children,
    })
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    error.value = err?.response?.data?.message || '生成失败，请稍后重试'
  } finally {
    clearInterval(stepTimer)
    loading.value = false
  }
}

function onMultiAgentComplete(response: ItineraryResponse) {
  result.value = response
  loading.value = false
}

function onMultiAgentError(err: Error) {
  error.value = err.message || '多智能体生成失败，请稍后重试'
  loading.value = false
}

onMounted(async () => {
  await cityStore.fetchCities()
  await planBookStore.fetchAll()
  const id = route.query.id as string | undefined
  if (id) {
    loading.value = true
    try {
      result.value = await agentApi.getItinerary(id)
    } catch {
      error.value = '加载历史行程失败'
    } finally {
      loading.value = false
    }
  }
})
</script>

<style scoped>
/* ─── Layout ─────────────────────────────────────────── */
.planner-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--cream);
}

/* ─── Sidebar ─────────────────────────────────────────── */
.sidebar {
  width: 260px;
  flex-shrink: 0;
  background: var(--forest);
  display: flex;
  flex-direction: column;
  overflow: hidden;
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

.sb-nav {
  padding: 8px 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  border-bottom: 1px solid rgba(255,255,255,0.07);
  flex-shrink: 0;
}

.sb-nav-item {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 10px;
  border-radius: 7px;
  font-size: 11.5px;
  color: rgba(250, 246, 240, 0.65);
  cursor: pointer;
  transition: all 0.18s;
  letter-spacing: 0.02em;
}
.sb-nav-item:hover { color: rgba(255,255,255,0.65); background: rgba(255,255,255,0.05); }

.sb-badge {
  margin-left: auto;
  background: rgba(var(--accent-rgb),0.2);
  color: var(--gold);
  font-size: 9.5px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 8px;
  min-width: 16px;
  text-align: center;
}

/* Form */
.sb-form {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.sf-heading {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(255,255,255,0.07);
}

.sf-title {
  font-family: 'Cormorant Garamond', serif;
  font-size: 17px;
  font-weight: 600;
  color: rgba(255,255,255,0.88);
  letter-spacing: 0.02em;
}

.sf-sub {
  font-size: 10px;
  color: rgba(255,255,255,0.25);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.sf-group { display: flex; flex-direction: column; gap: 7px; }

.sf-label {
  font-size: 9.5px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.3);
}

.sf-chips { display: flex; flex-wrap: wrap; gap: 5px; }

.sf-chip {
  padding: 4px 10px;
  border: 1px solid rgba(255,255,255,0.14);
  border-radius: 20px;
  background: transparent;
  color: rgba(255,255,255,0.45);
  font-size: 11.5px;
  cursor: pointer;
  transition: all 0.18s;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
}
.sf-chip:hover { border-color: rgba(255,255,255,0.3); color: rgba(255,255,255,0.75); }
.sf-chip.active {
  background: rgba(var(--accent-rgb),0.18);
  border-color: var(--gold);
  color: var(--gold);
}

.sf-label-sub {
  margin-top: 8px;
  font-size: 9px;
  opacity: 0.7;
}

.sf-custom-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(137, 57, 77, 0.45);
  border-radius: var(--radius);
}

.sf-custom-prefix {
  color: var(--term-green);
  font-weight: 700;
  font-size: 12px;
}

.sf-custom-input {
  flex: 1;
  min-width: 0;
  background: transparent;
  border: none;
  outline: none;
  font-size: 11.5px;
  color: rgba(255,255,255,0.85);
  font-family: inherit;
}

.sf-custom-input::placeholder {
  color: rgba(255,255,255,0.3);
}

.sf-custom-btn {
  padding: 3px 8px;
  font-size: 11px;
  border-radius: var(--radius);
  border: 1px solid rgba(110, 207, 111, 0.45);
  background: rgba(110, 207, 111, 0.12);
  color: var(--term-green);
  cursor: pointer;
  font-family: inherit;
}

.sf-custom-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.sf-custom-active {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 11px;
  color: var(--term-amber);
}

.sf-custom-active span { font-weight: 600; }

.sf-custom-clear {
  width: 18px;
  height: 18px;
  border: none;
  background: rgba(255,255,255,0.08);
  color: rgba(255,255,255,0.5);
  border-radius: 3px;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}

.sf-dates { display: flex; align-items: center; gap: 6px; }
.sf-date {
  flex: 1;
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.12);
  border-radius: 6px;
  color: rgba(255,255,255,0.7);
  font-size: 11px;
  padding: 5px 7px;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  min-width: 0;
}
.sf-date::-webkit-calendar-picker-indicator { filter: invert(0.6); }
.sf-date-sep { color: rgba(255,255,255,0.25); font-size: 11px; flex-shrink: 0; }
.sf-hint { font-size: 10px; color: rgba(255,255,255,0.2); }
.sf-hint-warn { color: #f87171; }

.sf-budget { display: flex; gap: 5px; }
.sf-budget-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 7px 4px;
  border: 1px solid rgba(255,255,255,0.12);
  border-radius: 7px;
  background: transparent;
  cursor: pointer;
  transition: all 0.18s;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
}
.sf-budget-btn:hover { border-color: rgba(255,255,255,0.3); background: rgba(255,255,255,0.05); }
.sf-budget-btn.active { border-color: var(--gold); background: rgba(var(--accent-rgb),0.12); }
.sfb-label { font-size: 12px; font-weight: 500; color: rgba(255,255,255,0.7); }
.sfb-desc { font-size: 9.5px; color: rgba(255,255,255,0.3); }
.sf-budget-btn.active .sfb-label { color: var(--gold); }
.sf-budget-btn.active .sfb-desc { color: rgba(var(--accent-rgb),0.6); }

.sf-people { display: flex; flex-direction: column; gap: 7px; }
.sf-counter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: rgba(255,255,255,0.5);
}
.sfc-btns {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(255,255,255,0.06);
  border-radius: 6px;
  padding: 2px 4px;
}
.sfc-btns button {
  width: 20px;
  height: 20px;
  background: none;
  border: none;
  color: rgba(255,255,255,0.5);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  transition: color 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
}
.sfc-btns button:hover { color: rgba(255,255,255,0.9); }
.sfc-btns span { font-size: 13px; color: rgba(255,255,255,0.8); min-width: 16px; text-align: center; }

.sf-generate {
  margin-top: 4px;
  width: 100%;
  padding: 10px;
  background: rgba(var(--accent-rgb),0.2);
  border: 1px solid rgba(var(--accent-rgb),0.4);
  border-radius: 8px;
  color: var(--gold);
  font-size: 13px;
  font-weight: 500;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  cursor: pointer;
  letter-spacing: 0.03em;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.sf-generate:hover:not(:disabled) {
  background: rgba(var(--accent-rgb),0.3);
  border-color: var(--gold);
}
.sf-generate:disabled { opacity: 0.4; cursor: not-allowed; }

.sf-loading { display: flex; align-items: center; gap: 8px; }
.sf-spinner {
  width: 12px; height: 12px;
  border: 1.5px solid rgba(var(--accent-rgb),0.3);
  border-top-color: var(--gold);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.sf-error {
  font-size: 11.5px;
  color: rgba(255,120,100,0.9);
  background: rgba(255,80,60,0.08);
  border: 1px solid rgba(255,80,60,0.15);
  border-radius: 6px;
  padding: 8px 10px;
  line-height: 1.5;
}

/* Mode toggle */
.sf-mode-toggle {
  display: flex;
  gap: 6px;
}

.sf-mode-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 10px;
  border: 1px solid rgba(255,255,255,0.12);
  border-radius: 8px;
  background: rgba(255,255,255,0.04);
  color: rgba(255,255,255,0.6);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}
.sf-mode-btn:hover {
  background: rgba(255,255,255,0.08);
  color: rgba(255,255,255,0.8);
}
.sf-mode-btn.active {
  background: rgba(255,255,255,0.12);
  border-color: var(--gold);
  color: #fff;
}

.sfm-icon { font-size: 14px; }

.sfm-label {
  font-weight: 500;
}

.sfm-badge {
  position: absolute;
  top: -6px;
  right: 2px;
  background: var(--gold);
  color: #fff;
  font-size: 8px;
  font-weight: 700;
  padding: 1px 4px;
  border-radius: 3px;
  letter-spacing: 0.5px;
}

/* Multi-agent dashboard area */
.main-ma {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: var(--cream);
}

/* ─── Main ─────────────────────────────────────────────── */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

/* Empty state */
.main-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 40px;
  gap: 14px;
}

.empty-mark {
  margin-bottom: 12px;
  opacity: 0.4;
}

.empty-title {
  font-family: 'Cormorant Garamond', serif;
  font-size: 28px;
  font-weight: 500;
  color: var(--text);
  letter-spacing: 0.01em;
}
.empty-desc {
  font-size: 13px;
  color: var(--text-3);
  text-align: center;
  max-width: 320px;
  line-height: 1.7;
}
.empty-features {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;
  border-top: 1px solid var(--cream-300);
  padding-top: 16px;
  width: 280px;
}
.ef-item {
  font-size: 12px;
  color: var(--text-3);
  padding: 4px 0;
  letter-spacing: 0.01em;
  border-bottom: 1px solid var(--cream-300);
}
.ef-item:last-child { border-bottom: none; }

/* Loading state */
.main-loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 28px;
}

.ml-label {
  font-family: 'Cormorant Garamond', serif;
  font-size: 22px;
  font-weight: 500;
  color: var(--text);
  letter-spacing: 0.01em;
}

.ml-track {
  display: flex;
  flex-direction: column;
  gap: 0;
  width: 280px;
  border-top: 1px solid var(--cream-300);
}

.ml-step {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px solid var(--cream-300);
  opacity: 0.28;
  transition: opacity 0.4s;
}
.ml-step.active, .ml-step.done { opacity: 1; }

.mls-bar {
  width: 24px;
  height: 2px;
  background: var(--cream-300);
  flex-shrink: 0;
  overflow: hidden;
}
.ml-step.done .mls-bar { background: var(--forest); }
.ml-step.active .mls-bar { background: var(--cream-300); }

.mls-fill {
  height: 100%;
  width: 0%;
  background: var(--gold);
}
.ml-step.active .mls-fill {
  width: 60%;
  animation: fill-bar 0.8s ease-in-out infinite alternate;
}
@keyframes fill-bar {
  from { width: 20%; }
  to { width: 90%; }
}

.mls-text {
  font-size: 12px;
  color: var(--text-2);
  letter-spacing: 0.01em;
}
.ml-step.done .mls-text { color: var(--text-3); }

.ml-sub { font-size: 11.5px; color: var(--text-3); letter-spacing: 0.02em; }

/* ─── Trip header ─────────────────────────────────────── */
.trip-header {
  padding: 24px 36px 20px;
  border-bottom: 1px solid var(--cream-300);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  flex-shrink: 0;
  background: var(--cream);
}

.th-left { display: flex; flex-direction: column; gap: 0; min-width: 0; flex: 1; }
.th-right { flex-shrink: 0; display: flex; align-items: flex-start; padding-top: 4px; }

.th-eyebrow {
  font-size: 11px;
  color: var(--text-3);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.th-sep { opacity: 0.4; }

.th-city {
  font-family: 'Cormorant Garamond', serif;
  font-size: 34px;
  font-weight: 500;
  color: var(--text);
  letter-spacing: -0.01em;
  line-height: 1.05;
  margin-bottom: 8px;
}

.th-summary {
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.7;
  max-width: 520px;
  margin-bottom: 12px;
  font-style: italic;
}

.th-sources {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.th-map-btn {
  padding: 7px 16px;
  border: 1px solid var(--gold);
  background: transparent;
  color: var(--gold);
  font-size: 11.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  white-space: nowrap;
  letter-spacing: 0.03em;
  margin-right: 8px;
}
.th-map-btn:hover {
  background: var(--gold);
  color: #fff;
}

.th-save-btn {
  padding: 7px 16px;
  border: 1px solid var(--cream-300);
  background: transparent;
  color: var(--text-3);
  font-size: 11.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  white-space: nowrap;
  letter-spacing: 0.03em;
}
.th-save-btn:hover {
  border-color: var(--text-2);
  color: var(--text);
}
.th-save-btn.saved {
  background: var(--forest);
  border-color: var(--forest);
  color: rgba(255,255,255,0.9);
}

/* ─── Day navigator ──────────────────────────────────── */
.day-nav {
  display: flex;
  align-items: center;
  padding: 12px 36px;
  border-bottom: 1px solid var(--cream-300);
  flex-shrink: 0;
  background: var(--cream);
  gap: 20px;
}

.dn-arrow {
  width: 28px;
  height: 28px;
  border: 1px solid var(--cream-300);
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-3);
  transition: all 0.18s;
  flex-shrink: 0;
}
.dn-arrow:hover:not(:disabled) { border-color: var(--text-2); color: var(--text); }
.dn-arrow:disabled { opacity: 0.2; cursor: not-allowed; }

.dn-center {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 14px;
}

.dn-day {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--text);
  font-family: 'SF Mono', 'JetBrains Mono', monospace;
  white-space: nowrap;
}

.dn-date {
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
  letter-spacing: 0.02em;
}

.dn-ticks {
  display: flex;
  gap: 4px;
  margin-left: auto;
  align-items: center;
}

.dn-tick {
  width: 20px;
  height: 2px;
  background: var(--cream-300);
  border: none;
  cursor: pointer;
  padding: 0;
  transition: all 0.2s;
}
.dn-tick:hover { background: var(--text-3); }
.dn-tick.active { background: var(--forest); }

/* ─── Day stage ──────────────────────────────────────── */
.day-stage {
  flex: 1;
  overflow: hidden;
  position: relative;
}

.day-content {
  height: 100%;
  overflow-y: auto;
  padding: 0;
  /* DayPlanCard handles its own inner padding */
}

/* Slide transitions */
.slide-next-enter-active,
.slide-next-leave-active,
.slide-prev-enter-active,
.slide-prev-leave-active {
  transition: transform 0.32s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.25s ease;
}

.slide-next-enter-from  { transform: translateX(48px); opacity: 0; }
.slide-next-leave-to    { transform: translateX(-48px); opacity: 0; }
.slide-prev-enter-from  { transform: translateX(-48px); opacity: 0; }
.slide-prev-leave-to    { transform: translateX(48px); opacity: 0; }
</style>
