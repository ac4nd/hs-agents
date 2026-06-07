package com.hypersense.boot.framework.agents.engine.route;

import com.hypersense.boot.framework.agents.enums.TodoStatus;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import com.hypersense.boot.framework.agents.model.TodoItem;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.EdgeAction;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 执行后路由
 * <p>
 * 根据当前 TODO 的执行策略决定下一步：
 * - delegate → 转到委派节点
 * - tool → 转到工具调用节点
 * - completed/other → 回到规划节点更新进度
 * </p>
 *
 * @author Claude
 * @since 2026/5/15
 */
@Slf4j
@Component
public class RouteAfterExecute implements EdgeAction<DeepAgentState> {

    @Override
    public String apply(DeepAgentState state) {
        Optional<TodoItem> currentTodo = state.currentTodo();

        if (currentTodo.isEmpty()) {
            log.info("RouteAfterExecute: 无当前 TODO → plan");
            return "plan";
        }

        TodoItem todo = currentTodo.get();

        // 如果已分配子 Agent → 委派
        if (todo.getAssignedAgent() != null && !todo.getAssignedAgent().isEmpty()) {
            log.info("RouteAfterExecute: 任务已分配子 Agent [{}] → delegate", todo.getAssignedAgent());
            return "delegate";
        }

        // 如果状态已完成/失败 → 回到规划
        if (todo.getStatus() == TodoStatus.COMPLETED || todo.getStatus() == TodoStatus.FAILED) {
            log.info("RouteAfterExecute: 任务已完成/失败 → plan");
            return "plan";
        }

        // 使用 ExecuteNode 决定的执行策略路由
        String strategy = state.executeStrategy();
        String nextNode = switch (strategy) {
            case "tool" -> "tool";
            case "delegate" -> "delegate";
            default -> "plan"; // direct 策略：无需工具或委派，回到 plan 更新状态
        };
        log.info("RouteAfterExecute: 策略={} → {}", strategy, nextNode);
        return nextNode;
    }
}
