package com.example.demo.hardware.dto;

import com.example.demo.hardware.entity.HardwarePrice;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class HardwarePriceExcelParseResult {

    private int totalRows;
    private List<HardwarePrice> rows = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    public void addError(String message) {
        this.errors.add(message);
    }
}
