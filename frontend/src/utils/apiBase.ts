/** 生产环境 API 域名（.env 中 VITE_API_BASE_URL）；开发留空则走 Vite 代理 /api */
const RAW = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.trim() || ''

export function getApiOrigin(): string {
  return RAW.replace(/\/$/, '')
}

/** 拼接 API 路径，如 apiUrl('/api/chat') */
export function apiUrl(path: string): string {
  const origin = getApiOrigin()
  const normalized = path.startsWith('/') ? path : `/${path}`
  return origin ? `${origin}${normalized}` : normalized
}
