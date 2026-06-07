package com.hypersense.boot.system.converter;

import com.hypersense.boot.system.model.entity.Dept;
import com.hypersense.boot.system.model.vo.DeptVO;
import com.hypersense.boot.system.model.form.DeptForm;
import org.mapstruct.Mapper;

/**
 * 部门对象转换器
 *
 * @author haoxr
 * @since 2022/7/29
 */
@Mapper(componentModel = "spring")
public interface DeptConverter {

    DeptForm toForm(Dept entity);
    
    DeptVO toVo(Dept entity);

    Dept toEntity(DeptForm deptForm);

}