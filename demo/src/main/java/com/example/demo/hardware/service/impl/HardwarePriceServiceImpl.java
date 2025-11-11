package com.example.demo.hardware.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.hardware.dto.HardwarePriceQuery;
import com.example.demo.hardware.dto.HardwarePriceRequest;
import com.example.demo.hardware.entity.HardwarePrice;
import com.example.demo.hardware.mapper.HardwarePriceMapper;
import com.example.demo.hardware.service.HardwarePriceService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
        if (StringUtils.hasText(query.getCategory())) {
            wrapper.eq(HardwarePrice::getCategory, query.getCategory());
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
        existed.setItemName(request.getItemName());
        existed.setCategory(StringUtils.hasText(request.getCategory()) ? request.getCategory().trim() : null);
        existed.setPrice(request.getPrice());
        existed.setRemark(request.getRemark());
        hardwarePriceMapper.updateById(existed);
        return existed;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        hardwarePriceMapper.deleteById(id);
    }

    private void validateUnique(LocalDate date, String itemName, Long excludeId) {
        LambdaQueryWrapper<HardwarePrice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HardwarePrice::getPriceDate, date)
            .eq(HardwarePrice::getItemName, itemName);
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
        price.setItemName(StringUtils.trimWhitespace(request.getItemName()));
        price.setCategory(StringUtils.hasText(request.getCategory()) ? request.getCategory().trim() : null);
        price.setPrice(request.getPrice());
        price.setRemark(request.getRemark());
        return price;
    }
}
