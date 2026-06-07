package com.hypersense.boot.codegen.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.codegen.mapper.GenTableColumnMapper;
import com.hypersense.boot.codegen.model.entity.GenTableColumn;
import com.hypersense.boot.codegen.service.GenTableColumnService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 代码生成字段配置服务实现类
 *
 * @author Ray.Hao
 * @since 2.10.0
 */
@Service
@RequiredArgsConstructor
public class GenTableColumnServiceImpl extends ServiceImpl<GenTableColumnMapper, GenTableColumn> implements GenTableColumnService {


}
