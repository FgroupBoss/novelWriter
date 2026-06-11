package com.novelwriter.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("nw_job")
public class JobEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String projectId;

    private String jobType;

    private String label;

    private String status;

    private String logs;

    private String resultJson;

    private String errorMsg;

    private String taskPayloadJson;

    private Integer progressStep;

    private Integer progressTotal;

    private String progressLabel;

    private LocalDateTime pausedAt;

    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;
}
