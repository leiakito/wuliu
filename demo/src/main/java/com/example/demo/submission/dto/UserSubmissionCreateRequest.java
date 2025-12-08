package com.example.demo.submission.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.Data;

@Data
public class UserSubmissionCreateRequest {

    @NotBlank
    private String trackingNumber;

    /**
     * 管理员可指定的归属用户名；普通用户忽略。
     */
    private String username;

    /**
     * 订单日期，用于匹配特定日期的订单（如中文单号按日期区分）
     */
    private LocalDate orderDate;
}
