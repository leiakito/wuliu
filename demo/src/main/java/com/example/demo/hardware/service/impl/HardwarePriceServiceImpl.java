package com.example.demo.hardware.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.util.ExcelHelper;
import com.example.demo.hardware.dto.HardwarePriceQuery;
import com.example.demo.hardware.dto.HardwarePriceRequest;
import com.example.demo.hardware.entity.HardwarePrice;
import com.example.demo.hardware.mapper.HardwarePriceMapper;
import com.example.demo.hardware.service.HardwarePriceService;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class HardwarePriceServiceImpl implements HardwarePriceService {

    private final HardwarePriceMapper hardwarePriceMapper;

    @Override
    public List<HardwarePrice> list(HardwarePriceQuery query) {
        LambdaQueryWrapper<HardwarePrice> wrapper = new LambdaQueryWrapper<>();
        LocalDate start = query.getStartDate();
        LocalDate end = query.getEndDate();
        if (start != null) {
            wrapper.ge(HardwarePrice::getPriceDate, start);
        }
        if (end != null) {
            wrapper.le(HardwarePrice::getPriceDate, end);
        }
        if (StringUtils.hasText(query.getItemName())) {
            wrapper.like(HardwarePrice::getItemName, query.getItemName().trim());
        }
        wrapper.orderByDesc(HardwarePrice::getPriceDate).orderByAsc(HardwarePrice::getItemName);
        return hardwarePriceMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public HardwarePrice create(HardwarePriceRequest request, String operator) {
        validateUnique(request.getPriceDate(), request.getItemName(), null);
        HardwarePrice price = toEntity(request);
        price.setCreatedBy(operator);
        hardwarePriceMapper.insert(price);
        return price;
    }

    @Override
    @Transactional
    public HardwarePrice update(Long id, HardwarePriceRequest request) {
        HardwarePrice existed = hardwarePriceMapper.selectById(id);
        if (existed == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "记录不存在");
        }
        validateUnique(request.getPriceDate(), request.getItemName(), id);
        existed.setPriceDate(request.getPriceDate());
        existed.setItemName(normalizeItemName(request.getItemName()));
        existed.setPrice(request.getPrice());
        hardwarePriceMapper.updateById(existed);
        return existed;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        hardwarePriceMapper.deleteById(id);
    }

    @Override
    @Transactional
    public List<HardwarePrice> batchCreate(List<HardwarePriceRequest> requests, String operator) {
        if (CollectionUtils.isEmpty(requests)) {
            return List.of();
        }
        List<HardwarePrice> created = new ArrayList<>();
        for (HardwarePriceRequest request : requests) {
            validateUnique(request.getPriceDate(), request.getItemName(), null);
            HardwarePrice price = toEntity(request);
            price.setCreatedBy(operator);
            hardwarePriceMapper.insert(price);
            created.add(price);
        }
        return created;
    }

    @Override
    @Transactional
    public List<HardwarePrice> importExcel(LocalDate priceDate, MultipartFile file, String operator) {
        if (priceDate == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先选择日期");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请上传 Excel 文件");
        }
        try {
            List<HardwarePrice> rows = ExcelHelper.readHardwarePrices(file.getInputStream(), priceDate, operator);
            if (CollectionUtils.isEmpty(rows)) {
                return List.of();
            }
            Map<String, HardwarePrice> latestByItem = new LinkedHashMap<>();
            for (HardwarePrice row : rows) {
                String normalized = normalizeItemName(row.getItemName());
                if (StringUtils.hasText(normalized)) {
                    row.setItemName(normalized);
                    latestByItem.put(normalized, row);
                }
            }
            if (latestByItem.isEmpty()) {
                return List.of();
            }

            LambdaQueryWrapper<HardwarePrice> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(HardwarePrice::getPriceDate, priceDate);
            Map<String, HardwarePrice> existedMap = hardwarePriceMapper.selectList(wrapper).stream()
                .filter(h -> StringUtils.hasText(h.getItemName()))
                .collect(java.util.stream.Collectors.toMap(
                    h -> normalizeItemName(h.getItemName()),
                    h -> h,
                    (a, b) -> a,
                    LinkedHashMap::new
                ));

            List<HardwarePrice> result = new ArrayList<>();
            for (HardwarePrice item : latestByItem.values()) {
                HardwarePrice existed = existedMap.get(item.getItemName());
                if (existed != null) {
                    existed.setPrice(item.getPrice());
                    existed.setItemName(item.getItemName());
                    existed.setCreatedBy(operator);
                    hardwarePriceMapper.updateById(existed);
                    result.add(existed);
                } else {
                    item.setCreatedBy(operator);
                    hardwarePriceMapper.insert(item);
                    result.add(item);
                }
            }
            return result;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 解析失败");
        }
    }

    private void validateUnique(LocalDate date, String itemName, Long excludeId) {
        String normalized = normalizeItemName(itemName);
        LambdaQueryWrapper<HardwarePrice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HardwarePrice::getPriceDate, date)
            .eq(HardwarePrice::getItemName, normalized);
        if (excludeId != null) {
            wrapper.ne(HardwarePrice::getId, excludeId);
        }
        Long count = hardwarePriceMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE, "同一日期的该硬件已存在");
        }
    }

    private HardwarePrice toEntity(HardwarePriceRequest request) {
        HardwarePrice price = new HardwarePrice();
        price.setPriceDate(request.getPriceDate());
        price.setItemName(normalizeItemName(request.getItemName()));
        price.setPrice(request.getPrice());
        return price;
    }

    private String normalizeItemName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = raw
            .replace('\u00A0', ' ') // 去除不间断空格
            .replaceAll("\\s+", " ");
        return cleaned.trim();
    }
}
