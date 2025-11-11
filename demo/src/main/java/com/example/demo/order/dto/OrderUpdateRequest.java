package com.example.demo.order.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class OrderUpdateRequest {
    private String trackingNumber;
    private String model;
    private String sn;
    private BigDecimal amount;
    private String status;
    private String remark;
}
