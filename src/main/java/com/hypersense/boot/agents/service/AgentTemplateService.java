package com.hypersense.boot.agents.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hypersense.boot.agents.model.entity.AgentTemplate;
import com.hypersense.boot.agents.model.form.AgentTemplateForm;
import com.hypersense.boot.agents.model.query.AgentTemplateQuery;
import com.hypersense.boot.agents.model.vo.AgentTemplateVO;

/**
 * Agent 模板业务接口
 *
 * @author Claude
 * @since 2026/6/16
 */
public interface AgentTemplateService extends IService<AgentTemplate> {

    /**
     * Agent 模板分页列表
     *
     * @param queryParams 查询参数
     * @return Agent 模板分页列表
     */
    IPage<AgentTemplateVO> getAgentTemplatePage(AgentTemplateQuery queryParams);

    /**
     * 获取 Agent 模板表单数据
     *
     * @param id 模板ID
     * @return Agent 模板表单数据
     */
    AgentTemplateForm getAgentTemplateForm(Long id);

    /**
     * 新增 Agent 模板
     *
     * @param agentTemplateForm Agent 模板表单对象
     * @return 是否新增成功
     */
    boolean saveAgentTemplate(AgentTemplateForm agentTemplateForm);

    /**
     * 修改 Agent 模板
     *
     * @param id                模板ID
     * @param agentTemplateForm Agent 模板表单对象
     * @return 是否修改成功
     */
    boolean updateAgentTemplate(Long id, AgentTemplateForm agentTemplateForm);

    /**
     * 删除 Agent 模板
     *
     * @param ids 模板ID，多个以英文逗号(,)分割
     */
    void deleteAgentTemplates(String ids);

}
