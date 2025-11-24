package com.example.demo.hardware.dto;

import com.example.demo.hardware.entity.HardwarePrice;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class HardwarePriceImportResult {

    private String fileName;
    private LocalDate priceDate;
    private boolean success;
    private String message;
    private int successCount;
    private int insertedCount;
    private int updatedCount;
    private int skippedCount;
    private int totalRows;
    private long durationMillis;
    private List<String> errors = new ArrayList<>();
    private List<HardwarePrice> records = new ArrayList<>();
}
