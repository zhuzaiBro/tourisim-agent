<template>
  <div class="weather-panel" v-if="cities.length > 0">
    <div class="wp-header">
      <span class="wp-title">当前天气</span>
      <span class="wp-update">{{ lastUpdate }}</span>
    </div>

    <div v-if="loading" class="wp-loading">
      <div class="wp-skeleton" v-for="i in cities.length" :key="i"></div>
    </div>

    <div v-else class="wp-cards">
      <div
        v-for="item in weatherData"
        :key="item.city"
        class="wp-card"
      >
        <div class="wc-top">
          <span class="wc-city">{{ item.nameCn }}</span>
          <span class="wc-icon">{{ item.icon }}</span>
        </div>
        <div class="wc-temp">{{ item.temp }}<span class="wc-unit">°C</span></div>
        <div class="wc-desc">{{ item.desc }}</div>
        <div class="wc-range">{{ item.tempMin }}° / {{ item.tempMax }}°</div>

        <!-- 5-day forecast strip -->
        <div class="wc-forecast">
          <div v-for="(day, i) in item.forecast" :key="i" class="wc-day">
            <span class="day-name">{{ day.label }}</span>
            <span class="day-icon">{{ day.icon }}</span>
            <span class="day-temp">{{ day.max }}°</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="error" class="wp-error">无法获取天气数据</div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { CITY_GEO, wmoToWeather } from '@/utils/cityData'

const props = defineProps<{ cities: string[] }>()

interface WeatherItem {
  city: string
  nameCn: string
  icon: string
  temp: number
  tempMin: number
  tempMax: number
  desc: string
  forecast: { label: string; icon: string; max: number }[]
}

const loading = ref(false)
const error = ref(false)
const weatherData = ref<WeatherItem[]>([])
const lastUpdate = ref('')

const DAY_LABELS = ['今', '明', '后', '四', '五']

async function fetchWeather(cityCode: string): Promise<WeatherItem | null> {
  const geo = CITY_GEO[cityCode]
  if (!geo) return null

  const url = `https://api.open-meteo.com/v1/forecast?latitude=${geo.lat}&longitude=${geo.lng}`
    + `&current=temperature_2m,weathercode,windspeed_10m`
    + `&daily=temperature_2m_max,temperature_2m_min,weathercode`
    + `&timezone=Asia%2FShanghai&forecast_days=5`

  const res = await fetch(url)
  if (!res.ok) return null
  const data = await res.json()

  const current = data.current
  const daily = data.daily
  const { icon, desc } = wmoToWeather(current.weathercode)

  const forecast = (daily.time as string[]).slice(0, 5).map((_, i) => ({
    label: DAY_LABELS[i],
    icon: wmoToWeather(daily.weathercode[i]).icon,
    max: Math.round(daily.temperature_2m_max[i]),
  }))

  return {
    city: cityCode,
    nameCn: geo.nameCn,
    icon,
    desc,
    temp: Math.round(current.temperature_2m),
    tempMin: Math.round(daily.temperature_2m_min[0]),
    tempMax: Math.round(daily.temperature_2m_max[0]),
    forecast,
  }
}

async function loadAll() {
  if (props.cities.length === 0) return
  loading.value = true
  error.value = false
  try {
    const results = await Promise.all(props.cities.map(fetchWeather))
    weatherData.value = results.filter(Boolean) as WeatherItem[]
    lastUpdate.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) + ' 更新'
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

watch(() => props.cities, loadAll, { immediate: true })
</script>

<style scoped>
.weather-panel {
  padding: 14px 20px 12px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}

.wp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.wp-title {
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.35);
}

.wp-update {
  font-size: 10px;
  color: rgba(255,255,255,0.2);
}

.wp-loading {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.wp-skeleton {
  height: 68px;
  border-radius: 8px;
  background: rgba(255,255,255,0.06);
  animation: shimmer 1.5s infinite;
}

@keyframes shimmer {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}

.wp-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.wp-card {
  background: rgba(255,255,255,0.07);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 10px;
  padding: 10px 12px;
}

.wc-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.wc-city {
  font-size: 12px;
  font-weight: 500;
  color: rgba(255,255,255,0.8);
}

.wc-icon {
  font-size: 18px;
}

.wc-temp {
  font-family: 'Cormorant Garamond', serif;
  font-size: 26px;
  font-weight: 500;
  color: #fff;
  line-height: 1;
}

.wc-unit {
  font-size: 14px;
  color: rgba(255,255,255,0.5);
  margin-left: 1px;
}

.wc-desc {
  font-size: 11px;
  color: rgba(255,255,255,0.45);
  margin-top: 2px;
}

.wc-range {
  font-size: 11px;
  color: rgba(255,255,255,0.35);
  margin-top: 1px;
}

/* Forecast strip */
.wc-forecast {
  display: flex;
  gap: 0;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid rgba(255,255,255,0.08);
}

.wc-day {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}

.day-name {
  font-size: 10px;
  color: rgba(255,255,255,0.35);
}

.day-icon {
  font-size: 13px;
}

.day-temp {
  font-size: 11px;
  color: rgba(255,255,255,0.6);
}

.wp-error {
  font-size: 12px;
  color: rgba(255,255,255,0.3);
  text-align: center;
  padding: 8px;
}
</style>
