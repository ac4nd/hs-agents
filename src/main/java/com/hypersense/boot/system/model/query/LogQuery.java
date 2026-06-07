package com.hypersense.boot.system.model.query;

import com.hypersense.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Schema(description = "日志分页查询对象")
@Getter
@Setter
public class LogQuery extends BaseQuery {

    @Schema(description="关键字(标题/内容/IP/操作人)")
    private String keywords;

    @Schema(description="操作时间范围")
    List<String> createTime;

}
