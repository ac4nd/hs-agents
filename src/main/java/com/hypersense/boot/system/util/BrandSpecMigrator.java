package com.hypersense.boot.system.util;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hypersense.boot.system.constant.BrandSpecDefaults;

/**
 * 品牌规范（brandSpec）版本迁移工具类
 *
 * <p>负责将历史版本（v1，扁平 colors + font 字符串）迁移到 v2.0 分层令牌结构
 * （identity / semantic / fonts / radius / logo）。仅做内存转换，不触碰数据库。</p>
 *
 * <p>v1 → v2 迁移规则：</p>
 * <ul>
 *     <li>{@code colors.primary}（v1）→ {@code colors.identity.primary}（v2）</li>
 *     <li>{@code colors.text}（v1）→ {@code colors.identity.neutral}（v2）</li>
 *     <li>缺失 {@code accent}：用 {@code #F97316} 默认值</li>
 *     <li>{@code colors.secondary} / {@code colors.background}（v1）：丢弃（v2 由 identity 派生）</li>
 *     <li>{@code font}（v1 字符串）→ {@code fonts.body}（自动追加 {@code ", system-ui, sans-serif"} fallback）</li>
 *     <li>缺失字段补 v2 默认</li>
 * </ul>
 *
 * <p>异常策略：解析失败或数据异常时返回 {@link BrandSpecDefaults#V2_DEFAULT}，
 * 不向上抛异常——保证查询接口可用性。</p>
 *
 * @author Claude
 * @since 2026/6/25
 */
public final class BrandSpecMigrator {

    /**
     * 用于 JSON 字段解析的 ObjectMapper（项目未注册全局 Bean，本地持有复用）
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 默认强调色（v1 缺失 accent 时回填）
     */
    private static final String DEFAULT_ACCENT = "#F97316";

    private BrandSpecMigrator() {
    }

    /**
     * 将任意版本的 brandSpec JSON 迁移到 v2.0
     *
     * <p>{@code null} / 空字符串 / 解析失败：返回 {@link BrandSpecDefaults#V2_DEFAULT}</p>
     *
     * @param brandSpec 原始 brandSpec JSON 字符串，可为空
     * @return v2.0 结构的 JSON 字符串
     */
    public static String migrateToV2(String brandSpec) {
        if (StrUtil.isBlank(brandSpec)) {
            return BrandSpecDefaults.V2_DEFAULT;
        }
        try {
            ObjectNode migrated = parseAndMigrate(brandSpec);
            return OBJECT_MAPPER.writeValueAsString(migrated);
        } catch (JsonProcessingException e) {
            // 解析失败：兜底返回默认值，避免阻断查询
            return BrandSpecDefaults.V2_DEFAULT;
        } catch (Exception e) {
            // 其它运行时异常同样兜底
            return BrandSpecDefaults.V2_DEFAULT;
        }
    }

