<template>
  <Teleport to="body">
    <Transition name="iam-fade">
      <div v-if="open" class="iam-root">
        <header class="iam-header">
          <div class="iam-title">
            <span class="iam-gem">◎</span>
            {{ cityName }} · 第 {{ dayNumber }} 天路线
          </div>
          <div class="iam-meta" v-if="route">
            <DataSourceBadge :source="route.dataSource" kind="route" />
            <span class="iam-dist">{{ route.totalDistanceKm }} km · {{ route.totalDurationMinutes }} 分钟</span>
            <span v-if="routeDrawMode" class="iam-draw-mode">{{ routeDrawMode }}</span>
          </div>
          <div class="iam-actions">
            <button
              v-if="pois.length > 0 && isAmapConfigured()"
              class="iam-nav-btn"
              @click="handleOpenNav"
            >高德导航</button>
            <button class="iam-close" @click="$emit('close')" aria-label="关闭地图">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M1.5 1.5l11 11M12.5 1.5l-11 11" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
              </svg>
            </button>
          </div>
        </header>

        <div class="iam-body">
          <div class="iam-map-wrap">
            <div ref="mapEl" class="iam-map" />
            <div v-if="mapError" class="iam-map-error">{{ mapError }}</div>
            <div v-if="!isAmapConfigured()" class="iam-map-hint">
              请在环境变量中配置 <code>VITE_AMAP_KEY</code> 与 <code>VITE_AMAP_SECURITY_CODE</code>（Web 端 JS API Key）
            </div>
          </div>

          <aside class="iam-side">
            <div id="amap-route-panel" class="iam-route-panel" />
            <div v-if="route?.legs?.length" class="iam-legs">
              <div class="iam-legs-title">路线分段</div>
              <ol>
                <li v-for="(leg, i) in route.legs" :key="i">
                  <span class="leg-from">{{ leg.fromName }}</span>
                  <span class="leg-arrow">→</span>
                  <span class="leg-to">{{ leg.toName }}</span>
                  <span class="leg-meta">{{ leg.distanceKm }}km · {{ leg.durationMinutes }}分 · {{ leg.transportSuggestion }}</span>
                  <span v-if="leg.instruction" class="leg-inst">{{ leg.instruction }}</span>
                </li>
              </ol>
            </div>
            <div v-if="!pois.length" class="iam-empty">暂无 POI 坐标，无法绘制地图</div>
          </aside>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, onUnmounted, nextTick, computed } from 'vue'
import type { RouteInfo } from '@/api/agent'
import DataSourceBadge from '@/components/DataSourceBadge.vue'
import {
  loadAmap,
  isAmapConfigured,
  isEstimatedRoute,
  drawStraightRoute,
  planAndDrawRoute,
  openAmapNavigation,
} from '@/utils/amap'

const props = defineProps<{
  open: boolean
  cityName: string
  dayNumber: number
  route: RouteInfo | null | undefined
  transportMode?: 'walking' | 'driving' | 'transit'
}>()

defineEmits<{ close: [] }>()

const mapEl = ref<HTMLElement | null>(null)
const mapError = ref('')
const routeDrawMode = ref('')

let mapInstance: any = null
let markers: any[] = []
let overlayLines: any[] = []

const pois = computed(() => props.route?.optimizedPois?.filter(p => p.lat && p.lng) ?? [])

const transport = computed(() => props.transportMode || 'transit')

function clearOverlays() {
  markers.forEach(m => m?.setMap?.(null))
  markers = []
  overlayLines.forEach(l => l?.setMap?.(null))
  overlayLines = []
}

function destroyMap() {
  clearOverlays()
  if (mapInstance) {
    mapInstance.destroy()
    mapInstance = null
  }
}

async function renderMap() {
  if (!props.open || !mapEl.value || pois.value.length === 0) return

  mapError.value = ''
  routeDrawMode.value = ''

  if (!isAmapConfigured()) {
    mapError.value = '高德 Key 未配置，无法加载地图'
    return
  }

  try {
    const AMap = await loadAmap()

    if (mapInstance) {
      destroyMap()
    }

    const center = pois.value[0]
    mapInstance = new AMap.Map(mapEl.value, {
      zoom: 13,
      center: [center.lng, center.lat],
      viewMode: '2D',
      resizeEnable: true,
    })

    // POI 标记
    pois.value.forEach((poi, i) => {
      const marker = new AMap.Marker({
        position: [poi.lng, poi.lat],
        title: poi.name,
        label: {
          content: `<div class="iam-amap-label"><span>${i + 1}</span>${poi.name}</div>`,
          direction: 'top',
        },
      })
      marker.setMap(mapInstance)
      marker.on('click', () => {
        const info = new AMap.InfoWindow({
          content: `<b>${poi.name}</b><br/>${poi.address || ''}<br/>${poi.ticketPrice || ''}`,
        })
        info.open(mapInstance, [poi.lng, poi.lat])
      })
      markers.push(marker)
    })

    mapInstance.setFitView(markers, false, [60, 60, 60, 60])

    // 路线绘制
    if (isEstimatedRoute(props.route?.dataSource)) {
      const line = drawStraightRoute(AMap, mapInstance, pois.value)
      overlayLines.push(line)
      routeDrawMode.value = '估算路线（直线示意）'
    } else {
      const { mode, fallback } = await planAndDrawRoute(AMap, mapInstance, pois.value, {
        transportMode: transport.value,
        cityName: props.cityName,
        panelId: 'amap-route-panel',
      })
      routeDrawMode.value = fallback
        ? '路网规划失败，已显示直线'
        : mode.includes('Walking') ? '高德 · 步行规划'
        : mode.includes('Transfer') ? '高德 · 公交规划'
        : '高德 · 驾车规划'
    }
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '地图加载失败'
    mapError.value = msg
  }
}

