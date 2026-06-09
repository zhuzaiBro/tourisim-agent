import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { v4 as uuidv4 } from 'uuid'
import { chatApi, conversationApi } from '@/api'
import type { ChatMessage, ConversationSession, TravelPreferences } from '@/types'
import { ElMessage } from 'element-plus'

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<ConversationSession[]>([])
  const currentSessionId = ref<string | null>(null)
  const loading = ref(false)
  const useStream = ref(false)
  const initialized = ref(false)
  /** 知识库分类过滤：空=全部 */
  const categoryFilter = ref<string | undefined>(undefined)

  const currentSession = computed(() =>
    sessions.value.find(s => s.id === currentSessionId.value) ?? null
  )

  const currentMessages = computed(() =>
    currentSession.value?.messages ?? []
  )

  // ---- Initialize: load conversations from backend ----

  async function init() {
    if (initialized.value) return
    initialized.value = true
    try {
      const list = await conversationApi.list()
      sessions.value = list.map(c => ({
        id: c.id,
        title: c.title,
        cities: c.cities || [],
        messages: [],
        createdAt: new Date(c.createdAt),
        updatedAt: new Date(c.updatedAt),
        messagesLoaded: false,
      } as ConversationSession & { messagesLoaded?: boolean }))
      if (sessions.value.length > 0) {
        currentSessionId.value = sessions.value[0].id
      }
    } catch (e) {
      // backend unavailable, start fresh
    }
  }

  // ---- Load messages for a session (lazy, on switch) ----

  async function loadMessages(sessionId: string) {
    const session = sessions.value.find(s => s.id === sessionId) as any
    if (!session || session.messagesLoaded) return
    try {
      const records = await conversationApi.getMessages(sessionId)
      session.messages = records.map((r: any) => ({
        id: String(r.id),
        role: r.role,
        content: r.content,
        sources: r.sources || [],
        timestamp: new Date(r.timestamp),
        loading: false,
      } as ChatMessage))
      session.messagesLoaded = true
    } catch (e) {
      // ignore
    }
  }

  // ---- Session management ----

  async function createSession(cities: string[] = []): Promise<string> {
    const title = cities.length > 0 ? cities.join('+') + '之旅' : '新对话'
    try {
      const conv = await conversationApi.create(title, cities)
      const session: ConversationSession = {
        id: conv.id,
        title: conv.title,
        cities,
        messages: [],
        createdAt: new Date(conv.createdAt),
        updatedAt: new Date(conv.updatedAt),
      }
      sessions.value.unshift(session)
      ;(session as any).messagesLoaded = true
      currentSessionId.value = conv.id
      return conv.id
    } catch (e) {
      // fallback: local only
      const sessionId = uuidv4()
      const session: ConversationSession = {
        id: sessionId,
        title,
        cities,
        messages: [],
        createdAt: new Date(),
        updatedAt: new Date(),
      }
      sessions.value.unshift(session)
      ;(session as any).messagesLoaded = true
      currentSessionId.value = sessionId
      return sessionId
    }
  }

  async function switchSession(sessionId: string) {
    currentSessionId.value = sessionId
    await loadMessages(sessionId)
  }

  async function deleteSession(sessionId: string) {
    const idx = sessions.value.findIndex(s => s.id === sessionId)
    if (idx !== -1) sessions.value.splice(idx, 1)
    // Remove from backend
    conversationApi.delete(sessionId).catch(() => {})
    chatApi.clearSession(sessionId).catch(() => {})
    if (currentSessionId.value === sessionId) {
      currentSessionId.value = sessions.value[0]?.id ?? null
      if (currentSessionId.value) {
        await loadMessages(currentSessionId.value)
      }
    }
  }

  function ensureCurrentSession(cities: string[]): Promise<string> {
    if (!currentSessionId.value) return createSession(cities)
    return Promise.resolve(currentSessionId.value)
  }

  // ---- Message sending ----

  async function sendMessage(
    question: string,
    cities: string[],
    preferences?: TravelPreferences,
    category?: string
  ) {
    if (loading.value || !question.trim()) return

    const sessionId = await ensureCurrentSession(cities)
    const session = sessions.value.find(s => s.id === sessionId)!

    if (session.messages.length === 0) {
      const newTitle = question.slice(0, 20) + (question.length > 20 ? '...' : '')
      session.title = newTitle
      // Update title in backend
      conversationApi.updateTitle(sessionId, newTitle).catch(() => {})
    }

    const userMsg: ChatMessage = {
      id: uuidv4(),
      role: 'user',
      content: question,
      timestamp: new Date(),
    }
    session.messages.push(userMsg)

    const assistantMsg: ChatMessage = {
      id: uuidv4(),
      role: 'assistant',
      content: '',
      timestamp: new Date(),
      loading: true,
      question,
      cities: [...cities],
      preferences: preferences ? { ...preferences } : undefined,
      category: category ?? categoryFilter.value,
    }
    session.messages.push(assistantMsg)
    session.updatedAt = new Date()
    loading.value = true

    try {
      if (useStream.value) {
        await sendMessageStream(sessionId, question, cities, preferences, category, assistantMsg)
      } else {
        await sendMessageNormal(sessionId, question, cities, preferences, category, assistantMsg)
      }
    } catch (error: any) {
      assistantMsg.content = '抱歉，请求失败，请稍后重试。'
      assistantMsg.error = true
      assistantMsg.loading = false
      ElMessage.error('AI 响应失败：' + (error.message || '未知错误'))
    } finally {
      loading.value = false
    }
  }

  async function regenerateMessage(messageId: string) {
    if (loading.value || !currentSession.value) return

    const session = currentSession.value
    const msgIdx = session.messages.findIndex(m => m.id === messageId)
    if (msgIdx === -1) return

    const msg = session.messages[msgIdx]
    if (msg.role !== 'assistant' || !msg.question) return

    msg.content = ''
    msg.loading = true
    msg.error = false
    msg.sources = undefined
    session.updatedAt = new Date()
    loading.value = true

    try {
      if (useStream.value) {
        await sendMessageStream(session.id, msg.question, msg.cities ?? [], msg.preferences, msg.category, msg)
      } else {
        await sendMessageNormal(session.id, msg.question, msg.cities ?? [], msg.preferences, msg.category, msg)
      }
    } catch (error: any) {
      msg.content = '抱歉，重新生成失败，请稍后重试。'
      msg.error = true
      msg.loading = false
      ElMessage.error('重新生成失败：' + (error.message || '未知错误'))
    } finally {
      loading.value = false
    }
  }

  async function sendMessageNormal(
    sessionId: string,
    question: string,
    cities: string[],
    preferences: TravelPreferences | undefined,
    category: string | undefined,
    assistantMsg: ChatMessage
  ) {
    const response = await chatApi.chat({ sessionId, cities, question, preferences, category, stream: false })
    assistantMsg.content = response.answer
    assistantMsg.sources = response.sources
    assistantMsg.loading = false
  }

  async function sendMessageStream(
    sessionId: string,
    question: string,
    cities: string[],
    preferences: TravelPreferences | undefined,
    category: string | undefined,
    assistantMsg: ChatMessage
  ) {
    const generator = chatApi.chatStream({ sessionId, cities, question, preferences, category, stream: true })
    assistantMsg.loading = false
    for await (const token of generator) {
      assistantMsg.content += token
    }
  }

  async function clearCurrentSession() {
    if (!currentSessionId.value) return
    const session = currentSession.value
    if (session) {
      session.messages = []
      ;(session as any).messagesLoaded = true
      try {
        await conversationApi.clearMessages(currentSessionId.value)
        await chatApi.clearSession(currentSessionId.value)
      } catch (e) {}
      ElMessage.success('对话已清空')
    }
  }

  function reset() {
    sessions.value = []
    currentSessionId.value = null
    initialized.value = false
    loading.value = false
  }

  return {
    sessions,
    currentSessionId,
    currentSession,
    currentMessages,
    loading,
    useStream,
    categoryFilter,
    initialized,
    init,
    createSession,
    switchSession,
    deleteSession,
    sendMessage,
    regenerateMessage,
    clearCurrentSession,
    reset,
  }
})
