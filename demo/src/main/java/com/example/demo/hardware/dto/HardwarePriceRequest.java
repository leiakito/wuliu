package com.example.demo.hardware.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class HardwarePriceRequest {

    @NotNull
    private LocalDate priceDate;
    @NotBlank
    private String itemName;
    private String category;
    @NotNull
    private BigDecimal price;
    private String remark;
}
