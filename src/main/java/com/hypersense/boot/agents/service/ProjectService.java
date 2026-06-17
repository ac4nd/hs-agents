package com.hypersense.boot.agents.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hypersense.boot.agents.model.entity.Project;
import com.hypersense.boot.agents.model.form.ProjectForm;
import com.hypersense.boot.agents.model.query.ProjectQuery;
import com.hypersense.boot.agents.model.vo.ProjectVO;

/**
 * 项目业务接口
 *
 * @author Claude
 * @since 2026/6/16
 */
public interface ProjectService extends IService<Project> {

    /**
     * 项目分页列表
     *
     * @param queryParams 查询参数
     * @return 项目分页列表
     */
    IPage<ProjectVO> getProjectPage(ProjectQuery queryParams);

    /**
     * 获取项目表单数据
     *
     * @param projectId 项目ID
     * @return 项目表单数据
     */
    ProjectForm getProjectForm(Long projectId);

    /**
     * 新增项目
     *
     * @param projectForm 项目表单对象
     * @return 是否新增成功
     */
    boolean saveProject(ProjectForm projectForm);

    /**
     * 修改项目
     *
     * @param projectId   项目ID
     * @param projectForm 项目表单对象
     * @return 是否修改成功
     */
    boolean updateProject(Long projectId, ProjectForm projectForm);

    /**
     * 删除项目
     *
     * @param ids 项目ID，多个以英文逗号(,)分割
     */
    void deleteProjects(String ids);

}
