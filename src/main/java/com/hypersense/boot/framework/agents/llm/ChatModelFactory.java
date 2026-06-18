package com.hypersense.boot.framework.agents.llm;

import com.hypersense.boot.common.exception.BusinessException;
import com.hypersense.boot.system.model.entity.LlmApiKeyConfig;
import com.hypersense.boot.system.model.entity.LlmModelConfig;
import com.hypersense.boot.system.model.entity.LlmVendorConfig;
import com.hypersense.boot.system.service.LlmApiKeyConfigService;
import com.hypersense.boot.system.service.LlmModelConfigService;
import com.hypersense.boot.system.service.LlmVendorConfigService;
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

    private static final long DEFAULT_TIMEOUT_SECONDS = 120L;

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
        return OpenAiChatModel.builder()
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
