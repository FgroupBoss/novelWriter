package com.novelwriter.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("nw_project")
public class ProjectEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String title;

    private String currentStage;

    private Integer currentChapter;

    /** JSON 数组字符串 */
    private String completedStages;

    /** 完整 workflow_state.json */
    private String stateJson;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
