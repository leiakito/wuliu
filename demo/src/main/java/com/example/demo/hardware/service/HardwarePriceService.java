package com.example.demo.hardware.service;

import com.example.demo.hardware.dto.HardwarePriceQuery;
import com.example.demo.hardware.dto.HardwarePriceRequest;
import com.example.demo.hardware.entity.HardwarePrice;
import java.util.List;

public interface HardwarePriceService {

    List<HardwarePrice> list(HardwarePriceQuery query);

    HardwarePrice create(HardwarePriceRequest request, String operator);

    HardwarePrice update(Long id, HardwarePriceRequest request);

    void delete(Long id);
}
