package com.example.demo.settlement.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class SettlementFilterRequest {

    private String status;
    private String batch;
    private String ownerUsername;
    private String model;
    private String trackingNumber;
    private String keyword;
    private LocalDate startDate;
    private LocalDate endDate;
    private long page = 1;
    private long size = 20;
}
