package com.hypersense.boot.system.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.system.model.entity.Role;
import com.hypersense.boot.system.model.vo.RolePageVO;
import com.hypersense.boot.common.model.Option;
import com.hypersense.boot.system.model.form.RoleForm;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.List;

/**
 * 角色对象转换器
 *
 * @author haoxr
 * @since 2022/5/29
 */
@Mapper(componentModel = "spring")
public interface RoleConverter {

    @Mapping(target = "dataScope", source = "dataScope")
    @Mapping(target = "dataScopeLabel", expression = "java(com.hypersense.boot.common.enums.DataScopeEnum.getByValue(role.getDataScope()) == null ? null : com.hypersense.boot.common.enums.DataScopeEnum.getByValue(role.getDataScope()).getLabel())")
    RolePageVO toPageVo(Role role);

    Page<RolePageVO> toPageVo(Page<Role> page);

    @Mappings({
            @Mapping(target = "value", source = "id"),
            @Mapping(target = "label", source = "name")
    })
    Option<Long> toOption(Role role);

    List<Option<Long>> toOptions(List<Role> roles);

    Role toEntity(RoleForm roleForm);

    RoleForm toForm(Role entity);
}