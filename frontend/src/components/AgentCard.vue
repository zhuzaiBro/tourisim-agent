<template>
  <div
    class="agent-card"
    :class="[statusClass, { compact }]"
  >
    <div class="agent-initial" :class="statusClass">{{ icon }}</div>
    <div class="agent-body">
      <div class="agent-name">{{ name }}</div>
      <div v-if="!compact" class="agent-role">{{ role }}</div>
      <div class="agent-status-line">
        <span class="status-dot" :class="statusClass"></span>
        <span class="status-text">{{ statusText }}</span>
        <span v-if="durationMs > 0" class="duration">{{ formatDuration }}</span>
      </div>
      <div v-if="toolHint && !compact" class="agent-tool">{{ toolHint }}</div>
      <div v-if="summary && !compact" class="agent-summary" :title="summary">{{ summary }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AgentState } from '@/types/multiAgent'
import { getAgentDisplayName } from '@/utils/agentLabels'

const props = withDefaults(defineProps<{
  agent: AgentState
  compact?: boolean
}>(), {
  compact: false
})

const name = computed(() =>
  getAgentDisplayName(props.agent.info.agentId, props.agent.info.displayName)
)
const role = computed(() => props.agent.info.roleDescription)
const summary = computed(() => props.agent.summary)
const durationMs = computed(() => props.agent.durationMs)

const icon = computed(() => {
  if (props.agent.info.icon) return props.agent.info.icon
  const n = name.value
  return n ? n.charAt(0) : '?'
})

const toolHint = computed(() => {
  if (!props.agent.toolName) return ''
  const provider = props.agent.lastToolProvider
  return provider ? `${props.agent.toolName} · ${provider}` : props.agent.toolName
})

const formatDuration = computed(() => {
  const ms = durationMs.value
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
})

const statusClass = computed(() => {
  switch (props.agent.status) {
    case 'RUNNING': return 'running'
    case 'COMPLETED': return 'completed'
    case 'FAILED': return 'failed'
    case 'FALLBACK': return 'fallback'
    case 'DEBATING': return 'debating'
    case 'VOTING': return 'voting'
    default: return 'idle'
  }
})

const statusText = computed(() => {
  switch (props.agent.status) {
    case 'IDLE': return '等待中'
    case 'RUNNING': return '工作中'
    case 'COMPLETED': return '已完成'
    case 'FAILED': return '失败'
    case 'FALLBACK': return '降级'
    case 'DEBATING': return '辩论中'
    case 'VOTING': return '投票中'
    default: return props.agent.status
  }
})
</script>

<style scoped>
.agent-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--cream);
  border: 1px solid transparent;
  border-left: 3px solid var(--cream-300);
  transition: border-color 0.3s ease, background 0.3s ease;
  min-width: 0;
}

.agent-card.compact {
  padding: 8px 10px;
  gap: 8px;
}

.agent-card.running {
  border-left-color: var(--forest);
  background: linear-gradient(90deg, rgba(var(--forest-rgb),0.04) 0%, rgba(var(--forest-rgb),0.01) 40%, transparent 100%);
}

.agent-card.completed {
  border-left-color: var(--forest-400);
  background: linear-gradient(90deg, rgba(var(--forest-rgb),0.06) 0%, transparent 100%);
}

.agent-card.failed,
.agent-card.fallback {
  border-left-color: var(--earth);
  background: linear-gradient(90deg, rgba(139,90,60,0.06) 0%, transparent 100%);
}

.agent-card.debating {
  border-left-color: var(--gold);
  background: linear-gradient(90deg, rgba(var(--accent-rgb),0.08) 0%, transparent 100%);
}

.agent-initial {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  flex-shrink: 0;
  background: var(--white);
  border: 1px solid var(--cream-300);
  transition: all 0.3s ease;
}

.compact .agent-initial {
  width: 26px;
  height: 26px;
  font-size: 13px;
  border-radius: 4px;
}

.agent-initial.running {
  border-color: var(--forest);
  background: rgba(var(--forest-rgb),0.06);
}

.agent-initial.completed {
  background: rgba(var(--forest-rgb),0.1);
  border-color: var(--forest-400);
}

.agent-initial.failed,
.agent-initial.fallback {
  border-color: var(--earth);
  background: rgba(139,90,60,0.06);
}

.agent-initial.debating {
  border-color: var(--gold);
  background: rgba(var(--accent-rgb),0.1);
}

.agent-body {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.agent-name {
  font-weight: 600;
  font-size: 13px;
  color: var(--text);
  margin-bottom: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.compact .agent-name {
  font-size: 12px;
}

.agent-role {
  font-size: 11px;
  color: var(--text-3);
  margin-bottom: 5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-status-line {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: var(--text-3);
}

.status-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-dot.idle      { background: var(--cream-300); }
.status-dot.running    { background: var(--forest); }
.status-dot.completed  { background: var(--forest-400); }
.status-dot.failed     { background: var(--earth); }
.status-dot.fallback   { background: var(--earth-light); }
.status-dot.debating   { background: var(--gold); }
.status-dot.voting     { background: var(--gold); }

.status-text {
  font-weight: 500;
  white-space: nowrap;
}

.duration {
  margin-left: auto;
  font-size: 10px;
  color: var(--text-3);
  font-family: 'SF Mono', 'Fira Code', monospace;
  flex-shrink: 0;
}

.agent-tool {
  margin-top: 3px;
  font-size: 10px;
  color: var(--text-3);
  font-family: 'SF Mono', 'Fira Code', monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-summary {
  margin-top: 4px;
  font-size: 11px;
  color: var(--text-2);
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
