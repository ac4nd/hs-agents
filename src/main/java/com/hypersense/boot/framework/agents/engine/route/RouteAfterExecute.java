package com.hypersense.boot.framework.agents.engine.route;

import com.hypersense.boot.framework.agents.enums.TodoStatus;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import com.hypersense.boot.framework.agents.model.TodoItem;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 执行后路由
 * <p>
 * 根据当前 TODO 的执行策略决定下一步：
 * <ul>
 *   <li>智能 HITL 触发 → END（由 streamExecute 检测 NEED_CONFIRMATION 触发中断）</li>
 *   <li>allDone 短路 → finalize（所有 TODO 已结束，取消 plan 循环后的终止条件）</li>
 *   <li>delegate → 转到委派节点</li>
 *   <li>tool → 转到工具调用节点</li>
 *   <li><b>direct（已废除）</b> → 强制改路由到 ToolNode，并在 state 注入警告</li>
 *   <li>completed/failed → execute（由 ExecuteNode 选下一个 TODO，不再回 plan）</li>
 * </ul>
 * </p>
 *
 * @author Claude
 * @since 2026/5/15
 */
@Slf4j
@Component
public class RouteAfterExecute implements EdgeAction<DeepAgentState> {

    /** direct 策略常量（已废除，仅用于检测） */
    private static final String STRATEGY_DIRECT = "direct";
    private static final String STRATEGY_TOOL = "tool";
    private static final String STRATEGY_DELEGATE = "delegate";

    @Override
    public String apply(DeepAgentState state) {
        // 智能 HITL Gate：ExecuteNode 置 NEED_CONFIRMATION=true 时立即结束图执行
        if (state.needConfirmation()) {
            log.info("RouteAfterExecute: 智能门控触发中断 → END");
            return StateGraph.END;
        }

        // allDone 短路：所有 TODO 已结束 → finalize
        // 取消 plan 循环后的关键终止条件：tool → execute 回流时，ExecuteNode 已检测 allDone 直接返回，
        // 这里再次确认所有 TODO COMPLETED/FAILED 后短路到 finalize，避免回 plan 触发漂移
        java.util.List<TodoItem> todos = state.todos();
        if (todos != null && !todos.isEmpty()) {
            boolean allDone = todos.stream().allMatch(t ->
                    t.getStatus() == TodoStatus.COMPLETED || t.getStatus() == TodoStatus.FAILED);
            if (allDone) {
                log.info("RouteAfterExecute: 所有 TODO 已结束 → finalize");
                return "finalize";
            }
        }

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

        // 改前：任务已完成/失败 → plan（重新规划）
        // 改后：任务已完成/失败 → execute（由 ExecuteNode 选下一个 TODO）
        // 取消 plan 循环：tool 完成后不再回 plan，由 ExecuteNode 自己推进下一个 PENDING TODO
        if (todo.getStatus() == TodoStatus.COMPLETED || todo.getStatus() == TodoStatus.FAILED) {
            log.info("RouteAfterExecute: 当前 TODO 已完成/失败 → execute（选下一个 TODO）");
            return "execute";
        }

        // 使用 ExecuteNode 决定的执行策略路由
        String strategy = state.executeStrategy();

        // 防御性检测：若 LLM 仍输出已废除的 direct 策略，强制改路由到 ToolNode。
        // 注：ExecuteNode.decideStrategy 已主动降级 direct → tool，正常路径不会走到这里。
        // 此分支作为第二道防线，防止未来某次改动或绕过决策器导致 direct 漏出。
        if (STRATEGY_DIRECT.equalsIgnoreCase(strategy)) {
            log.warn("RouteAfterExecute: LLM 输出了已废除的 direct 策略，强制改路由到 tool "
                    + "(TODO={})。请使用 reply_text 工具完成回复。", todo.getDescription());
            // 注意：EdgeAction 无法直接修改 state（只能返回下一节点名）。
            // 警告已通过日志记录；ToolNode 内部 LLM 决策会自然选择 reply_text 工具完成回复。
            return "tool";
        }

        String nextNode = switch (strategy) {
            case STRATEGY_TOOL -> "tool";
            case STRATEGY_DELEGATE -> "delegate";
            default -> "plan"; // 兜底：未知策略回到 plan 更新状态
        };
        log.info("RouteAfterExecute: 策略={} → {}", strategy, nextNode);
        return nextNode;
    }
}

