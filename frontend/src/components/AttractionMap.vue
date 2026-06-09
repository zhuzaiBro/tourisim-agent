<template>
  <Teleport to="body">
    <Transition name="am-root">
      <div v-if="open" class="am-root">

        <!-- ══ TOP BAR ══════════════════════════════════ -->
        <header class="am-header">
          <div class="am-brand">
            <span class="am-brand-icon">◎</span>
            <span class="am-brand-name">青岛<em>景点探索</em></span>
          </div>

          <!-- Category filter -->
          <div class="am-cats">
            <button
              v-for="c in CATEGORIES"
              :key="c.id"
              class="am-cat"
              :class="{ active: activeCats.has(c.id) }"
              @click="toggleCat(c.id)"
            >
              {{ c.emoji }} {{ c.label }}
            </button>
          </div>

          <div class="am-header-right">
            <!-- Route toggle -->
            <button
              class="am-route-toggle"
              :class="{ active: showRoute }"
              @click="showRoute = !showRoute"
              title="行程规划"
            >
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                <circle cx="2.5" cy="3" r="1.5" stroke="currentColor" stroke-width="1.2"/>
                <circle cx="10.5" cy="10" r="1.5" stroke="currentColor" stroke-width="1.2"/>
                <path d="M2.5 4.5C2.5 7 5 6 6.5 6.5S10.5 7.5 10.5 8.5" stroke="currentColor" stroke-width="1.2" stroke-dasharray="2 1.5"/>
              </svg>
              行程规划
              <span v-if="routeStops.length" class="am-badge">{{ routeStops.length }}</span>
            </button>

            <!-- Tile layer toggle -->
            <button class="am-tile-toggle" @click="cycleTile" :title="'当前：' + currentTileName">
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                <rect x="1" y="1" width="5" height="5" rx="1" stroke="currentColor" stroke-width="1.2"/>
                <rect x="7" y="1" width="5" height="5" rx="1" stroke="currentColor" stroke-width="1.2"/>
                <rect x="1" y="7" width="5" height="5" rx="1" stroke="currentColor" stroke-width="1.2"/>
                <rect x="7" y="7" width="5" height="5" rx="1" stroke="currentColor" stroke-width="1.2"/>
              </svg>
              {{ currentTileName }}
            </button>

            <button class="am-close" @click="$emit('close')">
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                <path d="M1.5 1.5l10 10M11.5 1.5l-10 10" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
              </svg>
            </button>
          </div>
        </header>

        <!-- ══ BODY ════════════════════════════════════ -->
        <div class="am-body">

          <!-- Map container -->
          <div ref="mapEl" class="am-map" />

          <!-- Map attribution -->
          <div class="am-attr">© OpenStreetMap · CartoDB · 高德地图</div>

          <!-- Stats bubble -->
          <div class="am-stats-bubble">
            <span class="am-stat-num">{{ visibleCount }}</span>
            <span class="am-stat-label">景点已显示</span>
          </div>

          <!-- ── Route sidebar ──────────────────────── -->
          <Transition name="am-sidebar">
            <aside v-if="showRoute" class="am-route-panel">
              <div class="am-rp-head">
                <span class="am-rp-title">我的行程</span>
                <button v-if="routeStops.length" class="am-rp-clear" @click="clearRoute">清空</button>
              </div>

              <div v-if="!routeStops.length" class="am-rp-empty">
                <div class="am-rp-empty-icon">🗺️</div>
                <p>点击景点卡片上的「加入行程」，<br>自动规划最佳路线</p>
              </div>

              <div v-else class="am-rp-list">
                <div
                  v-for="(stop, i) in routeStops"
                  :key="stop.id"
                  class="am-rp-item"
                >
                  <div class="am-rp-seq">{{ i + 1 }}</div>
                  <div class="am-rp-info">
                    <div class="am-rp-name">{{ stop.name }}</div>
                    <div class="am-rp-meta">{{ stop.category_label }} · {{ stop.fee }}</div>
                  </div>
                  <div class="am-rp-actions">
                    <button v-if="i > 0" class="am-rp-move" @click="moveStop(i, -1)" title="上移">↑</button>
                    <button v-if="i < routeStops.length - 1" class="am-rp-move" @click="moveStop(i, 1)" title="下移">↓</button>
                    <button class="am-rp-remove" @click="removeStop(stop.id)">×</button>
                  </div>
                </div>

                <!-- Route stats -->
                <div class="am-rp-stats">
                  <div class="am-rp-stat">
                    <span class="am-rp-stat-val">{{ routeStops.length }}</span>
                    <span class="am-rp-stat-key">个景点</span>
                  </div>
                  <div class="am-rp-stat">
                    <span class="am-rp-stat-val">{{ estimatedHours }}</span>
                    <span class="am-rp-stat-key">h 预估游览</span>
                  </div>
                  <div class="am-rp-stat">
                    <span class="am-rp-stat-val">{{ totalDistance }}</span>
                    <span class="am-rp-stat-key">km 步行距离</span>
                  </div>
                </div>

                <!-- Navigation buttons -->
                <div class="am-rp-nav-btns">
                  <button class="am-rp-nav-btn gaode" @click="openGaodeRoute">
                    <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                      <circle cx="6" cy="6" r="5" stroke="currentColor" stroke-width="1.2"/>
                      <path d="M6 3v3.5l2 1" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                    </svg>
                    高德地图导航
                  </button>
                  <button class="am-rp-nav-btn baidu" @click="openBaiduRoute">
                    <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                      <circle cx="6" cy="6" r="5" stroke="currentColor" stroke-width="1.2"/>
                      <path d="M4 6l2-2 2 2" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                    百度地图导航
                  </button>
                  <button class="am-rp-nav-btn export" @click="exportItinerary">
                    <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                      <rect x="1" y="1" width="10" height="10" rx="2" stroke="currentColor" stroke-width="1.2"/>
                      <path d="M4 6h4M6 4v4" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                    </svg>
                    复制行程文字
                  </button>
                </div>
                <div v-if="exportCopied" class="am-rp-copied">✓ 已复制到剪贴板</div>
              </div>
            </aside>
          </Transition>
        </div>

        <!-- ══ DETAIL PANEL ════════════════════════════ -->
        <Transition name="am-detail">
          <div v-if="selected" class="am-detail">
            <!-- Close -->
            <button class="am-detail-close" @click="selected = null">
              <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                <path d="M1 1l10 10M11 1L1 11" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
              </svg>
            </button>

            <!-- Emoji hero -->
            <div class="am-detail-hero" :style="{ background: CAT_COLORS[selected.category].bg }">
              <span class="am-detail-emoji">{{ selected.emoji }}</span>
              <div class="am-detail-cat-badge" :style="{ color: CAT_COLORS[selected.category].text, borderColor: CAT_COLORS[selected.category].border }">
                {{ selected.category_label }}
              </div>
            </div>

            <!-- Content -->
            <div class="am-detail-body">
              <div class="am-detail-head">
                <div>
                  <h2 class="am-detail-name">{{ selected.name }}</h2>
                  <div class="am-detail-tags">
                    <span v-for="tag in selected.tags" :key="tag" class="am-detail-tag">{{ tag }}</span>
                  </div>
                </div>
                <div class="am-detail-rating">
                  <div class="am-rating-stars">
                    <span v-for="i in 5" :key="i" :class="i <= Math.round(selected.rating) ? 'star-on' : 'star-off'">★</span>
                  </div>
                  <span class="am-rating-val">{{ selected.rating }}</span>
                </div>
              </div>

              <p class="am-detail-desc">{{ selected.description }}</p>

              <div class="am-detail-meta-row">
                <div class="am-detail-meta-item">
                  <span class="am-detail-meta-icon">🕐</span>
                  <div>
                    <div class="am-detail-meta-label">开放时间</div>
                    <div class="am-detail-meta-val">{{ selected.openHours }}</div>
                  </div>
                </div>
                <div class="am-detail-meta-item">
                  <span class="am-detail-meta-icon">🎫</span>
                  <div>
                    <div class="am-detail-meta-label">门票</div>
                    <div class="am-detail-meta-val">{{ selected.fee }}</div>
                  </div>
                </div>
                <div class="am-detail-meta-item">
                  <span class="am-detail-meta-icon">⏱</span>
                  <div>
                    <div class="am-detail-meta-label">建议游玩</div>
                    <div class="am-detail-meta-val">{{ selected.duration }}</div>
                  </div>
                </div>
              </div>

              <div v-if="selected.tips" class="am-detail-tips">
                <span class="am-tips-label">💡 小贴士</span>
                {{ selected.tips }}
              </div>

              <!-- Actions -->
              <div class="am-detail-actions">
                <button
                  class="am-action-btn primary"
                  :class="{ added: isInRoute(selected.id) }"
                  @click="toggleRoute(selected)"
                >
                  <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                    <path v-if="!isInRoute(selected.id)" d="M6.5 1v11M1 6.5h11" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
                    <path v-else d="M2 7l3 3 6-6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                  {{ isInRoute(selected.id) ? '已加入行程' : '加入行程' }}
                </button>

                <button class="am-action-btn secondary" @click="focusOnMap(selected)">
                  <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                    <circle cx="5.5" cy="5.5" r="4" stroke="currentColor" stroke-width="1.2"/>
                    <path d="M8.5 8.5l3 3" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                    <path d="M5.5 3.5v4M3.5 5.5h4" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                  </svg>
                  地图定位
                </button>

                <button class="am-action-btn secondary" @click="openGaodeSearch(selected)">
                  <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                    <path d="M6.5 1C4.01 1 2 3.01 2 5.5c0 3.37 4.5 7.5 4.5 7.5s4.5-4.13 4.5-7.5C11 3.01 8.99 1 6.5 1z" stroke="currentColor" stroke-width="1.2"/>
                    <circle cx="6.5" cy="5.5" r="1.5" stroke="currentColor" stroke-width="1.1"/>
                  </svg>
                  高德搜索
                </button>
              </div>
            </div>
          </div>
        </Transition>

      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted, nextTick } from 'vue'

defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

// ── Types ───────────────────────────────────────────
interface Attraction {
  id: string
  name: string
  category: string
  category_label: string
  lat: number
  lng: number
  emoji: string
  description: string
  rating: number
  openHours: string
  fee: string
  duration: string
  tips: string
  tags: string[]
}

// ── Constants ───────────────────────────────────────
const CATEGORIES = [
  { id: 'all', label: '全部', emoji: '🗺️' },
  { id: 'landmark', label: '地标', emoji: '🏛️' },
  { id: 'beach', label: '海滩', emoji: '🏖️' },
  { id: 'park', label: '公园', emoji: '🌿' },
  { id: 'cultural', label: '文化', emoji: '🎭' },
  { id: 'food', label: '美食街', emoji: '🍺' },
]

const CAT_COLORS: Record<string, { bg: string; text: string; border: string; marker: string }> = {
  landmark: { bg: 'rgba(58,143,110,0.08)', text: '#3A8F6E', border: 'rgba(58,143,110,0.3)', marker: '#3A8F6E' },
  beach:    { bg: 'rgba(14,116,144,0.08)', text: '#0e7490', border: 'rgba(14,116,144,0.3)', marker: '#0891B2' },
  park:     { bg: 'rgba(22,101,52,0.08)', text: '#166534', border: 'rgba(22,101,52,0.3)', marker: '#16A34A' },
  cultural: { bg: 'rgba(124,58,237,0.08)', text: '#7c3aed', border: 'rgba(124,58,237,0.3)', marker: '#7C3AED' },
  food:     { bg: 'rgba(82,183,136,0.08)', text: '#52B788', border: 'rgba(82,183,136,0.3)', marker: '#52B788' },
}

