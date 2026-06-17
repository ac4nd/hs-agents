package com.hypersense.boot.framework.agents.hitl;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HitlGateChecker 的判断结果
 *
 * @author Claude
 * @since 2026/6/17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HitlDecision {

    /** 是否需要用户确认 */
    private boolean needConfirm;

    /** 严重程度：low / medium / high */
    private String severity;

    /** 触发维度：ambiguity / risk / scope / null */
    private String dimension;

    /** 简短中文说明（≤30 字） */
    private String reason;

    public static HitlDecision pass() {
        return HitlDecision.builder()
                .needConfirm(false)
                .severity("low")
                .reason("自动放行")
                .build();
    }
}
