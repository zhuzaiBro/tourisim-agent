<template>
  <div class="stage-progress">
    <div class="stages-title">编排阶段</div>
    <div class="stages-list">
      <div
        v-for="stage in stages"
        :key="stage.stageNumber"
        class="stage-row"
        :class="stage.status"
      >
        <div class="stage-indicator">
          <div class="stage-circle" :class="stage.status">
            <span v-if="stage.status === 'completed'">
              <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                <path d="M1.5 5l2.5 2.5L8.5 2" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </span>
            <span v-else-if="stage.status === 'running'" class="spinner"></span>
            <span v-else class="stage-num">{{ stage.stageNumber }}</span>
          </div>
          <div v-if="stage.stageNumber < stages.length" class="stage-line" :class="stage.status"></div>
        </div>
        <div class="stage-info">
          <div class="stage-name">{{ stage.stageName }}</div>
          <div v-if="stage.agentIds.length" class="stage-agents">
            <span v-for="aid in stage.agentIds" :key="aid" class="stage-agent-chip">
              {{ agentLabel(aid) }}
            </span>
          </div>
          <div class="stage-meta">
            {{ stage.completedCount }}/{{ stage.agentCount }}
            <span v-if="stage.durationMs > 0"> &middot; {{ (stage.durationMs / 1000).toFixed(1) }}s</span>
          </div>
        </div>
      </div>
      <div v-if="stages.length === 0" class="stages-empty">
        等待阶段启动…
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { StageProgress } from '@/types/multiAgent'
import { getAgentDisplayName } from '@/utils/agentLabels'

defineProps<{
  stages: StageProgress[]
}>()

function agentLabel(agentId: string): string {
  const full = getAgentDisplayName(agentId)
  return full.length > 6 ? full.slice(0, 5) + '…' : full
}
</script>

<style scoped>
.stage-progress {
  padding: 0;
}

.stages-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-2);
  margin-bottom: 12px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
}

.stages-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.stages-empty {
  font-size: 12px;
  color: var(--text-3);
  padding: 8px 0;
}

.stage-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 5px 0;
}

.stage-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}

.stage-circle {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  transition: all 0.3s ease;
  flex-shrink: 0;
}

.stage-circle.pending {
  background: var(--cream);
  color: var(--text-3);
  border: 1.5px solid var(--cream-300);
}

.stage-circle.running {
  background: rgba(var(--forest-rgb),0.06);
  color: var(--forest);
  border: 1.5px solid var(--forest);
}

.stage-circle.completed {
  background: var(--forest);
  color: var(--white);
  border: 1.5px solid var(--forest);
}

.stage-num {
  font-size: 10px;
}

.spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(var(--forest-rgb),0.15);
  border-top-color: var(--forest);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.stage-line {
  width: 1.5px;
  flex: 1;
  min-height: 16px;
  background: var(--cream-300);
  transition: background 0.3s;
}

.stage-line.completed { background: var(--forest-400); }
.stage-line.running {
  background: linear-gradient(to bottom, var(--forest), var(--cream-300));
}

.stage-info {
  padding-top: 3px;
  min-width: 0;
}

.stage-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--text);
  margin-bottom: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.stage-agents {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin: 3px 0 2px;
}

.stage-agent-chip {
  font-size: 9px;
  padding: 1px 5px;
  border-radius: 3px;
  background: var(--cream);
  color: var(--text-3);
  border: 1px solid var(--cream-300);
}

.stage-row.running .stage-agent-chip {
  color: var(--forest);
  border-color: rgba(var(--forest-rgb), 0.25);
}

.stage-row.completed .stage-agent-chip {
  color: var(--forest-400);
}

.stage-meta {
  font-size: 10px;
  color: var(--text-3);
  font-family: 'SF Mono', 'Fira Code', monospace;
}
</style>
