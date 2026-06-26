package com.hypersense.boot.framework.agents.llm;

import com.hypersense.boot.common.exception.BusinessException;
import com.hypersense.boot.system.model.entity.LlmApiKeyConfig;
import com.hypersense.boot.system.model.entity.LlmModelConfig;
import com.hypersense.boot.system.model.entity.LlmVendorConfig;
import com.hypersense.boot.system.service.LlmApiKeyConfigService;
import com.hypersense.boot.system.service.LlmModelConfigService;
import com.hypersense.boot.system.service.LlmVendorConfigService;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * ChatModel 桥接器：sys_llm_* 配置 → LangChain4j ChatModel。
 * <p>
 * 串联三张表：
 * <pre>
 * sys_llm_model_config
 *      └─ api_key_config_id → sys_llm_api_key_config
 *                              └─ vendor_config_id → sys_llm_vendor_config
 *                                                     └─ config_key → Spring Environment
 * </pre>
 * API Key 不入库加密，统一走 vendor.config_key 占位符解析（开发环境 yml 默认值，生产环境 OS 环境变量）。
 * </p>
 *
 * @author Claude
 * @since 2026/6/18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatModelFactory {

    private final LlmModelConfigService llmModelConfigService;
    private final LlmApiKeyConfigService llmApiKeyConfigService;
    private final LlmVendorConfigService llmVendorConfigService;
    private final Environment environment;

    /**
     * LLM HTTP 默认超时（秒）。
     * <p>调大到 300 秒以容纳长输出场景：
     * <ul>
     *   <li>ToolNode.decideByLlm 在设计模式下需一次性生成完整 HTML（maxOutputTokens=16384）</li>
     *   <li>PlanNode 规划长链路 TODO 时也可能较慢</li>
     * </ul>
     * 实测 120s 在 glm-4.6 等模型上易出现 {@code HttpTimeoutException}。
     */
    private static final long DEFAULT_TIMEOUT_SECONDS = 300L;

    /**
     * 根据模型配置 ID 构造 ChatModel 实例。
     *
     * @param modelConfigId sys_llm_model_config.id
     * @return 已构建好的 ChatModel
     * @throws BusinessException 模型/API-KEY/厂商任一未启用，或占位符无法解析时抛出
     */
    public ChatModel build(Long modelConfigId) {
        if (modelConfigId == null) {
            throw new BusinessException("modelConfigId 不能为空");
        }

        LlmModelConfig mc = llmModelConfigService.getById(modelConfigId);
        if (mc == null) {
            throw new BusinessException("模型配置不存在: id=" + modelConfigId);
        }
        if (mc.getStatus() == null || mc.getStatus() != 1) {
            throw new BusinessException("模型未启用: " + mc.getModelName());
        }

        LlmApiKeyConfig akc = llmApiKeyConfigService.getById(mc.getApiKeyConfigId());
        if (akc == null) {
            throw new BusinessException("API-KEY 配置不存在: id=" + mc.getApiKeyConfigId());
        }
        if (akc.getStatus() == null || akc.getStatus() != 1) {
            throw new BusinessException("API-KEY 未启用: " + akc.getKeyName());
        }

        LlmVendorConfig vc = llmVendorConfigService.getById(akc.getVendorConfigId());
        if (vc == null) {
            throw new BusinessException("厂商配置不存在: id=" + akc.getVendorConfigId());
        }
        if (vc.getStatus() == null || vc.getStatus() != 1) {
            throw new BusinessException("厂商未启用: " + vc.getVendorName());
        }

        String apiKey = resolveApiKey(vc.getConfigKey());

        log.info("构建 ChatModel: vendor={}, model={}, endpoint={}, apiKeyPrefix={}, apiKeyLength={}",
                vc.getVendorCode(), mc.getModelName(), vc.getBaseUrl(),
                maskApiKey(apiKey), apiKey.length());

        // 当前所有厂商（智谱/DeepSeek/OpenAI）均走 OpenAI 兼容协议；
        // Ollama 待 langchain4j-ollama 依赖启用后再分支。
        //
        // 关键：langchain4j 1.0.0 的 OpenAiChatModel.timeout() 仅设置 HttpRequest 整体超时，
        // 但 JDK HttpClient 在长输出（如 8K+ tokens HTML 生成）场景下经常在 ~120s 触发 HttpTimeoutException，
        // 即便 timeout 设置为 300s 也未必生效（疑似版本 bug）。
        // 因此显式注入 JdkHttpClientBuilder，同时设置 connectTimeout 与 readTimeout，
        // 确保长输出场景下连接与读取都不会过早超时。
        JdkHttpClientBuilder httpClientBuilder = new JdkHttpClientBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
        return OpenAiChatModel.builder()
                .httpClientBuilder(httpClientBuilder)
                .baseUrl(vc.getBaseUrl())
                .apiKey(apiKey)
                .modelName(mc.getModelName())
                .temperature(mc.getTemperature() != null ? mc.getTemperature().doubleValue() : 0.7)
                .maxTokens(mc.getMaxOutputTokens() != null ? mc.getMaxOutputTokens() : 4096)
                .topP(mc.getTopP() != null ? mc.getTopP().doubleValue() : 1.0)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build();
    }

    /**
     * 构建 OpenAI 兼容协议的<b>流式</b> ChatModel。
     * <p>
     * 用于长输出场景（如 ToolNode 生成完整 HTML/CSS，maxOutputTokens ≥ 4K）：
     * 同步调用即便配置 readTimeout=300s 也容易在 ~120s 触发 HttpTimeoutException
     * （langchain4j 1.0.0 timeout 设置存在版本 bug）。流式调用以 SSE chunk 形式接收，
     * 每个 chunk 独立超时，本质规避整体 timeout。
     * </p>
     * <p>
     * 使用方需通过 {@link dev.langchain4j.model.chat.response.StreamingChatResponseHandler}
     * 异步接收 token 流；如需同步语义，调用方可用 {@link java.util.concurrent.CompletableFuture}
     * 在 onCompleteResponse 中完成 future。
     * </p>
     */
    public dev.langchain4j.model.chat.StreamingChatModel buildStreaming(Long modelConfigId) {
        LlmModelConfig mc = llmModelConfigService.getById(modelConfigId);
        if (mc == null) {
            throw new BusinessException("模型配置不存在: id=" + modelConfigId);
        }
        if (mc.getStatus() == null || mc.getStatus() != 1) {
            throw new BusinessException("模型未启用: " + mc.getModelName());
        }
        LlmApiKeyConfig akc = llmApiKeyConfigService.getById(mc.getApiKeyConfigId());
        if (akc == null) {
            throw new BusinessException("API-KEY 配置不存在: id=" + mc.getApiKeyConfigId());
        }
        if (akc.getStatus() == null || akc.getStatus() != 1) {
            throw new BusinessException("API-KEY 未启用: " + akc.getKeyName());
        }
        LlmVendorConfig vc = llmVendorConfigService.getById(akc.getVendorConfigId());
        if (vc == null) {
            throw new BusinessException("厂商配置不存在: id=" + akc.getVendorConfigId());
        }
        if (vc.getStatus() == null || vc.getStatus() != 1) {
            throw new BusinessException("厂商未启用: " + vc.getVendorName());
        }
        String apiKey = resolveApiKey(vc.getConfigKey());

        log.info("构建 StreamingChatModel: vendor={}, model={}, endpoint={}",
                vc.getVendorCode(), mc.getModelName(), vc.getBaseUrl());

        // 流式调用同样注入 JdkHttpClientBuilder 控制连接/读取超时
        JdkHttpClientBuilder httpClientBuilder = new JdkHttpClientBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                // 流式下 readTimeout 作用于单次 chunk 间隔，给 600s 兜底长生成场景
                .readTimeout(Duration.ofSeconds(600));
        return dev.langchain4j.model.openai.OpenAiStreamingChatModel.builder()
                .httpClientBuilder(httpClientBuilder)
                .baseUrl(vc.getBaseUrl())
                .apiKey(apiKey)
                .modelName(mc.getModelName())
                .temperature(mc.getTemperature() != null ? mc.getTemperature().doubleValue() : 0.7)
                .maxTokens(mc.getMaxOutputTokens() != null ? mc.getMaxOutputTokens() : 4096)
                .topP(mc.getTopP() != null ? mc.getTopP().doubleValue() : 1.0)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build();
    }

    /**
     * 通过 Spring Environment 解析 vendor.config_key 占位符得到真实 API Key。
     * <p>
     * 占位符约定为 {@code llm.vendor.xxx.key-n}：
     * <ul>
     *   <li>开发环境：application-dev.yml 提供默认值（如 {@code ${LLM_VENDOR_ZHIPU_KEY_1:f6de...}}）</li>
     *   <li>生产环境：通过 OS 环境变量 LLM_VENDOR_*_KEY_1 注入</li>
     * </ul>
     * </p>
     */
    private String resolveApiKey(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            throw new BusinessException("厂商 config_key 为空");
        }
        String placeholder = "${" + configKey + "}";
        try {
            String resolved = environment.resolveRequiredPlaceholders(placeholder);
            if (resolved == null || resolved.isBlank()) {
                // 占位符存在但值为空（如 ${LLM_VENDOR_DEEPSEEK_KEY_1:} 默认空字符串）
                // 给出明确的修复指引，避免被 ChatModelRegistry 静默回退遮蔽
                throw new BusinessException(
                        "API Key 解析为空: configKey=" + configKey
                                + "。请检查 OS 环境变量或 application-*.yml 的默认值。"
                                + "对应环境变量名约定: 把 configKey 中的点替换为下划线并大写，"
                                + "如 llm.vendor.deepseek.key-1 → LLM_VENDOR_DEEPSEEK_KEY_1");
            }
            return resolved;
        } catch (IllegalArgumentException e) {
            throw new BusinessException("API Key 占位符无法解析: " + configKey + " (" + e.getMessage() + ")");
        }
    }

    /** 脱敏：仅保留前 6 位与后 4 位用于诊断（如 f6de...DS2b） */
    private static String maskApiKey(String apiKey) {
        if (apiKey == null) return "null";
        int len = apiKey.length();
        if (len <= 10) return "***";
        return apiKey.substring(0, 6) + "..." + apiKey.substring(len - 4);
    }
}
