package com.hypersense.boot.framework.agents.engine.route;

import com.hypersense.boot.framework.agents.enums.TodoStatus;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import com.hypersense.boot.framework.agents.model.TodoItem;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 规划后路由
 * <p>
 * 检查是否还有待执行的 TODO：
 * - 智能 HITL 触发 → END（由 streamExecute 检测 NEED_CONFIRMATION 触发中断）
 * - has_pending → 转到 execute 节点
 * - all_completed → 转到 finalize 节点
 * </p>
 *
 * @author Claude
 * @since 2026/5/15
 */
@Slf4j
@Component
public class RouteAfterPlan implements EdgeAction<DeepAgentState> {

    @Override
    public String apply(DeepAgentState state) {
        // 智能 HITL Gate：PlanNode 置 NEED_CONFIRMATION=true 时立即结束图执行
        if (state.needConfirmation()) {
            log.info("RouteAfterPlan: 智能门控触发中断 → END");
            return StateGraph.END;
        }

        List<TodoItem> todos = state.todos();

        if (todos.isEmpty()) {
            log.info("RouteAfterPlan: 计划为空，结束执行");
            return "finalize";
        }

        boolean hasPending = todos.stream()
                .anyMatch(t -> t.getStatus() == TodoStatus.PENDING || t.getStatus() == TodoStatus.IN_PROGRESS);

        if (hasPending) {
            log.info("RouteAfterPlan: 还有待执行任务 → execute");
            return "execute";
        }

        log.info("RouteAfterPlan: 所有任务已完成 → finalize");
        return "finalize";
    }
}
