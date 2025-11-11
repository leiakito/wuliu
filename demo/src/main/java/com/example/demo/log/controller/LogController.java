package com.example.demo.log.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.demo.common.response.ApiResponse;
import com.example.demo.common.response.PageResponse;
import com.example.demo.log.service.SysLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Tag(name = "操作日志", description = "系统审计日志查询")
public class LogController {

    private final SysLogService sysLogService;

    @GetMapping
    @SaCheckRole("ADMIN")
    @Operation(summary = "分页查询日志", description = "管理员查看系统操作日志")
    public ApiResponse<PageResponse<?>> page(
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") long page,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(PageResponse.from(sysLogService.page(page, size)));
    }
}
