package com.example.demo.submission.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.common.response.ApiResponse;
import com.example.demo.common.response.PageResponse;
import com.example.demo.submission.dto.UserSubmissionBatchRequest;
import com.example.demo.submission.dto.UserSubmissionCreateRequest;
import com.example.demo.submission.dto.UserSubmissionQueryRequest;
import com.example.demo.submission.entity.UserSubmission;
import com.example.demo.submission.service.UserSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user-submissions")
@RequiredArgsConstructor
@Tag(name = "用户单号提交", description = "面向普通用户的单号登记与查询")
public class UserSubmissionController {

    private final UserSubmissionService userSubmissionService;

    @PostMapping
    @SaCheckLogin
    @Operation(summary = "提交单号", description = "普通用户提交待审核的物流单号")
    public ApiResponse<UserSubmission> submit(@Valid @RequestBody UserSubmissionCreateRequest request) {
        String operator = StpUtil.getLoginIdAsString();
        String targetUser = StringUtils.hasText(request.getUsername()) ? request.getUsername().trim() : operator;
        return ApiResponse.ok(userSubmissionService.create(request, operator, targetUser));
    }

    @PostMapping("/batch")
    @SaCheckLogin
    @Operation(summary = "批量提交单号")
    public ApiResponse<List<UserSubmission>> batchSubmit(@Valid @RequestBody UserSubmissionBatchRequest request) {
        String operator = StpUtil.getLoginIdAsString();
        String targetUser = StringUtils.hasText(request.getUsername()) ? request.getUsername().trim() : operator;
        return ApiResponse.ok(userSubmissionService.batchCreate(request, operator, targetUser));
    }

    @GetMapping("/mine")
    @SaCheckLogin
    @Operation(summary = "我的提交", description = "普通用户分页查看自己的提交记录")
    public ApiResponse<PageResponse<UserSubmission>> mine(UserSubmissionQueryRequest request) {
        IPage<UserSubmission> page = userSubmissionService.pageMine(request, StpUtil.getLoginIdAsString());
        return ApiResponse.ok(PageResponse.from(page));
    }

    @GetMapping
    @SaCheckRole("ADMIN")
    @Operation(summary = "全部提交", description = "管理员分页查看所有用户的提交记录")
    public ApiResponse<PageResponse<UserSubmission>> all(UserSubmissionQueryRequest request) {
        IPage<UserSubmission> page = userSubmissionService.pageAll(request);
        return ApiResponse.ok(PageResponse.from(page));
    }
}
