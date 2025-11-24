package com.example.demo.hardware.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class HardwarePriceQuery {
    private LocalDate startDate;
    private LocalDate endDate;
    private String itemName;
}
