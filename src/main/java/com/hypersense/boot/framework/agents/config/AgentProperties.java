package com.hypersense.boot.framework.agents.config;

import lombok.Data;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI Agent 配置属性
 *
 * @author Claude
 * @since 2026/5/15
 */
@Configuration
@ConfigurationProperties(prefix = "agent")
@Data
public class AgentProperties {

    /**
     * LLM 配置
     */
    private LlmConfig llm = new LlmConfig();

    /**
     * Deep Agent 配置
     */
    private DeepConfig deep = new DeepConfig();

    /**
     * 工具配置
     */
    private ToolsConfig tools = new ToolsConfig();

    /**
     * HITL（Human-in-the-Loop）配置
     */
    private HitlConfig hitl = new HitlConfig();

    /**
     * 技能系统配置
     */
    private SkillsConfig skills = new SkillsConfig();

    /**
     * 长期记忆配置
     */
    private MemoryConfig memory = new MemoryConfig();

    /**
     * 会话内多轮上下文配置（history 滑窗 + 滚动摘要）
     */
    private HistoryConfig history = new HistoryConfig();

    @Data
    public static class LlmConfig {

        /**
         * LLM 提供商（向后兼容字段，新代码请改用 chat-vendor / embedding-vendor）
         */
        private String provider = "openai";

        /**
         * Chat 模型使用的厂商，对应 vendors Map 的 key
         */
        private String chatVendor = "zhipu";

        /**
         * Embedding 模型使用的厂商，对应 vendors Map 的 key
         */
        private String embeddingVendor = "bailian";

        /**
         * 温度（0.0 - 2.0）
         */
        private Double temperature = 0.7;

        /**
         * 最大生成 token 数
         */
        private Integer maxTokens = 4096;

        /**
         * 多厂商配置：key 为厂商名（zhipu / bailian / deepseek / openai / ollama 等），
         * value 为 OpenAI 兼容 API 的连接参数
         */
        private Map<String, VendorConfig> vendors = new LinkedHashMap<>();

        /**
         * OpenAI 兼容 API 配置（向后兼容字段；新配置请填入 vendors）
         */
        private OpenAiConfig openai = new OpenAiConfig();

        /**
         * Ollama 本地模型配置（向后兼容字段；新配置请填入 vendors）
         */
        private OllamaConfig ollama = new OllamaConfig();

        /**
         * 根据 vendor 名取厂商配置；优先 vendors，未命中时回退到 openai/ollama 兼容字段。
         */
        public VendorConfig resolveVendor(String vendorName) {
            if (vendorName == null || vendorName.isBlank()) {
                vendorName = "openai";
            }
            VendorConfig v = vendors.get(vendorName);
            if (v != null) {
                return v;
            }
            // 向后兼容：把旧的 openai/ollama 字段包装成 VendorConfig
            if ("ollama".equalsIgnoreCase(vendorName)) {
                return new VendorConfig(ollama.getEndpoint(), null, ollama.getModelName(), null);
            }
            return new VendorConfig(openai.getEndpoint(), openai.getApiKey(), openai.getModelName(), null);
        }
    }

    /**
     * 单个 LLM/Embedding 厂商配置（OpenAI 兼容协议）
     */
    @Data
    public static class VendorConfig {

        /**
         * API 基地址（不含 /chat/completions 或 /embeddings 后缀）
         */
        private String endpoint;

        /**
         * API Key
         */
        private String apiKey;

        /**
         * Chat 模型名
         */
        private String chatModel;

        /**
         * Embedding 模型名
         */
        private String embeddingModel;

        public VendorConfig() {}

        public VendorConfig(String endpoint, String apiKey, String chatModel, String embeddingModel) {
            this.endpoint = endpoint;
            this.apiKey = apiKey;
            this.chatModel = chatModel;
            this.embeddingModel = embeddingModel;
        }
    }

    @Data
    public static class OpenAiConfig {

        /**
         * OpenAI 兼容 API 地址
         */
        private String endpoint = "https://api.openai.com/v1";

        /**
         * API 密钥
         */
        private String apiKey;

        /**
         * 模型名称
         */
        private String modelName = "gpt-4o";
    }

    @Data
    public static class OllamaConfig {

        /**
         * Ollama API 地址
         */
        private String endpoint = "http://localhost:11434";

