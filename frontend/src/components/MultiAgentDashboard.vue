<template>
  <div class="ma-dashboard">
    <!-- Header -->
    <div class="ma-header">
      <div class="ma-header-left">
        <h2 class="ma-title">多智能体协作网络</h2>
        <p class="ma-subtitle">
          <span class="ma-subtitle-count">{{ completedCount }}</span>
          <span class="ma-subtitle-sep">/</span>
          <span>{{ totalCount }}</span>
          <span class="ma-subtitle-label">个智能体已就绪</span>
        </p>
      </div>
      <div class="ma-header-right">
        <span class="ma-status" :class="status">{{ statusLabel }}</span>
        <span class="ma-elapsed" v-if="elapsed > 0">{{ formattedElapsed }}</span>
      </div>
    </div>

    <!-- DAG subgraph -->
    <AgentDependencyGraph
      :agents="agentList"
      :active-stage="activeStageNumber"
    />

    <!-- Agent Cards Grid -->
    <div class="ma-agents-grid">
      <AgentCard
        v-for="agent in agentList"
        :key="agent.info.agentId"
        :agent="agent"
      />
    </div>

    <!-- Bottom panel -->
    <div class="ma-bottom">
      <AgentActivityLog :entries="activityLog" />
      <div class="ma-bottom-right">
        <StageProgressBar :stages="stageProgress" />
        <DebateMonitor v-if="debate.active" :debate="debate" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { multiAgentApi, streamMultiAgentItinerary } from '@/api/multiAgent'
import type {
  AgentState,
  AgentInfo,
  ActivityLogEntry,
  StageProgress,
  DebateState,
  AgentStreamEvent,
  MultiAgentRequest,
} from '@/types/multiAgent'
import type { ItineraryResponse } from '@/api/agent'
import AgentCard from '@/components/AgentCard.vue'
import AgentActivityLog from '@/components/AgentActivityLog.vue'
import StageProgressBar from '@/components/StageProgressBar.vue'
import DebateMonitor from '@/components/DebateMonitor.vue'
import AgentDependencyGraph from '@/components/AgentDependencyGraph.vue'
import { EXECUTION_STAGES, getAgentDisplayName } from '@/utils/agentLabels'

const emit = defineEmits<{
  (e: 'complete', response: ItineraryResponse): void
  (e: 'error', error: Error): void
}>()

// State
const status = ref<'idle' | 'connecting' | 'running' | 'completed' | 'error'>('idle')
const elapsed = ref(0)
const agentStates = ref<Map<string, AgentState>>(new Map())
const activityLog = ref<ActivityLogEntry[]>([])
const stageProgress = ref<StageProgress[]>([])
const debate = ref<DebateState>({
  active: false,
  issue: '',
  participants: [],
  round: 0,
  maxRounds: 2,
  arguments: [],
  votes: [],
  consensus: null,
})

let timer: ReturnType<typeof setInterval> | null = null
let abortController: AbortController | null = null

// Computed
const agentList = computed(() => Array.from(agentStates.value.values()))
const totalCount = computed(() => agentList.value.length)
const completedCount = computed(() => agentList.value.filter(a => a.status === 'COMPLETED').length)
const activeStageNumber = computed(() => {
  const running = stageProgress.value.find(s => s.status === 'running')
  return running?.stageNumber ?? 0
})

const statusLabel = computed(() => {
  switch (status.value) {
    case 'connecting': return '连接中'
    case 'running': return '运行中'
    case 'completed': return '已完成'
    case 'error': return '出错'
    default: return '空闲'
  }
})

const formattedElapsed = computed(() => {
  const s = elapsed.value / 1000
  if (s < 60) return `${s.toFixed(1)}s`
  const m = Math.floor(s / 60)
  const rs = Math.floor(s % 60)
  return `${m}m ${rs}s`
})

// Initialize agent states from API
async function initAgents() {
  try {
    const { agents } = await multiAgentApi.getAgents()
    const map = new Map<string, AgentState>()
    for (const info of agents) {
      const localized = {
        ...info,
        displayName: getAgentDisplayName(info.agentId, info.displayName),
      }
      map.set(info.agentId, {
        info: localized,
        status: 'IDLE',
        stageNumber: 0,
        summary: '',
        lastThought: '',
        toolName: '',
        lastToolProvider: '',
        resultCount: 0,
        errorMessage: '',
        usedFallback: false,
        durationMs: 0,
        startedAt: 0,
      })
    }
    agentStates.value = map
  } catch {
    // If offline, show empty
  }
}

// Start multi-agent itinerary generation
async function generate(request: MultiAgentRequest) {
  status.value = 'connecting'
  elapsed.value = 0
  activityLog.value = []
  stageProgress.value = []
  debate.value = { active: false, issue: '', participants: [], round: 0, maxRounds: 2, arguments: [], votes: [], consensus: null }

  await initAgents()

  const startTime = Date.now()
  timer = setInterval(() => { elapsed.value = Date.now() - startTime }, 100)

  status.value = 'running'

  try {
    const stream = streamMultiAgentItinerary(request)

    for await (const event of stream) {
      handleEvent(event.type, event.data)
    }

    status.value = 'completed'
  } catch (e: any) {
    status.value = 'error'
    emit('error', e)
  } finally {
    if (timer) clearInterval(timer)
  }
}

