package com.example.demo.settlement.dto;

import java.util.List;
import lombok.Data;

@Data
public class SettlementBatchSnPriceResponse {

    private int updatedCount;

    private List<String> skippedSns;

    public SettlementBatchSnPriceResponse(int updatedCount, List<String> skippedSns) {
        this.updatedCount = updatedCount;
        this.skippedSns = skippedSns;
    }
}
