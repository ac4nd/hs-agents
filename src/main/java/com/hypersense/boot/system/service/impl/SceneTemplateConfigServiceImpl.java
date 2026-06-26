package com.hypersense.boot.system.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.setting.yaml.YamlUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hypersense.boot.common.annotation.DataPermission;
import com.hypersense.boot.common.annotation.Log;
import com.hypersense.boot.common.annotation.RepeatSubmit;
import com.hypersense.boot.common.enums.ActionTypeEnum;
import com.hypersense.boot.common.enums.LogModuleEnum;
import com.hypersense.boot.common.constant.SystemConstants;
import com.hypersense.boot.file.service.impl.MinioFileService;
import com.hypersense.boot.framework.security.util.SecurityUtils;
import com.hypersense.boot.framework.tenant.TenantContextHolder;
import com.hypersense.boot.system.converter.SceneTemplateConfigConverter;
import com.hypersense.boot.system.mapper.SceneTemplateConfigMapper;
import com.hypersense.boot.system.model.entity.SceneTemplateConfig;
import com.hypersense.boot.system.model.form.SceneTemplateConfigForm;
import com.hypersense.boot.system.model.query.SceneTemplateConfigQuery;
import com.hypersense.boot.system.model.vo.SceneTemplateConfigVO;
import com.hypersense.boot.system.service.SceneTemplateConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 场景模板配置业务实现类
 *
 * @author Claude
 * @since 2026/6/24
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SceneTemplateConfigServiceImpl
        extends ServiceImpl<SceneTemplateConfigMapper, SceneTemplateConfig>
        implements SceneTemplateConfigService {

    private static final String OFFICIAL_CATEGORY = "official";
    private static final Long OFFICIAL_OWNER = 0L;

    private final SceneTemplateConfigConverter sceneTemplateConfigConverter;
    private final MinioFileService minioFileService;

    @Override
    @DataPermission(userAlias = "t")
    public IPage<SceneTemplateConfigVO> getSceneTemplateConfigPage(SceneTemplateConfigQuery query) {
        int pageNum = query.getPageNum();
        int pageSize = query.getPageSize();
        String keywords = query.getKeywords();
        String category = query.getCategory();
        String uiCategory = query.getUiCategory();
        Integer isOfficial = query.getIsOfficial();
        Integer isPublished = query.getIsPublished();

        // 管理员（ROOT/ADMIN 角色）或显式 showAll 可看全部
        boolean isAdmin = SecurityUtils.isRoot()
                || SecurityUtils.getRoles().contains(SystemConstants.PLATFORM_ADMIN_ROLE_CODE)
                || Boolean.TRUE.equals(query.getShowAll());
        Long currentUserId = SecurityUtils.getUserId();

        LambdaQueryWrapper<SceneTemplateConfig> wrapper = new LambdaQueryWrapper<SceneTemplateConfig>()
                .and(StrUtil.isNotBlank(keywords), w -> w
                        .like(SceneTemplateConfig::getName, keywords)
                        .or().like(SceneTemplateConfig::getSlug, keywords))
                .eq(StrUtil.isNotBlank(category), SceneTemplateConfig::getCategory, category)
                .eq(StrUtil.isNotBlank(uiCategory), SceneTemplateConfig::getUiCategory, uiCategory)
                .eq(isOfficial != null, SceneTemplateConfig::getIsOfficial, isOfficial)
                .eq(isPublished != null, SceneTemplateConfig::getIsPublished, isPublished)
                .orderByAsc(SceneTemplateConfig::getSort)
                .orderByDesc(SceneTemplateConfig::getUpdateTime);

        if (!isAdmin) {
            // 普通用户：已发布 OR 自己创建的（未发布也可见）
            final Long userId = currentUserId;
            wrapper.and(w -> w
                    .eq(SceneTemplateConfig::getIsPublished, 1)
                    .or()
                    .eq(SceneTemplateConfig::getOwnerUserId, userId));
        }

        // 跨租户查询：临时关闭租户过滤
        TenantContextHolder.setIgnoreTenant(true);
        Page<SceneTemplateConfig> page;
        try {
            page = this.page(new Page<>(pageNum, pageSize), wrapper);
        } finally {
            TenantContextHolder.setIgnoreTenant(false);
        }
        return sceneTemplateConfigConverter.toPageVO(page);
    }

    @Override
    public SceneTemplateConfigForm getSceneTemplateConfigForm(Long id) {
        SceneTemplateConfig entity = this.getById(id);
        return sceneTemplateConfigConverter.toForm(entity);
    }

    @Override
    public SceneTemplateConfigVO getSceneTemplateConfigDetail(Long id) {
        SceneTemplateConfig entity = this.getById(id);
        return sceneTemplateConfigConverter.toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @RepeatSubmit
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.INSERT)
    public boolean saveSceneTemplateConfig(SceneTemplateConfigForm form) {
        Long currentUserId = SecurityUtils.getUserId();
        SceneTemplateConfig entity = sceneTemplateConfigConverter.toEntity(form);
        entity.setOwnerUserId(currentUserId);
        if (entity.getIsOfficial() == null) {
            entity.setIsOfficial(0);
        }
        if (StrUtil.isBlank(entity.getCategory())) {
            entity.setCategory("community");
        }
        return this.save(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @RepeatSubmit
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.UPDATE)
    public boolean updateSceneTemplateConfig(Long id, SceneTemplateConfigForm form) {
        SceneTemplateConfig oldEntity = this.getById(id);
        Assert.isTrue(oldEntity != null, "场景模板配置不存在");
        form.setId(id);
        sceneTemplateConfigConverter.updateEntity(form, oldEntity);
        return this.updateById(oldEntity);
    }

    @Override
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.DELETE)
    public void deleteSceneTemplateConfigs(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的模板ID不能为空");
        Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .map(Long::parseLong)
                .forEach(this::removeById);
    }

    @Override
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.IMPORT)
    public int importFromDir(String dir) {
        Assert.isTrue(StrUtil.isNotBlank(dir), "导入目录不能为空");
        Path root = Paths.get(dir);
        Assert.isTrue(Files.isDirectory(root), "导入路径不是目录: {}", dir);

        int success = 0;
        // 官方模板写入 tenant_id=0；临时关闭多租户过滤
        TenantContextHolder.setIgnoreTenant(true);
        try (Stream<Path> stream = Files.list(root)) {
            List<Path> dirs = stream.filter(Files::isDirectory).toList();
            for (Path subDir : dirs) {
                try {
                    if (importOne(subDir)) {
                        success++;
                    }
                } catch (Exception e) {
                    log.warn("导入场景模板配置失败, dir={}, msg={}", subDir, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("读取导入目录失败: {}", dir, e);
        } finally {
            TenantContextHolder.setIgnoreTenant(false);
        }
        log.info("场景模板配置批量导入完成, dir={}, success={}", dir, success);
        return success;
    }

    @Override
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.UPDATE)
    public boolean publish(Long id) {
        return this.lambdaUpdate()
                .eq(SceneTemplateConfig::getId, id)
                .set(SceneTemplateConfig::getIsPublished, 1)
                .update();
    }

    @Override
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.UPDATE)
    public boolean unpublish(Long id) {
        return this.lambdaUpdate()
                .eq(SceneTemplateConfig::getId, id)
                .set(SceneTemplateConfig::getIsPublished, 0)
                .update();
    }

    @Override
    public List<String> getDistinctUiCategories() {
        // 跨租户查询：临时关闭租户过滤（与 getSceneTemplateConfigPage 一致）
        TenantContextHolder.setIgnoreTenant(true);
        try {
            return this.listObjs(new LambdaQueryWrapper<SceneTemplateConfig>()
                            .select(SceneTemplateConfig::getUiCategory)
                            .eq(SceneTemplateConfig::getIsPublished, 1)
                            .isNotNull(SceneTemplateConfig::getUiCategory)
                            .groupBy(SceneTemplateConfig::getUiCategory)
                            .orderByAsc(SceneTemplateConfig::getUiCategory),
                    o -> o == null ? null : o.toString());
        } finally {
            TenantContextHolder.setIgnoreTenant(false);
        }
    }

    /**
     * 导入单个模板目录。
     *
     * @return true=已导入；false=跳过（缺 example.html 或已被显式忽略）
     */
    private boolean importOne(Path subDir) throws IOException {
        String slug = subDir.getFileName().toString();
        Path htmlPath = subDir.resolve("example.html");
        if (!Files.exists(htmlPath)) {
            log.debug("目录无 example.html，跳过: {}", slug);
            return false;
        }

        // 元数据：优先 template.json，否则 SKILL.md frontmatter
        String name = slug;
        String tagline = StrUtil.EMPTY;
        String moodJson = null;
        String paletteJson = null;
        String typographyJson = null;
        Integer slideCount = 1;
        String sourceUrl = subDir.toString();
        // UI 显示分类：优先 od.scenario，否则按 slug 派生
        String odScenario = null;

        Path templateJson = subDir.resolve("template.json");
        if (Files.exists(templateJson)) {
            String content = Files.readString(templateJson, StandardCharsets.UTF_8);
            if (JSONUtil.isTypeJSON(content)) {
                JSONObject json = JSONUtil.parseObj(content);
                name = json.getStr("name", slug);
                tagline = json.getStr("tagline", json.getStr("description", StrUtil.EMPTY));
                moodJson = jsonObjToString(json, "mood");
                paletteJson = jsonObjToString(json, "palette");
                typographyJson = jsonObjToString(json, "typography");
                slideCount = json.getInt("slide_count", 1);
                // template.json 可显式指定 ui_category 或 scenario
                odScenario = json.getStr("scenario", json.getStr("ui_category"));
            }
        } else {
            Path skillMd = subDir.resolve("SKILL.md");
            if (Files.exists(skillMd)) {
                String md = Files.readString(skillMd, StandardCharsets.UTF_8);
                Map<String, Object> front = parseYamlFrontmatter(md);
                if (front != null) {
                    Object nameVal = front.get("name");
                    if (nameVal != null && StrUtil.isNotBlank(nameVal.toString())) {
                        name = nameVal.toString();
                    }
                    Object descVal = front.get("description");
                    if (descVal != null) {
                        tagline = descVal.toString().replaceAll("\\s+", " ").trim();
                    }
                    Object odVal = front.get("od");
                    if (odVal instanceof Map) {
                        Object moodVal = ((Map<?, ?>) odVal).get("mood");
                        if (moodVal != null) {
                            moodJson = JSONUtil.toJsonStr(moodVal);
                        }
                        Object scenarioVal = ((Map<?, ?>) odVal).get("scenario");
                        if (scenarioVal != null && StrUtil.isNotBlank(scenarioVal.toString())) {
                            odScenario = scenarioVal.toString().trim().toLowerCase();
                        }
                    }
                }
            }
        }
        String uiCategory = deriveUiCategory(slug, odScenario);

        // 上传 HTML 到 MinIO
        long size = Files.size(htmlPath);
        String objectKey = "scene-template/0/" + slug + ".html";
        String htmlUrl;
        try (FileInputStream fis = new FileInputStream(htmlPath.toFile())) {
            htmlUrl = minioFileService.uploadStream(fis, size, objectKey, "text/html");
        }

        // upsert by slug（tenant_id=0，已忽略租户过滤）
        SceneTemplateConfig existing = this.getOne(new LambdaQueryWrapper<SceneTemplateConfig>()
                .eq(SceneTemplateConfig::getSlug, slug)
                .last("LIMIT 1"));
        if (existing == null) {
            SceneTemplateConfig entity = new SceneTemplateConfig();
            entity.setOwnerUserId(OFFICIAL_OWNER);
            entity.setSlug(slug);
            entity.setName(name);
            entity.setTagline(tagline);
            entity.setCategory(OFFICIAL_CATEGORY);
            entity.setUiCategory(uiCategory);
            entity.setMood(moodJson);
            entity.setPalette(paletteJson);
            entity.setTypography(typographyJson);
            entity.setSlideCount(slideCount);
            entity.setSourceUrl(sourceUrl);
            entity.setHtmlUrl(htmlUrl);
            entity.setIsOfficial(1);
            entity.setIsPublished(1);
            entity.setSort(0);
            this.save(entity);
        } else {
            existing.setName(name);
            existing.setTagline(tagline);
            existing.setUiCategory(uiCategory);
            existing.setMood(moodJson);
            existing.setPalette(paletteJson);
            existing.setTypography(typographyJson);
            existing.setSlideCount(slideCount);
            existing.setSourceUrl(sourceUrl);
            existing.setHtmlUrl(htmlUrl);
            this.updateById(existing);
        }
        return true;
    }

    /**
     * 把 JSONObject 里的对象字段转为 JSON 字符串（非对象则返回 null）。
     */
    private String jsonObjToString(JSONObject json, String key) {
        Object val = json.get(key);
        if (val == null || val instanceof CharSequence) {
            return val == null ? null : val.toString();
        }
        return JSONUtil.toJsonStr(val);
    }

    /**
     * 解析 Markdown 的 YAML frontmatter（首部 {@code ---} ... {@code ---} 包裹段）。
     *
     * @return frontmatter 解析后的 Map；不存在则 null
     */
    private Map<String, Object> parseYamlFrontmatter(String md) {
        if (md == null || !md.startsWith("---")) {
            return null;
        }
        int end = md.indexOf("\n---", 3);
        if (end < 0) {
            return null;
        }
        String yaml = md.substring(3, end);
        try {
            Object loaded = YamlUtil.load(new StringReader(yaml));
            return loaded instanceof Map ? (Map<String, Object>) loaded : null;
        } catch (Exception e) {
            log.debug("解析 SKILL.md frontmatter 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据 SKILL.md frontmatter 的 {@code od.scenario} 与 slug 派生 UI 显示分类。
     *
     * <p>规则（与 DDL 迁移 SQL 保持一致，单一实现避免分叉）：</p>
     * <ol>
     *   <li>scenario 非空则直接小写返回（信任源数据）；</li>
     *   <li>否则按 slug 前缀/包含匹配做一次性映射；</li>
     *   <li>兜底返回 {@code "other"}。</li>
     * </ol>
     *
     * @param slug     模板目录名
     * @param scenario 从 SKILL.md frontmatter {@code od.scenario} 解析出的原始字符串（可为 null/空）
     * @return 派生后的 UI 分类字符串
     */
    private String deriveUiCategory(String slug, String scenario) {
        if (StrUtil.isNotBlank(scenario)) {
            return scenario.trim().toLowerCase();
        }
        if (StrUtil.isBlank(slug)) {
            return "other";
        }
        String s = slug.toLowerCase();
        if (s.startsWith("html-ppt-") || s.startsWith("guizang-ppt")) {
            return "presentation";
        }
        if (s.contains("dashboard") || s.startsWith("flowai-live")) {
            return "dashboard";
        }
        if (s.contains("blog") || s.contains("email") || s.contains("newsletter")) {
            return "content";
        }
        if (s.contains("landing") || s.contains("pricing")
                || s.contains("marketing") || s.contains("sales")) {
            return "marketing";
        }
        if (s.contains("invoice") || s.contains("finance")
                || s.contains("dcf") || s.contains("valuation")) {
            return "finance";
        }
        if (s.contains("hr") || s.contains("onboarding")) {
            return "hr";
        }
        if (s.contains("clinical") || s.contains("health")) {
            return "healthcare";
        }
        if (s.contains("course") || s.contains("edu")) {
            return "education";
        }
        if (s.contains("runbook") || s.contains("docs") || s.contains("eng-")) {
            return "engineering";
        }
        if (s.contains("gamified") || s.contains("dating")) {
            return "personal";
        }
        if (s.contains("contact") || s.contains("widget") || s.contains("critique")) {
            return "design";
        }
        return "other";
    }

}
