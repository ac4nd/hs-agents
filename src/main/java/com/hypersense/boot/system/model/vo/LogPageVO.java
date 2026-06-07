package com.hypersense.boot.system.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hypersense.boot.common.enums.LogModuleEnum;
import com.hypersense.boot.common.enums.ActionTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "系统日志分页Vo")
public class LogPageVO implements Serializable {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "模块")
    private LogModuleEnum module;

    @Schema(description = "操作类型")
    private ActionTypeEnum actionType;

    @Schema(description = "操作标题")
    private String title;

    @Schema(description = "自定义日志内容")
    private String content;

    @Schema(description = "操作人ID")
    private Long operatorId;

    @Schema(description = "操作人名称")
    private String operatorName;

    @Schema(description = "状态：0失败 1成功")
    private Integer status;

    @Schema(description = "请求路径")
    private String requestUri;

    @Schema(description = "请求方式")
    private String requestMethod;

    @Schema(description = "IP 地址")
    private String ip;

    @Schema(description = "地区")
    private String region;

    @Schema(description = "设备")
    private String device;

    @Schema(description = "操作系统")
    private String os;

    @Schema(description = "浏览器")
    private String browser;

    @Schema(description = "执行时间(毫秒)")
    private Integer executionTime;

    @Schema(description = "错误信息")
    private String errorMsg;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
