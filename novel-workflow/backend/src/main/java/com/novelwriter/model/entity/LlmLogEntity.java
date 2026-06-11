package com.novelwriter.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("nw_llm_log")
public class LlmLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String projectId;

    private String stage;

    private Integer chapter;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Integer cacheHitTokens;

    private Integer cacheMissTokens;

    private String rawResponse;

    private LocalDateTime createdAt;
}
