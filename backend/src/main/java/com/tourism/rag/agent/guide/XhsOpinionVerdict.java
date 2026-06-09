package com.tourism.rag.agent.guide;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 小红书笔记对单个候选（景点/餐厅）的口碑结论 */
@Data
@Builder
public class XhsOpinionVerdict {

    private String name;
    private boolean recommend;
    /** 0-1，越高越值得推荐 */
    private double confidence;

    @Builder.Default
    private List<String> positives = new ArrayList<>();

    @Builder.Default
    private List<String> negatives = new ArrayList<>();

    /** 给用户看的综合推荐理由（含正负面提示） */
    private String reason;
}
