package com.hypersense.boot.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hypersense.boot.system.model.entity.DesignSystemConfig;
import com.hypersense.boot.system.model.form.DesignSystemConfigForm;
import com.hypersense.boot.system.model.query.DesignSystemConfigQuery;
import com.hypersense.boot.system.model.vo.DesignSystemConfigPageVO;
import com.hypersense.boot.system.model.vo.DesignSystemConfigTemplateVO;

import java.util.List;

/**
 * 设计体系配置服务接口
 *
 * <p>同时承担管理端与 agents 业务 Controller 的能力，对外提供统一的领域服务。</p>
 *
 * @author Claude
 * @since 2026/6/24
 */
public interface DesignSystemConfigService extends IService<DesignSystemConfig> {

    /**
     * 分页查询设计体系配置
     */
    IPage<DesignSystemConfigPageVO> getDesignSystemPage(DesignSystemConfigQuery query);

    /**
     * 根据 ID 获取详情（含 label 与所有者用户名）
     */
    DesignSystemConfigPageVO getDetailById(Long id);

    /**
     * 根据 ID 获取表单数据（用于编辑回显）
     */
    DesignSystemConfigForm getFormById(Long id);

    /**
     * 新建设计体系配置
     */
    boolean saveDesignSystem(DesignSystemConfigForm form);

    /**
     * 更新设计体系配置
     */
    boolean updateDesignSystem(Long id, DesignSystemConfigForm form);

    /**
     * 批量删除设计体系配置（逻辑删除）
     */
    boolean deleteDesignSystems(String ids);

    /**
     * 发布设计体系配置
     */
    boolean publishDesignSystem(Long id);

    /**
     * 获取官方模板列表
     */
    List<DesignSystemConfigTemplateVO> getTemplates();

    /**
     * 获取官方模板详情（按模板主键查询 sys_design_system_config_template）
     *
     * <p>与 {@link #getDetailById(Long)} 区别：后者只查 sys_design_system_config，
     * 而官方模板存储在独立的模板表，必须走此接口。</p>
     *
     * @param id 模板主键
     * @return 模板视图对象（brandSpec 已迁移到 v2.0）
     */
    DesignSystemConfigTemplateVO getTemplateDetailById(Long id);

    /**
     * 获取当前登录用户的个人设计体系列表（仅已发布）
     */
    List<DesignSystemConfigPageVO> listForCurrentUser();
}