        /**
         * 模型名称
         */
        private String modelName = "llama3";
    }

    @Data
    public static class DeepConfig {

        /**
         * 图递归限制
         */
        private Integer recursionLimit = 50;

        /**
         * 最大迭代次数
         */
        private Integer maxIterations = 25;

        /**
         * 是否启用检查点持久化
         */
        private Boolean checkpointEnabled = true;

        /**
         * 会话 TTL（秒），默认 30 分钟
         */
        private Long sessionTtl = 1800L;
    }

    @Data
    public static class ToolsConfig {

        /**
         * 网络搜索工具配置
         */
        private SearchConfig search = new SearchConfig();

        /**
         * 沙箱工具配置
         */
        private SandboxConfig sandbox = new SandboxConfig();

        /**
         * 工具重试配置
         */
        private ToolRetryConfig toolRetry = new ToolRetryConfig();
    }

    @Data
    public static class SearchConfig {

        /**
         * 是否启用网络搜索
         */
        private Boolean enabled = false;

        /**
         * 搜索 API 地址（SearXNG / SerpAPI 等）
         */
        private String endpoint;

        /**
         * API 密钥（可选）
         */
        private String apiKey;

        /**
         * 最大返回条数
         */
        private Integer maxResults = 5;
    }

    @Data
    public static class SandboxConfig {

        /**
         * 是否启用沙箱
         */
        private Boolean enabled = false;

        /**
         * 沙箱类型：local / remote / custom
         */
        private String type = "local";

        /**
         * 执行超时（秒）
         */
        private Integer timeout = 30;

        /**
         * 最大输出字节数（超出截断）
         */
        private Integer maxOutputBytes = 65536;

        /**
         * 允许的编程语言白名单（逗号分隔）
         * <p>
         * 支持：python, javascript, shell
         * </p>
         */
        private String allowedLanguages = "python,javascript,shell";

        /**
         * 工作目录（文件操作的根路径）
         */
        private String workDir;

        /**
         * 本地沙箱配置
         */
        private LocalSandboxConfig local = new LocalSandboxConfig();

        /**
         * 远程沙箱配置
         */
        private RemoteSandboxConfig remote = new RemoteSandboxConfig();

        /**
         * 自定义容器沙箱配置
         */
        private CustomSandboxConfig custom = new CustomSandboxConfig();
    }

    @Data
    public static class LocalSandboxConfig {

        /**
         * 是否清理环境变量，仅保留最小 PATH
         */
        private Boolean sanitizeEnv = true;
    }

    @Data
    public static class RemoteSandboxConfig {

        /**
         * 服务提供商：modal / daytona / runloop
         */
        private String provider = "modal";

        /**
         * API 端点
         */
        private String endpoint;

        /**
         * API 密钥
         */
        private String apiKey;

        /**
         * 沙箱实例超时（秒）
         */
        private Integer instanceTimeout = 300;
    }

    @Data
    public static class CustomSandboxConfig {

        /**
         * 容器运行时：docker / podman
         */
        private String runtime = "docker";

        /**
         * 镜像名称（默认使用 GodlikeAgents 多语言沙箱镜像）
         */
        private String image = "godlikeagents/sandbox:1.0.0";

        /**
         * 内存限制（如 512m）
         */
        private String memoryLimit = "512m";

        /**
         * CPU 限制（核心数）
         */
        private Double cpuLimit = 1.0;

        /**
         * Docker/Podman socket 路径（null = 自动检测）
         * <p>
         * Docker 默认: unix:///var/run/docker.sock
         * Podman rootless: unix:///run/user/{uid}/podman/podman.sock
         * </p>
         */
        private String socketPath;

        /**
         * 容器网络模式（默认 none 禁用网络，安全隔离）
         */
        private String networkMode = "none";

        /**
         * 容器内工作目录
         */
        private String workspacePath = "/workspace";

        /**
         * 宿主机卷挂载基础路径（null = 不挂载卷，使用容器临时存储）
         * <p>
         * 每个会话的隔离路径：{volumeBasePath}/{sessionId}/workspace
         * 映射到容器内 {workspacePath}
         * </p>
         */
        private String volumeBasePath;

        /**
         * 容器退出后自动删除
         */
        private Boolean autoRemove = true;

        /**
         * 安全选项（如 no-new-privileges）
         */
        private java.util.List<String> securityOpts = java.util.List.of("no-new-privileges");

