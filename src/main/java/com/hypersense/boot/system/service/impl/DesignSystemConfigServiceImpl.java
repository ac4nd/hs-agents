package com.hypersense.boot.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.common.exception.BusinessException;
import com.hypersense.boot.framework.security.util.SecurityUtils;
import com.hypersense.boot.system.constant.BrandSpecDefaults;
import com.hypersense.boot.system.converter.DesignSystemConfigConverter;
import com.hypersense.boot.system.mapper.DesignSystemConfigMapper;
import com.hypersense.boot.system.mapper.DesignSystemConfigTemplateMapper;
import com.hypersense.boot.system.model.entity.DesignSystemConfig;
import com.hypersense.boot.system.model.entity.DesignSystemConfigTemplate;
import com.hypersense.boot.system.model.entity.User;
import com.hypersense.boot.system.model.form.DesignSystemConfigForm;
import com.hypersense.boot.system.model.query.DesignSystemConfigQuery;
import com.hypersense.boot.system.model.vo.DesignSystemConfigPageVO;
import com.hypersense.boot.system.model.vo.DesignSystemConfigTemplateVO;
import com.hypersense.boot.system.service.DesignSystemConfigService;
import com.hypersense.boot.system.service.UserService;
import com.hypersense.boot.system.util.BrandSpecMigrator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 设计体系配置服务实现类
 *
 * @author Claude
 * @since 2026/6/24
 */
@Service
@RequiredArgsConstructor
public class DesignSystemConfigServiceImpl extends ServiceImpl<DesignSystemConfigMapper, DesignSystemConfig> implements DesignSystemConfigService {

    private final DesignSystemConfigConverter designSystemConfigConverter;
    private final DesignSystemConfigTemplateMapper designSystemConfigTemplateMapper;
    private final UserService userService;

    /**
     * 用于 JSON 字段格式校验的 ObjectMapper（项目未注册全局 Bean，本地持有复用）。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 默认代码规范 JSON（design-tokens）
     */
    private static final String DEFAULT_CODE_SPEC = "{\"tokens\":{\"color.primary\":\"#1677ff\",\"radius\":\"6px\",\"font.family\":\"Inter, sans-serif\",\"spacing.unit\":\"4px\"}}";

    @Override
    public IPage<DesignSystemConfigPageVO> getDesignSystemPage(DesignSystemConfigQuery query) {
        LambdaQueryWrapper<DesignSystemConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(query.getKeywords()), DesignSystemConfig::getName, query.getKeywords())
                .eq(StrUtil.isNotBlank(query.getCategory()), DesignSystemConfig::getCategory, query.getCategory())
                .eq(StrUtil.isNotBlank(query.getType()), DesignSystemConfig::getType, query.getType())
                .eq(query.getPublishStatus() != null, DesignSystemConfig::getPublishStatus, query.getPublishStatus())
                .orderByDesc(DesignSystemConfig::getUpdateTime);

        Page<DesignSystemConfig> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<DesignSystemConfig> entityPage = this.baseMapper.selectPage(page, wrapper);

        // 转换为 VO
        IPage<DesignSystemConfigPageVO> voPage = entityPage.convert(designSystemConfigConverter::toPageVO);
        List<DesignSystemConfigPageVO> records = voPage.getRecords();
        if (CollectionUtil.isEmpty(records)) {
            return voPage;
        }

