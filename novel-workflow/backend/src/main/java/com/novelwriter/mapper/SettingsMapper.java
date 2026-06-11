package com.novelwriter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novelwriter.model.entity.SettingsEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SettingsMapper extends BaseMapper<SettingsEntity> {
}
