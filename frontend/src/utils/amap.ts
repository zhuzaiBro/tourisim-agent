import AMapLoader from '@amap/amap-jsapi-loader'
import type { PoiInfo } from '@/api/agent'
import type { AMapRoutePlanOptions } from '@/types/amap'

const AMAP_KEY = import.meta.env.VITE_AMAP_KEY as string | undefined
const AMAP_SECURITY = import.meta.env.VITE_AMAP_SECURITY_CODE as string | undefined

let amapPromise: Promise<any> | null = null

export function isAmapConfigured(): boolean {
  return !!(AMAP_KEY && AMAP_KEY.length > 10)
}

/** 动态加载高德 JS API 2.0（单例） */
export function loadAmap(): Promise<any> {
  if (!isAmapConfigured()) {
    return Promise.reject(new Error('未配置 VITE_AMAP_KEY（需 Web 端 JS API Key）'))
  }
  if (!amapPromise) {
    if (AMAP_SECURITY) {
      window._AMapSecurityConfig = { securityJsCode: AMAP_SECURITY }
    }
    amapPromise = AMapLoader.load({
      key: AMAP_KEY!,
      version: '2.0',
      plugins: [
        'AMap.Driving',
        'AMap.Walking',
        'AMap.Riding',
        'AMap.Transfer',
        'AMap.Marker',
        'AMap.Polyline',
        'AMap.InfoWindow',
      ],
    })
  }
  return amapPromise
}

export function isEstimatedRoute(dataSource?: string): boolean {
  if (!dataSource) return false
  const s = dataSource.toLowerCase()
  return s.includes('estimated') || s.includes('haversine')
}

/** 直线折线兜底（境外估算路线等） */
export function drawStraightRoute(AMap: any, map: any, pois: PoiInfo[]): any {
  const path = pois.map(p => [p.lng, p.lat])
  const line = new AMap.Polyline({
    path,
    strokeColor: '#89394D',
    strokeWeight: 5,
    strokeOpacity: 0.85,
    lineJoin: 'round',
    lineDash: [8, 6],
  })
  map.add(line)
  return line
}

function parsePathFromResult(result: any): number[][] {
  const paths: number[][] = []
  const routes = result?.routes
  if (!routes?.length) return paths
  for (const route of routes) {
    for (const step of route.steps || []) {
      const seg = step.path as Array<{ lng: number; lat: number } | [number, number]>
      if (!seg?.length) continue
      for (const p of seg) {
        if (Array.isArray(p)) paths.push([p[0], p[1]])
        else paths.push([p.lng, p.lat])
      }
    }
  }
  return paths
}

function searchSegment(
  AMap: any,
  service: any,
  from: PoiInfo,
  to: PoiInfo,
): Promise<any> {
  const origin = new AMap.LngLat(from.lng, from.lat)
  const dest = new AMap.LngLat(to.lng, to.lat)
  return new Promise((resolve, reject) => {
    service.search(origin, dest, (status: string, result: any) => {
      if (status === 'complete') resolve(result)
      else reject(result)
    })
  })
}

/** 多景点路网规划并绘制到地图 */
export async function planAndDrawRoute(
  AMap: any,
  map: any,
  pois: PoiInfo[],
  options: AMapRoutePlanOptions,
): Promise<{ mode: string; fallback: boolean }> {
  if (pois.length < 2) return { mode: 'none', fallback: true }

  const panelId = options.panelId
  const mode = options.transportMode || 'transit'
  const pluginName = mode === 'walking'
    ? 'AMap.Walking'
    : mode === 'driving'
      ? 'AMap.Driving'
      : 'AMap.Transfer'

  return new Promise((resolve) => {
    AMap.plugin([pluginName], async () => {
      const serviceOpts: Record<string, unknown> = { map }
      if (panelId) serviceOpts.panel = panelId
      if (mode === 'driving') {
        serviceOpts.policy = AMap.DrivingPolicy?.LEAST_TIME ?? 0
      }
      if (mode === 'transit' && options.cityName) {
        serviceOpts.city = options.cityName
      }

      const ServiceClass = mode === 'walking'
        ? AMap.Walking
        : mode === 'driving'
          ? AMap.Driving
          : AMap.Transfer
      const service = new ServiceClass(serviceOpts)

      try {
        if (mode === 'walking' || pois.length === 2) {
          // 步行或仅 2 点：分段规划更稳
          const allPaths: number[][] = []
          for (let i = 0; i < pois.length - 1; i++) {
            const result = await searchSegment(AMap, service, pois[i], pois[i + 1])
            allPaths.push(...parsePathFromResult(result))
          }
          if (allPaths.length > 1) {
            const line = new AMap.Polyline({
              path: allPaths,
              strokeColor: '#1a8cff',
              strokeWeight: 6,
              strokeOpacity: 0.9,
              lineJoin: 'round',
            })
            map.add(line)
          }
          resolve({ mode: pluginName, fallback: false })
          return
        }

        // 驾车/公交：起终点 + 途经点
        const start = new AMap.LngLat(pois[0].lng, pois[0].lat)
        const end = new AMap.LngLat(pois[pois.length - 1].lng, pois[pois.length - 1].lat)
        const waypoints = pois.slice(1, -1).map(
          (p: PoiInfo) => new AMap.LngLat(p.lng, p.lat),
        )

        service.search(start, end, waypoints.length ? { waypoints } : {}, (status: string) => {
          if (status === 'complete') {
            resolve({ mode: pluginName, fallback: false })
          } else {
            drawStraightRoute(AMap, map, pois)
            resolve({ mode: 'straight', fallback: true })
          }
        })
      } catch {
        drawStraightRoute(AMap, map, pois)
        resolve({ mode: 'straight', fallback: true })
      }
    })
  })
}

/** 唤起高德地图 Web/App 导航 */
export function openAmapNavigation(pois: PoiInfo[], transportMode: string) {
  if (pois.length === 0) return
  const type = transportMode === 'walking' ? 'walk' : transportMode === 'driving' ? 'car' : 'bus'
  if (pois.length === 1) {
    const p = pois[0]
    window.open(
      `https://www.amap.com/search?query=${encodeURIComponent(p.name)}&query_type=RQ`,
      '_blank',
    )
    return
  }
  const from = pois[0]
  const to = pois[pois.length - 1]
  const via = pois.slice(1, -1)
    .map(p => `${p.lng},${p.lat},${encodeURIComponent(p.name)}`)
    .join(';')
  let url = `https://www.amap.com/dir?from[lnglat]=${from.lng},${from.lat}`
    + `&from[name]=${encodeURIComponent(from.name)}`
    + `&to[lnglat]=${to.lng},${to.lat}`
    + `&to[name]=${encodeURIComponent(to.name)}`
    + `&type=${type}`
  if (via) url += `&via=${via}`
  window.open(url, '_blank')
}
