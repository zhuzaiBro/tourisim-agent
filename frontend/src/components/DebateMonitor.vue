<template>
  <div v-if="debate.active" class="debate-monitor">
    <div class="debate-header">
      <span class="debate-title">交叉验证</span>
      <span class="debate-round">第 {{ debate.round }}/{{ debate.maxRounds }} 轮</span>
    </div>

    <div class="debate-issue">
      {{ debate.issue }}
    </div>

    <!-- Arguments -->
    <div class="debate-arguments">
      <div
        v-for="arg in debate.arguments"
        :key="arg.agentId"
        class="debate-arg"
      >
        <div class="arg-header">
          <span class="arg-agent">{{ arg.agentName }}</span>
          <span class="arg-vote" :class="voteClass(arg.vote)">{{ voteLabel(arg.vote) }}</span>
          <span class="arg-confidence">{{ (arg.confidence * 100).toFixed(0) }}%</span>
        </div>
        <div class="arg-text">{{ arg.argument }}</div>
      </div>
      <div v-if="debate.arguments.length === 0" class="debate-awaiting">
        等待各方论点…
      </div>
    </div>

    <!-- Vote tally bars -->
    <div v-if="Object.keys(voteTally).length > 0" class="debate-votes">
      <div
        v-for="(count, vote) in voteTally"
        :key="vote"
        class="vote-bar"
      >
        <span class="vote-label">{{ voteLabel(vote as string) }}</span>
        <div class="vote-bar-track">
          <div
            class="vote-bar-fill"
            :class="voteClass(vote)"
            :style="{ width: barWidth(count) }"
          ></div>
        </div>
        <span class="vote-count">{{ count }}</span>
      </div>
    </div>

    <!-- Consensus -->
    <div v-if="debate.consensus" class="consensus-result" :class="debate.consensus.action.toLowerCase()">
      <div class="consensus-action">{{ consensusLabel }}</div>
      <div v-if="debate.consensus.revisionInstructions" class="consensus-detail">
        {{ debate.consensus.revisionInstructions }}
      </div>
      <span class="consensus-confidence">
        {{ (debate.consensus.confidence * 100).toFixed(0) }}% 置信度
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DebateState } from '@/types/multiAgent'

const props = defineProps<{
  debate: DebateState
}>()

const voteTally = computed(() => props.debate.consensus?.voteTally || {})

const consensusLabel = computed(() => {
  switch (props.debate.consensus?.action) {
    case 'APPROVE': return '已通过'
    case 'REVISE': return '需修改'
    case 'REJECT': return '已驳回'
    default: return props.debate.consensus?.action || ''
  }
})

function voteClass(vote: string): string {
  switch (vote) {
    case 'APPROVE': return 'approve'
    case 'REVISE': return 'revise'
    case 'REJECT': return 'reject'
    default: return ''
  }
}

function voteLabel(vote: string): string {
  switch (vote) {
    case 'APPROVE': return '通过'
    case 'REVISE': return '修改'
    case 'REJECT': return '驳回'
    default: return vote
  }
}

function barWidth(count: number): string {
  const max = Math.max(...Object.values(voteTally.value || {}), 1)
  return (count / max * 100) + '%'
}
</script>

<style scoped>
.debate-monitor {
  background: rgba(var(--accent-rgb),0.06);
  border: 1px solid rgba(var(--accent-rgb),0.2);
  border-radius: 10px;
  padding: 14px 16px;
  margin-top: 12px;
}

.debate-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.debate-title {
  font-weight: 600;
  font-size: 13px;
  color: #92400e;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
  letter-spacing: 0.02em;
}

.debate-round {
  font-size: 11px;
  color: #92400e;
  background: rgba(var(--accent-rgb),0.15);
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
}

.debate-issue {
  font-size: 12px;
  color: var(--text-2);
  padding: 8px 12px;
  background: var(--white);
  border-radius: 6px;
  margin-bottom: 10px;
  line-height: 1.5;
  border: 1px solid var(--cream-200);
}

.debate-arguments {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 10px;
}

.debate-awaiting {
  font-size: 11px;
  color: var(--text-3);
  padding: 8px;
  text-align: center;
}

.debate-arg {
  padding: 8px 12px;
  background: var(--white);
  border-radius: 6px;
  font-size: 12px;
  border: 1px solid var(--cream-200);
}

.arg-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.arg-agent {
  font-weight: 600;
  color: var(--text);
  font-size: 11px;
}

.arg-vote {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 3px;
  font-weight: 600;
  letter-spacing: 0.03em;
}

.arg-vote.approve { background: #dcfce7; color: #166534; }
.arg-vote.revise  { background: #fef3c7; color: #92400e; }
.arg-vote.reject  { background: #fee2e2; color: #991b1b; }

.arg-confidence {
  margin-left: auto;
  font-size: 10px;
  color: var(--text-3);
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.arg-text {
  color: var(--text-2);
  line-height: 1.45;
  font-size: 11px;
}

.debate-votes {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-bottom: 10px;
}

.vote-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.vote-label {
  font-size: 10px;
  font-weight: 600;
  width: 50px;
  color: var(--text-3);
  letter-spacing: 0.03em;
}

.vote-bar-track {
  flex: 1;
  height: 6px;
  background: var(--cream-300);
  border-radius: 3px;
  overflow: hidden;
}

.vote-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.5s ease;
}

.vote-bar-fill.approve { background: var(--forest-400); }
.vote-bar-fill.revise  { background: var(--gold); }
.vote-bar-fill.reject  { background: var(--earth); }

.vote-count {
  font-size: 11px;
  font-weight: 600;
  color: var(--text);
  width: 16px;
  text-align: right;
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.consensus-result {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 12px;
}

.consensus-result.approve { background: #dcfce7; color: #166534; }
.consensus-result.revise  { background: #fef3c7; color: #92400e; }
.consensus-result.reject  { background: #fee2e2; color: #991b1b; }

.consensus-action {
  font-weight: 700;
  font-size: 13px;
}

.consensus-detail {
  font-size: 11px;
  opacity: 0.8;
  line-height: 1.4;
  width: 100%;
}

.consensus-confidence {
  margin-left: auto;
  font-size: 10px;
  font-weight: 600;
  font-family: 'SF Mono', 'Fira Code', monospace;
  flex-shrink: 0;
}
</style>
