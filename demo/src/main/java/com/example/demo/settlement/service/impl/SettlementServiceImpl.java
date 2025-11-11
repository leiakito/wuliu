package com.example.demo.settlement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.util.ExcelHelper;
import com.example.demo.config.AppProperties;
import com.example.demo.order.entity.OrderRecord;
import com.example.demo.order.mapper.OrderRecordMapper;
import com.example.demo.settlement.dto.SettlementConfirmRequest;
import com.example.demo.settlement.dto.SettlementExportRequest;
import com.example.demo.settlement.dto.SettlementFilterRequest;
import com.example.demo.settlement.entity.SettlementRecord;
import com.example.demo.settlement.mapper.SettlementRecordMapper;
import com.example.demo.settlement.service.SettlementService;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final SettlementRecordMapper settlementRecordMapper;
    private final OrderRecordMapper orderRecordMapper;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public List<SettlementRecord> createPending(List<OrderRecord> orders, boolean warnDouble) {
        if (CollectionUtils.isEmpty(orders)) {
            return List.of();
        }
        Set<String> trackingNumbers = orders.stream()
            .map(OrderRecord::getTrackingNumber)
            .collect(Collectors.toSet());
        LambdaQueryWrapper<SettlementRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SettlementRecord::getTrackingNumber, trackingNumbers);
        List<SettlementRecord> existed = settlementRecordMapper.selectList(wrapper);
        Map<String, SettlementRecord> existedMap = existed.stream()
            .collect(Collectors.toMap(SettlementRecord::getTrackingNumber, r -> r, (a, b) -> a));
        List<SettlementRecord> created = new ArrayList<>();
        for (OrderRecord order : orders) {
            if (existedMap.containsKey(order.getTrackingNumber())) {
                // 二次结账警告
                SettlementRecord existedRecord = existedMap.get(order.getTrackingNumber());
                if (warnDouble && Boolean.FALSE.equals(existedRecord.getWarning())) {
                    existedRecord.setWarning(true);
                    settlementRecordMapper.updateById(existedRecord);
                }
                continue;
            }
            SettlementRecord record = new SettlementRecord();
            record.setOrderId(order.getId());
            record.setTrackingNumber(order.getTrackingNumber());
            record.setAmount(order.getAmount());
            record.setCurrency(order.getCurrency());
            record.setStatus("PENDING");
            record.setManualInput(false);
            record.setWarning(warnDouble && Boolean.TRUE.equals(order.isInCurrentSettlement()));
            record.setRemark(order.getRemark());
            settlementRecordMapper.insert(record);
            created.add(record);
        }
        attachOrderInfo(created);
        return created;
    }

    @Override
    public IPage<SettlementRecord> list(SettlementFilterRequest request) {
        Page<SettlementRecord> page = Page.of(request.getPage(), request.getSize());
        LambdaQueryWrapper<SettlementRecord> wrapper = new LambdaQueryWrapper<>();
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            wrapper.eq(SettlementRecord::getStatus, request.getStatus());
        }
        if (request.getBatch() != null && !request.getBatch().isBlank()) {
            wrapper.eq(SettlementRecord::getSettleBatch, request.getBatch());
        }
        if (request.getStartDate() != null) {
            wrapper.ge(SettlementRecord::getPayableAt, request.getStartDate());
        }
        if (request.getEndDate() != null) {
            wrapper.le(SettlementRecord::getPayableAt, request.getEndDate());
        }
        wrapper.orderByDesc(SettlementRecord::getCreatedAt);
        IPage<SettlementRecord> result = settlementRecordMapper.selectPage(page, wrapper);
        attachOrderInfo(result.getRecords());
        return result;
    }

    @Override
    @Transactional
    public void confirm(Long id, SettlementConfirmRequest request) {
        SettlementRecord record = settlementRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "待结账数据不存在");
        }
        record.setAmount(request.getAmount());
        record.setRemark(request.getRemark());
        record.setStatus("CONFIRMED");
        record.setSettleBatch("BATCH-" + LocalDate.now());
        record.setPayableAt(LocalDate.now());
        settlementRecordMapper.updateById(record);
    }

    @Override
    public byte[] export(SettlementExportRequest request) {
        LambdaQueryWrapper<SettlementRecord> wrapper = new LambdaQueryWrapper<>();
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            wrapper.eq(SettlementRecord::getStatus, request.getStatus());
        }
        if (request.getBatch() != null && !request.getBatch().isBlank()) {
            wrapper.eq(SettlementRecord::getSettleBatch, request.getBatch());
        }
        if (request.getStartDate() != null) {
            wrapper.ge(SettlementRecord::getPayableAt, request.getStartDate());
        }
        if (request.getEndDate() != null) {
            wrapper.le(SettlementRecord::getPayableAt, request.getEndDate());
        }
        if (!CollectionUtils.isEmpty(request.getTrackingNumbers())) {
            wrapper.in(SettlementRecord::getTrackingNumber, request.getTrackingNumbers());
        }
        wrapper.last("LIMIT " + appProperties.getExport().getMaxRows());
        List<SettlementRecord> records = settlementRecordMapper.selectList(wrapper);
        attachOrderInfo(records);
        try {
            return ExcelHelper.writeSettlements(records);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "导出失败");
        }
    }

    @Override
    @Transactional
    public void delete(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        settlementRecordMapper.deleteBatchIds(ids);
    }

    @Override
    @Transactional
    public void syncFromOrder(OrderRecord order) {
        if (order == null || order.getId() == null) {
            return;
        }
        LambdaQueryWrapper<SettlementRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SettlementRecord::getOrderId, order.getId());
        List<SettlementRecord> records = settlementRecordMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return;
        }
        records.forEach(record -> {
            if (order.getTrackingNumber() != null) {
                record.setTrackingNumber(order.getTrackingNumber());
            }
            record.setAmount(order.getAmount());
            record.setCurrency(order.getCurrency());
            if (order.getRemark() != null) {
                record.setRemark(order.getRemark());
            }
        });
        records.forEach(settlementRecordMapper::updateById);
    }

    private void attachOrderInfo(List<SettlementRecord> records) {
        if (CollectionUtils.isEmpty(records)) {
            return;
        }
        List<Long> orderIds = records.stream()
            .map(SettlementRecord::getOrderId)
            .filter(id -> id != null && id > 0)
            .collect(Collectors.toList());
        if (orderIds.isEmpty()) {
            return;
        }
        List<OrderRecord> orders = orderRecordMapper.selectBatchIds(orderIds);
        Map<Long, OrderRecord> map = orders.stream().collect(Collectors.toMap(OrderRecord::getId, o -> o));
        records.forEach(record -> {
            OrderRecord order = map.get(record.getOrderId());
            if (order != null) {
                record.setOrderStatus(order.getStatus());
                record.setOrderAmount(order.getAmount());
            }
        });
    }
}
