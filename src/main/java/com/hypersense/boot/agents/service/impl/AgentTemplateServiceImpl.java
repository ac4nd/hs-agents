package com.hypersense.boot.agents.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.agents.converter.AgentTemplateConverter;
import com.hypersense.boot.agents.mapper.AgentTemplateMapper;
import com.hypersense.boot.agents.model.entity.AgentTemplate;
import com.hypersense.boot.agents.model.form.AgentTemplateForm;
import com.hypersense.boot.agents.model.query.AgentTemplateQuery;
import com.hypersense.boot.agents.model.vo.AgentTemplateVO;
import com.hypersense.boot.agents.service.AgentTemplateService;
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
 * Agent 模板业务实现类
 *
 * @author Claude
 * @since 2026/6/16
 */
@Service
@RequiredArgsConstructor
public class AgentTemplateServiceImpl extends ServiceImpl<AgentTemplateMapper, AgentTemplate> implements AgentTemplateService {

    private final AgentTemplateConverter agentTemplateConverter;
    private final UserService userService;

    @Override
    @DataPermission(userAlias = "t")
    public IPage<AgentTemplateVO> getAgentTemplatePage(AgentTemplateQuery queryParams) {
        int pageNum = queryParams.getPageNum();
        int pageSize = queryParams.getPageSize();
        String keywords = queryParams.getKeywords();
        Boolean hitlEnabled = queryParams.getHitlEnabled();

        Page<AgentTemplate> page = this.page(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AgentTemplate>()
                        .like(StrUtil.isNotBlank(keywords), AgentTemplate::getName, keywords)
                        .eq(hitlEnabled != null, AgentTemplate::getHitlEnabled, hitlEnabled)
                        .orderByDesc(AgentTemplate::getUpdateTime)
        );

        IPage<AgentTemplateVO> voPage = agentTemplateConverter.toPageVO(page);

        // 填充用户名
        Set<Long> ownerUserIds = voPage.getRecords().stream()
                .map(AgentTemplateVO::getOwnerUserId)
                .collect(Collectors.toSet());
        if (!ownerUserIds.isEmpty()) {
            Map<Long, String> userIdToNameMap = userService.listByIds(ownerUserIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getNickname));
            voPage.getRecords().forEach(vo -> vo.setOwnerUserName(userIdToNameMap.get(vo.getOwnerUserId())));
        }

        return voPage;
    }

    @Override
    public AgentTemplateForm getAgentTemplateForm(Long id) {
        AgentTemplate entity = this.getById(id);
        return agentTemplateConverter.toForm(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @RepeatSubmit
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.INSERT)
    public boolean saveAgentTemplate(AgentTemplateForm agentTemplateForm) {
        Long currentUserId = SecurityUtils.getUserId();
        AgentTemplate agentTemplate = agentTemplateConverter.toEntity(agentTemplateForm);
        agentTemplate.setOwnerUserId(currentUserId);
        return this.save(agentTemplate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @RepeatSubmit
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.UPDATE)
    public boolean updateAgentTemplate(Long id, AgentTemplateForm agentTemplateForm) {
        AgentTemplate oldEntity = this.getById(id);
        Assert.isTrue(oldEntity != null, "Agent 模板不存在");

        agentTemplateForm.setId(id);
        AgentTemplate agentTemplate = agentTemplateConverter.toEntity(agentTemplateForm);
        return this.updateById(agentTemplate);
    }

    @Override
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.DELETE)
    public void deleteAgentTemplates(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的模板ID不能为空");
        Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .forEach(this::removeById);
    }

}
