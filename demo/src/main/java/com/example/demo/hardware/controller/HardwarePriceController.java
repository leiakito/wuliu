package com.example.demo.hardware.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.example.demo.common.annotation.LogOperation;
import com.example.demo.common.response.ApiResponse;
import com.example.demo.hardware.dto.HardwarePriceBatchRequest;
import com.example.demo.hardware.dto.HardwarePriceImportResult;
import com.example.demo.hardware.dto.HardwarePriceQuery;
import com.example.demo.hardware.dto.HardwarePriceRequest;
import com.example.demo.hardware.entity.HardwarePrice;
import com.example.demo.hardware.service.HardwarePriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/hardware/prices")
@RequiredArgsConstructor
@Tag(name = "硬件价格", description = "每日硬件价格维护与查询")
public class HardwarePriceController {

    private final HardwarePriceService hardwarePriceService;

    @GetMapping
    @SaCheckLogin
    @Operation(summary = "查询硬件价格", description = "可按日期区间查询硬件价格")
    public ApiResponse<List<HardwarePrice>> list(HardwarePriceQuery query) {
        return ApiResponse.ok(hardwarePriceService.list(query));
    }

    @PostMapping
    @SaCheckRole("ADMIN")
    @LogOperation("新增硬件价格")
    @Operation(summary = "新增价格", description = "管理员新增当日硬件价格")
    public ApiResponse<HardwarePrice> create(@Valid @RequestBody HardwarePriceRequest request) {
        return ApiResponse.ok(hardwarePriceService.create(request, StpUtil.getLoginIdAsString()));
    }

    @PostMapping("/batch")
    @SaCheckRole("ADMIN")
    @LogOperation("批量新增硬件价格")
    @Operation(summary = "批量录入价格", description = "管理员可批量导入多条硬件价格")
    public ApiResponse<List<HardwarePrice>> batchCreate(@Valid @RequestBody HardwarePriceBatchRequest request) {
        return ApiResponse.ok(hardwarePriceService.batchCreate(request.getItems(), StpUtil.getLoginIdAsString()));
    }

    @PostMapping("/import")
    @SaCheckRole("ADMIN")
    @LogOperation("导入硬件价格")
    @Operation(summary = "导入Excel", description = "上传 Excel 按日期批量导入硬件价格")
    public ApiResponse<List<HardwarePrice>> importExcel(
        @RequestParam(value = "priceDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate priceDate,
        @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.ok(hardwarePriceService.importExcel(priceDate, file, StpUtil.getLoginIdAsString()));
    }

    @PostMapping("/import/batch")
    @SaCheckRole("ADMIN")
    @LogOperation("批量导入硬件价格")
    @Operation(summary = "批量导入Excel", description = "支持多文件上传，自动从文件名解析日期")
    public ApiResponse<List<HardwarePriceImportResult>> importExcelBatch(
        @RequestParam("files") List<MultipartFile> files
    ) {
        return ApiResponse.ok(hardwarePriceService.importExcelBatch(files, StpUtil.getLoginIdAsString()));
    }

    @PutMapping("/{id}")
    @SaCheckRole("ADMIN")
    @LogOperation("更新硬件价格")
    @Operation(summary = "更新价格", description = "管理员编辑硬件价格")
    public ApiResponse<HardwarePrice> update(@PathVariable Long id, @Valid @RequestBody HardwarePriceRequest request) {
        return ApiResponse.ok(hardwarePriceService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @SaCheckRole("ADMIN")
    @LogOperation("删除硬件价格")
    @Operation(summary = "删除价格", description = "管理员删除当日价格记录")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        hardwarePriceService.delete(id);
        return ApiResponse.ok();
    }
}
