package com.hypersense.boot.agents.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hypersense.boot.common.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Agent 会话实体(DB 层, 用于项目-会话绑定索引).
 * <p>
 * 与 Redis 主存储({@code AgentSessionVO})解耦:
 * <ul>
 *   <li>继承 {@link BaseEntity}, 复用 id / tenantId / createTime / updateTime 字段
 *       (表列已统一为 create_time/update_time, 由 MyMetaObjectHandler 自动填充)</li>
 *   <li>本 Entity 仅映射 agent_session 表中本次任务需要的字段
 *       (session_id / user_id / title / status + 继承的 tenant_id/create_time/update_time)</li>
 *   <li>其他字段(todos/files/final_response/hitl_xxx/interrupt_xxx/pending_approval)保留在表中但不映射,
 *       保持向后兼容</li>
 * </ul>
 * </p>
 *
 * @author Claude
 * @since 2026/6/24
 */
@TableName("agent_session")
@Getter
@Setter
@Schema(description = "Agent 会话实体(DB 层索引)")
public class AgentSessionEntity extends BaseEntity {

    /** 会话 ID(关联 Redis 中的 sessionId, 业务唯一键) */
    @Schema(description = "会话 ID")
    private String sessionId;

    /** 所属用户 ID */
    @Schema(description = "所属用户 ID")
    private Long userId;

    /** 会话标题(前端 TagsView / 列表展示用) */
    @Schema(description = "会话标题")
    private String title;

    /** 会话状态(冗余字段, 与 Redis 中 AgentSessionVO.status 同步) */
    @Schema(description = "会话状态")
    private String status;

    /** 逻辑删除标识(0=未删除, 1=已删除; BaseEntity 不含此字段, 故在子类声明) */
    @Schema(description = "逻辑删除标识")
    private Integer isDeleted;
}
