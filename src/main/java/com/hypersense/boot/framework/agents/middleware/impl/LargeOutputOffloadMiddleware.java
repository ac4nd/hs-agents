package com.hypersense.boot.framework.agents.middleware.impl;

import com.hypersense.boot.framework.agents.middleware.AgentMiddleware;
import com.hypersense.boot.framework.agents.model.DeepAgentState;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 大输出卸载中间件
 * <p>
 * 检测节点输出 MESSAGES 中的大型字符串，将其卸载到 FILES 通道中，
 * 原位置替换为引用标记。防止 MESSAGES 因大内容而膨胀。
 * </p>
 *
 * <h3>适用场景：</h3>
 * <ul>
 *   <li>工具返回大型文件内容（如日志文件、数据集）</li>
 *   <li>子 Agent 返回超长分析报告</li>
 * </ul>
 *
 * @author Claude
 * @since 2026/5/22
 */
@Slf4j
public class LargeOutputOffloadMiddleware implements AgentMiddleware {

    /** 默认阈值：10KB */
    private static final int DEFAULT_MAX_CHARS = 10 * 1024;

    private final int maxChars;

    public LargeOutputOffloadMiddleware() {
        this(DEFAULT_MAX_CHARS);
    }

    public LargeOutputOffloadMiddleware(int maxChars) {
        this.maxChars = maxChars;
    }

    @Override
    public String name() {
        return "large-output-offload";
    }

    @Override
    public Map<String, Object> after(String nodeName, DeepAgentState state, Map<String, Object> output) {
        if (output == null || output.isEmpty()) {
            return output;
        }

        // 检查 MESSAGES 中的大文本（节点返回的 AiMessage 会被 appender 处理）
        Object messagesObj = output.get(DeepAgentState.MESSAGES);
        if (messagesObj instanceof String text && text.length() > maxChars) {
            // 将大文本存入 FILES 通道
            String reference = "_offload/" + nodeName + "-" + System.currentTimeMillis() + ".txt";
            Map<String, String> files = new HashMap<>(state.files());
            files.put(reference, text);

            Map<String, Object> modified = new HashMap<>(output);
            modified.put(DeepAgentState.MESSAGES,
                    "[输出已卸载到文件: " + reference + "] 原始输出 " + text.length() + " 字符");
            modified.put(DeepAgentState.FILES, files);

            log.info("LargeOutputOffloadMiddleware: MESSAGES 输出已卸载, node={}, ref={}, 原始长度={}",
                    nodeName, reference, text.length());
            return modified;
        }

        return output;
    }
}
