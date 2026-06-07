package com.hypersense.boot.message.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 字典变更事件
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DictChangeEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String dictCode;
    private long timestamp;

    public DictChangeEvent(String dictCode) {
        this.dictCode = dictCode;
        this.timestamp = System.currentTimeMillis();
    }
}