const TILE_LAYERS = [
  {
    name: '高德标准',
    url: 'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
    subdomains: '1234',
    maxZoom: 19,
  },
  {
    name: '高德卫星',
    url: 'https://webst0{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}',
    subdomains: '1234',
    maxZoom: 19,
  },
  {
    name: '简洁风格',
    url: 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png',
    subdomains: 'abcd',
    maxZoom: 19,
  },
]

const ATTRACTIONS: Attraction[] = [
  {
    id: 'zhan-qiao',
    name: '栈桥',
    category: 'landmark',
    category_label: '历史地标',
    lat: 36.0595, lng: 120.3119,
    emoji: '🌊',
    description: '青岛最具代表性的历史地标，建于1891年，伸入胶州湾约440米。桥南端的回澜阁是青岛的城市标志，登阁可俯瞰胶州湾全景。',
    rating: 4.8,
    openHours: '全天开放（回澜阁 8:00–17:30）',
    fee: '免费（回澜阁 6元）',
    duration: '1–2 小时',
    tips: '清晨人少，可拍到海鸥飞翔、海浪拍岸的绝美照片。',
    tags: ['网红打卡', '历史', '夕阳', '海景'],
  },
  {
    id: 'haijie',
    name: '青岛海底世界',
    category: 'landmark',
    category_label: '景点',
    lat: 36.0583, lng: 120.3267,
    emoji: '🐠',
    description: '国家4A级旅游景区，包含水下隧道、鱼缸展区、海洋生物表演等。可近距离欣赏鲨鱼、海龟等珍稀海洋生物。',
    rating: 4.3,
    openHours: '08:30–17:30',
    fee: '120元',
    duration: '2–3 小时',
    tips: '儿童必去，配合第一海水浴场游玩效果最佳。',
    tags: ['亲子', '海洋', '室内'],
  },
  {
    id: 'beach-1',
    name: '第一海水浴场',
    category: 'beach',
    category_label: '海滩',
    lat: 36.0573, lng: 120.3290,
    emoji: '🏖️',
    description: '青岛最著名的浴场之一，沙滩细腻洁白，海水清澈，背靠汇泉角，坐拥优美的弧形海岸线，夏季游客众多。',
    rating: 4.6,
    openHours: '全天（游泳区夏季 7:00–19:00）',
    fee: '免费',
    duration: '2–4 小时',
    tips: '夏季建议早上9点前到达，避开拥挤时段。防晒不可少。',
    tags: ['游泳', '沙滩', '夏季必去'],
  },
  {
    id: 'beach-2',
    name: '第二海水浴场',
    category: 'beach',
    category_label: '海滩',
    lat: 36.0553, lng: 120.3381,
    emoji: '🌅',
    description: '八大关景区旁的天然海滩，人流量相对较少，沙质细软，周边别墅林立，环境清幽，是避开人群的好去处。',
    rating: 4.4,
    openHours: '全天开放',
    fee: '免费',
    duration: '1–3 小时',
    tips: '傍晚人少，适合散步看日落。周边八大关别墅群可顺路游览。',
    tags: ['安静', '海滩', '适合情侣'],
  },
  {
    id: 'badaguan',
    name: '八大关景区',
    category: 'landmark',
    category_label: '历史建筑',
    lat: 36.0570, lng: 120.3402,
    emoji: '🏡',
    description: '因八条以关隘命名的道路而得名，聚集了德、俄、英、法、日等二十余国不同风格的建筑200余栋，被誉为"万国建筑博览会"，四季皆美。',
    rating: 4.7,
    openHours: '全天开放',
    fee: '免费',
    duration: '1.5–3 小时',
    tips: '春天花树盛开，秋天红叶似火，是婚纱摄影圣地。建议骑行或步行游览。',
    tags: ['建筑', '花园', '浪漫', '摄影'],
  },
  {
    id: 'xiaoyu-hill',
    name: '小鱼山公园',
    category: 'park',
    category_label: '观景公园',
    lat: 36.0640, lng: 120.3220,
    emoji: '🗼',
    description: '青岛著名的城市山头公园，揽阁建于山顶，高18米，登顶可360°俯瞰青岛老城区、栈桥、海湾全景，是拍摄青岛全景的最佳视点。',
    rating: 4.6,
    openHours: '06:00–18:30',
    fee: '5元',
    duration: '1–1.5 小时',
    tips: '清晨登山，可在薄雾中俯瞰老城，极为壮观。山路台阶较多，穿舒适鞋。',
    tags: ['全景', '摄影', '登山', '老城'],
  },
  {
    id: 'signal-hill',
    name: '信号山公园',
    category: 'park',
    category_label: '历史公园',
    lat: 36.0672, lng: 120.3196,
    emoji: '⛰️',
    description: '德占青岛时期建造的信号发报站旧址，山顶有三座红色蘑菇亭，内有电动旋转展厅，可360°观览青岛市容与海湾风光。',
    rating: 4.4,
    openHours: '08:30–17:30',
    fee: '10元',
    duration: '1–1.5 小时',
    tips: '红蘑菇亭是青岛独特的地标，内有旋转观光台，值得体验。',
    tags: ['历史', '观景', '德国建筑', '旋转观光'],
  },
  {
    id: 'catholic-church',
    name: '圣弥厄尔大教堂',
    category: 'landmark',
    category_label: '历史建筑',
    lat: 36.0706, lng: 120.3175,
    emoji: '⛪',
    description: '青岛地标建筑，德国哥特式罗马风格，建于1934年，双塔高56米，是中国最大的哥特式建筑之一，内部壁画精美。',
    rating: 4.5,
    openHours: '08:30–17:00（周日礼拜时段限游览）',
    fee: '免费（室内10元）',
    duration: '0.5–1 小时',
    tips: '教堂广场周边是老城区步行游览的核心地带，可结合劈柴院一起游览。',
    tags: ['建筑', '历史', '欧式', '宗教'],
  },
  {
    id: 'pichayuan',
    name: '劈柴院',
    category: 'food',
    category_label: '特色街区',
    lat: 36.0716, lng: 120.3185,
    emoji: '🍻',
    description: '青岛最具老城风情的市井街巷，名吃汇聚，烤肉串、海鲜、锅贴、芝麻糊……各种青岛传统小吃应有尽有，夜晚灯火辉煌，烟火气十足。',
    rating: 4.3,
    openHours: '10:00–23:00（夜市 17:00后最热闹）',
    fee: '免费进入',
    duration: '1–2 小时',
    tips: '肚子饿再来，带足现金，夜晚更热闹。避开饭点高峰（18–20点）可少排队。',
    tags: ['美食', '夜市', '小吃', '市井'],
  },
  {
    id: 'beer-museum',
    name: '青岛啤酒博物馆',
    category: 'cultural',
    category_label: '工业博物馆',
    lat: 36.0843, lng: 120.3726,
    emoji: '🍺',
    description: '依托1903年德国人创建的原青岛啤酒厂改建，展示百年酿造历史与工艺，含参观流水线、品尝区，是了解青岛文化的必去地标。',
    rating: 4.5,
    openHours: '08:30–17:30（最后入场17:00）',
    fee: '80元（含2杯生啤）',
    duration: '1.5–2 小时',
    tips: '现场试饮的「原浆啤酒」最新鲜，外面买不到。建议平日前往，周末人多。',
    tags: ['博物馆', '啤酒', '工业', '体验'],
  },
  {
    id: 'wusi-square',
    name: '五四广场',
    category: 'landmark',
    category_label: '城市广场',
    lat: 36.0608, lng: 120.3739,
    emoji: '🔥',
    description: '青岛新城区的核心广场，标志雕塑"五月的风"高18米，以红色螺旋形态象征五四运动。广场濒临汇泉湾，视野开阔，夜晚灯光璀璨。',
    rating: 4.4,
    openHours: '全天开放',
    fee: '免费',
    duration: '0.5–1 小时',
    tips: '傍晚时分最美，可欣赏夕阳映照下的红色雕塑与海湾。周边奥帆中心步行可达。',
    tags: ['城市地标', '夜景', '雕塑', '海边'],
  },
  {
    id: 'olympic-sailing',
    name: '奥帆中心',
    category: 'landmark',
    category_label: '现代地标',
    lat: 36.0648, lng: 120.3818,
    emoji: '⛵',
    description: '2008年北京奥运会帆船比赛举办地，现已改造为奥帆博物馆与游艇码头。海风徐徐，百余艘游艇停泊，步道景色宜人。',
    rating: 4.3,
    openHours: '全天开放（奥帆博物馆 09:00–17:00）',
    fee: '免费（博物馆 20元）',
    duration: '1–1.5 小时',
    tips: '可租赁游艇出海体验帆船运动，需提前预约。与五四广场相距仅1公里，可一并游览。',
    tags: ['奥运', '帆船', '海边', '现代'],
  },
  {
    id: 'zhongshan-park',
    name: '中山公园',
    category: 'park',
    category_label: '城市公园',
    lat: 36.0641, lng: 120.3382,
    emoji: '🌸',
    description: '青岛最大的市区综合性公园，占地近80公顷，春季樱花烂漫，夏秋绿荫葱郁。园内有青岛动物园、儿童乐园，是市民最爱的休闲绿肺。',
    rating: 4.3,
    openHours: '06:00–20:00',
    fee: '免费（动物园10元）',
    duration: '1–2 小时',
    tips: '4月樱花季是全年最美时节，花期约两周，游客极多，建议工作日前往。',
    tags: ['公园', '樱花', '亲子', '动物园'],
  },
  {
    id: 'navy-museum',
    name: '海军博物馆',
    category: 'cultural',
    category_label: '军事博物馆',
    lat: 36.0589, lng: 120.3178,
    emoji: '⚓',
    description: '展示中国人民解放军海军发展历史的专题博物馆，馆内陈列有实物战舰、潜艇、舰载机等重型装备，可近距离登舰参观，颇为震撼。',
    rating: 4.5,
    openHours: '08:30–17:00（周一闭馆）',
    fee: '50元',
    duration: '1.5–2.5 小时',
    tips: '可登上退役驱逐舰和潜艇参观，军事迷和孩子必去。旺季建议提前买票。',
    tags: ['博物馆', '军事', '潜艇', '历史'],
  },
]

