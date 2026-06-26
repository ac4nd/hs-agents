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

    /** plan 周期上限：允许 1 次规划 + 1 次复用，第 3 次到达路由即强制终止（与 PlanNode 同步） */
    private static final int MAX_PLAN_CYCLES = 2;

    @Override
    public String apply(DeepAgentState state) {
        // 智能 HITL Gate：PlanNode 置 NEED_CONFIRMATION=true 时立即结束图执行
        if (state.needConfirmation()) {
            log.info("RouteAfterPlan: 智能门控触发中断 → END");
            return StateGraph.END;
        }

        // 简单回复短路：PlanNode DIRECT_REPLY 已写入 FINAL_RESPONSE，
        // 跳过 finalize 节点（避免 MemoryMiddleware 事实提取 LLM 调用与 NODE_EXECUTION 噪音）
        if (state.finalResponse().isPresent() && !state.finalResponse().get().isBlank()) {
            log.info("RouteAfterPlan: 检测到 DIRECT_REPLY 直接回复 → END（跳过汇总）");
            return StateGraph.END;
        }

        // === 双保险 2：plan 周期计数器，超过上限强制 finalize（与 PlanNode.MAX_PLAN_CYCLES 联动）===
        // plan_cycle_count 未在 SCHEMA 注册：若 PlanNode 写入失败被丢弃，此处读到 0，
        // 则下面的 todos 状态兜底逻辑仍生效（hasPending=false → finalize），双保险至少一道生效。
        int planCycleCount;
        try {
            planCycleCount = state.<Integer>value(DeepAgentState.PLAN_CYCLE_COUNT).orElse(0);
        } catch (ClassCastException cce) {
            log.warn("RouteAfterPlan: plan_cycle_count 类型异常，回退为 0: {}", cce.getMessage());
            planCycleCount = 0;
        }
        if (planCycleCount >= MAX_PLAN_CYCLES) {
            log.warn("RouteAfterPlan: plan 周期数 {} 达到上限，强制 finalize（防漂移）", planCycleCount);
            return "finalize";
        }

        List<TodoItem> todos = state.todos();

        if (todos == null || todos.isEmpty()) {
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
