package com.novelwriter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novelwriter.model.entity.JobEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JobMapper extends BaseMapper<JobEntity> {
}
