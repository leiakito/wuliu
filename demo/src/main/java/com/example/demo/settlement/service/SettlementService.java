package com.example.demo.settlement.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.order.entity.OrderRecord;
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
import java.util.List;

public interface SettlementService {

    List<SettlementRecord> createPending(List<OrderRecord> orders, boolean warnDouble);

    IPage<SettlementRecord> list(SettlementFilterRequest request, String username, String role);

    IPage<SettlementRecord> listByCursor(SettlementCursorRequest request, String username, String role);

    void confirm(Long id, SettlementConfirmRequest request, String operator);

    byte[] export(SettlementExportRequest request, String username, String role);

    void delete(List<Long> ids);

    void syncFromOrder(OrderRecord order);

    int updateAmountByModel(SettlementBatchPriceRequest request);

    void confirmBatch(SettlementBatchConfirmRequest request, String operator);

    void updateAmount(Long id, SettlementAmountRequest request);

    SettlementBatchSnPriceResponse updateAmountBySn(SettlementBatchSnPriceRequest request);

    int deleteConfirmed();

    int confirmAll(SettlementFilterRequest request, String operator, String username, String role);
}
