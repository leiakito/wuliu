package com.example.demo.order.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class OrderFilterRequest {

    private LocalDate startDate;
    private LocalDate endDate;
    private String category;
    private String status;
    private String keyword;
    private long page = 1;
    private long size = 20;
}
