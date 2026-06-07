package com.hypersense.boot.framework.agents.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 长期记忆实体
 * <p>
 * 存储从对话中提取的事实、偏好、决策等信息，
 * 通过 pgvector 向量化支持语义检索。
 * </p>
 *
 * @author Claude
 * @since 2026/5/27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMemory {

    private Long id;

    /** 多租户隔离 */
    private Long tenantId;

    /** 用户级记忆 */
    private Long userId;

    /** 事实内容（如"用户喜欢 docx 格式"） */
    private String content;

    /** 分类：preference / fact / decision / procedure */
    private String category;

    /** 向量化表示（pgvector vector 类型） */
    private float[] embedding;

    /** 来源会话 ID */
    private String sessionId;

    /** 访问次数（用于重要性排序） */
    private Integer accessCount;

    private LocalDateTime createdAt;

    private LocalDateTime lastAccessedAt;

    /**
     * 记忆分类枚举
     */
    public static final class Category {
        /** 用户偏好（格式、风格、工具选择） */
        public static final String PREFERENCE = "preference";
        /** 客观事实（项目信息、技术栈、API 端点） */
        public static final String FACT = "fact";
        /** 重要决策（架构选择、方案取舍） */
        public static final String DECISION = "decision";
        /** 操作流程（用户总结的步骤、工作方式） */
        public static final String PROCEDURE = "procedure";

        private Category() {}
    }
}
