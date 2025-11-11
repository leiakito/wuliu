package com.example.demo.hardware.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.example.demo.common.annotation.LogOperation;
import com.example.demo.common.response.ApiResponse;
import com.example.demo.hardware.dto.HardwarePriceQuery;
import com.example.demo.hardware.dto.HardwarePriceRequest;
import com.example.demo.hardware.entity.HardwarePrice;
import com.example.demo.hardware.service.HardwarePriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
