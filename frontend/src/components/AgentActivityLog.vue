<template>
  <div class="activity-log">
    <div class="log-header">
      <span class="log-title">活动日志</span>
      <span class="log-count">{{ entries.length }}</span>
    </div>
    <div ref="logContainer" class="log-entries">
      <div
        v-for="entry in entries"
        :key="entry.id"
        class="log-entry"
        :class="entry.type"
      >
        <span class="log-time">{{ formatTime(entry.timestamp) }}</span>
        <span class="log-symbol" :class="entry.type">{{ entry.icon }}</span>
        <span class="log-agent">{{ entry.agentName || '系统' }}</span>
        <span class="log-message">{{ entry.message }}</span>
      </div>
      <div v-if="entries.length === 0" class="log-empty">
        等待编排事件…
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import type { ActivityLogEntry } from '@/types/multiAgent'

const props = defineProps<{
  entries: ActivityLogEntry[]
}>()

const logContainer = ref<HTMLElement>()

watch(() => props.entries.length, async () => {
  await nextTick()
  if (logContainer.value) {
    logContainer.value.scrollTop = logContainer.value.scrollHeight
  }
})

function formatTime(ts: number): string {
  return new Date(ts).toLocaleTimeString('zh-CN', {
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  })
}
</script>

<style scoped>
.activity-log {
  background: var(--forest);
  border-radius: 10px;
  overflow: hidden;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: rgba(0,0,0,0.2);
  border-bottom: 1px solid rgba(255,255,255,0.06);
}

.log-title {
  color: rgba(255,255,255,0.7);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.04em;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
}

.log-count {
  color: rgba(255,255,255,0.3);
  font-size: 10px;
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.log-entries {
  max-height: 240px;
  overflow-y: auto;
  padding: 6px 0;
}

.log-entry {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 3px 14px;
  font-size: 11px;
  line-height: 1.7;
  transition: background 0.15s;
}
.log-entry:hover {
  background: rgba(255,255,255,0.03);
}

.log-time {
  color: rgba(255,255,255,0.18);
  flex-shrink: 0;
  font-size: 10px;
}

.log-symbol {
  flex-shrink: 0;
  font-size: 8px;
  width: 10px;
  text-align: center;
  color: rgba(255,255,255,0.25);
}

/* Symbol colors by event type */
.log-symbol.AGENT_STARTED       { color: rgba(255,255,255,0.6); }
.log-symbol.AGENT_COMPLETED     { color: #7ebf8f; }
.log-symbol.AGENT_FAILED        { color: #e08870; }
.log-symbol.AGENT_TOOL_CALL     { color: var(--gold); }
.log-symbol.DEBATE_INITIATED    { color: var(--gold); }
.log-symbol.CONSENSUS_REACHED   { color: var(--gold); }
.log-symbol.STAGE_STARTED       { color: rgba(255,255,255,0.8); }
.log-symbol.FINAL_RESULT        { color: #7ebf8f; }

.log-agent {
  color: rgba(255,255,255,0.45);
  flex-shrink: 0;
  font-weight: 500;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log-message {
  color: rgba(255,255,255,0.3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Message color by event type */
.log-entry.AGENT_STARTED .log-message   { color: rgba(255,255,255,0.65); }
.log-entry.AGENT_COMPLETED .log-message  { color: #7ebf8f; }
.log-entry.AGENT_FAILED .log-message     { color: #e08870; }
.log-entry.AGENT_TOOL_CALL .log-message  { color: rgba(var(--accent-rgb),0.8); }
.log-entry.DEBATE_INITIATED .log-message { color: rgba(var(--accent-rgb),0.7); }
.log-entry.CONSENSUS_REACHED .log-message { color: rgba(var(--accent-rgb),0.7); }
.log-entry.STAGE_STARTED .log-message    { color: rgba(255,255,255,0.9); font-weight: 500; }
.log-entry.FINAL_RESULT .log-message     { color: #7ebf8f; font-weight: 500; }

.log-empty {
  padding: 28px;
  text-align: center;
  color: rgba(255,255,255,0.15);
  font-size: 12px;
  font-family: 'DM Sans', 'PingFang SC', sans-serif;
}
</style>
