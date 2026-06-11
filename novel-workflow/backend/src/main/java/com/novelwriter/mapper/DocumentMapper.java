package com.novelwriter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novelwriter.model.entity.DocumentEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentMapper extends BaseMapper<DocumentEntity> {
}
