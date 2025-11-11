package com.example.demo.settlement.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class SettlementGenerateRequest {

    @NotEmpty
    private List<String> trackingNumbers;
}
