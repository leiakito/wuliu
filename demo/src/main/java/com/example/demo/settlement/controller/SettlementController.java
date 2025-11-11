package com.example.demo.settlement.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.common.annotation.LogOperation;
import com.example.demo.common.response.ApiResponse;
import com.example.demo.common.response.PageResponse;
import com.example.demo.order.dto.BatchFetchRequest;
import com.example.demo.order.entity.OrderRecord;
import com.example.demo.order.service.OrderService;
import com.example.demo.settlement.dto.SettlementConfirmRequest;
import com.example.demo.settlement.dto.SettlementExportRequest;
import com.example.demo.settlement.dto.SettlementFilterRequest;
import com.example.demo.settlement.dto.SettlementGenerateRequest;
import com.example.demo.settlement.entity.SettlementRecord;
import com.example.demo.settlement.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
@Tag(name = "结算管理", description = "待结账生成、确认、删除与导出功能")
public class SettlementController {

    private final SettlementService settlementService;
    private final OrderService orderService;

    @PostMapping("/generate")
    @SaCheckLogin
    @LogOperation("生成待结账")
    @Operation(summary = "生成待结账", description = "根据单号列表生成待结账数据，缺失的单号会自动抓取")
    public ApiResponse<List<SettlementRecord>> generate(@Valid @RequestBody SettlementGenerateRequest request) {
        String operator = StpUtil.getLoginIdAsString();
        List<OrderRecord> orders = new ArrayList<>(orderService.findByTracking(request.getTrackingNumbers()));
        Set<String> found = orders.stream().map(OrderRecord::getTrackingNumber).collect(Collectors.toSet());
        List<String> missing = request.getTrackingNumbers().stream()
            .filter(num -> !found.contains(num))
            .collect(Collectors.toList());
        if (!missing.isEmpty()) {
            BatchFetchRequest fetchRequest = new BatchFetchRequest();
            fetchRequest.setTrackingNumbers(missing);
            orders.addAll(orderService.syncFromThirdParty(fetchRequest, operator));
        }
        return ApiResponse.ok(settlementService.createPending(orders, true));
    }

    @GetMapping
    @SaCheckLogin
    @Operation(summary = "分页查询结算", description = "按状态、批次和时间段查询待结账/已结账记录")
    public ApiResponse<PageResponse<SettlementRecord>> page(SettlementFilterRequest request) {
        IPage<SettlementRecord> page = settlementService.list(request);
        return ApiResponse.ok(PageResponse.from(page));
    }

    @PutMapping("/{id}/confirm")
    @SaCheckRole("ADMIN")
    @LogOperation("结账确认")
    @Operation(summary = "确认结账", description = "由管理员填写实际金额并确认结账")
    public ApiResponse<Void> confirm(
        @Parameter(description = "待确认的结算记录 ID", required = true) @PathVariable Long id,
        @Valid @RequestBody SettlementConfirmRequest request) {
        settlementService.confirm(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping
    @SaCheckRole("ADMIN")
    @Operation(summary = "批量删除待结账", description = "仅限管理员执行的删单操作")
    public ApiResponse<Void> delete(
        @Parameter(description = "需要删除的结算记录 ID 列表", required = true)
        @RequestBody List<Long> ids) {
        settlementService.delete(ids);
        return ApiResponse.ok();
    }

    @GetMapping("/export")
    @SaCheckLogin
    @LogOperation("导出结账数据")
    @Operation(summary = "导出结算数据", description = "将筛选结果导出为 Excel 文件")
    public ResponseEntity<byte[]> export(SettlementExportRequest request) {
        byte[] bytes = settlementService.export(request);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=settlements.xlsx")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(bytes);
    }
}
