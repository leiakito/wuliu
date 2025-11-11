package com.example.demo.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.order.entity.OrderRecord;
import com.example.demo.order.mapper.OrderRecordMapper;
import com.example.demo.report.dto.DashboardResponse;
import com.example.demo.report.service.ReportService;
import com.example.demo.settlement.entity.SettlementRecord;
import com.example.demo.settlement.mapper.SettlementRecordMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final OrderRecordMapper orderRecordMapper;
    private final SettlementRecordMapper settlementRecordMapper;

    @Override
    public DashboardResponse dashboard(LocalDate start, LocalDate end) {
        LambdaQueryWrapper<OrderRecord> orderWrapper = new LambdaQueryWrapper<>();
        if (start != null) {
            orderWrapper.ge(OrderRecord::getOrderDate, start);
        }
        if (end != null) {
            orderWrapper.le(OrderRecord::getOrderDate, end);
        }
        List<OrderRecord> orders = orderRecordMapper.selectList(orderWrapper);
        LambdaQueryWrapper<SettlementRecord> settleWrapper = new LambdaQueryWrapper<>();
        settleWrapper.eq(SettlementRecord::getStatus, "PENDING");
        List<SettlementRecord> pending = settlementRecordMapper.selectList(settleWrapper);

        DashboardResponse response = new DashboardResponse();
        response.setOrderCount(orders.size());
        response.setWaitingSettlementCount(pending.size());
        response.setTotalAmount(orders.stream()
            .map(OrderRecord::getAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        response.setPendingAmount(pending.stream()
            .map(SettlementRecord::getAmount)
            .filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        return response;
    }
}
