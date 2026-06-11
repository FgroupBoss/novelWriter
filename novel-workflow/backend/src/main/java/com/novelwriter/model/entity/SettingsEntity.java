package com.novelwriter.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("nw_settings")
public class SettingsEntity {

    @TableId
    private Integer id;

    private String apiKey;

    private String baseUrl;

    private String model;

    private LocalDateTime updatedAt;
}