// ── State ───────────────────────────────────────────
const mapEl = ref<HTMLElement | null>(null)
const selected = ref<Attraction | null>(null)
const routeStops = ref<Attraction[]>([])
const showRoute = ref(false)
const activeCats = ref(new Set(['all']))
const tileIdx = ref(0)
const exportCopied = ref(false)

let mapInstance: any = null
let tileLayer: any = null
let markerMap: Map<string, any> = new Map()
let routeLine: any = null

const currentTileName = computed(() => TILE_LAYERS[tileIdx.value].name)

const filteredAttractions = computed(() => {
  if (activeCats.value.has('all')) return ATTRACTIONS
  return ATTRACTIONS.filter(a => activeCats.value.has(a.category))
})

const visibleCount = computed(() => filteredAttractions.value.length)

const estimatedHours = computed(() => {
  const perStop = 1.5 // avg 1.5h per attraction
  return (routeStops.value.length * perStop).toFixed(1)
})

const totalDistance = computed(() => {
  if (routeStops.value.length < 2) return '0'
  let dist = 0
  for (let i = 1; i < routeStops.value.length; i++) {
    dist += haversine(
      routeStops.value[i - 1].lat, routeStops.value[i - 1].lng,
      routeStops.value[i].lat, routeStops.value[i].lng,
    )
  }
  return dist.toFixed(1)
})

