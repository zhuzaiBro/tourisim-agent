<template>
  <span class="ds-badge" :class="badgeClass" :title="tooltip">
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  source?: string
  /** 仅当无 source 时：false=Mock */
  real?: boolean
  kind?: 'weather' | 'poi' | 'food' | 'accommodation' | 'route'
}>()

const normalized = computed(() => {
  if (props.source) return props.source.toLowerCase()
  if (props.real === false) return 'mock'
  return 'unknown'
})

const label = computed(() => {
  const s = normalized.value
  if (s === 'mock' || s === 'builtin' || s === 'fallback') {
    if (props.kind === 'weather') return '天气·参考'
    if (props.kind === 'poi') return '景点·内置'
    if (props.kind === 'food') return '美食·精选'
    if (props.kind === 'accommodation') return '住宿·精选'
    if (props.kind === 'route') return '路线·内置'
    return 'Mock'
  }
  if (s.includes('climate_estimate')) return '天气·气候估算'
  if (s === 'gaode_weather') return '天气·高德'
  if (s.includes('xhs_guide+gaode_api') || (s.includes('xhs') && s.includes('gaode'))) {
    if (props.kind === 'food') return '美食·小红书+高德'
    return '景点·小红书+高德'
  }
  if (s === 'xhs_guide' || s.includes('xhs')) {
    if (props.kind === 'food') return '美食·小红书'
    return '景点·小红书'
  }
  if (s.includes('rag') && s.includes('xhs')) return '景点·多源'
  if (s.includes('rag') && s.includes('gaode')) return '高德+RAG'
  if (s.includes('llm')) {
    if (props.kind === 'food') return '美食·AI补全'
    if (props.kind === 'accommodation') return '住宿·AI补全'
    return '景点·AI补全'
  }
  if (s === 'gaode_api+batch' || s === 'gaode_api') {
    if (props.kind === 'route') return '路线·高德'
    if (props.kind === 'poi') return '景点·高德'
    if (props.kind === 'food') return '美食·高德'
    if (props.kind === 'accommodation') return '住宿·高德'
    return '高德'
  }
  if (s === 'estimated_route' || s.includes('haversine')) return '路线·估算'
  if (s === 'rag') return '知识库'
  if (s === 'mysql') return '数据库'
  if (props.kind === 'weather') return '天气'
  if (props.kind === 'poi') return '景点'
  if (props.kind === 'food') return '美食'
  if (props.kind === 'accommodation') return '住宿'
  if (props.kind === 'route') return '路线'
  return s === 'unknown' ? '未知' : s
})

const isMock = computed(() => {
  const s = normalized.value
  return s === 'mock' || s === 'builtin' || s === 'fallback'
})

const isEstimated = computed(() => {
  const s = normalized.value
  return s.includes('climate_estimate') || s.includes('estimated') || s.includes('haversine')
})

const badgeClass = computed(() => {
  if (isMock.value) return 'badge-mock'
  if (isEstimated.value) return 'badge-estimated'
  return 'badge-real'
})

const tooltip = computed(() => {
  const s = normalized.value
  if (isMock.value) return '演示兜底数据'
  if (isEstimated.value) return '估算/气候数据：' + s
  return '数据源：' + s
})
</script>

<style scoped>
.ds-badge {
  display: inline-flex;
  align-items: center;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.04em;
  padding: 2px 7px;
  border-radius: 4px;
  line-height: 1.4;
  white-space: nowrap;
}
.badge-real {
  background: rgba(110, 207, 111, 0.12);
  color: var(--term-green, #6ecf6f);
  border: 1px solid rgba(110, 207, 111, 0.35);
}
.badge-estimated {
  background: rgba(212, 168, 83, 0.12);
  color: #c9a227;
  border: 1px solid rgba(212, 168, 83, 0.35);
}
.badge-mock {
  background: rgba(var(--forest-rgb), 0.15);
  color: var(--forest-400);
  border: 1px solid rgba(var(--forest-rgb), 0.4);
}
</style>
