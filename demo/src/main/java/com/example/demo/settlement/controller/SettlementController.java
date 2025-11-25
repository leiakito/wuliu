package com.example.demo.settlement.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.common.annotation.LogOperation;
import com.example.demo.common.response.ApiResponse;
import com.example.demo.common.response.PageResponse;
import com.example.demo.settlement.dto.SettlementAmountRequest;
import com.example.demo.settlement.dto.SettlementBatchConfirmRequest;
import com.example.demo.settlement.dto.SettlementBatchPriceRequest;
import com.example.demo.settlement.dto.SettlementBatchSnPriceRequest;
import com.example.demo.settlement.dto.SettlementBatchSnPriceResponse;
import com.example.demo.settlement.dto.SettlementConfirmRequest;
import com.example.demo.settlement.dto.SettlementExportRequest;
import com.example.demo.settlement.dto.SettlementFilterRequest;
import com.example.demo.settlement.entity.SettlementRecord;
import com.example.demo.settlement.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
        settlementService.confirm(id, request, StpUtil.getLoginIdAsString());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/amount")
    @SaCheckRole("ADMIN")
    @LogOperation("修改结算金额")
    @Operation(summary = "修改单条结算金额", description = "同步更新关联订单金额")
    public ApiResponse<Void> updateAmount(
        @Parameter(description = "结算记录 ID", required = true) @PathVariable Long id,
        @Valid @RequestBody SettlementAmountRequest request) {
        settlementService.updateAmount(id, request);
        return ApiResponse.ok();
    }

    @PutMapping("/price-by-model")
    @SaCheckRole("ADMIN")
    @LogOperation("批量设置结算金额")
    @Operation(summary = "按型号批量设置金额", description = "针对同一型号的结算记录批量设置金额")
    public ApiResponse<Integer> updatePriceByModel(@Valid @RequestBody SettlementBatchPriceRequest request) {
        int updated = settlementService.updateAmountByModel(request);
        return ApiResponse.ok(updated);
    }

    @PutMapping("/price-by-sn")
    @SaCheckRole("ADMIN")
    @LogOperation("按SN批量设置结算金额")
    @Operation(summary = "按SN批量设置金额", description = "根据SN列表批量设置结算记录金额，只更新没有价格的SN")
    public ApiResponse<SettlementBatchSnPriceResponse> updatePriceBySn(@Valid @RequestBody SettlementBatchSnPriceRequest request) {
        SettlementBatchSnPriceResponse response = settlementService.updateAmountBySn(request);
        return ApiResponse.ok(response);
    }

    @PutMapping("/confirm-batch")
    @SaCheckRole("ADMIN")
    @LogOperation("批量确认结账")
    @Operation(summary = "批量确认", description = "批量确认所选结算记录，金额可选统一设置")
    public ApiResponse<Void> confirmBatch(@Valid @RequestBody SettlementBatchConfirmRequest request) {
        settlementService.confirmBatch(request, StpUtil.getLoginIdAsString());
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
