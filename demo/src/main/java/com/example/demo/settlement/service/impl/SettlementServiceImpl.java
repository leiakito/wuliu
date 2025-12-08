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
import com.example.demo.order.entity.OrderCellStyle;
import com.example.demo.order.mapper.OrderCellStyleMapper;
import com.example.demo.submission.entity.UserSubmission;
import com.example.demo.submission.mapper.UserSubmissionMapper;
import com.example.demo.settlement.dto.SettlementAmountRequest;
import com.example.demo.settlement.dto.SettlementBatchConfirmRequest;
import com.example.demo.settlement.dto.SettlementBatchPriceRequest;
import com.example.demo.settlement.dto.SettlementBatchSnPriceRequest;
import com.example.demo.settlement.dto.SettlementBatchSnPriceResponse;
import com.example.demo.settlement.dto.SettlementConfirmRequest;
import com.example.demo.settlement.dto.SettlementCursorRequest;
import com.example.demo.settlement.dto.SettlementExportRequest;
import com.example.demo.settlement.dto.SettlementFilterRequest;
import com.example.demo.settlement.entity.SettlementRecord;
import com.example.demo.settlement.mapper.SettlementRecordMapper;
import com.example.demo.settlement.service.SettlementCacheService;
import com.example.demo.settlement.service.SettlementService;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final SettlementRecordMapper settlementRecordMapper;
    private final OrderRecordMapper orderRecordMapper;
    private final HardwarePriceMapper hardwarePriceMapper;
    private final UserSubmissionMapper userSubmissionMapper;
    private final AppProperties appProperties;
    private final SettlementCacheService cacheService;
    private final OrderCellStyleMapper orderCellStyleMapper;

    // 内部类：保存提交人和归属人信息
    private static class SubmissionInfo {
        String submitterUsername;
        String ownerUsername;

        SubmissionInfo(String submitterUsername, String ownerUsername) {
            this.submitterUsername = submitterUsername;
            this.ownerUsername = ownerUsername;
        }
    }

    @Override
    @Transactional
    public List<SettlementRecord> createPending(List<OrderRecord> orders, boolean warnDouble) {
        if (CollectionUtils.isEmpty(orders)) {
            return List.of();
        }
        Set<String> trackingNumbers = orders.stream()
            .map(OrderRecord::getTrackingNumber)
            .collect(Collectors.toSet());
        Set<Long> orderIds = orders.stream()
            .map(OrderRecord::getId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        
        // 按 order_id 查询已存在的结账记录（用于判断是否重复）
        Map<Long, SettlementRecord> existedByOrderIdMap = new HashMap<>();
        if (!orderIds.isEmpty()) {
            LambdaQueryWrapper<SettlementRecord> orderIdWrapper = new LambdaQueryWrapper<>();
            orderIdWrapper.in(SettlementRecord::getOrderId, orderIds);
            List<SettlementRecord> existedByOrderId = settlementRecordMapper.selectList(orderIdWrapper);
            existedByOrderIdMap = existedByOrderId.stream()
                .filter(r -> r.getOrderId() != null)
                .collect(Collectors.toMap(SettlementRecord::getOrderId, r -> r, (a, b) -> a));
        }
        
        // 同时按 trackingNumber 查询，用于二次结账警告（同一单号在不同订单中出现）
        LambdaQueryWrapper<SettlementRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SettlementRecord::getTrackingNumber, trackingNumbers);
        List<SettlementRecord> existed = settlementRecordMapper.selectList(wrapper);
        Map<String, SettlementRecord> existedByTrackingMap = existed.stream()
            .collect(Collectors.toMap(SettlementRecord::getTrackingNumber, r -> r, (a, b) -> a));
        
        Map<String, SubmissionInfo> submissionInfoMap = resolveSubmissionInfo(trackingNumbers);
        List<SettlementRecord> created = new ArrayList<>();
        for (OrderRecord order : orders) {
            // 按 order_id 判断是否已存在（同一订单不重复创建）
            if (order.getId() != null && existedByOrderIdMap.containsKey(order.getId())) {
                // 该订单已有结账记录，更新警告标记
                SettlementRecord existedRecord = existedByOrderIdMap.get(order.getId());
                if (warnDouble && Boolean.FALSE.equals(existedRecord.getWarning())) {
                    existedRecord.setWarning(true);
                    settlementRecordMapper.updateById(existedRecord);
                }
                continue;
            }
            
            // 检查是否有相同单号的其他订单已结账（二次结账警告）
            boolean hasOtherSettlement = existedByTrackingMap.containsKey(order.getTrackingNumber());
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
            // 如果同单号已有其他结账记录，或订单本身标记为当前结算中，则设置警告
            record.setWarning(warnDouble && (hasOtherSettlement || Boolean.TRUE.equals(order.isInCurrentSettlement())));
            record.setRemark(order.getRemark());
            // 设置归属人和提交人信息
            SubmissionInfo info = submissionInfoMap.get(order.getTrackingNumber());
            if (info != null) {
                record.setOwnerUsername(info.ownerUsername);
                record.setSubmitterUsername(info.submitterUsername);
            } else {
                record.setOwnerUsername(order.getCreatedBy());
                record.setSubmitterUsername(null);
            }
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

        // 尝试从缓存获取
        BigDecimal cachedPrice = cacheService.getHardwarePrice(target, date);
        if (cachedPrice != null) {
            return cachedPrice;
        }

        // 缓存未命中，查询数据库
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
                BigDecimal result = price.getPrice();
                // 写入缓存
                cacheService.cacheHardwarePrice(target, date, result);
                return result;
            }
        }
        return null;
    }

    @Override
    public IPage<SettlementRecord> listByCursor(SettlementCursorRequest request, String username, String role) {
        LambdaQueryWrapper<SettlementRecord> wrapper = buildQueryWrapper(request, username, role);

        // 游标分页：基于 ID 的范围查询，性能不受页数影响
        if (request.getLastId() != null && request.getLastId() > 0) {
            // 根据排序方向决定使用 lt 还是 gt
            boolean isAsc = "ascending".equalsIgnoreCase(request.getSortOrder());
            if (isAsc) {
                wrapper.gt(SettlementRecord::getId, request.getLastId());
            } else {
                wrapper.lt(SettlementRecord::getId, request.getLastId());
            }
        }

        // 应用排序
        applySorting(wrapper, request.getSortProp(), request.getSortOrder());

        // 限制查询数量
        wrapper.last("LIMIT " + request.getSize());

        log.info("游标分页查询 - lastId: {}, size: {}", request.getLastId(), request.getSize());

        List<SettlementRecord> records = settlementRecordMapper.selectList(wrapper);

        // 查询总数（仅在首次查询时）
        Long total = 0L;
        if (request.getLastId() == null) {
            LambdaQueryWrapper<SettlementRecord> countWrapper = buildQueryWrapper(request, username, role);
            total = settlementRecordMapper.selectCount(countWrapper);
        }

        log.info("游标分页结果: 返回{}条记录, 总数{}", records.size(), total);

        attachOrderInfo(records);

        // 构造分页结果
        Page<SettlementRecord> page = new Page<>();
        page.setRecords(records);
        page.setTotal(total);
        page.setSize(request.getSize());
        return page;
    }

    @Override
    public IPage<SettlementRecord> list(SettlementFilterRequest request, String username, String role) {
        LambdaQueryWrapper<SettlementRecord> wrapper = buildQueryWrapper(request, username, role);
        applySorting(wrapper, request.getSortProp(), request.getSortOrder());

        log.info("执行查询前的wrapper条件数: {}", wrapper.getExpression().getNormal().size());

        Page<SettlementRecord> page = Page.of(request.getPage(), request.getSize());
        IPage<SettlementRecord> result = settlementRecordMapper.selectPage(page, wrapper);

        log.info("查询结果: 返回{}条记录, 总数{}", result.getRecords().size(), result.getTotal());

        attachOrderInfo(result.getRecords());
        return result;
    }

    private LambdaQueryWrapper<SettlementRecord> buildQueryWrapper(Object request, String username, String role) {
        LambdaQueryWrapper<SettlementRecord> wrapper = new LambdaQueryWrapper<>();

        // 提取共同的筛选条件
        String status = null;
        String batch = null;
        String model = null;
        String trackingNumber = null;
        String orderSn = null;
        String keyword = null;
        LocalDate startDate = null;
        LocalDate endDate = null;
        String ownerUsername = null;

        if (request instanceof SettlementFilterRequest) {
            SettlementFilterRequest r = (SettlementFilterRequest) request;
            status = r.getStatus();
            batch = r.getBatch();
            model = r.getModel();
            trackingNumber = r.getTrackingNumber();
            orderSn = r.getOrderSn();
            keyword = r.getKeyword();
            startDate = r.getStartDate();
            endDate = r.getEndDate();
            ownerUsername = r.getOwnerUsername();
        } else if (request instanceof SettlementCursorRequest) {
            SettlementCursorRequest r = (SettlementCursorRequest) request;
            status = r.getStatus();
            batch = r.getBatch();
            model = r.getModel();
            trackingNumber = r.getTrackingNumber();
            orderSn = r.getOrderSn();
            keyword = r.getKeyword();
            startDate = r.getStartDate();
            endDate = r.getEndDate();
            ownerUsername = r.getOwnerUsername();
        }

        // 权限过滤：非管理员只能看到自己提交或归属于自己的单号
        log.info("权限过滤 - username: {}, role: {}", username, role);
        if (!"ADMIN".equals(role) && StringUtils.hasText(username)) {
            log.info("应用普通用户权限过滤，只显示 submitterUsername={} 或 ownerUsername={} 的记录", username, username);
            wrapper.and(w -> w.eq(SettlementRecord::getSubmitterUsername, username)
                    .or()
                    .eq(SettlementRecord::getOwnerUsername, username));
        } else {
            log.info("管理员权限，不应用权限过滤");
        }

        // 应用筛选条件
        if (status != null && !status.isBlank()) {
            wrapper.eq(SettlementRecord::getStatus, status);
        }
        if (batch != null && !batch.isBlank()) {
            wrapper.eq(SettlementRecord::getSettleBatch, batch);
        }
        if (StringUtils.hasText(model)) {
            wrapper.like(SettlementRecord::getModel, model.trim());
        }
        if (StringUtils.hasText(trackingNumber)) {
            wrapper.like(SettlementRecord::getTrackingNumber, trackingNumber.trim());
        }
        if (StringUtils.hasText(orderSn)) {
            // orderSn 是关联字段，需要先查询 OrderRecord 找到匹配的 orderId
            String sn = orderSn.trim().toUpperCase();
            log.info("查询SN: {}", sn);
            LambdaQueryWrapper<OrderRecord> orderWrapper = new LambdaQueryWrapper<>();
            // 使用精确匹配，忽略大小写
            orderWrapper.apply("UPPER(sn) = {0}", sn);
            List<OrderRecord> matchedOrders = orderRecordMapper.selectList(orderWrapper);
            log.info("找到匹配的订单数量: {}", matchedOrders.size());
            if (CollectionUtils.isEmpty(matchedOrders)) {
                // 没有匹配的订单，返回空结果的 wrapper
                log.warn("未找到SN对应的订单: {}", sn);
                wrapper.eq(SettlementRecord::getId, -1L); // 强制返回空结果
                return wrapper;
            }

            // 关键修复：使用 orderId 精确匹配，而不是 trackingNumber
            Set<Long> matchedOrderIds = matchedOrders.stream()
                .map(OrderRecord::getId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

            log.info("匹配的订单ID: {}", matchedOrderIds);

            if (matchedOrderIds.isEmpty()) {
                log.warn("找到订单但没有有效的订单ID");
                wrapper.eq(SettlementRecord::getId, -1L); // 强制返回空结果
                return wrapper;
            }

            wrapper.in(SettlementRecord::getOrderId, matchedOrderIds);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            Set<String> matchTracking = new HashSet<>();
            LambdaQueryWrapper<OrderRecord> orderWrapper = new LambdaQueryWrapper<>();
            orderWrapper.like(OrderRecord::getTrackingNumber, kw)
                .or().like(OrderRecord::getModel, kw)
                .or().like(OrderRecord::getSn, kw);
            List<OrderRecord> matchedOrders = orderRecordMapper.selectList(orderWrapper);
            if (!CollectionUtils.isEmpty(matchedOrders)) {
                matchTracking.addAll(matchedOrders.stream()
                    .map(OrderRecord::getTrackingNumber)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet()));
            }
            wrapper.and(w -> {
                w.like(SettlementRecord::getTrackingNumber, kw)
                    .or().like(SettlementRecord::getModel, kw);
                if (!matchTracking.isEmpty()) {
                    w.or().in(SettlementRecord::getTrackingNumber, matchTracking);
                }
            });
        }
        if (startDate != null) {
            wrapper.ge(SettlementRecord::getOrderTime, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le(SettlementRecord::getOrderTime, endDate.plusDays(1).atStartOfDay());
        }
        if (StringUtils.hasText(ownerUsername)) {
            wrapper.eq(SettlementRecord::getOwnerUsername, ownerUsername.trim());
        }

        return wrapper;
    }

    private void applySorting(LambdaQueryWrapper<SettlementRecord> wrapper, String sortProp, String sortOrder) {
        // 支持动态排序
        if (StringUtils.hasText(sortProp)) {
            boolean isAsc = "ascending".equalsIgnoreCase(sortOrder);
            switch (sortProp) {
                case "orderTime":
                    wrapper.orderBy(true, isAsc, SettlementRecord::getOrderTime);
                    break;
                case "amount":
                    wrapper.orderBy(true, isAsc, SettlementRecord::getAmount);
                    break;
                case "trackingNumber":
                    wrapper.orderBy(true, isAsc, SettlementRecord::getTrackingNumber);
                    break;
                case "status":
                    wrapper.orderBy(true, isAsc, SettlementRecord::getStatus);
                    break;
                default:
                    wrapper.orderByDesc(SettlementRecord::getCreatedAt);
                    break;
            }
        } else {
            wrapper.orderByDesc(SettlementRecord::getCreatedAt);
        }
    }


    @Override
    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
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
        // 确认操作：将状态改为 CONFIRMED
        record.setStatus("CONFIRMED");
        record.setSettleBatch("BATCH-" + LocalDate.now());
        record.setConfirmedBy(operator);
        record.setConfirmedAt(LocalDateTime.now());
        // 更新提交人为当前确认操作的用户
        record.setSubmitterUsername(operator);
        int updated = settlementRecordMapper.updateById(record);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
        updateOrderWithSettlement(record, targetAmount, order);
        markSubmissionCompleted(record.getTrackingNumber());
    }

    @Override
    public byte[] export(SettlementExportRequest request, String username, String role) {
        LambdaQueryWrapper<SettlementRecord> wrapper = new LambdaQueryWrapper<>();

        // 权限过滤：非管理员只能导出自己提交或归属于自己的单号
        log.info("导出权限过滤 - username: {}, role: {}", username, role);
        if (!"ADMIN".equals(role) && StringUtils.hasText(username)) {
            log.info("应用普通用户导出权限过滤");
            wrapper.and(w -> w.eq(SettlementRecord::getSubmitterUsername, username)
                    .or()
                    .eq(SettlementRecord::getOwnerUsername, username));
        } else {
            log.info("管理员导出权限，不应用权限过滤");
        }

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            wrapper.eq(SettlementRecord::getStatus, request.getStatus());
        }
        if (request.getBatch() != null && !request.getBatch().isBlank()) {
            wrapper.eq(SettlementRecord::getSettleBatch, request.getBatch());
        }
        if (request.getStartDate() != null) {
            wrapper.ge(SettlementRecord::getOrderTime, request.getStartDate().atStartOfDay());
        }
        if (request.getEndDate() != null) {
            wrapper.le(SettlementRecord::getOrderTime, request.getEndDate().plusDays(1).atStartOfDay());
        }
        if (StringUtils.hasText(request.getOwnerUsername())) {
            wrapper.eq(SettlementRecord::getOwnerUsername, request.getOwnerUsername().trim());
        }
        if (StringUtils.hasText(request.getSubmitterUsername())) {
            wrapper.eq(SettlementRecord::getSubmitterUsername, request.getSubmitterUsername().trim());
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
    @CacheEvict(value = "orders", allEntries = true)
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
            wrapper.ge(SettlementRecord::getOrderTime, request.getStartDate().atStartOfDay());
        }
        if (request.getEndDate() != null) {
            wrapper.le(SettlementRecord::getOrderTime, request.getEndDate().plusDays(1).atStartOfDay());
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
        int successCount = 0;
        for (SettlementRecord record : records) {
            record.setAmount(request.getAmount());
            int updated = settlementRecordMapper.updateById(record);
            if (updated > 0) {
                successCount++;
            } else {
                log.warn("按型号批量更新金额时检测到乐观锁冲突，跳过 settlementId={}", record.getId());
            }
        }
        return successCount;
    }

    @Override
    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public void confirmBatch(SettlementBatchConfirmRequest request, String operator) {
        if (CollectionUtils.isEmpty(request.getIds())) {
            return;
        }
        for (Long id : request.getIds()) {
            SettlementRecord record = settlementRecordMapper.selectById(id);
            if (record == null) {
                // 记录不存在，跳过本条
                continue;
            }

            // 1) 计算目标金额：优先使用批量确认的金额，其次使用结算记录自身金额
            BigDecimal targetAmount = request.getAmount();
            if (targetAmount == null) {
                targetAmount = record.getAmount();
            }

            // 2) 如果最终金额仍为空：根据需求，批量确认时跳过本条记录，不修改状态
            if (targetAmount == null) {
                continue;
            }

            // 3) 按单条确认的规则更新该记录及其关联订单/提交状态
            record.setAmount(targetAmount);
            record.setRemark(request.getRemark());
            // 确认操作：将状态改为 CONFIRMED
            record.setStatus("CONFIRMED");
            record.setSettleBatch("BATCH-" + LocalDate.now());
            record.setConfirmedBy(operator);
            record.setConfirmedAt(LocalDateTime.now());
            // 更新提交人为当前确认操作的用户
            record.setSubmitterUsername(operator);
            int updated = settlementRecordMapper.updateById(record);
            if (updated == 0) {
                // 批量操作中单条失败，记录日志但继续处理其他记录
                log.warn("批量确认时检测到乐观锁冲突，跳过 settlementId={}", id);
                continue;
            }

            // 同步订单金额与状态，并根据需要更新用户提交状态
            updateOrderWithSettlement(record, targetAmount, null);
            markSubmissionCompleted(record.getTrackingNumber());
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
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
        int updated = settlementRecordMapper.updateById(record);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
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
        records.forEach(record -> {
            int updated = settlementRecordMapper.updateById(record);
            if (updated == 0) {
                log.warn("同步订单到结算记录时检测到乐观锁冲突，跳过 settlementId={}", record.getId());
            }
        });
        if (order.getAmount() != null) {
            int updated = orderRecordMapper.updateById(order);
            if (updated == 0) {
                log.warn("同步订单金额时检测到乐观锁冲突，orderId={}", order.getId());
            }
        }
    }

    private void attachOrderInfo(List<SettlementRecord> records) {
        if (CollectionUtils.isEmpty(records)) {
            return;
        }
        List<Long> orderIds = records.stream()
            .map(SettlementRecord::getOrderId)
            .filter(id -> id != null && id > 0)
            .distinct() // 去重避免重复查询
            .collect(Collectors.toList());
        Map<Long, OrderRecord> orderMap;
        if (!orderIds.isEmpty()) {
            // 优化：只查询需要的字段，减少数据传输量
            LambdaQueryWrapper<OrderRecord> orderWrapper = new LambdaQueryWrapper<>();
            orderWrapper.select(
                OrderRecord::getId,
                OrderRecord::getStatus,
                OrderRecord::getAmount,
                OrderRecord::getCreatedBy,
                OrderRecord::getOrderTime,
                OrderRecord::getSn,
                OrderRecord::getModel,
                OrderRecord::getOrderDate,
                OrderRecord::getCurrency
            ).in(OrderRecord::getId, orderIds);
            List<OrderRecord> orders = orderRecordMapper.selectList(orderWrapper);
            orderMap = orders.stream().collect(Collectors.toMap(OrderRecord::getId, o -> o));

            // 读取订单样式并映射到结算记录
            if (!orderIds.isEmpty()) {
                LambdaQueryWrapper<OrderCellStyle> styleWrapper = new LambdaQueryWrapper<>();
                styleWrapper.in(OrderCellStyle::getOrderId, orderIds);
                List<OrderCellStyle> styles = orderCellStyleMapper.selectList(styleWrapper);
                Map<Long, List<OrderCellStyle>> stylesByOrder = styles.stream()
                    .collect(Collectors.groupingBy(OrderCellStyle::getOrderId));
                // 将样式写入 SettlementRecord 的 transient 字段
                records.forEach(r -> {
                    Long oid = r.getOrderId();
                    if (oid == null) return;
                    List<OrderCellStyle> list = stylesByOrder.getOrDefault(oid, Collections.emptyList());
                    for (OrderCellStyle s : list) {
                        String field = s.getField();
                        if ("tracking".equals(field)) {
                            r.setTrackingBgColor(s.getBgColor());
                            r.setTrackingFontColor(s.getFontColor());
                            r.setTrackingStrike(Boolean.TRUE.equals(s.getStrike()));
                        } else if ("model".equals(field)) {
                            r.setModelBgColor(s.getBgColor());
                            r.setModelFontColor(s.getFontColor());
                            r.setModelStrike(Boolean.TRUE.equals(s.getStrike()));
                        } else if ("sn".equals(field)) {
                            r.setSnBgColor(s.getBgColor());
                            r.setSnFontColor(s.getFontColor());
                            r.setSnStrike(Boolean.TRUE.equals(s.getStrike()));
                        } else if ("amount".equals(field)) {
                            r.setAmountBgColor(s.getBgColor());
                            r.setAmountFontColor(s.getFontColor());
                            r.setAmountStrike(Boolean.TRUE.equals(s.getStrike()));
                        } else if ("remark".equals(field)) {
                            r.setRemarkBgColor(s.getBgColor());
                            r.setRemarkFontColor(s.getFontColor());
                            r.setRemarkStrike(Boolean.TRUE.equals(s.getStrike()));
                        }
                    }
                });
            }
        } else {
            orderMap = Collections.emptyMap();
        }
        List<String> trackingNumbers = records.stream()
            .map(SettlementRecord::getTrackingNumber)
            .filter(StringUtils::hasText)
            .distinct() // 去重避免重复查询
            .collect(Collectors.toList());
        Map<String, List<String>> submissionMap = new HashMap<>();
        if (!trackingNumbers.isEmpty()) {
            // 优化：只查询需要的字段
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

    // 新方法：同时获取提交人和归属人信息
    private Map<String, SubmissionInfo> resolveSubmissionInfo(Set<String> trackingNumbers) {
        if (CollectionUtils.isEmpty(trackingNumbers)) {
            return Collections.emptyMap();
        }

        Map<String, SubmissionInfo> result = new HashMap<>();
        LambdaQueryWrapper<UserSubmission> submissionWrapper = new LambdaQueryWrapper<>();
        submissionWrapper.select(UserSubmission::getTrackingNumber, UserSubmission::getOwnerUsername, UserSubmission::getUsername);
        submissionWrapper.in(UserSubmission::getTrackingNumber, trackingNumbers);
        List<UserSubmission> submissions = userSubmissionMapper.selectList(submissionWrapper);

        submissions.forEach(submission -> {
            if (!StringUtils.hasText(submission.getTrackingNumber())) {
                return;
            }
            String owner = StringUtils.hasText(submission.getOwnerUsername())
                ? submission.getOwnerUsername()
                : submission.getUsername();
            String submitter = submission.getUsername();

            if (StringUtils.hasText(owner) || StringUtils.hasText(submitter)) {
                result.putIfAbsent(submission.getTrackingNumber(), new SubmissionInfo(submitter, owner));
            }
        });

        return result;
    }

    private Map<String, String> resolveOwnerByTracking(Set<String> trackingNumbers) {
        if (CollectionUtils.isEmpty(trackingNumbers)) {
            return Collections.emptyMap();
        }

        // 尝试从缓存批量获取
        Map<String, String> cachedOwners = cacheService.getOwnerUsernames(trackingNumbers);

        // 找出缓存未命中的 tracking numbers
        Set<String> uncachedNumbers = trackingNumbers.stream()
                .filter(tn -> !cachedOwners.containsKey(tn))
                .collect(Collectors.toSet());

        Map<String, String> result = new HashMap<>(cachedOwners);

        // 只查询缓存未命中的数据
        if (!uncachedNumbers.isEmpty()) {
            LambdaQueryWrapper<UserSubmission> submissionWrapper = new LambdaQueryWrapper<>();
            submissionWrapper.select(UserSubmission::getTrackingNumber, UserSubmission::getOwnerUsername, UserSubmission::getUsername);
            submissionWrapper.in(UserSubmission::getTrackingNumber, uncachedNumbers);
            List<UserSubmission> submissions = userSubmissionMapper.selectList(submissionWrapper);

            Map<String, String> newOwners = new HashMap<>();
            submissions.forEach(submission -> {
                if (!StringUtils.hasText(submission.getTrackingNumber())) {
                    return;
                }
                String owner = StringUtils.hasText(submission.getOwnerUsername())
                    ? submission.getOwnerUsername()
                    : submission.getUsername();
                if (StringUtils.hasText(owner)) {
                    newOwners.putIfAbsent(submission.getTrackingNumber(), owner);
                }
            });

            // 将新查询的数据写入缓存
            if (!newOwners.isEmpty()) {
                cacheService.cacheOwnerUsernames(newOwners);
                result.putAll(newOwners);
            }
        }

        return result;
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
        // 只有状态真的改变时才更新状态变更时间
        String oldStatus = order.getStatus();
        if (!"PAID".equals(oldStatus)) {
            order.setStatus("PAID");
            order.setStatusChangedAt(LocalDateTime.now());
            order.setPaidAt(LocalDateTime.now());
        }
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

    @Override
    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public SettlementBatchSnPriceResponse updateAmountBySn(SettlementBatchSnPriceRequest request) {
        if (CollectionUtils.isEmpty(request.getSns())) {
            return new SettlementBatchSnPriceResponse(0, List.of());
        }

        // 去重并清理 SN 列表（保留原始大小写）
        Set<String> uniqueSns = request.getSns().stream()
            .map(String::trim)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());

        if (uniqueSns.isEmpty()) {
            return new SettlementBatchSnPriceResponse(0, List.of());
        }

        // 规范化SN用于匹配（转大写）
        Set<String> normalizedSns = uniqueSns.stream()
            .map(String::toUpperCase)
            .collect(Collectors.toSet());

        // 首先通过 SN 查询对应的 OrderRecord
        LambdaQueryWrapper<OrderRecord> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.in(OrderRecord::getSn, uniqueSns);
        List<OrderRecord> orders = orderRecordMapper.selectList(orderWrapper);

        if (orders.isEmpty()) {
            return new SettlementBatchSnPriceResponse(0, List.of());
        }

        // 创建 SN / ID / Tracking 到 OrderRecord 的映射，便于精准匹配
        Map<Long, OrderRecord> idToOrderMap = new HashMap<>();
        orders.forEach(order -> {
            if (order.getId() != null) {
                idToOrderMap.put(order.getId(), order);
            }
        });

        // 提取 tracking numbers
        Set<String> trackingNumbers = orders.stream()
            .map(OrderRecord::getTrackingNumber)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());

        // 拉取所有共享这些运单号的订单，用于判定唯一性，避免误匹配其他 SN
        Map<String, List<OrderRecord>> trackingToOrders = new HashMap<>();
        if (!trackingNumbers.isEmpty()) {
            LambdaQueryWrapper<OrderRecord> trackingOrderWrapper = new LambdaQueryWrapper<>();
            trackingOrderWrapper.in(OrderRecord::getTrackingNumber, trackingNumbers);
            List<OrderRecord> ordersByTracking = orderRecordMapper.selectList(trackingOrderWrapper);
            ordersByTracking.forEach(order -> {
                if (StringUtils.hasText(order.getTrackingNumber())) {
                    trackingToOrders.computeIfAbsent(order.getTrackingNumber(), k -> new ArrayList<>()).add(order);
                }
            });
        }

        if (trackingNumbers.isEmpty()) {
            return new SettlementBatchSnPriceResponse(0, List.of());
        }

        // 查询对应的结算记录
        LambdaQueryWrapper<SettlementRecord> settlementWrapper = new LambdaQueryWrapper<>();
        settlementWrapper.in(SettlementRecord::getTrackingNumber, trackingNumbers);
        List<SettlementRecord> allSettlements = settlementRecordMapper.selectList(settlementWrapper);

        if (allSettlements.isEmpty()) {
            return new SettlementBatchSnPriceResponse(0, List.of());
        }

        // 关键修复：只更新匹配输入SN的结算记录，且只更新没有价格的
        List<SettlementRecord> toUpdate = new ArrayList<>();
        Set<Long> toUpdateOrderIds = new HashSet<>();
        Set<String> skippedSnSet = new LinkedHashSet<>(); // 已有价格的SN，去重

        for (SettlementRecord settlement : allSettlements) {
            // 通过 orderId 查找对应的订单
            OrderRecord matchedOrder = null;
            if (settlement.getOrderId() != null && settlement.getOrderId() > 0) {
                matchedOrder = idToOrderMap.get(settlement.getOrderId());
            }

            // 如果通过 orderId 没找到，且仅当该运单号只对应唯一订单时再按 trackingNumber 匹配，避免误匹配其他 SN
            if (matchedOrder == null && StringUtils.hasText(settlement.getTrackingNumber())) {
                List<OrderRecord> candidates = trackingToOrders.get(settlement.getTrackingNumber());
                if (candidates != null && candidates.size() == 1) {
                    matchedOrder = candidates.get(0);
                }
            }

            // 检查订单的SN是否在输入的SN列表中
            if (matchedOrder != null && StringUtils.hasText(matchedOrder.getSn())) {
                String orderSnNormalized = matchedOrder.getSn().toUpperCase();
                if (normalizedSns.contains(orderSnNormalized)) {
                    // 核心逻辑：检查是否已有价格
                    boolean hasPrice = settlement.getAmount() != null &&
                                      settlement.getAmount().compareTo(BigDecimal.ZERO) > 0;

                    if (hasPrice) {
                        // 已有价格，跳过并记录
                        skippedSnSet.add(matchedOrder.getSn());
                    } else {
                        // 没有价格，加入更新列表
                        toUpdate.add(settlement);
                        if (matchedOrder.getId() != null) {
                            toUpdateOrderIds.add(matchedOrder.getId());
                        }
                    }
                }
            }
        }

        // 更新结算记录的金额（只更新没有价格的）
        int successCount = 0;
        for (SettlementRecord record : toUpdate) {
            record.setAmount(request.getAmount());
            int updated = settlementRecordMapper.updateById(record);
            if (updated > 0) {
                successCount++;
            } else {
                log.warn("按SN批量更新金额时检测到乐观锁冲突，跳过 settlementId={}", record.getId());
            }
        }

        // 同步更新订单金额（只更新目标订单）
        if (!toUpdateOrderIds.isEmpty()) {
            LambdaUpdateWrapper<OrderRecord> orderUpdate = Wrappers.lambdaUpdate();
            orderUpdate.in(OrderRecord::getId, toUpdateOrderIds);
            orderUpdate.set(OrderRecord::getAmount, request.getAmount());
            orderRecordMapper.update(null, orderUpdate);
        }

        return new SettlementBatchSnPriceResponse(successCount, new ArrayList<>(skippedSnSet));
    }

    @Override
    @Transactional
    public int deleteConfirmed(String username, String role) {
        log.info("删除已确认的结算记录 - username: {}, role: {}", username, role);
        LambdaQueryWrapper<SettlementRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SettlementRecord::getStatus, "CONFIRMED");

        // 权限过滤：非管理员只能删除自己提交或归属于自己的记录
        if (!"ADMIN".equals(role) && StringUtils.hasText(username)) {
            log.info("应用普通用户权限过滤，只删除 submitterUsername={} 或 ownerUsername={} 的已确认记录", username, username);
            wrapper.and(w -> w.eq(SettlementRecord::getSubmitterUsername, username)
                    .or()
                    .eq(SettlementRecord::getOwnerUsername, username));
        } else {
            log.info("管理员权限，删除所有已确认记录");
        }

        int count = settlementRecordMapper.delete(wrapper);
        log.info("已删除 {} 条已确认的结算记录", count);

        // 清除缓存
        cacheService.evictAllOwnerCache();

        return count;
    }

    @Override
    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public int confirmAll(SettlementFilterRequest request, String operator, String username, String role) {
        // 使用传入的状态筛选条件（DRAFT 或 PENDING）
        LambdaQueryWrapper<SettlementRecord> wrapper = buildQueryWrapper(request, username, role);

        List<SettlementRecord> recordsToConfirm = settlementRecordMapper.selectList(wrapper);

        int confirmedCount = 0;
        for (SettlementRecord record : recordsToConfirm) {
            // 只处理金额大于0的记录（金额为null或0的跳过）
            if (record.getAmount() != null && record.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                // 确认操作：将状态改为 CONFIRMED
                record.setStatus("CONFIRMED");
                record.setSettleBatch("BATCH-" + LocalDate.now());
                record.setConfirmedBy(operator);
                record.setConfirmedAt(LocalDateTime.now());
                // 更新提交人为当前确认操作的用户
                record.setSubmitterUsername(operator);
                int updated = settlementRecordMapper.updateById(record);
                if (updated == 0) {
                    // 全部确认时单条失败，记录日志但继续处理其他记录
                    log.warn("全部确认时检测到乐观锁冲突，跳过 settlementId={}", record.getId());
                    continue;
                }

                // 同步订单金额与状态，并根据需要更新用户提交状态
                updateOrderWithSettlement(record, record.getAmount(), null);
                markSubmissionCompleted(record.getTrackingNumber());
                confirmedCount++;
            }
        }
        return confirmedCount;
    }

    @Override
    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public int moveToDraft(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        log.info("批量移动到待结账 - 记录ID数量: {}", ids.size());
        LambdaUpdateWrapper<SettlementRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(SettlementRecord::getId, ids)
                .set(SettlementRecord::getStatus, "DRAFT");
        int count = settlementRecordMapper.update(null, wrapper);
        log.info("已将 {} 条记录移动到待结账", count);
        return count;
    }

    @Override
    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public int moveToPending(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        log.info("批量移动到结账管理 - 记录ID数量: {}", ids.size());
        // 将待结账工作区的数据（DRAFT 或 CONFIRMED）移动到结账管理，统一改为 PENDING 状态
        LambdaUpdateWrapper<SettlementRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(SettlementRecord::getId, ids)
                .set(SettlementRecord::getStatus, "PENDING")
                .set(SettlementRecord::getSettleBatch, null)  // 清除批次信息，等待正式确认
                .set(SettlementRecord::getConfirmedBy, null)  // 清除确认人
                .set(SettlementRecord::getConfirmedAt, null); // 清除确认时间
        int count = settlementRecordMapper.update(null, wrapper);
        log.info("已将 {} 条记录移动到结账管理（重置为 PENDING 状态）", count);
        return count;
    }
}
