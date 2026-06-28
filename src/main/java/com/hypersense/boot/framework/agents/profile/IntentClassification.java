// src/main/java/com/hypersense/boot/framework/agents/profile/IntentClassification.java
package com.hypersense.boot.framework.agents.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * IntentClassifierNode 输出 DTO。
 * primary 为主能力档位；secondary 为复合任务时串联顺序。
 */
public record IntentClassification(
        @JsonProperty("primary") String primary,
        @JsonProperty("secondary") List<String> secondary,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("reason") String reason,
        @JsonProperty("profileHints") Map<String, Object> profileHints
) {
    public List<String> safeSecondary() {
        return secondary == null ? List.of() : secondary;
    }
}