// Handle each SSE event
function handleEvent(type: string, data: any) {
  if (type.startsWith('AGENT_') || type.startsWith('STAGE_') ||
      type === 'ORCHESTRATION_STARTED' || type === 'DEBATE_INITIATED' ||
      type === 'CONSENSUS_REACHED') {

    const event = data as AgentStreamEvent
    if (!event) return

    if (type === 'ORCHESTRATION_STARTED') {
      const total = (event.metadata?.totalStages as number) || EXECUTION_STAGES.length
      stageProgress.value = EXECUTION_STAGES.slice(0, total).map(s => ({
        stageNumber: s.number,
        stageName: s.name,
        agentIds: [...s.agentIds],
        agentCount: s.agentIds.length,
        completedCount: 0,
        status: 'pending' as const,
        durationMs: 0,
      }))
    }

    // Update agent state
    if (event.agentId) {
      const agent = agentStates.value.get(event.agentId)
      if (agent) {
        switch (type) {
          case 'AGENT_STARTED':
            agent.status = 'RUNNING'
            agent.stageNumber = event.stageNumber || agent.stageNumber
            agent.startedAt = event.timestampMs
            break
          case 'AGENT_THINKING':
            agent.lastThought = event.thought || ''
            break
          case 'AGENT_TOOL_CALL':
            agent.toolName = event.toolName || ''
            agent.lastToolProvider = (event.metadata?.provider as string) || ''
            break
          case 'AGENT_COMPLETED':
            agent.status = 'COMPLETED'
            agent.summary = event.summary || ''
            agent.durationMs = event.durationMs || 0
            break
          case 'AGENT_FAILED':
            agent.status = 'FAILED'
            agent.errorMessage = event.summary || ''
            break
          case 'AGENT_FALLBACK':
            agent.status = 'FALLBACK'
            agent.usedFallback = true
            break
        }
      }
    }

    // Update stage progress
    if (type === 'STAGE_STARTED') {
      const stageNum = event.stageNumber || stageProgress.value.length + 1
      const count = event.metadata?.agentCount || 0
      const preset = EXECUTION_STAGES.find(s => s.number === stageNum)
      let stage = stageProgress.value.find(s => s.stageNumber === stageNum)
      if (!stage) {
        stage = {
          stageNumber: stageNum,
          stageName: event.stageName || preset?.name || '',
          agentIds: preset ? [...preset.agentIds] : [],
          agentCount: count || preset?.agentIds.length || 0,
          completedCount: 0,
          status: 'running',
          durationMs: 0,
        }
        stageProgress.value.push(stage)
      } else {
        stage.stageName = event.stageName || stage.stageName || preset?.name || ''
        stage.agentCount = count || stage.agentCount
        stage.status = 'running'
        if (!stage.agentIds.length && preset) stage.agentIds = [...preset.agentIds]
      }
    } else if (type === 'STAGE_COMPLETED') {
      const stage = stageProgress.value.find(
        s => s.stageNumber === event.stageNumber
      )
      if (stage) {
        stage.status = 'completed'
        stage.completedCount = stage.agentCount
        stage.durationMs = event.durationMs || 0
      }
    } else if (type === 'AGENT_STARTED' && event.agentId) {
      const stage = stageProgress.value.find(s => s.stageNumber === event.stageNumber)
      if (stage && !stage.agentIds.includes(event.agentId)) {
        stage.agentIds.push(event.agentId)
      }
    } else if (type === 'AGENT_COMPLETED' || type === 'AGENT_FALLBACK') {
      const runningStage = stageProgress.value.find(s => s.status === 'running')
      if (runningStage) {
        runningStage.completedCount++
      }
    }

    // Debate events
    if (type === 'DEBATE_INITIATED') {
      debate.value.active = true
      debate.value.issue = (event.metadata?.issue as string) || event.summary || ''
      debate.value.participants = (event.metadata?.participants as string[]) || []
      debate.value.round = 1
      debate.value.arguments = []
      debate.value.votes = []
      for (const pid of debate.value.participants) {
        const p = agentStates.value.get(pid)
        if (p && p.status !== 'COMPLETED') p.status = 'DEBATING'
      }
    } else if (type === 'DEBATE_ARGUMENT') {
      debate.value.arguments.push({
        agentId: event.agentId || '',
        agentName: event.agentName || '',
        argument: (event.metadata?.argument as string) || event.summary || '',
        vote: (event.metadata?.vote as string) || 'APPROVE',
        confidence: (event.metadata?.confidence as number) || 0.8,
      })
      if (typeof event.metadata?.round === 'number') {
        debate.value.round = event.metadata.round as number
      }
    } else if (type === 'VOTE_CAST') {
      debate.value.votes.push({
        agentId: event.agentId || '',
        agentName: event.agentName || '',
        vote: (event.metadata?.vote as string) || 'APPROVE',
        confidence: (event.metadata?.confidence as number) || 0.8,
      })
    } else if (type === 'CONSENSUS_REACHED') {
      debate.value.consensus = {
        action: (event.metadata?.action as string) || 'APPROVE',
        confidence: (event.metadata?.confidence as number) || 1.0,
        voteTally: (event.metadata?.voteTally as Record<string, number>) || {},
        revisionInstructions: (event.metadata?.revisionInstructions as string) || null,
      }
      for (const pid of debate.value.participants) {
        const p = agentStates.value.get(pid)
        if (p?.status === 'DEBATING' || p?.status === 'VOTING') p.status = 'COMPLETED'
      }
    }

    // Add to activity log
    addLogEntry(type, event)
  }

  if (type === 'ERROR' || type === 'ORCHESTRATION_FAILED') {
    status.value = 'error'
    const msg = typeof data === 'string'
      ? data
      : (data?.error || data?.summary || '多 Agent 编排失败')
    emit('error', new Error(msg))
    return
  }

  // Handle final result
  if (type === 'FINAL_RESULT') {
    status.value = 'completed'
    if (data && data.itineraryId) {
      emit('complete', data as ItineraryResponse)
    }
  }
}

