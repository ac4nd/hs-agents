package com.hypersense.boot.framework.agents.serializer;

import com.hypersense.boot.agents.service.AgentService;
import com.hypersense.boot.framework.agents.sandbox.Sandbox;
import com.hypersense.boot.framework.agents.sandbox.SandboxManager;
import com.hypersense.boot.system.model.entity.LlmModelConfig;
import com.hypersense.boot.system.service.LlmModelConfigService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 附件 → 多模态 UserMessage 的统一构造器。
 * <p>
 * 作用：把 {@code AgentServiceImpl.buildMultimodalUserMessage} 中沉在私有方法里的能力
 * 提取到独立 Bean，供 {@code PlanNode} / {@code FinalizeNode} / {@code ExecuteNode}
 * 等不接 tools 的节点在调 LLM 前按需附加图片，避免「附件仅存在于首条 UserMessage、
 * 被节点纯文本重拼丢弃」的链路断点。
 * </p>
 * <p>
 * 设计原则：
 * <ul>
 *   <li>任何异常均通过 {@link Optional#empty()} 兜底，调用方降级为纯文本路径</li>
 *   <li>不依赖 state，仅依赖 sessionId + 附件路径列表（节点上下文无关）</li>
 *   <li>对图片上限、单图大小做校验，超限返回 empty 走降级（与 AgentServiceImpl 保持一致）</li>
 * </ul>
 * </p>
 *
 * @author Claude
 * @since 2026/6/24
 */
@Slf4j
@Component
public class AttachmentContext {

    /** 单条消息图片上限（与 AgentServiceImpl.MAX_IMAGES_PER_MESSAGE 对齐） */
    private static final int MAX_IMAGES_PER_MESSAGE = 3;
    /** 单图字节上限：5MB（base64 编码后约 6.7MB，控制在 LLM 厂商常规上限内） */
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private final SandboxManager sandboxManager;
    private final AgentService agentService;
    private final LlmModelConfigService modelConfigService;

    @Autowired
    public AttachmentContext(@Nullable SandboxManager sandboxManager,
                             @Nullable @Lazy AgentService agentService,
                             @Nullable LlmModelConfigService modelConfigService) {
        this.sandboxManager = sandboxManager;
        this.agentService = agentService;
        this.modelConfigService = modelConfigService;
    }

    /**
     * 按当前会话模型能力 + 附件路径，构造多模态 UserMessage。
     * <p>
     * 触发多模态的硬性条件（任一不满足则返回 empty）：
     * <ol>
     *   <li>attachmentPaths 含图片扩展名</li>
     *   <li>当前会话绑定模型 {@code supports_vision=1}</li>
     *   <li>沙箱可读且图片数/单图大小未超限</li>
     * </ol>
     * </p>
     *
     * @param sessionId 会话 ID（用于沙箱 + 模型能力查询）
     * @param text 作为 TextContent 的文本（节点 prompt）
     * @param attachmentPaths 沙箱相对路径列表
     * @return 多模态 UserMessage；不可用时返回 empty（调用方降级为纯文本）
     */
    public Optional<UserMessage> buildMultimodal(@Nullable String sessionId,
                                                 String text,
                                                 @Nullable List<String> attachmentPaths) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        List<String> imagePaths = filterImageAttachments(attachmentPaths);
        if (imagePaths.isEmpty() || !supportsVision(sessionId)) {
            return Optional.empty();
        }
        if (imagePaths.size() > MAX_IMAGES_PER_MESSAGE) {
            log.warn("[AttachmentContext] 图片数 {} 超过上限 {}，降级为纯文本 sessionId={}",
                    imagePaths.size(), MAX_IMAGES_PER_MESSAGE, sessionId);
            return Optional.empty();
        }

        try {
            Sandbox sandbox = sandboxManager.getOrCreate(sessionId);
            List<Content> contents = new ArrayList<>(imagePaths.size() + 1);
            contents.add(TextContent.from(text == null ? "" : text));
            for (String path : imagePaths) {
                byte[] bytes = sandbox.readAllBytes(path);
                if (bytes == null || bytes.length == 0) {
                    log.warn("[AttachmentContext] 图片读取为空，跳过 sessionId={} path={}", sessionId, path);
                    continue;
                }
                if (bytes.length > MAX_IMAGE_BYTES) {
                    log.warn("[AttachmentContext] 图片超过 {}MB 上限，跳过 path={} size={}KB",
                            MAX_IMAGE_BYTES / 1024 / 1024, path, bytes.length / 1024);
                    continue;
                }
                String base64 = Base64.getEncoder().encodeToString(bytes);
                String mimeType = guessImageMimeType(path);
                contents.add(ImageContent.from(base64, mimeType));
                log.info("[AttachmentContext] 附加图片 path={} size={}KB mimeType={}",
                        path, bytes.length / 1024, mimeType);
            }
            // 全部图片读取失败 → 降级
            if (contents.size() <= 1) {
                log.warn("[AttachmentContext] 全部图片读取失败，降级为纯文本 sessionId={}", sessionId);
                return Optional.empty();
            }
            return Optional.of(UserMessage.from(contents));
        } catch (Exception e) {
            log.warn("[AttachmentContext] 多模态构造失败，降级纯文本 sessionId={}: {}",
                    sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 执行带多模态降级的 LLM 调用。
     * <p>
     * 若 LLM 端返回 4xx 且错误信息含 image_url / image / vision / multimodal 等关键字，
     * 判定为「模型实际不支持视觉输入」→ 把消息列表中所有 UserMessage 替换为纯文本
     * （取 fallbackText 构造）后重试一次，保证对话链路不挂掉。
     * </p>
     * <p>
     * 非视觉相关异常原样抛出，交由上层节点处理。
     * </p>
     *
     * @param chatModel 当前会话绑定的模型
     * @param messages 原始消息列表（可能含多模态 UserMessage）
     * @param sessionId 会话 ID（仅用于日志）
     * @param fallbackText 降级时用于替换多模态 UserMessage 的纯文本
     */
    public ChatResponse chatWithVisionFallback(ChatModel chatModel,
                                               List<ChatMessage> messages,
                                               @Nullable String sessionId,
                                               String fallbackText) {
        try {
            return chatModel.chat(messages);
        } catch (RuntimeException e) {
            if (!isVisionRejection(e) || fallbackText == null) {
                throw e;
            }
            log.warn("[AttachmentContext] LLM 拒绝多模态请求，降级为纯文本重试 sessionId={} reason={}",
                    sessionId, firstLine(e.getMessage()));
            List<ChatMessage> fallback = messages.stream()
                    .map(m -> m instanceof UserMessage ? UserMessage.from(fallbackText) : m)
                    .collect(Collectors.toList());
            return chatModel.chat(fallback);
        }
    }

    /** 判断异常是否为「模型不支持视觉」类拒绝（OpenAI 兼容协议常见错误特征） */
    private static boolean isVisionRejection(Throwable e) {
        if (e == null) return false;
        String msg = e.getMessage();
        if (msg != null) {
            String low = msg.toLowerCase(Locale.ROOT);
            if (low.contains("image_url")
                    || low.contains("multimodal")
                    || low.contains("image content")
                    || low.contains("does not support image")
                    || low.contains("does not support vision")) {
                return true;
            }
        }
        // 兼容 cause 链（如 langchain4j 把异常包装在 ExecutionException 里）
        return isVisionRejection(e.getCause());
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int nl = s.indexOf('\n');
        return nl > 0 ? s.substring(0, Math.min(nl, 200)) : s.substring(0, Math.min(s.length(), 200));
    }

    /**
     * 判断当前会话绑定的模型是否支持视觉输入。
     */
    public boolean supportsVision(@Nullable String sessionId) {
        if (agentService == null || modelConfigService == null || sessionId == null || sessionId.isBlank()) {
            return false;
        }
        try {
            Long modelConfigId = agentService.getSessionModelConfigId(sessionId);
            if (modelConfigId == null) return false;
            LlmModelConfig mc = modelConfigService.getById(modelConfigId);
            return mc != null && Integer.valueOf(1).equals(mc.getSupportsVision());
        } catch (Exception e) {
            log.warn("[AttachmentContext] supportsVision 查询失败 sessionId={}: {}", sessionId, e.getMessage());
            return false;
        }
    }

    /** 按扩展名白名单筛选图片路径 */
    public List<String> filterImageAttachments(@Nullable List<String> attachmentPaths) {
        if (attachmentPaths == null || attachmentPaths.isEmpty()) {
            return List.of();
        }
        List<String> images = new ArrayList<>();
        for (String p : attachmentPaths) {
            if (p == null) continue;
            String ext = extractExtension(p);
            if (isImageExt(ext)) images.add(p);
        }
        return images;
    }

    private static boolean isImageExt(String ext) {
        return switch (ext) {
            case "png", "jpg", "jpeg", "gif", "webp", "bmp" -> true;
            default -> false;
        };
    }

    private static String guessImageMimeType(String path) {
        return switch (extractExtension(path)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "application/octet-stream";
        };
    }

    private static String extractExtension(String path) {
        if (path == null) return "";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
