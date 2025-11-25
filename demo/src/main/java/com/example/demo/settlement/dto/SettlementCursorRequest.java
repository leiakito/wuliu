package com.example.demo.settlement.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class SettlementCursorRequest {

    private String status;
    private String batch;
    private String ownerUsername;
    private String model;
    private String trackingNumber;
    private String orderSn;
    private String keyword;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long lastId; // 游标：上一页最后一条记录的 ID
    private long size = 20;
    private String sortProp;
    private String sortOrder;
}
