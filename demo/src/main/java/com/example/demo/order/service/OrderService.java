package com.example.demo.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.order.dto.BatchFetchRequest;
import com.example.demo.order.dto.OrderAmountRequest;
import com.example.demo.order.dto.OrderCategoryStats;
import com.example.demo.order.dto.OrderCreateRequest;
import com.example.demo.order.dto.OrderFilterRequest;
import com.example.demo.order.dto.OrderUpdateRequest;
import com.example.demo.order.entity.OrderRecord;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface OrderService {

    void importOrders(MultipartFile file, String operator);

    IPage<OrderRecord> query(OrderFilterRequest request);

    OrderRecord create(OrderCreateRequest request, String operator);

    void updateStatus(Long id, String status);

    void updateAmount(Long id, OrderAmountRequest request);

    OrderRecord update(Long id, OrderUpdateRequest request);

    List<OrderRecord> findByTracking(List<String> trackingNumbers);

    List<OrderCategoryStats> listCategoryStats(OrderFilterRequest request);

    List<OrderRecord> syncFromThirdParty(BatchFetchRequest request, String operator);
}
