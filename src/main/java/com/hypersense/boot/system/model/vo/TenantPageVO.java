package com.hypersense.boot.system.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "租户分页对象")
@Data
public class TenantPageVO implements Serializable {

    @Schema(description = "租户ID")
    private Long id;

    @Schema(description = "租户名称")
    private String name;

    @Schema(description = "租户编码")
    private String code;

    @Schema(description = "联系人姓名")
    private String contactName;

    @Schema(description = "联系人电话")
    private String contactPhone;

    @Schema(description = "联系人邮箱")
    private String contactEmail;

    @Schema(description = "租户域名")
    private String domain;

    @Schema(description = "租户Logo")
    private String logo;

    @Schema(description = "套餐ID")
    private Long planId;

    @Schema(description = "状态(1-正常 0-禁用)")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "过期时间（NULL表示永不过期）")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