function handleOpenNav() {
  openAmapNavigation(pois.value, transport.value)
}

watch(
  () => [props.open, props.route, props.dayNumber, props.transportMode],
  async () => {
    if (props.open) {
      await nextTick()
      await renderMap()
    } else {
      destroyMap()
    }
  },
  { deep: true },
)

onUnmounted(destroyMap)
</script>

<style scoped>
.iam-root {
  position: fixed;
  inset: 0;
  z-index: 9000;
  background: #3D1520;
  display: flex;
  flex-direction: column;
}
.iam-fade-enter-active, .iam-fade-leave-active { transition: opacity 0.25s; }
.iam-fade-enter-from, .iam-fade-leave-to { opacity: 0; }

.iam-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 20px;
  background: rgba(0,0,0,0.5);
  border-bottom: 1px solid rgba(255,255,255,0.08);
  flex-shrink: 0;
}
.iam-title {
  font-family: 'Cormorant Garamond', serif;
  font-size: 20px;
  color: var(--text-on-theme);
  flex: 1;
}
.iam-gem { color: var(--gold); margin-right: 8px; }
.iam-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: rgba(255,255,255,0.5);
  flex-wrap: wrap;
}
.iam-draw-mode { color: rgba(255,255,255,0.35); font-size: 11px; }
.iam-actions { display: flex; align-items: center; gap: 8px; }

.iam-nav-btn {
  background: rgba(12,146,62,0.2);
  border: 1px solid rgba(12,146,62,0.45);
  color: #6ecf6f;
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
}
.iam-nav-btn:hover { background: rgba(12,146,62,0.35); }

.iam-close {
  background: transparent;
  border: 1px solid rgba(255,255,255,0.2);
  color: rgba(255,255,255,0.7);
  width: 32px;
  height: 32px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.iam-close:hover { border-color: var(--gold); color: var(--gold); }

.iam-body { flex: 1; position: relative; display: flex; min-height: 0; }
.iam-map-wrap { flex: 1; position: relative; min-width: 0; }
.iam-map { width: 100%; height: 100%; }

.iam-map-error,
.iam-map-hint {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 12px;
  padding: 10px 12px;
  background: rgba(0,0,0,0.72);
  border: 1px solid rgba(255,255,255,0.12);
  color: rgba(255,255,255,0.75);
  font-size: 12px;
  border-radius: 6px;
  z-index: 2;
}
.iam-map-hint code { color: var(--gold); font-size: 11px; }

.iam-side {
  width: 300px;
  background: rgba(0,0,0,0.55);
  border-left: 1px solid rgba(255,255,255,0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex-shrink: 0;
}

.iam-route-panel {
  flex: 1;
  min-height: 120px;
  overflow-y: auto;
  color: #333;
}
/* 高德路线面板在深色侧栏中的可读性 */
.iam-route-panel :deep(.amap-lib-driving),
.iam-route-panel :deep(.amap-lib-walking),
.iam-route-panel :deep(.amap-lib-transfer) {
  background: #f8f8f8;
  border-radius: 4px;
  margin: 8px;
}

.iam-legs {
  padding: 12px 16px 16px;
  border-top: 1px solid rgba(255,255,255,0.08);
  max-height: 40%;
  overflow-y: auto;
}
.iam-legs-title {
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.4);
  margin-bottom: 12px;
}
.iam-legs ol { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 10px; }
.iam-legs li { font-size: 12px; color: rgba(255,255,255,0.75); line-height: 1.5; }
.leg-from, .leg-to { color: #f5f0e8; }
.leg-arrow { margin: 0 4px; color: var(--gold); }
.leg-meta { display: block; font-size: 10px; color: rgba(255,255,255,0.35); margin-top: 2px; }
.leg-inst { display: block; font-size: 10px; color: rgba(255,255,255,0.45); margin-top: 2px; }

.iam-empty {
  padding: 24px 16px;
  color: rgba(255,255,255,0.4);
  font-size: 13px;
  text-align: center;
}

:global(.iam-amap-label) {
  background: rgba(26, 26, 30, 0.92);
  border: 1px solid var(--gold);
  color: #f5f0e8;
  font-size: 11px;
  padding: 3px 8px 3px 3px;
  border-radius: 4px;
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.35);
}
:global(.iam-amap-label span) {
  background: var(--gold);
  color: #1a1a1e;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 10px;
}

@media (max-width: 768px) {
  .iam-body { flex-direction: column; }
  .iam-side { width: 100%; max-height: 38vh; border-left: none; border-top: 1px solid rgba(255,255,255,0.08); }
}
</style>
