package com.example.demo.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.util.ExcelHelper;
import com.example.demo.order.dto.BatchFetchRequest;
import com.example.demo.order.dto.OrderAmountRequest;
import com.example.demo.order.dto.OrderCategoryStats;
import com.example.demo.order.dto.OrderCreateRequest;
import com.example.demo.order.dto.OrderFilterRequest;
import com.example.demo.order.dto.OrderUpdateRequest;
import com.example.demo.order.entity.OrderRecord;
import com.example.demo.order.mapper.OrderRecordMapper;
import com.example.demo.order.util.TrackingCategoryUtil;
import com.example.demo.order.service.OrderService;
import com.example.demo.settlement.service.SettlementService;
import com.example.demo.submission.entity.UserSubmission;
import com.example.demo.submission.mapper.UserSubmissionMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRecordMapper orderRecordMapper;
    private final SettlementService settlementService;
    private final UserSubmissionMapper userSubmissionMapper;

    @Override
    @Transactional
    public void importOrders(MultipartFile file, String operator) {
        try {
            List<OrderRecord> records = ExcelHelper.readOrders(file.getInputStream(), operator);
            List<OrderRecord> needSettlement = new ArrayList<>();
            for (OrderRecord record : records) {
                record.setImported(Boolean.TRUE);
                if (record.getTrackingNumber() != null) {
                    record.setCategory(TrackingCategoryUtil.resolve(record.getTrackingNumber()));
                }
                upsertBySn(record);
                if (hasSubmission(record.getTrackingNumber())) {
                    needSettlement.add(record);
                }
            }
            if (!needSettlement.isEmpty()) {
                settlementService.createPending(needSettlement, true);
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 解析失败");
        }
    }

    @Override
    public IPage<OrderRecord> query(OrderFilterRequest request) {
        Page<OrderRecord> page = Page.of(request.getPage(), request.getSize());
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
        if (request.getStartDate() != null) {
            wrapper.ge(OrderRecord::getOrderDate, request.getStartDate());
        }
        if (request.getEndDate() != null) {
            wrapper.le(OrderRecord::getOrderDate, request.getEndDate());
        }
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            wrapper.eq(OrderRecord::getCategory, request.getCategory());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            wrapper.eq(OrderRecord::getStatus, request.getStatus());
        }
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            wrapper.and(w -> w.like(OrderRecord::getTrackingNumber, request.getKeyword())
                .or().like(OrderRecord::getModel, request.getKeyword()));
        }
        wrapper.orderByDesc(OrderRecord::getOrderDate);
        return orderRecordMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional
    public OrderRecord create(OrderCreateRequest request, String operator) {
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderRecord::getSn, request.getSn());
        OrderRecord existed = orderRecordMapper.selectOne(wrapper);
        if (existed != null) {
            throw new BusinessException(ErrorCode.DUPLICATE, "SN 已存在");
        }
        OrderRecord record = new OrderRecord();
        record.setOrderDate(request.getOrderDate());
        if (record.getOrderDate() != null && request.getOrderTime() == null) {
            record.setOrderTime(request.getOrderDate().atStartOfDay());
        } else {
            record.setOrderTime(request.getOrderTime());
        }
        if (record.getOrderTime() == null) {
            record.setOrderTime(LocalDateTime.now());
        }
        if (record.getOrderDate() == null && record.getOrderTime() != null) {
            record.setOrderDate(record.getOrderTime().toLocalDate());
        }
        record.setTrackingNumber(request.getTrackingNumber());
        record.setModel(request.getModel());
        record.setSn(request.getSn());
        record.setRemark(request.getRemark());
        record.setCategory(TrackingCategoryUtil.resolve(record.getTrackingNumber()));
        record.setAmount(request.getAmount());
        record.setCurrency("CNY");
        record.setStatus("UNPAID");
        record.setCreatedBy(operator);
        record.setImported(Boolean.TRUE);
        orderRecordMapper.insert(record);
        if (hasSubmission(record.getTrackingNumber())) {
            settlementService.createPending(List.of(record), true);
        }
        return record;
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status) {
        OrderRecord record = orderRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        record.setStatus(status);
        orderRecordMapper.updateById(record);
    }

    @Override
    @Transactional
    public void updateAmount(Long id, OrderAmountRequest request) {
        OrderRecord record = orderRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        record.setAmount(request.getAmount());
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            record.setCurrency(request.getCurrency());
        }
        if (request.getRemark() != null) {
            record.setRemark(request.getRemark());
        }
        orderRecordMapper.updateById(record);
    }

    @Override
    public List<OrderRecord> findByTracking(List<String> trackingNumbers) {
        if (CollectionUtils.isEmpty(trackingNumbers)) {
            return List.of();
        }
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
        var unique = trackingNumbers.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(str -> !str.isEmpty())
            .collect(Collectors.toSet());
        if (unique.isEmpty()) {
            return List.of();
        }
        wrapper.in(OrderRecord::getTrackingNumber, unique);
        return orderRecordMapper.selectList(wrapper);
    }

    @Override
    @CacheEvict(value = "orderDetail", allEntries = true)
    @Transactional
    public List<OrderRecord> syncFromThirdParty(BatchFetchRequest request, String operator) {
        if (request == null || CollectionUtils.isEmpty(request.getTrackingNumbers())) {
            return List.of();
        }
        List<OrderRecord> existing = findByTracking(request.getTrackingNumbers());
        List<String> existNumbers = existing.stream()
            .map(OrderRecord::getTrackingNumber)
            .collect(Collectors.toList());

        List<OrderRecord> created = new ArrayList<>();
        for (String tracking : request.getTrackingNumbers()) {
            if (existNumbers.contains(tracking)) {
                continue;
            }
            OrderRecord record = new OrderRecord();
            record.setTrackingNumber(tracking);
            record.setOrderDate(LocalDate.now());
            record.setOrderTime(LocalDateTime.now());
            record.setCategory(TrackingCategoryUtil.resolve(record.getTrackingNumber()));
            record.setStatus("UNPAID");
            BigDecimal autoAmount = request.getManualAmount() != null ? request.getManualAmount() : BigDecimal.ZERO;
            record.setAmount(autoAmount);
            record.setCurrency("CNY");
            record.setRemark("自动抓取生成");
            record.setCreatedBy(operator);
            record.setSn(tracking);
            record.setImported(Boolean.TRUE);
            orderRecordMapper.insert(record);
            existNumbers.add(tracking);
            created.add(record);
        }
        if (!created.isEmpty()) {
            settlementService.createPending(created, true);
        }
        return created;
    }

    @Override
    @Transactional
    public OrderRecord update(Long id, OrderUpdateRequest request) {
        OrderRecord record = orderRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (StringUtils.hasText(request.getTrackingNumber())) {
            record.setTrackingNumber(request.getTrackingNumber().trim());
            record.setCategory(TrackingCategoryUtil.resolve(record.getTrackingNumber()));
        }
        if (StringUtils.hasText(request.getModel())) {
            record.setModel(request.getModel().trim());
        }
        if (StringUtils.hasText(request.getSn())) {
            record.setSn(request.getSn().trim());
        }
        if (request.getAmount() != null) {
            record.setAmount(request.getAmount());
        }
        if (StringUtils.hasText(request.getStatus())) {
            record.setStatus(request.getStatus());
        }
        if (request.getRemark() != null) {
            record.setRemark(request.getRemark());
        }
        record.setCurrency("CNY");
        orderRecordMapper.updateById(record);
        settlementService.syncFromOrder(record);
        return record;
    }

    @Override
    public List<OrderCategoryStats> listCategoryStats(OrderFilterRequest request) {
        QueryWrapper<OrderRecord> wrapper = new QueryWrapper<>();
        if (request.getStartDate() != null) {
            wrapper.ge("order_date", request.getStartDate());
        }
        if (request.getEndDate() != null) {
            wrapper.le("order_date", request.getEndDate());
        }
        if (StringUtils.hasText(request.getCategory())) {
            wrapper.eq("category", request.getCategory());
        }
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq("status", request.getStatus());
        }
        if (StringUtils.hasText(request.getKeyword())) {
            wrapper.and(w -> w.like("tracking_number", request.getKeyword())
                .or().like("model", request.getKeyword()));
        }
        wrapper.select("COALESCE(category, '未分配') AS category_name", "COUNT(*) AS total_count");
        wrapper.groupBy("COALESCE(category, '未分配')");
        List<Map<String, Object>> rows = orderRecordMapper.selectMaps(wrapper);
        return rows.stream().map(row -> {
            OrderCategoryStats stats = new OrderCategoryStats();
            Object nameObj = row.get("category_name");
            stats.setCategory(nameObj == null ? "未分配" : nameObj.toString());
            Object countObj = row.get("total_count");
            stats.setCount(countObj == null ? 0 : ((Number) countObj).longValue());
            return stats;
        }).collect(Collectors.toList());
    }

    private void upsertBySn(OrderRecord incoming) {
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderRecord::getSn, incoming.getSn());
        OrderRecord existed = orderRecordMapper.selectOne(wrapper);
        if (incoming.getOrderDate() == null && incoming.getOrderTime() != null) {
            incoming.setOrderDate(incoming.getOrderTime().toLocalDate());
        }
        if (incoming.getOrderTime() == null && incoming.getOrderDate() != null) {
            incoming.setOrderTime(incoming.getOrderDate().atStartOfDay());
        }
        incoming.setImported(Boolean.TRUE);
        if (existed == null) {
            orderRecordMapper.insert(incoming);
        } else {
            incoming.setId(existed.getId());
            orderRecordMapper.updateById(incoming);
        }
    }

    private boolean hasSubmission(String trackingNumber) {
        if (!StringUtils.hasText(trackingNumber)) {
            return false;
        }
        LambdaQueryWrapper<UserSubmission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSubmission::getTrackingNumber, trackingNumber.trim())
            .ne(UserSubmission::getStatus, "COMPLETED");
        return userSubmissionMapper.selectCount(wrapper) > 0;
    }
}