function addLogEntry(type: string, event: AgentStreamEvent) {
  // Minimalist symbols instead of emoji
  const iconMap: Record<string, string> = {
    AGENT_STARTED: '●',
    AGENT_THINKING: '○',
    AGENT_TOOL_CALL: '◇',
    AGENT_COMPLETED: '◆',
    AGENT_FAILED: '▲',
    AGENT_FALLBACK: '△',
    AGENT_MESSAGE: '→',
    STAGE_STARTED: '▷',
    STAGE_COMPLETED: '▶',
    DEBATE_INITIATED: '⇌',
    VOTE_CAST: '◉',
    CONSENSUS_REACHED: '●',
    FINAL_RESULT: '■',
    ORCHESTRATION_STARTED: '▸',
  }

  activityLog.value.push({
    id: event.eventId || String(Date.now()),
    timestamp: event.timestampMs || Date.now(),
    type,
    agentId: event.agentId || '',
    agentName: event.agentName || getAgentDisplayName(event.agentId || ''),
    message: event.summary || event.thought || '',
    detail: event.thought || '',
    icon: iconMap[type] || '·',
  })

  // Limit log to 200 entries
  if (activityLog.value.length > 200) {
    activityLog.value = activityLog.value.slice(-150)
  }
}

function stop() {
  if (abortController) {
    abortController.abort()
  }
  if (timer) clearInterval(timer)
  status.value = 'idle'
}

onMounted(() => { initAgents() })
onUnmounted(() => { stop() })

defineExpose({ generate, stop })
</script>

<style scoped>
.ma-dashboard {
  width: 100%;
  box-sizing: border-box;
  background: var(--white);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  padding: 28px 32px;
  overflow: hidden;
}

/* ─── Header ─────────────────────────── */
.ma-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--cream-200);
}

.ma-title {
  font-family: 'Cormorant Garamond', serif;
  font-size: 22px;
  font-weight: 600;
  color: var(--text);
  margin: 0 0 4px 0;
  letter-spacing: 0.01em;
}

.ma-subtitle {
  font-size: 13px;
  color: var(--text-3);
  margin: 0;
  display: flex;
  align-items: center;
  gap: 2px;
}

.ma-subtitle-count {
  color: var(--forest);
  font-weight: 600;
  font-size: 15px;
}

.ma-subtitle-sep {
  color: var(--cream-300);
  margin: 0 2px;
}

.ma-subtitle-label {
  margin-left: 6px;
  color: var(--text-3);
}

.ma-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.ma-status {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  padding: 3px 10px;
  border-radius: 4px;
}

.ma-status.idle       { color: var(--text-3); background: var(--cream); }
.ma-status.connecting { color: #92400e; background: #fef3c7; }
.ma-status.running    { color: var(--forest); background: rgba(var(--forest-rgb),0.08); }
.ma-status.completed  { color: #166534; background: #dcfce7; }
.ma-status.error      { color: #991b1b; background: #fee2e2; }

.ma-elapsed {
  font-size: 12px;
  color: var(--text-3);
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-weight: 500;
}

/* ─── Agent Grid ─────────────────────── */
.ma-agents-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 24px;
}

/* ─── Bottom Panel ───────────────────── */
.ma-bottom {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 900px) {
  .ma-dashboard {
    padding: 20px 18px;
  }
  .ma-agents-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .ma-bottom {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .ma-header {
    flex-direction: column;
    gap: 10px;
  }
  .ma-agents-grid {
    grid-template-columns: 1fr;
  }
}
</style>
