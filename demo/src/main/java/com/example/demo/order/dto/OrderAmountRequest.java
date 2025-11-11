package com.example.demo.order.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class OrderAmountRequest {
    @NotNull
    private BigDecimal amount;
    private String currency;
    private String remark;
}
