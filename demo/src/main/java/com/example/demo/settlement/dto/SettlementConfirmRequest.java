package com.example.demo.settlement.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class SettlementConfirmRequest {

    @NotNull
    private BigDecimal amount;
    private String remark;
}