    /**
     * 解析 JSON 并按需迁移，返回迁移后的 ObjectNode
     *
     * <p>内部逻辑：</p>
     * <ol>
     *     <li>解析失败抛 {@link JsonProcessingException}（由上层 {@link #migrateToV2} 兜底）</li>
     *     <li>以 v2 默认为模板深拷贝，确保所有分层令牌字段都存在</li>
     *     <li>version 非 "2.0" 或缺失：执行 v1→v2 字段映射</li>
     *     <li>已为 v2.0：保留原值，但确保 identity.{primary,accent,neutral} 一定存在</li>
     * </ol>
     *
     * @param brandSpec 原始 brandSpec JSON 字符串，非空
     * @return 迁移后的 ObjectNode
     * @throws JsonProcessingException JSON 解析失败
     */
    private static ObjectNode parseAndMigrate(String brandSpec) throws JsonProcessingException {
        // 以 v2 默认为模板深拷贝，确保 fonts/radius/semantic 等字段补全
        ObjectNode base = (ObjectNode) OBJECT_MAPPER.readTree(BrandSpecDefaults.V2_DEFAULT);
        JsonNode source = OBJECT_MAPPER.readTree(brandSpec.trim());

        if (!(source instanceof ObjectNode src)) {
            // 非 JSON 对象（数组 / 原始值）：无法迁移，直接返回 v2 默认
            return base;
        }

        JsonNode versionNode = src.get("version");
        boolean isV2 = versionNode != null && "2.0".equals(versionNode.asText());

        // colors 处理
        ObjectNode baseColors = (ObjectNode) base.get("colors");
        ObjectNode baseIdentity = (ObjectNode) baseColors.get("identity");
        JsonNode srcColors = src.get("colors");

        if (isV2) {
            // v2.0：直接拷贝 identity / semantic，但确保必要字段存在
            if (srcColors != null && srcColors.isObject()) {
                JsonNode srcIdentity = srcColors.get("identity");
                if (srcIdentity != null && srcIdentity.isObject()) {
                    copyIfPresent((ObjectNode) srcIdentity, baseIdentity, "primary");
                    copyIfPresent((ObjectNode) srcIdentity, baseIdentity, "accent");
                    copyIfPresent((ObjectNode) srcIdentity, baseIdentity, "neutral");
                }
                JsonNode srcSemantic = srcColors.get("semantic");
                if (srcSemantic != null && srcSemantic.isObject()) {
                    baseColors.set("semantic", srcSemantic);
                }
            }
        } else {
            // v1 → v2 迁移
            if (srcColors != null && srcColors.isObject()) {
                ObjectNode srcColorsObj = (ObjectNode) srcColors;
                // colors.primary → colors.identity.primary
                copyIfPresent(srcColorsObj, baseIdentity, "primary");
                // colors.text → colors.identity.neutral
                if (hasText(srcColorsObj, "text")) {
                    baseIdentity.put("neutral", srcColorsObj.get("text").asText());
                }
                // 缺失 accent：默认值（baseIdentity 已含 #F97316，无需额外处理）
                copyIfPresent(srcColorsObj, baseIdentity, "accent");
                // colors.secondary / colors.background：丢弃
            }
        }

        // fonts 处理
        ObjectNode baseFonts = (ObjectNode) base.get("fonts");
        JsonNode srcFonts = src.get("fonts");
        if (isV2 && srcFonts != null && srcFonts.isObject()) {
            // v2.0 完整拷贝 fonts
            base.set("fonts", srcFonts);
        } else {
            // v1：font 字符串 → fonts.body（追加 fallback）
            JsonNode srcFont = src.get("font");
            if (srcFont != null && !srcFont.isNull() && StrUtil.isNotBlank(srcFont.asText())) {
                String fontStr = srcFont.asText().trim();
                if (!fontStr.toLowerCase().contains("system-ui")) {
                    fontStr = fontStr + ", system-ui, sans-serif";
                }
                baseFonts.put("body", fontStr);
                baseFonts.put("display", fontStr);
            }
        }

        // radius 处理（v2 直接覆盖，v1 没有 radius 字段则保留 base 默认）
        JsonNode srcRadius = src.get("radius");
        if (isV2 && srcRadius != null && srcRadius.isObject()) {
            base.set("radius", srcRadius);
        }

        // logo 处理（任意版本只要存在就覆盖）
        JsonNode srcLogo = src.get("logo");
        if (srcLogo != null && !srcLogo.isNull() && StrUtil.isNotBlank(srcLogo.asText())) {
            base.put("logo", srcLogo.asText());
        }

        // 确保 accent 一定存在（v1 缺失时回填默认 #F97316）
        if (!baseIdentity.has("accent") || baseIdentity.get("accent").isNull()
                || StrUtil.isBlank(baseIdentity.get("accent").asText())) {
            baseIdentity.put("accent", DEFAULT_ACCENT);
        }

        return base;
    }

    /**
     * 拷贝字段（仅当源字段存在且非空时）
     */
    private static void copyIfPresent(ObjectNode src, ObjectNode dst, String field) {
        JsonNode node = src.get(field);
        if (node != null && !node.isNull() && StrUtil.isNotBlank(node.asText())) {
            dst.set(field, node);
        }
    }

    /**
     * 判断字段是否存在且为非空文本
     */
    private static boolean hasText(ObjectNode src, String field) {
        JsonNode node = src.get(field);
        return node != null && !node.isNull() && StrUtil.isNotBlank(node.asText());
    }
}
