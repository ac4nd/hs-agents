package com.hypersense.boot.framework.agents.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersense.boot.framework.agents.checkpoint.PostgresqlSaver;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

/**
 * AI Agent 自动配置类
 *
 * @author Claude
 * @since 2026/5/15
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AgentAutoConfiguration {

    private final AgentProperties agentProperties;

    /**
     * OpenAI 兼容 ChatModel（支持 OpenAI / DeepSeek / 通义千问等）
     */
    @Bean
    @ConditionalOnProperty(name = "agent.llm.provider", havingValue = "openai", matchIfMissing = true)
    @ConditionalOnMissingBean(ChatModel.class)
    public ChatModel openAiChatModel() {
        AgentProperties.LlmConfig llm = agentProperties.getLlm();
        AgentProperties.OpenAiConfig openai = llm.getOpenai();

        log.info("初始化 OpenAI 兼容 ChatModel: endpoint={}, model={}", openai.getEndpoint(), openai.getModelName());

        return OpenAiChatModel.builder()
                .baseUrl(openai.getEndpoint())
                .apiKey(openai.getApiKey())
                .modelName(openai.getModelName())
                .temperature(llm.getTemperature())
                .maxTokens(llm.getMaxTokens())
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    // TODO: Ollama ChatModel 待 langchain4j-ollama 依赖可用后启用
    // 参见 pom.xml 中被注释的 langchain4j-ollama 依赖

    /**
     * PostgreSQL 检查点持久化
     * <p>
     * 通过 agent.deep.checkpoint-enabled=true 启用，使用 PostgreSQL JSONB 存储检查点状态。
     * </p>
     */
    @Bean
    @ConditionalOnProperty(name = "agent.deep.checkpoint-enabled", havingValue = "true")
    public PostgresqlSaver postgresqlSaver(JdbcTemplate jdbcTemplate) {
        log.info("初始化 PostgreSQL 检查点持久化（PostgresqlSaver）");
        ObjectMapper objectMapper = new ObjectMapper();
        PostgresqlSaver saver = new PostgresqlSaver(jdbcTemplate, objectMapper);
        saver.setup();
        return saver;
    }

    /**
     * 内存检查点持久化（fallback）
     * <p>
     * 当未启用 PostgreSQL 检查点时使用内存存储。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(BaseCheckpointSaver.class)
    public MemorySaver memorySaver() {
        log.info("初始化内存检查点持久化（MemorySaver）");
        return new MemorySaver();
    }
}
