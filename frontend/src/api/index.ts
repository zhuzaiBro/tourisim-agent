import axios from 'axios'
import type { ChatRequest, ChatResponse, City, AuthResponse, User } from '@/types'
import { ElMessage } from 'element-plus'

// ============================================================
// Axios 实例配置
// ============================================================
const api = axios.create({
  baseURL: '/api',
  timeout: 120000,
  headers: {
    'Content-Type': 'application/json',
    'Accept-Charset': 'utf-8',
  },
})

// 请求拦截器 - 注入 JWT Token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('voyage_token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Token 过期或无效，清除登录状态并跳转
      localStorage.removeItem('voyage_token')
      localStorage.removeItem('voyage_user')
      localStorage.removeItem('voyage_sessions')
      window.location.href = '/login'
      return Promise.reject(error)
    }
    const message = error.response?.data?.message
      || error.response?.data?.error
      || error.message
      || '网络请求失败'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

// ============================================================
// 认证 API
// ============================================================
export const authApi = {
  register(data: { username: string; email: string; password: string }): Promise<AuthResponse> {
    return api.post('/auth/register', data).then(r => r.data)
  },

  login(data: { email: string; password: string }): Promise<AuthResponse> {
    return api.post('/auth/login', data).then(r => r.data)
  },

  /** Admin-only login — returns 400 if not ADMIN role */
  adminLogin(data: { email: string; password: string }): Promise<AuthResponse> {
    return api.post('/auth/admin/login', data).then(r => r.data)
  },

  /** Bootstrap first admin account (requires setupKey) */
  adminSetup(data: { setupKey: string; username: string; email: string; password: string }): Promise<AuthResponse> {
    return api.post('/auth/admin/setup', data).then(r => r.data)
  },

  me(): Promise<User> {
    return api.get('/auth/me').then(r => r.data)
  },
}

// ============================================================
// 对话历史 API
// ============================================================
export interface ConversationMeta {
  id: string
  title: string
  cities: string[]
  createdAt: string
  updatedAt: string
}

export interface MessageRecord {
  id: number
  role: 'user' | 'assistant'
  content: string
  sources?: ChatResponse['sources']
  timestamp: string
}

export const conversationApi = {
  list(): Promise<ConversationMeta[]> {
    return api.get('/conversations').then(r => r.data)
  },

  create(title: string, cities: string[]): Promise<ConversationMeta> {
    return api.post('/conversations', { title, cities }).then(r => r.data)
  },

  updateTitle(id: string, title: string): Promise<ConversationMeta> {
    return api.put(`/conversations/${id}/title`, { title }).then(r => r.data)
  },

  delete(id: string): Promise<void> {
    return api.delete(`/conversations/${id}`).then(r => r.data)
  },

  getMessages(id: string): Promise<MessageRecord[]> {
    return api.get(`/conversations/${id}/messages`).then(r => r.data)
  },

  clearMessages(id: string): Promise<void> {
    return api.delete(`/conversations/${id}/messages`).then(r => r.data)
  },
}

// ============================================================
// 城市 API
// ============================================================
export const cityApi = {
  getEnabledCities(): Promise<City[]> {
    return api.get('/cities').then(r => r.data)
  },

  addCity(city: Partial<City>): Promise<City> {
    return api.post('/cities', city).then(r => r.data)
  },

  initDefaultCities(): Promise<{ message: string }> {
    return api.post('/cities/init').then(r => r.data)
  },
}

// ============================================================
// 聊天 API
// ============================================================
export const chatApi = {
  chat(request: ChatRequest): Promise<ChatResponse> {
    return api.post('/chat', request).then(r => r.data)
  },

  clearSession(sessionId: string): Promise<void> {
    return api.delete(`/chat/${sessionId}`).then(r => r.data)
  },

  async *chatStream(request: ChatRequest): AsyncGenerator<string> {
    const token = localStorage.getItem('voyage_token')
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (token) headers['Authorization'] = `Bearer ${token}`

    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers,
      body: JSON.stringify({ ...request, stream: true }),
    })

    if (!response.ok || !response.body) {
      throw new Error(`HTTP error: ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const chunk = decoder.decode(value, { stream: true })
      const lines = chunk.split('\n')
      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const data = line.slice(6)
          if (data === '[DONE]') {
              reader.cancel()
              return
            }
          yield data.replace(/\\n/g, '\n')
        }
      }
    }
  },
}

// ============================================================
// 摄入 API
// ============================================================
export const ingestApi = {
  ingestQingdao(): Promise<{ message: string; status: string }> {
    return api.post('/ingest/qingdao').then(r => r.data)
  },

  ingestCity(request: {
    cityCode: string
    sourceType: 'BUILTIN' | 'FILE' | 'URL' | 'MYSQL'
    filePath?: string
    url?: string
  }): Promise<{ message: string; status: string }> {
    return api.post('/ingest/city', request).then(r => r.data)
  },

  uploadFile(file: File, cityCode: string, category: string = 'knowledge'): Promise<{ message: string; status: string; fileName: string }> {
    const form = new FormData()
    form.append('file', file)
    form.append('cityCode', cityCode)
    form.append('category', category)
    return api.post('/ingest/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000,
    }).then(r => r.data)
  },

  ingestFromDb(cityCode: string): Promise<{ message: string; status: string }> {
    return api.post(`/ingest/db/${cityCode}`).then(r => r.data)
  },
}

// ============================================================
// 城市管理 API（管理界面用）
// ============================================================
export const cityManageApi = {
  getAllCities(): Promise<CityAdmin[]> {
    return api.get('/cities/all').then(r => r.data)
  },

  addCity(city: {
    code: string
    nameCn: string
    nameEn?: string
    province?: string
    description?: string
  }): Promise<CityAdmin> {
    return api.post('/cities', city).then(r => r.data)
  },

  initDefault(): Promise<{ message: string }> {
    return api.post('/cities/init').then(r => r.data)
  },
}

export interface CityAdmin {
  code: string
  nameCn: string
  nameEn: string
  province: string
  description: string
  enabled: boolean
  knowledgeIngested: boolean
}

export default api
