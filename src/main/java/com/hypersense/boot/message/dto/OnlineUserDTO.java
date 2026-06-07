package com.hypersense.boot.message.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 在线用户信息DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlineUserDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String username;
    private int sessionCount;
    private long loginTime;
}
