package com.hypersense.boot.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.system.model.entity.User;
import com.hypersense.boot.system.model.query.UserQuery;
import com.hypersense.boot.system.model.form.UserForm;
import com.hypersense.boot.common.annotation.DataPermission;
import com.hypersense.boot.framework.security.model.UserAuthInfo;
import com.hypersense.boot.system.model.dto.UserExportDTO;
import com.hypersense.boot.system.model.vo.UserPageVO;
import com.hypersense.boot.system.model.vo.UserProfileVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户持久层接口
 *
 * @author Ray.Hao
 * @since 2022/1/14
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 获取用户分页列表
     *
     * @param page        分页参数
     * @param queryParams 查询参数
     * @return 用户分页列表
     */
    @DataPermission(deptAlias = "u", userAlias = "u")
    Page<UserPageVO> getUserPage(Page<UserPageVO> page, @Param("queryParams") UserQuery queryParams);

    /**
     * 获取用户表单详情
     *
     * @param userId 用户ID
     * @return 用户表单详情
     */
    UserForm getUserFormData(Long userId);

    /**
     * 根据用户名获取认证信息
     *
     * @param username 用户名
     * @return 认证信息
     */
    UserAuthInfo getAuthInfoByUsername(String username);

    /**
     * 根据微信openid获取用户认证信息
     *
     * @param openid 微信openid
     * @return 认证信息
     */
    UserAuthInfo getAuthInfoByOpenId(String openid);

    /**
     * 根据手机号获取用户认证信息
     *
     * @param mobile 手机号
     * @return 认证信息
     */
    UserAuthInfo getAuthInfoByMobile(String mobile);

    /**
     * 获取导出用户列表
     *
     * @param queryParams 查询参数
     * @return 导出用户列表
     */
    @DataPermission(deptAlias = "u", userAlias = "u")
    List<UserExportDTO> listExportUsers(UserQuery queryParams);

    /**
     * 获取用户个人中心信息
     *
     * @param userId 用户ID
     * @return 用户个人中心信息
     */
    UserProfileVO getUserProfile(Long userId);

}
