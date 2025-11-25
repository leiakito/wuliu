package com.example.demo.settlement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.util.ExcelHelper;
import com.example.demo.config.AppProperties;
import com.example.demo.hardware.entity.HardwarePrice;
import com.example.demo.hardware.mapper.HardwarePriceMapper;
import com.example.demo.order.entity.OrderRecord;
import com.example.demo.order.mapper.OrderRecordMapper;
import com.example.demo.submission.entity.UserSubmission;
import com.example.demo.submission.mapper.UserSubmissionMapper;
import com.example.demo.settlement.dto.SettlementAmountRequest;
import com.example.demo.settlement.dto.SettlementBatchConfirmRequest;
import com.example.demo.settlement.dto.SettlementBatchPriceRequest;
import com.example.demo.settlement.dto.SettlementConfirmRequest;
import com.example.demo.settlement.dto.SettlementExportRequest;
import com.example.demo.settlement.dto.SettlementFilterRequest;
import com.example.demo.settlement.entity.SettlementRecord;
import com.example.demo.settlement.mapper.SettlementRecordMapper;
import com.example.demo.settlement.service.SettlementService;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final SettlementRecordMapper settlementRecordMapper;
    private final OrderRecordMapper orderRecordMapper;
    private final HardwarePriceMapper hardwarePriceMapper;
    private final UserSubmissionMapper userSubmissionMapper;
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
        Map<String, String> ownerMap = resolveOwnerByTracking(trackingNumbers);
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
            record.setModel(order.getModel());
            BigDecimal price = resolveHardwarePrice(order);
            BigDecimal amount = price != null ? price : order.getAmount();
            record.setAmount(amount);
            record.setCurrency(order.getCurrency() != null ? order.getCurrency() : "CNY");
            record.setStatus("PENDING");
            record.setManualInput(false);
            record.setWarning(warnDouble && Boolean.TRUE.equals(order.isInCurrentSettlement()));
            record.setRemark(order.getRemark());
            record.setOwnerUsername(ownerMap.getOrDefault(order.getTrackingNumber(), order.getCreatedBy()));
            record.setOrderTime(order.getOrderTime());
            settlementRecordMapper.insert(record);
            created.add(record);

            if (price != null) {
                order.setAmount(price);
                if (order.getCurrency() == null) {
                    order.setCurrency("CNY");
                }
                orderRecordMapper.updateById(order);
            }
        }
        attachOrderInfo(created);
        return created;
    }

    private BigDecimal resolveHardwarePrice(OrderRecord order) {
        if (order == null || !StringUtils.hasText(order.getModel())) {
            return null;
        }
        LocalDate date = order.getOrderDate();
        if (date == null && order.getOrderTime() != null) {
            date = order.getOrderTime().toLocalDate();
        }
        if (date == null) {
            return null;
        }
        String target = normalizeItemName(order.getModel());
        if (!StringUtils.hasText(target)) {
            return null;
        }
        LambdaQueryWrapper<HardwarePrice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HardwarePrice::getPriceDate, date);
        List<HardwarePrice> prices = hardwarePriceMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(prices)) {
            return null;
        }
        for (HardwarePrice price : prices) {
            String name = normalizeItemName(price.getItemName());
            if (!StringUtils.hasText(name)) {
                continue;
            }
            if (target.equalsIgnoreCase(name) || target.contains(name) || name.contains(target)) {
                return price.getPrice();
            }
        }
        return null;
    }

    @Override
    public IPage<SettlementRecord> list(SettlementFilterRequest request) {
        LambdaQueryWrapper<SettlementRecord> wrapper = new LambdaQueryWrapper<>();
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            wrapper.eq(SettlementRecord::getStatus, request.getStatus());
        }
        if (request.getBatch() != null && !request.getBatch().isBlank()) {
            wrapper.eq(SettlementRecord::getSettleBatch, request.getBatch());
        }
        if (StringUtils.hasText(request.getModel())) {
            wrapper.like(SettlementRecord::getModel, request.getModel().trim());
        }
        if (StringUtils.hasText(request.getTrackingNumber())) {
            wrapper.like(SettlementRecord::getTrackingNumber, request.getTrackingNumber().trim());
        }
        if (StringUtils.hasText(request.getKeyword())) {
            String keyword = request.getKeyword().trim();
            Set<String> matchTracking = new HashSet<>();
            LambdaQueryWrapper<OrderRecord> orderWrapper = new LambdaQueryWrapper<>();
            orderWrapper.like(OrderRecord::getTrackingNumber, keyword)
                .or().like(OrderRecord::getModel, keyword)
                .or().like(OrderRecord::getSn, keyword);
            List<OrderRecord> matchedOrders = orderRecordMapper.selectList(orderWrapper);
            if (!CollectionUtils.isEmpty(matchedOrders)) {
                matchTracking.addAll(matchedOrders.stream()
                    .map(OrderRecord::getTrackingNumber)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet()));
            }
            wrapper.and(w -> {
                w.like(SettlementRecord::getTrackingNumber, keyword)
                    .or().like(SettlementRecord::getModel, keyword);
                if (!matchTracking.isEmpty()) {
                    w.or().in(SettlementRecord::getTrackingNumber, matchTracking);
                }
            });
        }
        if (request.getStartDate() != null) {
            wrapper.ge(SettlementRecord::getPayableAt, request.getStartDate());
        }
        if (request.getEndDate() != null) {
            wrapper.le(SettlementRecord::getPayableAt, request.getEndDate());
        }
        if (StringUtils.hasText(request.getOwnerUsername())) {
            wrapper.eq(SettlementRecord::getOwnerUsername, request.getOwnerUsername().trim());
        }
        wrapper.orderByDesc(SettlementRecord::getCreatedAt);
        Page<SettlementRecord> page = Page.of(request.getPage(), request.getSize());
        IPage<SettlementRecord> result = settlementRecordMapper.selectPage(page, wrapper);
        attachOrderInfo(result.getRecords());
        return result;
    }

    @Override
    @Transactional
    public void confirm(Long id, SettlementConfirmRequest request, String operator) {
        SettlementRecord record = settlementRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "待结账数据不存在");
        }
        BigDecimal targetAmount = request.getAmount();
        if (targetAmount == null) {
            targetAmount = record.getAmount();
        }
        OrderRecord order = null;
        if (targetAmount == null) {
            order = loadOrder(record);
            if (order != null) {
                targetAmount = order.getAmount();
            }
        }
        if (targetAmount == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先设置金额");
        }
        record.setAmount(targetAmount);
        record.setRemark(request.getRemark());
        record.setStatus("CONFIRMED");
        record.setSettleBatch("BATCH-" + LocalDate.now());
        record.setPayableAt(LocalDate.now());
        record.setConfirmedBy(operator);
        record.setConfirmedAt(LocalDateTime.now());
        settlementRecordMapper.updateById(record);
        updateOrderWithSettlement(record, targetAmount, order);
        markSubmissionCompleted(record.getTrackingNumber());
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
        if (StringUtils.hasText(request.getOwnerUsername())) {
            wrapper.eq(SettlementRecord::getOwnerUsername, request.getOwnerUsername().trim());
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
    public int updateAmountByModel(SettlementBatchPriceRequest request) {
        String model = request.getModel().trim();
        LambdaQueryWrapper<SettlementRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SettlementRecord::getModel, model);
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(SettlementRecord::getStatus, request.getStatus().trim());
        }
        if (StringUtils.hasText(request.getBatch())) {
            wrapper.eq(SettlementRecord::getSettleBatch, request.getBatch().trim());
        }
        if (request.getStartDate() != null) {
            wrapper.ge(SettlementRecord::getPayableAt, request.getStartDate());
        }
        if (request.getEndDate() != null) {
            wrapper.le(SettlementRecord::getPayableAt, request.getEndDate());
        }
        if (StringUtils.hasText(request.getOwnerUsername())) {
            wrapper.eq(SettlementRecord::getOwnerUsername, request.getOwnerUsername().trim());
        }
        List<SettlementRecord> records = settlementRecordMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return 0;
        }
        Set<Long> targetOrderIds = records.stream()
            .map(SettlementRecord::getOrderId)
            .filter(id -> id != null && id > 0)
            .collect(Collectors.toSet());
        if (!targetOrderIds.isEmpty()) {
            LambdaUpdateWrapper<OrderRecord> orderUpdate = Wrappers.lambdaUpdate();
            orderUpdate.in(OrderRecord::getId, targetOrderIds);
            orderUpdate.set(OrderRecord::getAmount, request.getAmount());
            orderRecordMapper.update(null, orderUpdate);
        }
        // fallback using tracking numbers without order ids
        Set<String> pendingTracking = records.stream()
            .filter(record -> record.getOrderId() == null || record.getOrderId() <= 0)
            .map(SettlementRecord::getTrackingNumber)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());
        if (!pendingTracking.isEmpty()) {
            LambdaUpdateWrapper<OrderRecord> trackingUpdate = Wrappers.lambdaUpdate();
            trackingUpdate.in(OrderRecord::getTrackingNumber, pendingTracking);
            trackingUpdate.set(OrderRecord::getAmount, request.getAmount());
            orderRecordMapper.update(null, trackingUpdate);
        }
        records.forEach(record -> {
            record.setAmount(request.getAmount());
            settlementRecordMapper.updateById(record);
        });
        return records.size();
    }

    @Override
    @Transactional
    public void confirmBatch(SettlementBatchConfirmRequest request, String operator) {
        if (CollectionUtils.isEmpty(request.getIds())) {
            return;
        }
        for (Long id : request.getIds()) {
            SettlementConfirmRequest payload = new SettlementConfirmRequest();
            payload.setAmount(request.getAmount());
            payload.setRemark(request.getRemark());
            confirm(id, payload, operator);
        }
    }

    @Override
    @Transactional
    public void updateAmount(Long id, SettlementAmountRequest request) {
        SettlementRecord record = settlementRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "待结账数据不存在");
        }
        BigDecimal amount = request.getAmount();
        record.setAmount(amount);
        if (request.getRemark() != null) {
            record.setRemark(request.getRemark());
        }
        settlementRecordMapper.updateById(record);
        updateOrderAmount(record, amount);
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
            record.setModel(order.getModel());
            if (order.getAmount() != null) {
                record.setAmount(order.getAmount());
            } else if (record.getAmount() == null || BigDecimal.ZERO.compareTo(record.getAmount()) == 0) {
                BigDecimal price = resolveHardwarePrice(order);
                if (price != null) {
                    record.setAmount(price);
                    order.setAmount(price);
                }
            }
            record.setCurrency(order.getCurrency());
            if (order.getRemark() != null) {
                record.setRemark(order.getRemark());
            }
        });
        records.forEach(settlementRecordMapper::updateById);
        if (order.getAmount() != null) {
            orderRecordMapper.updateById(order);
        }
    }

    private void attachOrderInfo(List<SettlementRecord> records) {
        if (CollectionUtils.isEmpty(records)) {
            return;
        }
        List<Long> orderIds = records.stream()
            .map(SettlementRecord::getOrderId)
            .filter(id -> id != null && id > 0)
            .collect(Collectors.toList());
        Map<Long, OrderRecord> orderMap;
        if (!orderIds.isEmpty()) {
            List<OrderRecord> orders = orderRecordMapper.selectBatchIds(orderIds);
            orderMap = orders.stream().collect(Collectors.toMap(OrderRecord::getId, o -> o));
        } else {
            orderMap = Collections.emptyMap();
        }
        List<String> trackingNumbers = records.stream()
            .map(SettlementRecord::getTrackingNumber)
            .filter(StringUtils::hasText)
            .collect(Collectors.toList());
        Map<String, List<String>> submissionMap = new HashMap<>();
        if (!trackingNumbers.isEmpty()) {
            LambdaQueryWrapper<UserSubmission> submissionWrapper = new LambdaQueryWrapper<>();
            submissionWrapper.select(UserSubmission::getTrackingNumber, UserSubmission::getUsername, UserSubmission::getOwnerUsername);
            submissionWrapper.in(UserSubmission::getTrackingNumber, trackingNumbers);
            List<UserSubmission> submissions = userSubmissionMapper.selectList(submissionWrapper);
            submissions.forEach(submission -> {
                if (!StringUtils.hasText(submission.getTrackingNumber())) {
                    return;
                }
                if (!StringUtils.hasText(submission.getOwnerUsername()) && !StringUtils.hasText(submission.getUsername())) {
                    return;
                }
                List<String> users = submissionMap.computeIfAbsent(submission.getTrackingNumber(), key -> new ArrayList<>());
                if (StringUtils.hasText(submission.getUsername()) && !users.contains(submission.getUsername())) {
                    users.add(submission.getUsername());
                }
            });
        }
        records.forEach(record -> {
            if (!StringUtils.hasText(record.getOwnerUsername())) {
                String owner = resolveOwnerByTracking(Set.of(record.getTrackingNumber())).get(record.getTrackingNumber());
                if (StringUtils.hasText(owner)) {
                    record.setOwnerUsername(owner);
                }
            }
            OrderRecord order = orderMap.get(record.getOrderId());
            if (order != null) {
                record.setOrderStatus(order.getStatus());
                record.setOrderAmount(order.getAmount() != null ? order.getAmount() : record.getAmount());
                record.setOrderCreatedBy(order.getCreatedBy());
                if (!StringUtils.hasText(record.getOwnerUsername())) {
                    record.setOwnerUsername(order.getCreatedBy());
                }
                record.setOrderTime(record.getOrderTime() != null ? record.getOrderTime() : order.getOrderTime());
                record.setOrderSn(order.getSn());
                if (!StringUtils.hasText(record.getModel()) && StringUtils.hasText(order.getModel())) {
                    record.setModel(order.getModel());
                }
                if ((record.getManualInput() == null || Boolean.FALSE.equals(record.getManualInput()))
                    && (record.getAmount() == null || BigDecimal.ZERO.compareTo(record.getAmount()) == 0)) {
                    BigDecimal price = resolveHardwarePrice(order);
                    if (price != null) {
                        record.setAmount(price);
                        record.setOrderAmount(price);
                        order.setAmount(price);
                        orderRecordMapper.updateById(order);
                        settlementRecordMapper.updateById(record);
                    }
                }
            }
            List<String> submissionUsers = submissionMap.get(record.getTrackingNumber());
            record.setSubmissionUsers(submissionUsers == null ? Collections.emptyList() : submissionUsers);
        });
    }

    private Map<String, String> resolveOwnerByTracking(Set<String> trackingNumbers) {
        if (CollectionUtils.isEmpty(trackingNumbers)) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<UserSubmission> submissionWrapper = new LambdaQueryWrapper<>();
        submissionWrapper.select(UserSubmission::getTrackingNumber, UserSubmission::getOwnerUsername, UserSubmission::getUsername);
        submissionWrapper.in(UserSubmission::getTrackingNumber, trackingNumbers);
        List<UserSubmission> submissions = userSubmissionMapper.selectList(submissionWrapper);
        Map<String, String> map = new HashMap<>();
        submissions.forEach(submission -> {
            if (!StringUtils.hasText(submission.getTrackingNumber())) {
                return;
            }
            String owner = StringUtils.hasText(submission.getOwnerUsername())
                ? submission.getOwnerUsername()
                : submission.getUsername();
            if (StringUtils.hasText(owner)) {
                map.putIfAbsent(submission.getTrackingNumber(), owner);
            }
        });
        return map;
    }

    private IPage<SettlementRecord> emptyPage(SettlementFilterRequest request) {
        Page<SettlementRecord> empty = Page.of(request.getPage(), request.getSize());
        empty.setTotal(0);
        empty.setRecords(List.of());
        return empty;
    }

    private byte[] emptyExport() {
        try {
            return ExcelHelper.writeSettlements(List.of());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "导出失败");
        }
    }

    private void updateOrderWithSettlement(SettlementRecord record, BigDecimal amount, OrderRecord existingOrder) {
        OrderRecord order = existingOrder;
        if (order == null && record.getOrderId() != null) {
            order = orderRecordMapper.selectById(record.getOrderId());
        }
        if (order == null && record.getTrackingNumber() != null && !record.getTrackingNumber().isBlank()) {
            LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(OrderRecord::getTrackingNumber, record.getTrackingNumber().trim())
                .orderByDesc(OrderRecord::getOrderTime)
                .orderByDesc(OrderRecord::getCreatedAt)
                .last("LIMIT 1");
            order = orderRecordMapper.selectOne(wrapper);
        }
        if (order == null) {
            return;
        }
        order.setStatus("PAID");
        if (amount != null) {
            order.setAmount(amount);
        }
        orderRecordMapper.updateById(order);
    }

    private void updateOrderAmount(SettlementRecord record, BigDecimal amount) {
        if (amount == null) {
            return;
        }
        OrderRecord order = loadOrder(record);
        if (order == null) {
            return;
        }
        order.setAmount(amount);
        orderRecordMapper.updateById(order);
    }

    private OrderRecord loadOrder(SettlementRecord record) {
        if (record.getOrderId() != null) {
            OrderRecord order = orderRecordMapper.selectById(record.getOrderId());
            if (order != null) {
                return order;
            }
        }
        if (record.getTrackingNumber() != null && !record.getTrackingNumber().isBlank()) {
            LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(OrderRecord::getTrackingNumber, record.getTrackingNumber().trim())
                .orderByDesc(OrderRecord::getOrderTime)
                .orderByDesc(OrderRecord::getCreatedAt)
                .last("LIMIT 1");
            return orderRecordMapper.selectOne(wrapper);
        }
        return null;
    }

    private void markSubmissionCompleted(String trackingNumber) {
        if (!StringUtils.hasText(trackingNumber)) {
            return;
        }
        LambdaQueryWrapper<SettlementRecord> pendingQuery = new LambdaQueryWrapper<>();
        pendingQuery.eq(SettlementRecord::getTrackingNumber, trackingNumber)
            .ne(SettlementRecord::getStatus, "CONFIRMED");
        Long remaining = settlementRecordMapper.selectCount(pendingQuery);
        if (remaining != null && remaining > 0) {
            return;
        }
        LambdaQueryWrapper<UserSubmission> query = new LambdaQueryWrapper<>();
        query.eq(UserSubmission::getTrackingNumber, trackingNumber)
            .ne(UserSubmission::getStatus, "COMPLETED");
        List<UserSubmission> submissions = userSubmissionMapper.selectList(query);
        if (submissions.isEmpty()) {
            return;
        }
        submissions.forEach(submission -> {
            submission.setStatus("COMPLETED");
            submission.setUpdatedAt(LocalDateTime.now());
            userSubmissionMapper.updateById(submission);
        });
    }

    private String normalizeItemName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String cleaned = raw
            .replace('\u00A0', ' ')
            .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]", "")
            .toUpperCase();
        return cleaned.trim();
    }
}
