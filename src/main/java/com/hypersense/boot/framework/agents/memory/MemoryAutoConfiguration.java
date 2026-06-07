package com.hypersense.boot.framework.agents.memory;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 长期记忆系统 Spring 自动配置
 * <p>
 * 当 application.yml 配置 agent.memory.enabled=true 时自动激活。
 * 基于 Spring Boot Conditional 装配链：
 * <pre>
 * ZhipuEmbeddingClient → MemoryRepository → MemoryService → MemoryMiddleware
 * </pre>
 *
 * <h3>配置示例：</h3>
 * <pre>
 * agent:
 *   memory:
 *     enabled: true
 *     embedding-model: embedding-3
 *     embedding-dimensions: 1536
 * </pre>
 *
 * @author Claude
 * @since 2026/5/27
 */
@Slf4j
@Configuration
public class MemoryAutoConfiguration {

    /**
     * 智谱 Embedding 客户端（RestTemplate 直调，避免 zai-sdk Jackson 版本冲突）
     */
    @Bean("memoryEmbeddingClient")
    @ConditionalOnProperty(prefix = "agent.memory", name = "enabled", havingValue = "true")
    public ZhipuEmbeddingClient memoryEmbeddingClient(AgentProperties agentProperties) {
        AgentProperties.MemoryConfig memConfig = agentProperties.getMemory();
        String apiKey = agentProperties.getLlm().getOpenai().getApiKey();
        String endpoint = agentProperties.getLlm().getOpenai().getEndpoint();
        log.info("MemoryAutoConfiguration: 创建 Embedding 客户端, endpoint={}, model={}",
                endpoint, memConfig.getEmbeddingModel());
        return new ZhipuEmbeddingClient(endpoint, apiKey, memConfig.getEmbeddingModel());
    }

    /**
     * 记忆存储（JdbcTemplate + pgvector，自动建表）
     */
    @Bean
    @ConditionalOnBean(name = "memoryEmbeddingClient")
    public MemoryRepository memoryRepository(JdbcTemplate jdbcTemplate, AgentProperties agentProperties) {
        AgentProperties.MemoryConfig config = agentProperties.getMemory();
        MemoryRepository repo = new MemoryRepository(jdbcTemplate, config);
        repo.initialize();
        log.info("MemoryAutoConfiguration: MemoryRepository 已创建");
        return repo;
    }

    /**
     * 记忆核心服务（事实提取 + 向量化 + 检索）
     */
    @Bean
    @ConditionalOnBean(name = "memoryEmbeddingClient")
    public MemoryService memoryService(MemoryRepository repository,
                                       ZhipuEmbeddingClient embeddingClient,
                                       ChatModel chatModel,
                                       AgentProperties agentProperties) {
        log.info("MemoryAutoConfiguration: MemoryService 已创建");
        return new MemoryService(repository, embeddingClient, chatModel, agentProperties.getMemory());
    }

    /**
     * 记忆中间件（注入到图节点的 before/after 钩子）
     */
    @Bean
    @ConditionalOnBean(MemoryService.class)
    public MemoryMiddleware memoryMiddleware(MemoryService memoryService) {
        log.info("MemoryAutoConfiguration: MemoryMiddleware 已创建");
        return new MemoryMiddleware(memoryService);
    }
}
