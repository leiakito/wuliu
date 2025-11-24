package com.example.demo.settlement.dto;

import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class SettlementBatchConfirmRequest {

    @NotEmpty
    private List<Long> ids;

    private BigDecimal amount;

    private String remark;
}