// ── Lifecycle ───────────────────────────────────────
const { open } = defineProps<{ open: boolean }>()

watch(() => open, async (val) => {
  if (val) {
    await nextTick()
    await initMap()
  } else {
    destroyMap()
  }
})

onUnmounted(destroyMap)

// ── Map setup ───────────────────────────────────────
async function initMap() {
  if (!mapEl.value) return
  const L = (await import('leaflet')).default
  await import('leaflet/dist/leaflet.css')

  mapInstance = L.map(mapEl.value, {
    center: [36.0671, 120.3380],
    zoom: 14,
    zoomControl: false,
    attributionControl: false,
  })

  L.control.zoom({ position: 'topright' }).addTo(mapInstance)

  // Initial tile
  applyTile(L)

  // Add markers
  renderMarkers(L)

  // Route line placeholder
  routeLine = L.polyline([], {
    color: '#52B788',
    weight: 3,
    opacity: 0.8,
    dashArray: '8 6',
  }).addTo(mapInstance)
}

function applyTile(L: any) {
  if (tileLayer) { tileLayer.remove(); tileLayer = null }
  const t = TILE_LAYERS[tileIdx.value]
  tileLayer = L.tileLayer(t.url, { maxZoom: t.maxZoom, subdomains: t.subdomains }).addTo(mapInstance)
}

async function cycleTile() {
  tileIdx.value = (tileIdx.value + 1) % TILE_LAYERS.length
  if (!mapInstance) return
  const L = (await import('leaflet')).default
  applyTile(L)
}

function renderMarkers(L: any) {
  markerMap.forEach(m => m.remove())
  markerMap.clear()

  for (const attr of ATTRACTIONS) {
    const color = CAT_COLORS[attr.category]?.marker || '#3A8F6E'
    const icon = L.divIcon({
      className: '',
      html: `<div class="am-pin" style="--pin-color:${color}">
        <span class="am-pin-emoji">${attr.emoji}</span>
      </div>`,
      iconSize: [40, 48],
      iconAnchor: [20, 46],
    })

    const marker = L.marker([attr.lat, attr.lng], { icon })
      .addTo(mapInstance)
      .on('click', () => { selected.value = attr })

    markerMap.set(attr.id, marker)
  }
}

function destroyMap() {
  if (mapInstance) { mapInstance.remove(); mapInstance = null }
  markerMap.clear()
  tileLayer = null
  routeLine = null
}

// Update markers visibility when category filter changes
watch(filteredAttractions, () => {
  markerMap.forEach((marker, id) => {
    const visible = filteredAttractions.value.some(a => a.id === id)
    if (visible) { marker.setOpacity(1); marker.getElement()?.classList.remove('am-pin-hidden') }
    else { marker.setOpacity(0); marker.getElement()?.classList.add('am-pin-hidden') }
  })
})

// Update route polyline
watch(routeStops, (stops) => {
  if (!routeLine) return
  routeLine.setLatLngs(stops.map(s => [s.lat, s.lng]))
}, { deep: true })

// ── Category filter ─────────────────────────────────
function toggleCat(id: string) {
  if (id === 'all') {
    activeCats.value = new Set(['all'])
    return
  }
  activeCats.value.delete('all')
  if (activeCats.value.has(id)) {
    activeCats.value.delete(id)
    if (activeCats.value.size === 0) activeCats.value = new Set(['all'])
  } else {
    activeCats.value.add(id)
  }
  // trigger reactivity
  activeCats.value = new Set(activeCats.value)
}

// ── Route planning ──────────────────────────────────
function isInRoute(id: string) {
  return routeStops.value.some(s => s.id === id)
}

function toggleRoute(attr: Attraction) {
  if (isInRoute(attr.id)) {
    routeStops.value = routeStops.value.filter(s => s.id !== attr.id)
  } else {
    routeStops.value.push(attr)
    showRoute.value = true
  }
}

function removeStop(id: string) {
  routeStops.value = routeStops.value.filter(s => s.id !== id)
}

function moveStop(idx: number, dir: -1 | 1) {
  const stops = [...routeStops.value]
  const target = idx + dir
  ;[stops[idx], stops[target]] = [stops[target], stops[idx]]
  routeStops.value = stops
}

function clearRoute() {
  routeStops.value = []
}

