/** Vercel / 生产构建直连 API；本地 dev 留空则走 Vite 代理 /api */
const PROD_DEFAULT = 'https://tourisim-api.zood.work'
const RAW =
  (import.meta.env.VITE_API_BASE_URL as string | undefined)?.trim() ||
  (import.meta.env.PROD ? PROD_DEFAULT : '')

export function getApiOrigin(): string {
  return RAW.replace(/\/$/, '')
}

/** 拼接 API 路径，如 apiUrl('/api/chat') */
export function apiUrl(path: string): string {
  const origin = getApiOrigin()
  const normalized = path.startsWith('/') ? path : `/${path}`
  return origin ? `${origin}${normalized}` : normalized
}
