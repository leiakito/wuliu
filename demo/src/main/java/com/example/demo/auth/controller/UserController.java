package com.example.demo.auth.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.demo.auth.dto.ResetPasswordRequest;
import com.example.demo.auth.dto.UserRequest;
import com.example.demo.auth.entity.SysUser;
import com.example.demo.auth.service.SysUserService;
import com.example.demo.common.annotation.LogOperation;
import com.example.demo.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "管理员专用的账号维护接口")
public class UserController {

    private final SysUserService sysUserService;

    @GetMapping
    @SaCheckRole("ADMIN")
    @Operation(summary = "用户列表", description = "列出系统内所有账号")
    public ApiResponse<List<SysUser>> list() {
        return ApiResponse.ok(sysUserService.list());
    }

    @PostMapping
    @SaCheckRole("ADMIN")
    @LogOperation("新增用户")
    @Operation(summary = "新增账号", description = "创建新的后台用户，默认密码可在请求体中指定")
    public ApiResponse<SysUser> create(@Valid @RequestBody UserRequest request) {
        return ApiResponse.ok(sysUserService.create(request));
    }

    @PutMapping("/{id}")
    @SaCheckRole("ADMIN")
    @LogOperation("更新用户")
    @Operation(summary = "更新账号信息", description = "修改用户角色、状态或基本资料")
    public ApiResponse<Void> update(
        @Parameter(description = "用户主键 ID", required = true) @PathVariable Long id,
        @Valid @RequestBody UserRequest request) {
        sysUserService.update(id, request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/password")
    @SaCheckRole("ADMIN")
    @LogOperation("重置密码")
    @Operation(summary = "重置密码", description = "管理员为指定用户重置登录密码")
    public ApiResponse<Void> resetPassword(
        @Parameter(description = "用户主键 ID", required = true) @PathVariable Long id,
        @Valid @RequestBody ResetPasswordRequest request) {
        sysUserService.resetPassword(id, request.getPassword());
        return ApiResponse.ok();
    }
    @DeleteMapping("/{id}")
    @SaCheckRole("ADMIN")//检测只有管理员能删除
    @Operation(summary = "删除用户",description = "根据ID删除用户")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysUserService.delete(id);
        return ApiResponse.ok();
    }
}
