package com.hypersense.boot.agents.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.agents.converter.DesignSystemConverter;
import com.hypersense.boot.agents.mapper.DesignSystemMapper;
import com.hypersense.boot.agents.model.entity.DesignSystem;
import com.hypersense.boot.agents.model.form.DesignSystemForm;
import com.hypersense.boot.agents.model.query.DesignSystemQuery;
import com.hypersense.boot.agents.model.vo.DesignSystemVO;
import com.hypersense.boot.agents.service.DesignSystemService;
import com.hypersense.boot.common.annotation.DataPermission;
import com.hypersense.boot.common.enums.ActionTypeEnum;
import com.hypersense.boot.common.enums.LogModuleEnum;
import com.hypersense.boot.common.annotation.Log;
import com.hypersense.boot.common.annotation.RepeatSubmit;
import com.hypersense.boot.framework.security.util.SecurityUtils;
import com.hypersense.boot.system.model.entity.User;
import com.hypersense.boot.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 设计系统业务实现类
 *
 * @author Claude
 * @since 2026/6/16
 */
@Service
@RequiredArgsConstructor
public class DesignSystemServiceImpl extends ServiceImpl<DesignSystemMapper, DesignSystem> implements DesignSystemService {

    private final DesignSystemConverter designSystemConverter;
    private final UserService userService;

    @Override
    @DataPermission(userAlias = "t")
    public IPage<DesignSystemVO> getDesignSystemPage(DesignSystemQuery queryParams) {
        int pageNum = queryParams.getPageNum();
        int pageSize = queryParams.getPageSize();
        String keywords = queryParams.getKeywords();
        String category = queryParams.getCategory();
        String type = queryParams.getType();
        Integer publishStatus = queryParams.getPublishStatus();

        Page<DesignSystem> page = this.page(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<DesignSystem>()
                        .like(StrUtil.isNotBlank(keywords), DesignSystem::getName, keywords)
                        .eq(StrUtil.isNotBlank(category), DesignSystem::getCategory, category)
                        .eq(StrUtil.isNotBlank(type), DesignSystem::getType, type)
                        .eq(publishStatus != null, DesignSystem::getPublishStatus, publishStatus)
                        .orderByDesc(DesignSystem::getUpdateTime)
        );

        IPage<DesignSystemVO> voPage = designSystemConverter.toPageVO(page);

        // 填充用户名
        Set<Long> ownerUserIds = voPage.getRecords().stream()
                .map(DesignSystemVO::getOwnerUserId)
                .collect(Collectors.toSet());
        if (!ownerUserIds.isEmpty()) {
            Map<Long, String> userIdToNameMap = userService.listByIds(ownerUserIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getNickname));
            voPage.getRecords().forEach(vo -> vo.setOwnerUserName(userIdToNameMap.get(vo.getOwnerUserId())));
        }

        return voPage;
    }

    @Override
    public List<DesignSystemVO> listTemplates() {
        List<DesignSystem> list = this.list(new LambdaQueryWrapper<DesignSystem>()
                .eq(DesignSystem::getPublishStatus, 1)
                .eq(DesignSystem::getCategory, "official")
                .select(DesignSystem::getId, DesignSystem::getName, DesignSystem::getType)
                .orderByDesc(DesignSystem::getUpdateTime)
        );

        List<DesignSystemVO> voList = designSystemConverter.toPageVO(new Page<>(1, list.size())).getRecords();

        // 填充用户名
        Set<Long> ownerUserIds = voList.stream()
                .map(DesignSystemVO::getOwnerUserId)
                .collect(Collectors.toSet());
        if (!ownerUserIds.isEmpty()) {
            Map<Long, String> userIdToNameMap = userService.listByIds(ownerUserIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getNickname));
            voList.forEach(vo -> vo.setOwnerUserName(userIdToNameMap.get(vo.getOwnerUserId())));
        }

        return voList;
    }

    @Override
    public DesignSystemForm getDesignSystemForm(Long id) {
        DesignSystem entity = this.getById(id);
        return designSystemConverter.toForm(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @RepeatSubmit
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.INSERT)
    public boolean saveDesignSystem(DesignSystemForm designSystemForm) {
        Long currentUserId = SecurityUtils.getUserId();
        DesignSystem designSystem = designSystemConverter.toEntity(designSystemForm);
        designSystem.setOwnerUserId(currentUserId);
        return this.save(designSystem);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @RepeatSubmit
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.UPDATE)
    public boolean updateDesignSystem(Long id, DesignSystemForm designSystemForm) {
        DesignSystem oldEntity = this.getById(id);
        Assert.isTrue(oldEntity != null, "设计系统不存在");

        designSystemForm.setId(id);
        DesignSystem designSystem = designSystemConverter.toEntity(designSystemForm);
        return this.updateById(designSystem);
    }

    @Override
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.DELETE)
    public void deleteDesignSystems(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的设计系统ID不能为空");
        Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .forEach(this::removeById);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.UPDATE)
    public boolean publishDesignSystem(Long id) {
        DesignSystem designSystem = this.getById(id);
        Assert.isTrue(designSystem != null, "设计系统不存在");

        designSystem.setPublishStatus(1);
        return this.updateById(designSystem);
    }

}
