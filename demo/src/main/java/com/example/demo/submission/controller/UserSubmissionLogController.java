package com.example.demo.submission.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.common.response.ApiResponse;
import com.example.demo.common.response.PageResponse;
import com.example.demo.submission.dto.SubmissionLogQueryRequest;
import com.example.demo.submission.entity.UserSubmissionLog;
import com.example.demo.submission.service.UserSubmissionLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user-submission-logs")
@RequiredArgsConstructor
@Tag(name = "用户提交日志", description = "管理员查看用户提交原文记录")
public class UserSubmissionLogController {

    private final UserSubmissionLogService userSubmissionLogService;

    @GetMapping
    @SaCheckLogin
    @SaCheckRole("ADMIN")
    @Operation(summary = "分页查询提交日志")
    public ApiResponse<PageResponse<UserSubmissionLog>> page(SubmissionLogQueryRequest request) {
        IPage<UserSubmissionLog> page = userSubmissionLogService.page(request);
        return ApiResponse.ok(PageResponse.from(page));
    }
}
