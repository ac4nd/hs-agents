package com.hypersense.boot.framework.agents.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hypersense.boot.framework.agents.checkpoint.PostgresqlSaver;
import com.hypersense.boot.framework.agents.serializer.LangChain4jJacksonModule;
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
     * OpenAI 兼容 ChatModel（支持 OpenAI / 智谱 GLM / 阿里百炼 / DeepSeek / 通义千问 等）
     * <p>
     * 根据 {@code agent.llm.chat-vendor} 选择 vendors Map 中对应厂商的 endpoint/apiKey/chat-model。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(ChatModel.class)
    public ChatModel chatModel() {
        AgentProperties.LlmConfig llm = agentProperties.getLlm();
        AgentProperties.VendorConfig vendor = llm.resolveVendor(llm.getChatVendor());

        log.info("初始化 ChatModel: vendor={}, endpoint={}, model={}",
                llm.getChatVendor(), vendor.getEndpoint(), vendor.getChatModel());

        return OpenAiChatModel.builder()
                .baseUrl(vendor.getEndpoint())
                .apiKey(vendor.getApiKey())
                .modelName(vendor.getChatModel())
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
        // 注册 LangChain4j 消息类型的 Jackson 序列化器，否则 checkpoint 持久化时 UserMessage 等无法序列化
        // 同时注册 JavaTimeModule 以支持 TodoItem/AgentSession 等含 LocalDateTime 字段的对象
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new LangChain4jJacksonModule());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
