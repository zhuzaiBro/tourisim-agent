// ──────────────────────────── Multi-Agent Types ────────────────────────────

/** Agent metadata (from GET /api/multi-agent/agents) */
export interface AgentInfo {
  agentId: string
  displayName: string
  roleDescription: string
  style: string
  icon: string
  dependencies: string[]
  toolNames: string[]
}

/** Agent runtime state during orchestration */
export type AgentStatus =
  | 'IDLE'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'FALLBACK'
  | 'DEBATING'
  | 'VOTING'

/** Runtime tracking per agent */
export interface AgentState {
  info: AgentInfo
  status: AgentStatus
  stageNumber: number
  summary: string
  lastThought: string
  toolName: string
  lastToolProvider: string
  resultCount: number
  errorMessage: string
  usedFallback: boolean
  durationMs: number
  startedAt: number
}

/** A single SSE event from the streaming endpoint */
export interface AgentStreamEvent {
  type: string
  eventId: string
  agentId: string
  agentName: string
  stageName: string
  stageNumber: number
  summary: string
  thought: string
  toolName: string
  metadata: Record<string, any>
  timestampMs: number
  durationMs: number
}

/** Stage progress tracking */
export interface StageProgress {
  stageNumber: number
  stageName: string
  agentIds: string[]
  agentCount: number
  completedCount: number
  status: 'pending' | 'running' | 'completed'
  durationMs: number
}

/** Activity log entry (for the scrolling log) */
export interface ActivityLogEntry {
  id: string
  timestamp: number
  type: string
  agentId: string
  agentName: string
  message: string
  detail: string
  icon: string
}

/** Debate state */
export interface DebateState {
  active: boolean
  issue: string
  participants: string[]
  round: number
  maxRounds: number
  arguments: DebateArgument[]
  votes: VoteRecord[]
  consensus: ConsensusInfo | null
}

export interface DebateArgument {
  agentId: string
  agentName: string
  argument: string
  vote: string
  confidence: number
}

export interface VoteRecord {
  agentId: string
  agentName?: string
  vote: string
  confidence: number
  reason?: string
}

export interface ConsensusInfo {
  action: 'APPROVE' | 'REVISE' | 'REJECT'
  confidence: number
  voteTally: Record<string, number>
  revisionInstructions: string | null
}

/** Multi-agent request */
export interface MultiAgentRequest {
  cityCode: string
  cityName?: string
  startDate: string
  endDate: string
  preferences?: string[]
  dietaryRestrictions?: string[]
  tastePreferences?: string[]
  budget?: 'low' | 'medium' | 'high'
  transportMode?: 'walking' | 'driving' | 'transit'
  accommodationType?: 'hotel' | 'homestay' | 'hostel' | 'any'
  adults?: number
  children?: number
}
