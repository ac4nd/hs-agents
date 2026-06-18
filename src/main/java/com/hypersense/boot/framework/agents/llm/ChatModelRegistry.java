package com.hypersense.boot.framework.agents.llm;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * ChatModel 注册表：modelConfigId → ChatModel 实例（Caffeine 缓存）。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>懒加载：首次请求时调用 {@link ChatModelFactory#build} 构造，避免启动期无效连接</li>
 *   <li>兜底机制：{@link #getOrDefault(Long)} 在 modelConfigId 为空或构造失败时回退 Spring 单例</li>
 *   <li>双层缓存一致性：本表 + AgentServiceImpl.graphCache；切换模型时由调用方同步 invalidate 两层</li>
 * </ul>
 * </p>
 *
 * @author Claude
 * @since 2026/6/18
 */
@Slf4j
@Component
public class ChatModelRegistry {

    private final ChatModelFactory factory;
    private final ChatModel fallbackChatModel;

    private final Cache<Long, ChatModel> cache = Caffeine.newBuilder()
            .maximumSize(64)
            .expireAfterAccess(2, TimeUnit.HOURS)
            .removalListener((key, value, cause) ->
                    log.debug("ChatModelRegistry eviction: modelConfigId={}, cause={}", key, cause))
            .build();

    public ChatModelRegistry(ChatModelFactory factory,
                             @Qualifier("chatModel") @Nullable ChatModel fallbackChatModel) {
        this.factory = factory;
        this.fallbackChatModel = fallbackChatModel;
    }

    /**
     * 获取或构建指定模型的 ChatModel（不兜底，失败抛异常）。
     */
    public ChatModel get(Long modelConfigId) {
        if (modelConfigId == null) {
            throw new IllegalArgumentException("modelConfigId 不能为空");
        }
        return cache.get(modelConfigId, factory::build);
    }

    /**
     * 获取或构建指定模型的 ChatModel；modelConfigId 为空时回退兜底单例。
     * <p>
     * <b>注意：</b>modelConfigId 非空时，构建失败<b>不再</b>静默回退到兜底单例。
     * 之前的静默回退会引发隐蔽 bug：用户切换到 DeepSeek 后因环境变量未配置，
     * 实际仍用智谱兜底，错误码 1113（智谱特有）让排查方向走偏。
     * 现在直接抛出真实异常，让前端看到「API Key 解析为空」的明确提示。
     * </p>
     * 兜底场景仅保留：modelConfigId 为 null（旧 session 向后兼容）。
     */
    public ChatModel getOrDefault(@Nullable Long modelConfigId) {
        if (modelConfigId == null) {
            return fallbackChatModel;
        }
        // 不捕获异常：让 streamExecute / execute 真实失败，前端能看到明确报错
        return cache.get(modelConfigId, factory::build);
    }

    /**
     * 使单个模型缓存失效（配置变更时调用）。
     */
    public void invalidate(Long modelConfigId) {
        if (modelConfigId != null) {
            cache.invalidate(modelConfigId);
        }
    }

    /**
     * 清空所有缓存。
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }
}
