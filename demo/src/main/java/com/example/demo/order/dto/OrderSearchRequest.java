package com.example.demo.order.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class OrderSearchRequest {
    @NotEmpty
    private List<String> trackingNumbers;
}
