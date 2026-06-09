package com.tourism.rag.dto.multiagent;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Static metadata about an agent, returned by the status/info endpoint.
 * Used by the frontend to render agent cards before orchestration starts.
 */
@Data
@Builder
public class AgentInfo {

    private String agentId;
    private String displayName;
    private String roleDescription;
    private String style;
    private String icon;
    private List<String> dependencies;
    private List<String> toolNames;
}
