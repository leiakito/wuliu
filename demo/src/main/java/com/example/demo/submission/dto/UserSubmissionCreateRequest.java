package com.example.demo.submission.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserSubmissionCreateRequest {

    @NotBlank
    private String trackingNumber;

    /**
     * 管理员可指定的归属用户名；普通用户忽略。
     */
    private String username;
}
