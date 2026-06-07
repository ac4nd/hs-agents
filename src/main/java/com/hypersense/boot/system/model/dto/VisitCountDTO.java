package com.hypersense.boot.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 访问计数数据传输对象
 *
 * @author Ray.Hao
 * @since 2.10.0
 */
@Schema(description = "访问计数数据传输对象")
@Data
public class VisitCountDTO {

    @Schema(description = "日期 yyyy-MM-dd")
    private String date;

    @Schema(description = "访问次数")
    private Integer count;
}
