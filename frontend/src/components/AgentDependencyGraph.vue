<template>
  <div class="agent-graph">
    <div class="graph-title">编排子图 · DAG</div>
    <svg
      class="graph-svg"
      viewBox="0 0 380 400"
      xmlns="http://www.w3.org/2000/svg"
      aria-label="多智能体依赖子图"
    >
      <!-- 依赖边 -->
      <g class="edges">
        <line
          v-for="(edge, i) in edges"
          :key="'e' + i"
          :x1="edge.x1"
          :y1="edge.y1"
          :x2="edge.x2"
          :y2="edge.y2"
          class="edge-line"
          :class="{ active: isEdgeActive(edge) }"
          marker-end="url(#arrow)"
        />
      </g>
      <defs>
        <marker id="arrow" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto">
          <path d="M0,0 L6,3 L0,6 Z" class="arrow-head" />
        </marker>
      </defs>

      <!-- 阶段背景带 -->
      <g class="stage-bands">
        <rect
          v-for="stage in EXECUTION_STAGES"
          :key="'band' + stage.number"
          :x="8"
          :y="stageBandY(stage.number) - 14"
          width="344"
          height="52"
          rx="6"
          class="stage-band"
          :class="stageBandClass(stage.number)"
        />
        <text
          v-for="stage in EXECUTION_STAGES"
          :key="'lbl' + stage.number"
          :x="14"
          :y="stageBandY(stage.number) + 4"
          class="stage-band-label"
        >S{{ stage.number }} {{ stage.name }}</text>
      </g>

      <!-- 节点 -->
      <g
        v-for="agent in graphAgents"
        :key="agent.info.agentId"
        class="node-group"
        :class="statusClass(agent.status)"
      >
        <rect
          :x="agent.pos.x - 52"
          :y="agent.pos.y - 18"
          width="104"
          height="36"
          rx="6"
          class="node-rect"
        />
        <text :x="agent.pos.x" :y="agent.pos.y - 4" class="node-icon" text-anchor="middle">
          {{ agent.info.icon || '◇' }}
        </text>
        <text :x="agent.pos.x" :y="agent.pos.y + 10" class="node-label" text-anchor="middle">
          {{ shortName(agent.info.agentId) }}
        </text>
      </g>
    </svg>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AgentState } from '@/types/multiAgent'
import {
  AGENT_GRAPH_POSITIONS,
  EXECUTION_STAGES,
  getAgentDisplayName,
} from '@/utils/agentLabels'

const props = defineProps<{
  agents: AgentState[]
  activeStage?: number
}>()

interface Edge {
  from: string
  to: string
  x1: number
  y1: number
  x2: number
  y2: number
}

const graphAgents = computed(() =>
  props.agents
    .filter(a => AGENT_GRAPH_POSITIONS[a.info.agentId])
    .map(a => ({
      ...a,
      pos: AGENT_GRAPH_POSITIONS[a.info.agentId],
    }))
)

const edges = computed(() => {
  const result: Edge[] = []
  for (const agent of props.agents) {
    const toPos = AGENT_GRAPH_POSITIONS[agent.info.agentId]
    if (!toPos) continue
    for (const dep of agent.info.dependencies || []) {
      const fromPos = AGENT_GRAPH_POSITIONS[dep]
      if (!fromPos) continue
      result.push({
        from: dep,
        to: agent.info.agentId,
        x1: fromPos.x,
        y1: fromPos.y + 18,
        x2: toPos.x,
        y2: toPos.y - 18,
      })
    }
  }
  return result
})

function stageBandY(stageNum: number): number {
  const map: Record<number, number> = { 1: 36, 2: 118, 3: 200, 4: 282, 5: 364 }
  return map[stageNum] ?? 36
}

function stageBandClass(stageNum: number): string {
  if (!props.activeStage) return ''
  if (props.activeStage === stageNum) return 'active'
  if (props.activeStage > stageNum) return 'done'
  return ''
}

function statusClass(status: string): string {
  return status.toLowerCase()
}

function shortName(agentId: string): string {
  const full = getAgentDisplayName(agentId)
  return full.length > 8 ? full.slice(0, 7) + '…' : full
}

function isEdgeActive(edge: Edge): boolean {
  const to = props.agents.find(a => a.info.agentId === edge.to)
  return to?.status === 'RUNNING' || to?.status === 'COMPLETED'
}
</script>

<style scoped>
.agent-graph {
  background: var(--cream);
  border: 1px solid var(--cream-200);
  border-radius: 8px;
  padding: 12px 14px 8px;
  margin-bottom: 16px;
}

.graph-title {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-3);
  margin-bottom: 8px;
}

.graph-svg {
  width: 100%;
  height: auto;
  max-height: 320px;
  display: block;
}

.edge-line {
  stroke: var(--cream-300);
  stroke-width: 1.2;
  opacity: 0.7;
}

.edge-line.active {
  stroke: var(--forest);
  opacity: 1;
}

.arrow-head {
  fill: var(--cream-300);
}

.edge-line.active + defs .arrow-head,
.edge-line.active ~ .arrow-head {
  fill: var(--forest);
}

.stage-band {
  fill: transparent;
  stroke: transparent;
}

.stage-band.active {
  fill: rgba(var(--forest-rgb), 0.06);
  stroke: rgba(var(--forest-rgb), 0.2);
}

.stage-band.done {
  fill: rgba(var(--forest-rgb), 0.03);
}

.stage-band-label {
  font-size: 9px;
  fill: var(--text-3);
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.node-rect {
  fill: var(--white);
  stroke: var(--cream-300);
  stroke-width: 1.2;
  transition: stroke 0.25s, fill 0.25s;
}

.node-group.running .node-rect {
  stroke: var(--forest);
  fill: rgba(var(--forest-rgb), 0.08);
}

.node-group.completed .node-rect {
  stroke: var(--forest-400);
  fill: rgba(var(--forest-rgb), 0.12);
}

.node-group.failed .node-rect,
.node-group.fallback .node-rect {
  stroke: var(--earth);
  fill: rgba(139, 90, 60, 0.08);
}

.node-group.debating .node-rect,
.node-group.voting .node-rect {
  stroke: var(--gold);
  fill: rgba(var(--accent-rgb), 0.1);
}

.node-icon {
  font-size: 11px;
  fill: var(--text-2);
}

.node-label {
  font-size: 9px;
  fill: var(--text);
  font-weight: 600;
}
</style>
