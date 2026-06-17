package com.hypersense.boot.framework.agents.memory;

import com.hypersense.boot.framework.agents.middleware.AgentMiddleware;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 长期记忆中间件
 * <p>
 * plan 节点 before: 检索相关记忆注入 INSTRUCTIONS
 * finalize 节点 after: 从对话中提取事实并存储
 *
 * @author Claude
 * @since 2026/5/27
 */
@Slf4j
@RequiredArgsConstructor
public class MemoryMiddleware implements AgentMiddleware {

    /**
     * 跨中间件→节点传递增强后的 instructions。
     * <p>
     * LangGraph4j 节点执行期间 {@code state.data()} 返回 unmodifiable 视图，
     * 直接 put 会抛 UnsupportedOperationException。改用 ThreadLocal 旁路传递，
     * 由 {@code PlanNode} 在 apply 入口读取并合并到 prompt。
     * </p>
     */
    public static final ThreadLocal<String> ENHANCED_INSTRUCTIONS = new ThreadLocal<>();

    private final MemoryService memoryService;

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public void before(String nodeName, DeepAgentState state) {
        if (!"plan".equals(nodeName)) {
            return;
        }

        Long userId = state.userId();
        if (userId == null || userId == 0L) {
            return;
        }

        String instructions = state.instructions();
        if (instructions.contains(memoryService.getMemoryMarker())) {
            return;
        }

        try {
            List<AgentMemory> memories = memoryService.retrieve(
                    userId, state.tenantId(), instructions, memoryService.getMaxRetrievalCount());
            if (memories.isEmpty()) {
                return;
            }

            String enhancedInstructions = instructions + "\n\n"
                    + memoryService.getMemoryMarker() + "\n"
                    + memoryService.formatMemoryContext(memories);

            // 不能直接 state.data().put() — LangGraph4j 节点执行期间 data() 返回不可修改视图。
            // 改为 ThreadLocal 传递，由 PlanNode 读取合并。
            ENHANCED_INSTRUCTIONS.set(enhancedInstructions);
            log.info("MemoryMiddleware: 注入 {} 条记忆到 plan 节点（ThreadLocal）", memories.size());
        } catch (Exception e) {
            log.error("MemoryMiddleware: 记忆注入失败", e);
        }
    }

    @Override
    public Map<String, Object> after(String nodeName, DeepAgentState state, Map<String, Object> output) {
        if (!"finalize".equals(nodeName)) {
            return output;
        }

        Long userId = state.userId();
        if (userId == null || userId == 0L) {
            return output;
        }

        try {
            memoryService.extractAndStore(userId, state.tenantId(),
                    state.sessionId(), state.chatMessages());
        } catch (Exception e) {
            log.error("MemoryMiddleware: 事实提取失败", e);
        }

        return output;
    }
}
