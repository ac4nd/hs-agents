package com.hypersense.boot.agents.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hypersense.boot.agents.model.entity.DesignSystem;
import com.hypersense.boot.agents.model.form.DesignSystemForm;
import com.hypersense.boot.agents.model.query.DesignSystemQuery;
import com.hypersense.boot.agents.model.vo.DesignSystemVO;

import java.util.List;

/**
 * 设计系统业务接口
 *
 * @author Claude
 * @since 2026/6/16
 */
public interface DesignSystemService extends IService<DesignSystem> {

    /**
     * 设计系统分页列表
     *
     * @param queryParams 查询参数
     * @return 设计系统分页列表
     */
    IPage<DesignSystemVO> getDesignSystemPage(DesignSystemQuery queryParams);

    /**
     * 获取设计系统模板列表（用于下拉选择）
     *
     * @return 设计系统模板列表
     */
    List<DesignSystemVO> listTemplates();

    /**
     * 获取设计系统表单数据
     *
     * @param id 设计系统ID
     * @return 设计系统表单数据
     */
    DesignSystemForm getDesignSystemForm(Long id);

    /**
     * 新增设计系统
     *
     * @param designSystemForm 设计系统表单对象
     * @return 是否新增成功
     */
    boolean saveDesignSystem(DesignSystemForm designSystemForm);

    /**
     * 修改设计系统
     *
     * @param id               设计系统ID
     * @param designSystemForm 设计系统表单对象
     * @return 是否修改成功
     */
    boolean updateDesignSystem(Long id, DesignSystemForm designSystemForm);

    /**
     * 删除设计系统
     *
     * @param ids 设计系统ID，多个以英文逗号(,)分割
     */
    void deleteDesignSystems(String ids);

    /**
     * 发布设计系统
     *
     * @param id 设计系统ID
     * @return 是否发布成功
     */
    boolean publishDesignSystem(Long id);

}
