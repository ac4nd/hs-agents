package com.hypersense.boot.system.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.common.model.Option;
import com.hypersense.boot.system.model.entity.User;
import com.hypersense.boot.system.model.dto.CurrentUserDTO;
import com.hypersense.boot.system.model.form.UserForm;
import com.hypersense.boot.system.model.dto.UserImportDTO;
import com.hypersense.boot.system.model.form.UserProfileForm;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.List;

/**
 * 用户对象转换器
 *
 * @author Ray.Hao
 * @since 2022/6/8
 */
@Mapper(componentModel = "spring")
public interface UserConverter {

    UserForm toForm(User entity);

    @InheritInverseConfiguration(name = "toForm")
    User toEntity(UserForm entity);

    @Mappings({
            @Mapping(target = "userId", source = "id")
    })
    CurrentUserDTO toCurrentUserDto(User entity);

    User toEntity(UserImportDTO vo);

    User toEntity(UserProfileForm formData);

    @Mappings({
            @Mapping(target = "label", source = "nickname"),
            @Mapping(target = "value", source = "id")
    })
    Option<String> toOption(User entity);

    List<Option<String>> toOptions(List<User> list);
}
