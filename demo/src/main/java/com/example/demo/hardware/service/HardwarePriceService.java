package com.example.demo.hardware.service;

import com.example.demo.hardware.dto.HardwarePriceQuery;
import com.example.demo.hardware.dto.HardwarePriceRequest;
import com.example.demo.hardware.entity.HardwarePrice;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface HardwarePriceService {

    List<HardwarePrice> list(HardwarePriceQuery query);

    HardwarePrice create(HardwarePriceRequest request, String operator);

    HardwarePrice update(Long id, HardwarePriceRequest request);

    void delete(Long id);

    List<HardwarePrice> batchCreate(List<HardwarePriceRequest> requests, String operator);

    List<HardwarePrice> importExcel(LocalDate priceDate, MultipartFile file, String operator);
}
