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

            state.data().put(DeepAgentState.INSTRUCTIONS, enhancedInstructions);
            log.info("MemoryMiddleware: 注入 {} 条记忆到 plan 节点", memories.size());
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
