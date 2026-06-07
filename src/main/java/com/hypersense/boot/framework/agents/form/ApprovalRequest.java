package com.hypersense.boot.framework.agents.form;

import com.hypersense.boot.framework.agents.model.ApprovalDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 人工审批请求 DTO
 * <p>
 * 前端调用审批接口时提交的决策和反馈。
 * </p>
 *
 * @author Claude
 * @since 2026/5/23
 */
@Schema(description = "人工审批请求")
@Data
public class ApprovalRequest {

    /** 审批决策 */
    @Schema(description = "审批决策：APPROVED / REJECTED / MODIFIED", required = true)
    @NotNull(message = "审批决策不能为空")
    private ApprovalDecision decision;

    /** 人工反馈文本 */
    @Schema(description = "人工反馈意见")
    private String feedback;

    /** 修改后的参数（仅当 decision=MODIFIED 时使用） */
    @Schema(description = "修改后的参数（仅 MODIFIED 决策时使用）")
    private Map<String, Object> modifiedParams;
}
