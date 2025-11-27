package com.example.demo.order.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.common.annotation.LogOperation;
import com.example.demo.common.response.ApiResponse;
import com.example.demo.common.response.PageResponse;
import com.example.demo.order.dto.BatchFetchRequest;
import com.example.demo.order.dto.OrderCategoryStats;
import com.example.demo.order.dto.OrderCreateRequest;
import com.example.demo.order.dto.OrderFilterRequest;
import com.example.demo.order.dto.OrderAmountRequest;
import com.example.demo.order.dto.OrderUpdateRequest;
import com.example.demo.order.entity.OrderRecord;
import com.example.demo.order.service.OrderService;
import com.example.demo.order.dto.OrderSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "订单管理", description = "物流单号的导入、维护与批量操作")
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    @SaCheckRole("ADMIN")
    @Operation(summary = "分页查询订单", description = "支持按日期、分类、状态与关键字筛选物流单号")
    public ApiResponse<PageResponse<OrderRecord>> page(OrderFilterRequest request) {
        // 防御性验证：确保 page 和 size 参数有效
        if (request.getPage() <= 0) {
            request.setPage(1);
        }
        if (request.getSize() <= 0 || request.getSize() > 1000) {
            request.setSize(50);
        }
        IPage<OrderRecord> page = orderService.query(request);
        return ApiResponse.ok(PageResponse.from(page));
    }

    @PostMapping
    @SaCheckRole("ADMIN")
    @LogOperation("新增物流单号")
    @Operation(summary = "新增物流单号", description = "录入单条物流单号及相关信息")
    public ApiResponse<OrderRecord> create(@Valid @RequestBody OrderCreateRequest request) {
        return ApiResponse.ok(orderService.create(request, StpUtil.getLoginIdAsString()));
    }

    @PostMapping("/import")
    @SaCheckRole("ADMIN")
    @LogOperation("批量导入物流单号")
    @Operation(summary = "批量导入", description = "上传 Excel 以批量导入物流单号")
    public ApiResponse<Map<String, Object>> importExcel(
        @Parameter(description = "包含订单明细的 Excel 文件", required = true)
        @RequestParam("file") MultipartFile file) {
        Map<String, Object> report = orderService.importOrders(file, StpUtil.getLoginIdAsString());
        return ApiResponse.ok(report);
    }

    @PostMapping("/fetch")
    @SaCheckRole("ADMIN")
    @LogOperation("批量抓取物流单")
    @Operation(summary = "批量抓取", description = "根据输入的物流单号列表自动拉取或补全订单信息")
    public ApiResponse<List<OrderRecord>> fetch(@Valid @RequestBody BatchFetchRequest request) {
        return ApiResponse.ok(orderService.syncFromThirdParty(request, StpUtil.getLoginIdAsString()));
    }

    @PostMapping("/search")
    @SaCheckLogin
    @Operation(summary = "按SN和单号查询状态", description = "用户通过单号SN查询是否已录入或结账")
    public ApiResponse<List<OrderRecord>> search(@Valid @RequestBody OrderSearchRequest request) {
        return ApiResponse.ok(orderService.search(request.getTrackingNumbers()));
    }

    @PatchMapping("/{id}/status")
    @SaCheckRole("ADMIN")
    @LogOperation("更新订单状态")
    @Operation(summary = "更新订单状态", description = "根据订单 ID 更新当前物流单状态")
    public ApiResponse<Void> changeStatus(
        @Parameter(description = "订单主键 ID", required = true) @PathVariable Long id,
        @Parameter(description = "新的状态值，如 PAID/UNPAID/NOT_RECEIVED", required = true)
        @RequestParam String status) {
        orderService.updateStatus(id, status);
        return ApiResponse.ok();
    }

    @PatchMapping("/{id}/amount")
    @SaCheckRole("ADMIN")
    @LogOperation("更新订单金额")
    @Operation(summary = "更新金额", description = "根据订单 ID 设置/修改金额和备注")
    public ApiResponse<Void> changeAmount(
        @Parameter(description = "订单主键 ID", required = true) @PathVariable Long id,
        @Valid @RequestBody OrderAmountRequest request) {
        orderService.updateAmount(id, request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    @SaCheckRole("ADMIN")
    @LogOperation("编辑物流单号")
    @Operation(summary = "编辑物流单号", description = "修改运单号/型号/SN/金额/状态/备注")
    public ApiResponse<OrderRecord> update(
        @Parameter(description = "订单主键 ID", required = true) @PathVariable Long id,
        @Valid @RequestBody OrderUpdateRequest request) {
        return ApiResponse.ok(orderService.update(id, request));
    }

    @GetMapping("/categories")
    @SaCheckLogin
    @Operation(summary = "物流分类统计", description = "基于筛选条件返回各物流公司数量")
    public ApiResponse<List<OrderCategoryStats>> categories(OrderFilterRequest request) {
        return ApiResponse.ok(orderService.listCategoryStats(request));
    }
}
