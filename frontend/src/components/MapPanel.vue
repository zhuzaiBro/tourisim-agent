<template>
  <div class="map-panel">
    <div class="map-header">
      <span class="map-title">目的地地图</span>
      <button class="map-close" @click="$emit('close')">
        <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
          <path d="M1 1l10 10M11 1L1 11" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </button>
    </div>
    <div class="map-container" ref="mapRef"></div>
    <div v-if="!cities.length" class="map-empty">选择城市后显示地图</div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { CITY_GEO } from '@/utils/cityData'

const props = defineProps<{ cities: string[] }>()
defineEmits<{ close: [] }>()

const mapRef = ref<HTMLElement | null>(null)
let mapInstance: any = null
let markers: any[] = []

async function initMap() {
  if (!mapRef.value) return

  // Lazy-load leaflet to avoid SSR issues
  const L = (await import('leaflet')).default
  await import('leaflet/dist/leaflet.css')

  // Fix default icon paths
  delete (L.Icon.Default.prototype as any)._getIconUrl
  L.Icon.Default.mergeOptions({
    iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
    iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
    shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
  })

  if (mapInstance) {
    mapInstance.remove()
    mapInstance = null
  }

  mapInstance = L.map(mapRef.value, {
    zoomControl: true,
    attributionControl: false,
    scrollWheelZoom: true,
  })

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 18,
  }).addTo(mapInstance)

  updateMarkers(L)
}

function updateMarkers(L?: any) {
  if (!mapInstance) return

  markers.forEach(m => m.remove())
  markers = []

  const coords = props.cities
    .map(code => ({ code, geo: CITY_GEO[code] }))
    .filter(c => c.geo)

  if (coords.length === 0) return

  const bounds: [number, number][] = []

  for (const { code, geo } of coords) {
    // Custom styled marker
    const icon = (L || (window as any).L).divIcon({
      className: '',
      html: `<div class="map-marker"><span>${geo.nameCn}</span></div>`,
      iconSize: [60, 28],
      iconAnchor: [30, 28],
    })

    const marker = (L || (window as any).L)
      .marker([geo.lat, geo.lng], { icon })
      .addTo(mapInstance)
      .bindPopup(`<strong>${geo.nameCn}</strong><br><small>${geo.nameEn}</small>`)

    markers.push(marker)
    bounds.push([geo.lat, geo.lng])
  }

  if (bounds.length === 1) {
    mapInstance.setView(bounds[0], 10)
  } else {
    mapInstance.fitBounds(bounds, { padding: [40, 40] })
  }
}

watch(() => props.cities, async () => {
  if (!mapInstance) {
    await nextTick()
    await initMap()
  } else {
    const L = (await import('leaflet')).default
    updateMarkers(L)
  }
}, { deep: true })

onMounted(async () => {
  if (props.cities.length > 0) {
    await nextTick()
    await initMap()
  }
})

onUnmounted(() => {
  if (mapInstance) {
    mapInstance.remove()
    mapInstance = null
  }
})
</script>

<style scoped>
.map-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--white);
  border-left: 1px solid var(--cream-300);
}

.map-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--cream-300);
  flex-shrink: 0;
}

.map-title {
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.06em;
  color: var(--text-2);
}

.map-close {
  width: 24px;
  height: 24px;
  background: none;
  border: 1px solid var(--cream-300);
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-3);
  transition: border-color 0.15s, color 0.15s;
}

.map-close:hover {
  border-color: var(--text-2);
  color: var(--text);
}

.map-container {
  flex: 1;
  min-height: 0;
}

.map-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: var(--text-3);
  pointer-events: none;
}
</style>

<style>
/* Map marker global style */
.map-marker {
  background: var(--forest);
  color: #fff;
  padding: 4px 10px;
  border-radius: 14px;
  font-size: 12px;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  font-weight: 500;
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(var(--forest-rgb),0.35);
  position: relative;
}

.map-marker::after {
  content: '';
  position: absolute;
  bottom: -5px;
  left: 50%;
  transform: translateX(-50%);
  width: 0;
  height: 0;
  border-left: 5px solid transparent;
  border-right: 5px solid transparent;
  border-top: 5px solid var(--forest);
}

/* Leaflet popup */
.leaflet-popup-content-wrapper {
  border-radius: 8px !important;
  box-shadow: 0 4px 20px rgba(0,0,0,0.12) !important;
  font-family: 'DM Sans', 'PingFang SC', sans-serif !important;
}
</style>
