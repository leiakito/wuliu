package com.example.demo.settlement.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.order.entity.OrderRecord;
import com.example.demo.settlement.dto.SettlementConfirmRequest;
import com.example.demo.settlement.dto.SettlementExportRequest;
import com.example.demo.settlement.dto.SettlementFilterRequest;
import com.example.demo.settlement.entity.SettlementRecord;
import java.util.List;

public interface SettlementService {

    List<SettlementRecord> createPending(List<OrderRecord> orders, boolean warnDouble);

    IPage<SettlementRecord> list(SettlementFilterRequest request);

    void confirm(Long id, SettlementConfirmRequest request);

    byte[] export(SettlementExportRequest request);

    void delete(List<Long> ids);

    void syncFromOrder(OrderRecord order);
}
