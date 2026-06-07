package com.hypersense.boot.framework.agents.model;

import com.hypersense.boot.framework.agents.enums.AgentEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent SSE 推送事件
 *
 * @author Claude
 * @since 2026/5/15
 */
@Schema(description = "Agent 事件")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEvent {

    /** 事件类型 */
    @Schema(description = "事件类型")
    private AgentEventType type;

    /** 事件消息 */
    @Schema(description = "事件消息")
    private String message;

    /** 事件数据 */
    @Schema(description = "事件数据")
    private Object data;

    /** 时间戳 */
    @Schema(description = "时间戳")
    private long timestamp;
}
