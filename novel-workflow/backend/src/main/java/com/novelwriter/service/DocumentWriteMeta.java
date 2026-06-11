package com.novelwriter.service;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentWriteMeta {
    private String stageId;
    private Integer chapter;
    private String sourceType;
    private String jobId;
}
