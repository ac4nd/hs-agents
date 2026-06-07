package com.hypersense.boot.framework.agents.config;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
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

    @Data
    public static class LlmConfig {

        /**
         * LLM 提供商：openai / ollama
         */
        private String provider = "openai";

        /**
         * 温度（0.0 - 2.0）
         */
        private Double temperature = 0.7;

        /**
         * 最大生成 token 数
         */
        private Integer maxTokens = 4096;

        /**
         * OpenAI 兼容 API 配置
         */
        private OpenAiConfig openai = new OpenAiConfig();

        /**
         * Ollama 本地模型配置
         */
        private OllamaConfig ollama = new OllamaConfig();
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
}
