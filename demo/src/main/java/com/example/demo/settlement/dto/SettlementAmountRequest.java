package com.example.demo.settlement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class SettlementAmountRequest {

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal amount;

    private String remark;
}