function focusOnMap(attr: Attraction) {
  mapInstance?.setView([attr.lat, attr.lng], 17, { animate: true })
}

// ── External navigation ─────────────────────────────
function openGaodeSearch(attr: Attraction) {
  const url = `https://www.amap.com/search?query=${encodeURIComponent(attr.name)}&city=青岛`
  window.open(url, '_blank')
}

function openGaodeRoute() {
  if (routeStops.value.length < 1) return
  // Gaode Maps web route URL (multi-waypoint)
  const stops = routeStops.value
  if (stops.length === 1) {
    const s = stops[0]
    window.open(`https://www.amap.com/search?query=${encodeURIComponent(s.name)}&city=青岛`, '_blank')
    return
  }
  const from = stops[0]
  const to = stops[stops.length - 1]
  const waypoints = stops.slice(1, -1).map(s => `${s.lng},${s.lat},${encodeURIComponent(s.name)}`).join(';')
  const url = `https://www.amap.com/dir?from[lnglat]=${from.lng},${from.lat}&from[name]=${encodeURIComponent(from.name)}&to[lnglat]=${to.lng},${to.lat}&to[name]=${encodeURIComponent(to.name)}&via=${waypoints}&type=walk`
  window.open(url, '_blank')
}

function openBaiduRoute() {
  if (routeStops.value.length < 1) return
  const stops = routeStops.value
  if (stops.length === 1) {
    const s = stops[0]
    window.open(`https://map.baidu.com/search/${encodeURIComponent(s.name + ' 青岛')}/`, '_blank')
    return
  }
  const from = stops[0]
  const to = stops[stops.length - 1]
  const url = `https://map.baidu.com/orde?from=${encodeURIComponent(from.name)}&to=${encodeURIComponent(to.name)}&region=青岛&mode=driving`
  window.open(url, '_blank')
}

async function exportItinerary() {
  const lines = [
    `🗺️ 青岛一日游行程（共 ${routeStops.value.length} 站 · 约 ${estimatedHours.value}h）`,
    '',
    ...routeStops.value.map((s, i) =>
      `${i + 1}. ${s.emoji} ${s.name}\n   📍 ${s.category_label} | 🎫 ${s.fee} | ⏱ ${s.duration}\n   💡 ${s.tips || '精彩景点，值得探索'}`
    ),
    '',
    `📍 总步行距离约 ${totalDistance.value} km`,
    `生成于 VoyageOS · ${new Date().toLocaleDateString('zh-CN')}`,
  ]
  await navigator.clipboard.writeText(lines.join('\n'))
  exportCopied.value = true
  setTimeout(() => { exportCopied.value = false }, 2500)
}

// ── Utilities ───────────────────────────────────────
function haversine(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6371
  const dLat = (lat2 - lat1) * Math.PI / 180
  const dLng = (lng2 - lng1) * Math.PI / 180
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLng / 2) ** 2
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Cormorant+Garamond:ital,wght@0,500;0,600;1,400&family=DM+Sans:wght@300;400;500&display=swap');

/* ── Root ─────────────────────────────────────────── */
.am-root {
  position: fixed;
  inset: 0;
  z-index: 300;
  display: flex;
  flex-direction: column;
  background: #0A0F0A;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
}

.am-root-enter-active, .am-root-leave-active {
  transition: opacity 0.25s ease, transform 0.3s cubic-bezier(0.32, 0, 0.12, 1);
}
.am-root-enter-from, .am-root-leave-to {
  opacity: 0;
  transform: scale(0.98);
}

/* ── Header ───────────────────────────────────────── */
.am-header {
  height: 54px;
  background: rgba(12, 18, 12, 0.95);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(255,255,255,0.06);
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 0 20px;
  flex-shrink: 0;
  z-index: 10;
}

.am-brand {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.am-brand-icon {
  color: var(--gold);
  font-size: 12px;
}

.am-brand-name {
  font-family: 'Cormorant Garamond', serif;
  font-size: 16px;
  font-weight: 500;
  color: #E8E0D0;
  letter-spacing: 0.01em;
}

.am-brand-name em {
  font-style: italic;
  color: var(--gold);
}

/* Category pills */
.am-cats {
  display: flex;
  gap: 6px;
  flex: 1;
  overflow-x: auto;
  padding: 0 4px;
}
.am-cats::-webkit-scrollbar { display: none; }

.am-cat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 20px;
  padding: 4px 11px;
  font-size: 12px;
  color: rgba(255,255,255,0.55);
  cursor: pointer;
  transition: all 0.15s;
}
.am-cat:hover { border-color: rgba(var(--accent-rgb),0.4); color: var(--gold); }
.am-cat.active {
  background: rgba(var(--accent-rgb),0.12);
  border-color: rgba(var(--accent-rgb),0.4);
  color: var(--gold);
}

/* Header right */
.am-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.am-route-toggle, .am-tile-toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 7px;
  padding: 5px 11px;
  font-size: 12px;
  color: rgba(255,255,255,0.55);
  cursor: pointer;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  transition: all 0.15s;
  white-space: nowrap;
  position: relative;
}
.am-route-toggle:hover, .am-tile-toggle:hover {
  border-color: rgba(var(--accent-rgb),0.4);
  color: var(--gold);
}
.am-route-toggle.active {
  background: rgba(var(--forest-rgb),0.3);
  border-color: rgba(var(--forest-rgb),0.8);
  color: #6EE7A0;
}

.am-badge {
  position: absolute;
  top: -5px;
  right: -5px;
  background: var(--gold);
  color: #0A0F0A;
  font-size: 10px;
  font-weight: 700;
  min-width: 16px;
  height: 16px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
}

