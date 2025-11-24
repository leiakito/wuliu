package com.example.demo.settlement.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class SettlementExportRequest {
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String batch;
    private String ownerUsername;
    private List<String> trackingNumbers;
}
