import type { ToolCallLog } from '@/api/agent'

export interface TripDataSources {
  weather?: string
  poi?: string
  food?: string
  accommodation?: string
  route?: string
}

/** 从 toolCallLogs 解析行程级数据源（对齐后端 gaode/xhs/rag 复合 provider） */
export function resolveTripDataSources(logs: ToolCallLog[] | undefined): TripDataSources {
  if (!logs?.length) return {}

  const last = (name: string) =>
    [...logs].reverse().find(l => l.toolName === name && l.success)

  const poiLog = last('searchPOI')
  const weatherLog = last('getWeather')
  const foodLog = last('recommendFood')
  const accLog = last('recommendAccommodation')
  const routeLogs = logs.filter(l => l.toolName === 'planRoute' && l.success)

  let route: string | undefined
  if (routeLogs.length > 0) {
    const providers = [...new Set(routeLogs.map(l => l.provider))]
    if (providers.every(p => p === 'estimated_route')) {
      route = 'estimated_route'
    } else if (providers.includes('gaode_api')) {
      route = routeLogs.length > 1 ? 'gaode_api+batch' : 'gaode_api'
    } else {
      route = providers[0]
    }
  }

  return {
    weather: weatherLog?.provider,
    poi: poiLog?.provider,
    food: foodLog?.provider,
    accommodation: accLog?.provider,
    route,
  }
}

/** 从多条记录的 dataSource 汇总展示用来源（优先体现小红书） */
export function aggregateDataSource(
  sources: (string | undefined)[],
): string | undefined {
  const list = sources.filter((s): s is string => !!s)
  if (!list.length) return undefined
  const hasXhs = list.some(s => s.includes('xhs'))
  const hasGaode = list.some(s => s.includes('gaode'))
  const hasRag = list.some(s => s.includes('rag'))
  const hasLlm = list.some(s => s.includes('llm'))
  const parts: string[] = []
  if (hasXhs) parts.push('xhs')
  if (hasGaode) parts.push('gaode_api')
  if (hasRag) parts.push('rag')
  if (hasLlm) parts.push('llm')
  if (parts.length > 1) return parts.join('+')
  return list[0]
}

/** 格式化 POI tag：day:2 → 第2天 */
export function formatPoiDayTag(tag: string): string | null {
  const m = /^day:(\d+)$/.exec(tag.trim())
  return m ? `第${m[1]}天` : null
}