.am-close {
  width: 32px;
  height: 32px;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255,255,255,0.5);
  transition: all 0.15s;
}
.am-close:hover { background: rgba(239,68,68,0.15); border-color: rgba(239,68,68,0.4); color: #FCA5A5; }

/* ── Body ─────────────────────────────────────────── */
.am-body {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.am-map {
  width: 100%;
  height: 100%;
}

/* Attribution overlay */
.am-attr {
  position: absolute;
  bottom: 6px;
  left: 8px;
  font-size: 10px;
  color: rgba(255,255,255,0.3);
  pointer-events: none;
  z-index: 5;
}

/* Stats bubble */
.am-stats-bubble {
  position: absolute;
  top: 14px;
  left: 14px;
  background: rgba(12, 18, 12, 0.88);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(var(--accent-rgb),0.2);
  border-radius: 10px;
  padding: 6px 13px;
  display: flex;
  align-items: center;
  gap: 5px;
  z-index: 5;
  pointer-events: none;
}

.am-stat-num {
  font-family: 'Cormorant Garamond', serif;
  font-size: 18px;
  font-weight: 600;
  color: var(--gold);
  line-height: 1;
}

.am-stat-label {
  font-size: 11px;
  color: rgba(255,255,255,0.45);
}

/* ── Route Sidebar ────────────────────────────────── */
.am-route-panel {
  position: absolute;
  top: 0;
  right: 0;
  width: 310px;
  height: 100%;
  background: rgba(10, 14, 10, 0.94);
  backdrop-filter: blur(16px);
  border-left: 1px solid rgba(255,255,255,0.07);
  display: flex;
  flex-direction: column;
  z-index: 6;
  overflow: hidden;
}

.am-sidebar-enter-active, .am-sidebar-leave-active { transition: transform 0.28s cubic-bezier(0.32, 0, 0.12, 1); }
.am-sidebar-enter-from, .am-sidebar-leave-to { transform: translateX(100%); }

.am-rp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 18px 12px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  flex-shrink: 0;
}

.am-rp-title {
  font-family: 'Cormorant Garamond', serif;
  font-size: 16px;
  font-weight: 500;
  color: #E8E0D0;
}

.am-rp-clear {
  background: none;
  border: none;
  font-size: 11.5px;
  color: rgba(255,255,255,0.3);
  cursor: pointer;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  padding: 3px 7px;
  border-radius: 4px;
  transition: color 0.12s;
}
.am-rp-clear:hover { color: #FCA5A5; }

.am-rp-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: rgba(255,255,255,0.3);
  font-size: 12.5px;
  padding: 30px 24px;
  line-height: 1.8;
  gap: 12px;
}

.am-rp-empty-icon { font-size: 30px; opacity: 0.5; }

.am-rp-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 10px 0;
}
.am-rp-list::-webkit-scrollbar { width: 3px; }
.am-rp-list::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.08); border-radius: 2px; }

.am-rp-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  transition: background 0.12s;
}
.am-rp-item:hover { background: rgba(255,255,255,0.03); }

