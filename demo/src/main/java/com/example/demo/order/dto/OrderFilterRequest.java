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
    private String sortBy;      // 排序字段，如 status, orderDate 等
    private String sortOrder;   // 排序方向：asc 或 desc
}