        /**
         * 进程数限制
         */
        private Integer pidsLimit = 100;
    }

    /**
     * HITL（Human-in-the-Loop）配置
     */
    @Data
    public static class HitlConfig {

        /** 全局开关（默认关闭） */
        private Boolean enabled = false;

        /** 默认中断节点列表 */
        private List<String> interruptNodes = List.of("tool");

        /** 是否在委派节点也触发中断 */
        private Boolean interruptOnDelegate = false;

        /** 最大中断次数（0 = 无限制） */
        private Integer maxInterrupts = 0;

        /** 智能 HITL Gate 配置（基于 LLM 判断） */
        private SmartGateConfig smartGate = new SmartGateConfig();
    }

    /**
     * 智能 HITL Gate 配置
     * <p>
     * 在指定决策点调用 LLM，根据「歧义 / 风险 / 改动巨大」三个维度判断是否需要用户确认。
     * </p>
     */
    @Data
    public static class SmartGateConfig {

        /** 是否启用智能门控（默认关闭，开启后才会调用 LLM 判断） */
        private Boolean enabled = false;

        /** 启用的决策点（plan_completed / before_todo_execute） */
        private List<String> decisionPoints = List.of("plan_completed", "before_todo_execute");

        /** severity=low 时是否自动放行不中断（默认 true） */
        private Boolean autoSkipLow = true;
    }

    /**
     * 技能系统配置
     */
    @Data
    public static class SkillsConfig {

        /**
         * 技能目录列表（支持多个目录）
         * <p>
         * 每个目录下的子文件夹如果包含 SKILL.md 文件，会被识别为一个技能。
         * 通过 Spring Profile 切换不同目录实现技能包切换。
         * </p>
         */
        private List<String> dirs = new ArrayList<>();
    }

    /**
     * 工具重试配置（Spring Boot 配置绑定）
     * <p>
     * 运行时通过 {@link com.hypersense.boot.framework.agents.config.ToolRetryConfig#fromProperties} 转换为框架配置。
     * </p>
     */
    @Data
    public static class ToolRetryConfig {

        /** 是否启用工具重试（默认关闭） */
        private Boolean enabled = false;

        /** 最大尝试次数（含首次调用），默认 3 */
        private Integer maxAttempts = 3;

        /** 初始退避延迟（毫秒），默认 1000 */
        private Long initialDelayMs = 1000L;

        /** 最大退避延迟（毫秒），默认 30000 */
        private Long maxDelayMs = 30000L;

        /** 退避倍数，默认 2.0 */
        private Double backoffMultiplier = 2.0;
    }

    /**
     * 长期记忆配置（基于智谱 Embedding-3 + pgvector）
     */
    @Data
    public static class MemoryConfig {

        /** 是否启用长期记忆（默认关闭） */
        private Boolean enabled = false;

        /** Embedding 模型名称 */
        private String embeddingModel = "embedding-3";

        /** Embedding 向量维度（512/768/1024/1536，pgvector 索引上限 2000 维） */
        private Integer embeddingDimensions = 1536;

        /** 记忆保留天数 */
        private Integer retentionDays = 90;

        /** 向量相似度阈值（0.0-1.0，低于此阈值不返回） */
        private Double similarityThreshold = 0.7;

        /** 最大检索记忆条数 */
        private Integer maxRetrievalCount = 10;

        /** 事实提取最小消息数（少于则不提取） */
        private Integer minMessagesForExtraction = 4;
    }

    /**
     * 会话内多轮上下文配置
     * <p>
     * 控制 AgentSessionVO.history 的滑窗大小与滚动摘要行为，
     * 避免长会话中 history 全量拼接导致 prompt token 线性增长。
     * </p>
     */
    @Data
    public static class HistoryConfig {
        /** 会话内多轮上下文最多保留的最近消息条数（超出则进入待摘要区） */
        private Integer maxRecentMessages = 10;

        /** 是否启用 LLM 滚动摘要（关闭则仅滑窗丢弃，不调用 LLM） */
        private Boolean enableSummary = true;

        /** 待摘要消息累计到此条数时触发一次 LLM 摘要 */
        private Integer summaryTriggerThreshold = 4;

        /** 摘要的目标最大字符数（写入 prompt 的限制） */
        private Integer summaryMaxChars = 500;
    }
}
