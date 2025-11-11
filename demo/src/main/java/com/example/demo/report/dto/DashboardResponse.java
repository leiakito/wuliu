package com.example.demo.report.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class DashboardResponse {
    private long orderCount;
    private long waitingSettlementCount;
    private BigDecimal totalAmount;
    private BigDecimal pendingAmount;
}
