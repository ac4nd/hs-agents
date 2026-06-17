package com.hypersense.boot.agents.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hypersense.boot.agents.model.entity.DesignSystem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设计系统持久层接口
 *
 * @author Claude
 * @since 2026/6/16
 */
@Mapper
public interface DesignSystemMapper extends BaseMapper<DesignSystem> {

}
