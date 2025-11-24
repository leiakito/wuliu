package com.example.demo.hardware.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class HardwarePriceBatchRequest {

    @NotEmpty(message = "请至少提供一条价格记录")
    @Valid
    private List<HardwarePriceRequest> items;
}
