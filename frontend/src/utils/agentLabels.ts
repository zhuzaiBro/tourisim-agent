/** 与后端 multi-agent 专家 id 对齐 */
export const AGENT_LABELS: Record<string, string> = {
  'weather-analysis': '气象分析专家',
  'poi-discovery': '景点发现专家',
  'route-optimization': '路线优化专家',
  'food-recommendation': '美食发现专家',
  'accommodation-recommendation': '住宿安排专家',
  'day-scheduling': '时间规划专家',
  'budget-planning': '预算规划专家',
  'narrative-generation': '旅行叙事作家',
  'safety-validation': '质量审核专家',
}

/** 后端 ExecutionPlan 五阶段拓扑（用于子图分层） */
export const EXECUTION_STAGES: { number: number; name: string; agentIds: string[] }[] = [
  { number: 1, name: '气象 + 景点', agentIds: ['weather-analysis', 'poi-discovery'] },
  { number: 2, name: '路线 + 美食 + 住宿', agentIds: ['route-optimization', 'food-recommendation', 'accommodation-recommendation'] },
  { number: 3, name: '日程编排', agentIds: ['day-scheduling'] },
  { number: 4, name: '预算 + 叙事', agentIds: ['budget-planning', 'narrative-generation'] },
  { number: 5, name: '质量审核', agentIds: ['safety-validation'] },
]

/** 子图节点固定坐标（与 EXECUTION_STAGES 分层一致） */
export const AGENT_GRAPH_POSITIONS: Record<string, { x: number; y: number }> = {
  'weather-analysis': { x: 90, y: 36 },
  'poi-discovery': { x: 270, y: 36 },
  'route-optimization': { x: 55, y: 118 },
  'food-recommendation': { x: 180, y: 118 },
  'accommodation-recommendation': { x: 305, y: 118 },
  'day-scheduling': { x: 180, y: 200 },
  'budget-planning': { x: 90, y: 282 },
  'narrative-generation': { x: 270, y: 282 },
  'safety-validation': { x: 180, y: 364 },
}

export function getAgentDisplayName(agentId: string, fallback?: string): string {
  return AGENT_LABELS[agentId] ?? fallback ?? agentId
}
