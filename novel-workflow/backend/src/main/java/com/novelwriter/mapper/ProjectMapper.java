package com.novelwriter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novelwriter.model.entity.ProjectEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapper<ProjectEntity> {
}
