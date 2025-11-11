package com.example.demo.order.dto;

import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class BatchFetchRequest {

    @NotEmpty
    private List<String> trackingNumbers;
    private BigDecimal manualAmount;
}
