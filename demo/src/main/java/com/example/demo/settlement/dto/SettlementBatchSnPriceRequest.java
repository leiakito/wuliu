package com.example.demo.settlement.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class SettlementBatchSnPriceRequest {

    @NotEmpty
    private List<String> sns;

    @NotNull
    private BigDecimal amount;
}
