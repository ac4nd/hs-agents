package com.hypersense.boot.framework.agents.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话元数据更新表单（title + pinned 部分更新）
 * <p>
 * 任一字段为 null 表示不更新该字段；后端按 PATCH 语义处理。
 * </p>
 *
 * @author Claude
 * @since 2026/6/24
 */
@Schema(description = "会话元数据更新请求（title / pinned 部分更新）")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionMetaForm {

    /** 新标题；为 null 表示不更新 */
    @Schema(description = "新标题（可选，null 表示不更新）", example = "我的 Java 助手")
    private String title;

    /** 是否置顶；为 null 表示不更新 */
    @Schema(description = "是否置顶（可选，null 表示不更新）", example = "true")
    private Boolean pinned;
}
