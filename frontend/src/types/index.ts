// ============================================================
// 全局类型定义
// ============================================================

export interface User {
  userId: number
  username: string
  email: string
  role: 'USER' | 'ADMIN'
}

export interface AuthResponse {
  token: string
  userId: number
  username: string
  email: string
  role: 'USER' | 'ADMIN'
}

export interface City {
  code: string
  nameCn: string
  nameEn: string
  province: string
  description: string
  coverImage?: string
  knowledgeIngested: boolean
}

export interface TravelPreferences {
  type?: 'family' | 'couple' | 'food' | 'photography' | 'elderly' | 'backpacker'
  budget?: '经济' | '中等' | '豪华'
  days?: number
  interests?: string[]
}

export type KnowledgeCategory =
  | 'attraction'
  | 'food'
  | 'transport'
  | 'accommodation'
  | 'festival'
  | 'knowledge'

export interface ChatRequest {
  sessionId?: string
  cities?: string[]
  question: string
  preferences?: TravelPreferences
  /** 知识库分类过滤，空=不限 */
  category?: KnowledgeCategory | string
  stream?: boolean
}

export interface SourceReference {
  source: string
  city: string
  category: string
  excerpt?: string
  score?: number
}

export interface ChatResponse {
  sessionId: string
  answer: string
  sources: SourceReference[]
  timestamp: string
  retrievedChunks: number
  filteredCities: string[]
}

export type MessageRole = 'user' | 'assistant' | 'system'

export interface ChatMessage {
  id: string
  role: MessageRole
  content: string
  sources?: SourceReference[]
  timestamp: Date
  loading?: boolean
  error?: boolean
  question?: string  // original user question (stored on assistant msgs for regenerate)
  cities?: string[]  // cities at time of message (for regenerate)
  preferences?: TravelPreferences
  category?: string  // knowledge category filter at time of message
}

export interface ConversationSession {
  id: string
  title: string
  cities: string[]
  messages: ChatMessage[]
  createdAt: Date
  updatedAt: Date
}
