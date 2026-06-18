package com.hypersense.boot.framework.agents.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 会话附件信息（上传后返回给前端，记录沙箱工作目录中的相对路径）。
 *
 * @author Claude
 * @since 2026/6/18
 */
@Data
@Builder
@Schema(description = "会话附件信息")
public class AttachmentVO {

    @Schema(description = "原始文件名（去重后可能带序号）")
    private String name;

    @Schema(description = "沙箱工作目录中的相对路径（如 uploads/report.pdf）")
    private String path;

    @Schema(description = "文件大小（字节）")
    private Long size;

    @Schema(description = "MIME 类型")
    private String mimeType;

    @Schema(description = "上传时间（ISO-8601）")
    private String uploadedAt;
}
