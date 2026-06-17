package com.hypersense.boot.agents.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.agents.converter.ProjectConverter;
import com.hypersense.boot.agents.mapper.ProjectMapper;
import com.hypersense.boot.agents.model.entity.Project;
import com.hypersense.boot.agents.model.form.ProjectForm;
import com.hypersense.boot.agents.model.query.ProjectQuery;
import com.hypersense.boot.agents.model.vo.ProjectVO;
import com.hypersense.boot.agents.service.ProjectService;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目业务实现类
 *
 * @author Claude
 * @since 2026/6/16
 */
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {

    private final ProjectConverter projectConverter;
    private final UserService userService;

    @Override
    @DataPermission(userAlias = "t")
    public IPage<ProjectVO> getProjectPage(ProjectQuery queryParams) {
        int pageNum = queryParams.getPageNum();
        int pageSize = queryParams.getPageSize();
        String keywords = queryParams.getKeywords();
        String sandboxType = queryParams.getSandboxType();
        Integer status = queryParams.getStatus();

        Page<Project> projectPage = this.page(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Project>()
                        .like(StrUtil.isNotBlank(keywords), Project::getName, keywords)
                        .eq(StrUtil.isNotBlank(sandboxType), Project::getSandboxType, sandboxType)
                        .eq(status != null, Project::getStatus, status)
                        .orderByDesc(Project::getUpdateTime)
        );

        IPage<ProjectVO> voPage = projectConverter.toPageVO(projectPage);

        // 填充用户名
        Set<Long> ownerUserIds = voPage.getRecords().stream()
                .map(ProjectVO::getOwnerUserId)
                .collect(Collectors.toSet());
        if (!ownerUserIds.isEmpty()) {
            Map<Long, String> userIdToNameMap = userService.listByIds(ownerUserIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getNickname));
            voPage.getRecords().forEach(vo -> vo.setOwnerUserName(userIdToNameMap.get(vo.getOwnerUserId())));
        }

        return voPage;
    }

    @Override
    public ProjectForm getProjectForm(Long projectId) {
        Project entity = this.getById(projectId);
        return projectConverter.toForm(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @RepeatSubmit
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.INSERT)
    public boolean saveProject(ProjectForm projectForm) {
        Long currentUserId = SecurityUtils.getUserId();
        Project project = projectConverter.toEntity(projectForm);
        project.setOwnerUserId(currentUserId);
        return this.save(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @RepeatSubmit
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.UPDATE)
    public boolean updateProject(Long projectId, ProjectForm projectForm) {
        Project oldProject = this.getById(projectId);
        Assert.isTrue(oldProject != null, "项目不存在");

        projectForm.setId(projectId);
        Project project = projectConverter.toEntity(projectForm);
        return this.updateById(project);
    }

    @Override
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.DELETE)
    public void deleteProjects(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的项目ID不能为空");
        Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .forEach(this::removeById);
    }

}