.am-rp-seq {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: rgba(var(--accent-rgb),0.15);
  border: 1px solid rgba(var(--accent-rgb),0.35);
  color: var(--gold);
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.am-rp-info { flex: 1; min-width: 0; }
.am-rp-name { font-size: 13px; font-weight: 500; color: #E8E0D0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.am-rp-meta { font-size: 11px; color: rgba(255,255,255,0.35); margin-top: 2px; }

.am-rp-actions { display: flex; align-items: center; gap: 2px; flex-shrink: 0; }

.am-rp-move {
  width: 22px;
  height: 22px;
  background: none;
  border: none;
  color: rgba(255,255,255,0.2);
  cursor: pointer;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.12s;
}
.am-rp-move:hover { background: rgba(255,255,255,0.06); color: rgba(255,255,255,0.7); }

.am-rp-remove {
  width: 22px;
  height: 22px;
  background: none;
  border: none;
  color: rgba(255,255,255,0.2);
  cursor: pointer;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.12s;
  line-height: 1;
}
.am-rp-remove:hover { background: rgba(239,68,68,0.12); color: #FCA5A5; }

/* Route stats */
.am-rp-stats {
  display: flex;
  gap: 0;
  padding: 12px 16px;
  border-top: 1px solid rgba(255,255,255,0.05);
  border-bottom: 1px solid rgba(255,255,255,0.05);
  margin-top: 6px;
}

.am-rp-stat {
  flex: 1;
  text-align: center;
}

.am-rp-stat-val {
  display: block;
  font-family: 'Cormorant Garamond', serif;
  font-size: 20px;
  font-weight: 600;
  color: var(--gold);
  line-height: 1;
}

.am-rp-stat-key {
  display: block;
  font-size: 10px;
  color: rgba(255,255,255,0.3);
  margin-top: 3px;
}

/* Nav buttons */
.am-rp-nav-btns {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 16px;
}

.am-rp-nav-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 9px;
  border-radius: 8px;
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  transition: all 0.15s;
  border: 1px solid transparent;
}

.am-rp-nav-btn.gaode { background: rgba(12,146,62,0.15); border-color: rgba(12,146,62,0.3); color: #4ADE80; }
.am-rp-nav-btn.gaode:hover { background: rgba(12,146,62,0.25); }
.am-rp-nav-btn.baidu { background: rgba(59,130,246,0.1); border-color: rgba(59,130,246,0.25); color: #93C5FD; }
.am-rp-nav-btn.baidu:hover { background: rgba(59,130,246,0.2); }
.am-rp-nav-btn.export { background: rgba(var(--accent-rgb),0.1); border-color: rgba(var(--accent-rgb),0.25); color: var(--gold); }
.am-rp-nav-btn.export:hover { background: rgba(var(--accent-rgb),0.18); }

.am-rp-copied {
  text-align: center;
  font-size: 12px;
  color: #4ADE80;
  padding: 4px 0 8px;
}

/* ── Detail Panel ─────────────────────────────────── */
.am-detail {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  max-height: 52vh;
  background: rgba(12, 16, 12, 0.97);
  backdrop-filter: blur(20px);
  border-top: 1px solid rgba(255,255,255,0.07);
  display: flex;
  overflow: hidden;
  z-index: 7;
}

.am-detail-enter-active, .am-detail-leave-active { transition: transform 0.3s cubic-bezier(0.32, 0, 0.12, 1); }
.am-detail-enter-from, .am-detail-leave-to { transform: translateY(100%); }

.am-detail-close {
  position: absolute;
  top: 14px;
  right: 16px;
  width: 28px;
  height: 28px;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 7px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255,255,255,0.4);
  z-index: 2;
  transition: all 0.15s;
}
.am-detail-close:hover { background: rgba(239,68,68,0.15); color: #FCA5A5; }

/* Hero */
.am-detail-hero {
  width: 100px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px;
}

.am-detail-emoji {
  font-size: 40px;
  line-height: 1;
}

.am-detail-cat-badge {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 10px;
  border: 1px solid;
  white-space: nowrap;
}

/* Body */
.am-detail-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 50px 16px 16px;
  min-width: 0;
}
.am-detail-body::-webkit-scrollbar { width: 3px; }
.am-detail-body::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.08); border-radius: 2px; }

.am-detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.am-detail-name {
  font-family: 'Cormorant Garamond', serif;
  font-size: 22px;
  font-weight: 600;
  color: #F0EAE0;
  letter-spacing: -0.01em;
  margin-bottom: 5px;
}

.am-detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.am-detail-tag {
  font-size: 10.5px;
  background: rgba(var(--accent-rgb),0.1);
  border: 1px solid rgba(var(--accent-rgb),0.2);
  color: #C4A04A;
  padding: 2px 7px;
  border-radius: 4px;
}

.am-detail-rating {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex-shrink: 0;
}

.am-rating-stars { letter-spacing: 1px; font-size: 12px; }
.star-on { color: #F59E0B; }
.star-off { color: rgba(255,255,255,0.15); }

.am-rating-val {
  font-size: 12px;
  color: rgba(255,255,255,0.4);
}

.am-detail-desc {
  font-size: 13.5px;
  color: rgba(255,255,255,0.6);
  line-height: 1.75;
  margin-bottom: 12px;
}

.am-detail-meta-row {
  display: flex;
  gap: 0;
  margin-bottom: 10px;
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 10px;
  overflow: hidden;
}

.am-detail-meta-item {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  border-right: 1px solid rgba(255,255,255,0.06);
}
.am-detail-meta-item:last-child { border-right: none; }

.am-detail-meta-icon { font-size: 15px; flex-shrink: 0; }
.am-detail-meta-label { font-size: 10px; color: rgba(255,255,255,0.3); margin-bottom: 2px; }
.am-detail-meta-val { font-size: 12px; color: rgba(255,255,255,0.7); font-weight: 500; }

.am-detail-tips {
  font-size: 12.5px;
  color: rgba(255,255,255,0.45);
  line-height: 1.65;
  background: rgba(var(--accent-rgb),0.06);
  border-left: 2px solid rgba(var(--accent-rgb),0.3);
  padding: 7px 12px;
  border-radius: 0 6px 6px 0;
  margin-bottom: 12px;
}

.am-tips-label {
  font-weight: 500;
  color: var(--gold);
  margin-right: 4px;
}

/* Actions */
.am-detail-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.am-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  transition: all 0.15s;
  border: 1px solid transparent;
}

.am-action-btn.primary {
  background: rgba(var(--forest-rgb),0.6);
  border-color: rgba(var(--forest-rgb),0.9);
  color: #6EE7A0;
}
.am-action-btn.primary:hover { background: rgba(var(--forest-rgb),0.85); }
.am-action-btn.primary.added {
  background: rgba(22,101,52,0.3);
  border-color: rgba(22,163,74,0.5);
  color: #4ADE80;
}

.am-action-btn.secondary {
  background: rgba(255,255,255,0.04);
  border-color: rgba(255,255,255,0.08);
  color: rgba(255,255,255,0.5);
}
.am-action-btn.secondary:hover { border-color: rgba(var(--accent-rgb),0.35); color: var(--gold); }
</style>

<!-- Global styles for Leaflet markers -->
<style>
.am-pin {
  width: 40px;
  height: 48px;
  position: relative;
  cursor: pointer;
  transition: transform 0.18s cubic-bezier(0.34, 1.56, 0.64, 1), filter 0.18s;
}

.am-pin:hover {
  transform: scale(1.15) translateY(-3px);
  filter: drop-shadow(0 6px 14px rgba(0,0,0,0.5));
  z-index: 1000 !important;
}

.am-pin::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 36px;
  height: 36px;
  border-radius: 50% 50% 50% 0;
  background: var(--pin-color, var(--forest));
  transform: translateX(-50%) rotate(-45deg);
  box-shadow: 0 3px 12px rgba(0,0,0,0.4);
}

.am-pin-emoji {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  z-index: 1;
}

.am-pin-hidden {
  display: none !important;
}
</style>
