package com.example.demo.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class SettlementBatchPriceRequest {

    @NotBlank
    private String model;

    @NotNull
    private BigDecimal amount;

    private String status;

    private String batch;

    private String ownerUsername;

    private LocalDate startDate;

    private LocalDate endDate;
}
