package com.example.demo.announcement.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.announcement.dto.AnnouncementCreateRequest;
import com.example.demo.announcement.entity.Announcement;
import com.example.demo.announcement.service.AnnouncementService;
import com.example.demo.common.annotation.LogOperation;
import com.example.demo.common.response.ApiResponse;
import com.example.demo.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
@Tag(name = "系统公告", description = "管理员发布公告，用户查看公告")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @PostMapping
    @SaCheckRole("ADMIN")
    @LogOperation("发布公告")
    @Operation(summary = "发布公告", description = "管理员创建新的系统公告")
    public ApiResponse<Announcement> create(@Valid @RequestBody AnnouncementCreateRequest request) {
        return ApiResponse.ok(announcementService.create(request, StpUtil.getLoginIdAsString()));
    }

    @GetMapping
    @SaCheckLogin
    @Operation(summary = "公告列表", description = "分页获取系统公告")
    public ApiResponse<PageResponse<Announcement>> page(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size) {
        IPage<Announcement> result = announcementService.page(page, size);
        return ApiResponse.ok(PageResponse.from(result));
    }

    @GetMapping("/latest")
    @SaCheckLogin
    @Operation(summary = "最新公告", description = "获取最新的一条公告")
    public ApiResponse<Announcement> latest() {
        return ApiResponse.ok(announcementService.latest());
    }
}
