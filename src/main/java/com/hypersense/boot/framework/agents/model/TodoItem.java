package com.hypersense.boot.framework.agents.model;

import com.hypersense.boot.framework.agents.enums.TodoStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Deep Agent TODO 项
 *
 * @author Claude
 * @since 2026/5/15
 */
@Schema(description = "Agent TODO 项")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodoItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 唯一标识 */
    @Schema(description = "TODO ID")
    private String id;

    /** 任务描述 */
    @Schema(description = "任务描述")
    private String description;

    /** 状态 */
    @Schema(description = "状态")
    private TodoStatus status;

    /** 执行结果 */
    @Schema(description = "执行结果")
    private String result;

    /** 委派给的子 Agent 名称（空表示自身执行） */
    @Schema(description = "委派给的子 Agent 名称")
    private String assignedAgent;

    /** 更新时间 */
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
