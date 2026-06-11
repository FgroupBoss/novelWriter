package com.novelwriter.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("nw_prompt_record")
public class PromptRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String projectId;

    private String jobId;

    private String stageId;

    private Integer chapter;

    /** template | system | user | task */
    private String promptType;

    private String relPath;

    private String content;

    private Integer charCount;

    private LocalDateTime createdAt;
}
