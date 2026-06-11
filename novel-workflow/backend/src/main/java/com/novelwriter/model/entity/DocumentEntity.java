package com.novelwriter.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("nw_document")
public class DocumentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String projectId;

    private String relPath;

    private String content;

    private String artifactType;

    private String category;

    private String stageId;

    private Integer chapter;

    private String sourceType;

    private String jobId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
