package com.tourism.rag.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IngestJobStatus {
    private String jobId;
    private String cityCode;
    private String status;
    private String message;
    private Integer chunks;
    private Long finishedAtMs;
}
