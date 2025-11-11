package com.example.demo.report.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.demo.common.response.ApiResponse;
import com.example.demo.report.dto.DashboardResponse;
import com.example.demo.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
@Tag(name = "报表统计", description = "仪表盘与统计数据接口")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/dashboard")
    @SaCheckLogin
    @Operation(summary = "获取仪表盘", description = "统计指定时间范围内的订单数量与待结账金额")
    public ApiResponse<DashboardResponse> dashboard(
        @Parameter(description = "开始日期", example = "2025-01-01")
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
        @Parameter(description = "结束日期", example = "2025-01-31")
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return ApiResponse.ok(reportService.dashboard(start, end));
    }
}
