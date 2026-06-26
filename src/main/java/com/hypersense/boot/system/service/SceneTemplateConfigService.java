package com.hypersense.boot.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hypersense.boot.system.model.entity.SceneTemplateConfig;
import com.hypersense.boot.system.model.form.SceneTemplateConfigForm;
import com.hypersense.boot.system.model.query.SceneTemplateConfigQuery;
import com.hypersense.boot.system.model.vo.SceneTemplateConfigVO;

import java.util.List;

/**
 * 场景模板配置业务接口
 *
 * @author Claude
 * @since 2026/6/24
 */
public interface SceneTemplateConfigService extends IService<SceneTemplateConfig> {

    /**
     * 场景模板配置分页列表
     *
     * @param query 查询参数
     * @return 场景模板配置分页列表
     */
    IPage<SceneTemplateConfigVO> getSceneTemplateConfigPage(SceneTemplateConfigQuery query);

    /**
     * 获取场景模板配置表单数据（回显）
     *
     * @param id 模板ID
     * @return 场景模板配置表单数据
     */
    SceneTemplateConfigForm getSceneTemplateConfigForm(Long id);

    /**
     * 获取场景模板配置详情
     *
     * @param id 模板ID
     * @return 场景模板配置视图对象
     */
    SceneTemplateConfigVO getSceneTemplateConfigDetail(Long id);

    /**
     * 新增场景模板配置
     *
     * @param form 场景模板配置表单对象
     * @return 是否新增成功
     */
    boolean saveSceneTemplateConfig(SceneTemplateConfigForm form);

    /**
     * 修改场景模板配置
     *
     * @param id  模板ID
     * @param form 场景模板配置表单对象
     * @return 是否修改成功
     */
    boolean updateSceneTemplateConfig(Long id, SceneTemplateConfigForm form);

    /**
     * 删除场景模板配置（批量）
     *
     * @param ids 模板ID，多个以英文逗号(,)分割
     */
    void deleteSceneTemplateConfigs(String ids);

    /**
     * 从本地模板目录批量导入官方场景模板
     *
     * <p>遍历 dir 下每个子目录（slug = 目录名），优先读取 template.json，
     * 否则解析 SKILL.md 的 YAML frontmatter；将 example.html 上传到 MinIO，
     * 并按 slug upsert 到 sys_scene_template_config（官方模板 tenant_id=0）。</p>
     *
     * @param dir 本地模板根目录
     * @return 成功导入条数
     */
    int importFromDir(String dir);

    /**
     * 发布场景模板（对所有用户可见）
     *
     * @param id 模板ID
     * @return 是否成功
     */
    boolean publish(Long id);

    /**
     * 取消发布场景模板（仅创建人/管理员可见）
     *
     * @param id 模板ID
     * @return 是否成功
     */
    boolean unpublish(Long id);

    /**
     * 获取所有已发布模板的去重 UI 分类（用于前端分类筛选 chips）。
     *
     * <p>跨租户查询（官方模板 tenant_id=0），仅返回 is_published=1 且 ui_category 非空的分类，
     * 按 ui_category 升序排序。</p>
     *
     * @return 去重后的 UI 分类字符串列表
     */
    List<String> getDistinctUiCategories();

}