        // brandSpec 兜底迁移到 v2.0
        records.forEach(vo -> vo.setBrandSpec(BrandSpecMigrator.migrateToV2(vo.getBrandSpec())));
        fillLabelsAndOwner(records);
        return voPage;
    }

    @Override
    public DesignSystemConfigPageVO getDetailById(Long id) {
        DesignSystemConfig entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException("设计体系配置不存在");
        }
        DesignSystemConfigPageVO vo = designSystemConfigConverter.toPageVO(entity);
        // brandSpec 兜底迁移到 v2.0
        vo.setBrandSpec(BrandSpecMigrator.migrateToV2(vo.getBrandSpec()));
        fillLabelsAndOwner(Collections.singletonList(vo));
        return vo;
    }

    @Override
    public DesignSystemConfigForm getFormById(Long id) {
        DesignSystemConfig entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException("设计体系配置不存在");
        }
        return designSystemConfigConverter.toForm(entity);
    }

    @Override
    @Transactional
    public boolean saveDesignSystem(DesignSystemConfigForm form) {
        DesignSystemConfig entity = designSystemConfigConverter.toEntity(form);
        Long currentUserId = SecurityUtils.getUserId();
        entity.setOwnerUserId(currentUserId);
        entity.setCreateBy(currentUserId);
        entity.setUpdateBy(currentUserId);
        if (StrUtil.isBlank(entity.getCategory())) {
            entity.setCategory("personal");
        }
        if (StrUtil.isBlank(entity.getType())) {
            entity.setType("web");
        }
        if (entity.getPublishStatus() == null) {
            entity.setPublishStatus(0);
        }
        // 入口处将任意版本的 brandSpec 统一迁移到 v2.0，避免 v1 旧结构被严格校验拒绝
        form.setBrandSpec(migrateBrandSpecIfPresent(form.getBrandSpec()));
        // 校验并标准化 JSON 字段（空字符串视为 null，非空则校验合法性；空值后续回填默认 JSON）
        entity.setBrandSpec(normalizeJsonField("brandSpec", form.getBrandSpec()));
        entity.setCodeSpec(normalizeJsonField("codeSpec", form.getCodeSpec()));
        entity.setAssets(normalizeJsonField("assets", form.getAssets()));
        if (StrUtil.isBlank(entity.getBrandSpec())) {
            // 新增时未提供 brandSpec：回填 v2.0 默认值
            entity.setBrandSpec(BrandSpecDefaults.V2_DEFAULT);
        }
        if (StrUtil.isBlank(entity.getCodeSpec())) {
            entity.setCodeSpec(DEFAULT_CODE_SPEC);
        }
        return this.save(entity);
    }

    @Override
    @Transactional
    public boolean updateDesignSystem(Long id, DesignSystemConfigForm form) {
        DesignSystemConfig exists = this.getById(id);
        if (exists == null) {
            throw new BusinessException("设计体系配置不存在");
        }
        DesignSystemConfig entity = designSystemConfigConverter.toEntity(form);
        entity.setId(id);
        entity.setUpdateBy(SecurityUtils.getUserId());
        // 入口处将任意版本的 brandSpec 统一迁移到 v2.0，避免 v1 旧结构被严格校验拒绝
        form.setBrandSpec(migrateBrandSpecIfPresent(form.getBrandSpec()));
        // 校验并标准化 JSON 字段（覆盖 Converter 拷贝的原值，确保入库前为合法 JSON 或 null）
        entity.setBrandSpec(normalizeJsonField("brandSpec", form.getBrandSpec()));
        entity.setCodeSpec(normalizeJsonField("codeSpec", form.getCodeSpec()));
        entity.setAssets(normalizeJsonField("assets", form.getAssets()));
        return this.updateById(entity);
    }

    @Override
    @Transactional
    public boolean deleteDesignSystems(String ids) {
        if (StrUtil.isBlank(ids)) {
            throw new BusinessException("删除的设计体系配置数据为空");
        }
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

    @Override
    @Transactional
    public boolean publishDesignSystem(Long id) {
        DesignSystemConfig entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException("设计体系配置不存在");
        }
        if (Integer.valueOf(1).equals(entity.getPublishStatus())) {
            throw new BusinessException("设计体系配置已发布");
        }
        entity.setPublishStatus(1);
        entity.setUpdateBy(SecurityUtils.getUserId());
        return this.updateById(entity);
    }

    @Override
    public List<DesignSystemConfigTemplateVO> getTemplates() {
        List<DesignSystemConfigTemplate> templates = designSystemConfigTemplateMapper.selectList(
                new LambdaQueryWrapper<DesignSystemConfigTemplate>()
                        .eq(DesignSystemConfigTemplate::getIsActive, 1)
                        .orderByAsc(DesignSystemConfigTemplate::getSortOrder)
        );
        return templates.stream().map(designSystemConfigConverter::toTemplateVO).toList();
    }

    @Override
    public DesignSystemConfigTemplateVO getTemplateDetailById(Long id) {
        DesignSystemConfigTemplate entity = designSystemConfigTemplateMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("官方模板不存在");
        }
        // brandSpec 兜底迁移到 v2.0
        entity.setBrandSpec(BrandSpecMigrator.migrateToV2(entity.getBrandSpec()));
        return designSystemConfigConverter.toTemplateVO(entity);
    }

    @Override
    public List<DesignSystemConfigPageVO> listForCurrentUser() {
        Long currentUserId = SecurityUtils.getUserId();
        List<DesignSystemConfig> entities = this.list(
                new LambdaQueryWrapper<DesignSystemConfig>()
                        .eq(DesignSystemConfig::getOwnerUserId, currentUserId)
                        .eq(DesignSystemConfig::getCategory, "personal")
                        .eq(DesignSystemConfig::getPublishStatus, 1)
                        .orderByDesc(DesignSystemConfig::getCreateTime)
        );
        if (CollectionUtil.isEmpty(entities)) {
            return Collections.emptyList();
        }
        List<DesignSystemConfigPageVO> records = entities.stream()
                .map(designSystemConfigConverter::toPageVO)
                .collect(Collectors.toList());
        // brandSpec 兜底迁移到 v2.0
        records.forEach(vo -> vo.setBrandSpec(BrandSpecMigrator.migrateToV2(vo.getBrandSpec())));
        fillLabelsAndOwner(records);
        return records;
    }

    /**
     * 在写入路径前置调用：若 brandSpec 非空，则迁移到 v2.0；为空时保持 null/空字符串不变，
     * 以便后续 {@link #normalizeJsonField} 走原空值语义（save 回填默认 / update 不覆盖 DB 已有值）。
     */
    private String migrateBrandSpecIfPresent(String brandSpec) {
        return StrUtil.isBlank(brandSpec) ? brandSpec : BrandSpecMigrator.migrateToV2(brandSpec);
    }

    /**
     * 校验并标准化 JSON 字段：空字符串视为 null，非空则验证是合法 JSON。
     * 失败抛 BusinessException，message 含字段名供前端定位。
     *
     * <p>brandSpec 字段额外校验：必须是 v2.0 结构，包含 colors.identity.{primary,accent,neutral}。
     * 缺失抛 BizException（项目实际类名 BusinessException）。</p>
     */
    private String normalizeJsonField(String fieldName, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        JsonNode tree;
        try {
            // 用 Jackson 解析，确保是合法 JSON（对象、数组、原始值均可）
            tree = OBJECT_MAPPER.readTree(trimmed);
        } catch (JsonProcessingException e) {
            throw new BusinessException(fieldName + " 不是合法的 JSON：" + e.getOriginalMessage());
        }
        // brandSpec 字段额外校验 v2.0 必要字段
        if ("brandSpec".equals(fieldName) && tree != null && tree.isObject()) {
            validateBrandSpecV2(tree);
        }
        return trimmed;
    }

    /**
     * 校验 brandSpec 是否为 v2.0 结构，缺失必要字段抛 BusinessException。
     * 必要字段：colors.identity.{primary,accent,neutral}
     */
    private void validateBrandSpecV2(JsonNode tree) {
        JsonNode identity = tree.path("colors").path("identity");
        String missing;
        if (identity.isMissingNode() || !identity.isObject()) {
            missing = "colors.identity";
        } else if (isBlankToken(identity, "primary")) {
            missing = "colors.identity.primary";
        } else if (isBlankToken(identity, "accent")) {
            missing = "colors.identity.accent";
        } else if (isBlankToken(identity, "neutral")) {
            missing = "colors.identity.neutral";
        } else {
            return;
        }
        throw new BusinessException("brandSpec 缺少必要字段：colors.identity.{primary,accent,neutral}（缺失：" + missing + "）");
    }

    /**
     * 判断 identity 节点下指定字段是否缺失或为空白字符串
     */
    private boolean isBlankToken(JsonNode identity, String field) {
        JsonNode node = identity.get(field);
        return node == null || node.isNull() || StrUtil.isBlank(node.asText());
    }

    /**
     * 为 VO 列表填充 label 字段与所有者用户名
     */
    private void fillLabelsAndOwner(List<DesignSystemConfigPageVO> records) {
        Set<Long> userIds = records.stream()
                .map(DesignSystemConfigPageVO::getOwnerUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> userIdToName = Collections.emptyMap();
        if (!userIds.isEmpty()) {
            List<User> users = userService.list(
                    new LambdaQueryWrapper<User>().in(User::getId, userIds));
            userIdToName = users.stream()
                    .collect(Collectors.toMap(User::getId, u -> u.getUsername() == null ? "" : u.getUsername(), (a, b) -> a));
        }

        for (DesignSystemConfigPageVO vo : records) {
            vo.setOwnerUserName(userIdToName.getOrDefault(vo.getOwnerUserId(), ""));
            vo.setCategoryLabel(getCategoryLabel(vo.getCategory()));
            vo.setTypeLabel(getTypeLabel(vo.getType()));
            vo.setPublishStatusLabel(getPublishStatusLabel(vo.getPublishStatus()));
        }
    }

    /**
     * 计算分类显示标签
     */
    private String getCategoryLabel(String category) {
        if ("official".equals(category)) {
            return "官方预设";
        }
        return "个人体系";
    }

    /**
     * 计算类型显示标签
     */
    private String getTypeLabel(String type) {
        if ("app".equals(type)) {
            return "应用";
        }
        return "网页";
    }

    /**
     * 计算发布状态显示标签
     */
    private String getPublishStatusLabel(Integer status) {
        if (status != null && status == 1) {
            return "已发布";
        }
        return "草稿";
    }
}
