import axios from 'axios'
import type {
  AgentInfo,
  MultiAgentRequest,
} from '@/types/multiAgent'
import { apiUrl } from '@/utils/apiBase'

// Reuse the same ItineraryResponse from agent.ts
import type { ItineraryResponse } from './agent'

const api = axios.create({
  baseURL: apiUrl('/api/multi-agent'),
  timeout: 180000, // 3 min for full orchestration
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('voyage_token')
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  return config
})

// ──────────────────────────── API ────────────────────────────

export const multiAgentApi = {
  /** Non-streaming itinerary generation */
  generateItinerary(req: MultiAgentRequest): Promise<ItineraryResponse> {
    return api.post('/itinerary', req).then(r => r.data)
  },

  /** Get all registered agents */
  getAgents(): Promise<{ agents: AgentInfo[]; totalCount: number; enabled: boolean }> {
    return api.get('/agents').then(r => r.data)
  },

  /** Get multi-agent system status */
  getStatus(): Promise<any> {
    return api.get('/status').then(r => r.data)
  },
}

/**
 * SSE streaming client for multi-agent itinerary generation.
 * Returns an AsyncGenerator that yields AgentStreamEvent objects and
 * the final ItineraryResponse.
 */
export async function* streamMultiAgentItinerary(
  req: MultiAgentRequest
): AsyncGenerator<{ type: string; data: any }> {
  const token = localStorage.getItem('voyage_token')

  const response = await fetch(apiUrl('/api/multi-agent/itinerary/stream'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(req),
  })

  if (!response.ok) {
    let message = `请求失败 (${response.status})`
    try {
      const body = await response.json()
      if (body?.message) message = body.message
    } catch {
      // ignore non-JSON body
    }
    throw new Error(message)
  }

  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    let currentEventType = ''
    let currentData = ''

    for (const line of lines) {
      if (line.startsWith('event:')) {
        currentEventType = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        currentData = line.slice(5).trim()
      } else if (line === '' && currentData) {
        try {
          const parsed = JSON.parse(currentData)
          yield { type: currentEventType || 'message', data: parsed }
        } catch {
          yield { type: currentEventType || 'message', data: currentData }
        }
        currentEventType = ''
        currentData = ''
      }
    }
  }
}
