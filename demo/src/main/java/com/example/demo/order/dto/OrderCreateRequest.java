package com.example.demo.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class OrderCreateRequest {

    private LocalDate orderDate;
    @NotBlank
    @Size(max = 64)
    private String trackingNumber;
    private String model;
    @NotBlank
    @Size(max = 64)
    private String sn;
    private String remark;
    private String category;
    private BigDecimal amount;
    private String currency = "CNY";
    private String customerName;
    private LocalDateTime orderTime;
}
