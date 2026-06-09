/** 高德 JS API 2.0 最小类型声明 */
declare global {
  interface Window {
    _AMapSecurityConfig?: { securityJsCode: string }
  }
}

export interface AMapLngLat {
  lng: number
  lat: number
}

export interface AMapRoutePlanOptions {
  transportMode: 'walking' | 'driving' | 'transit'
  cityName?: string
  panelId?: string
}

export {}
