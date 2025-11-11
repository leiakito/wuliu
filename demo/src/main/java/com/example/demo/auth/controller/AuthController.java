package com.example.demo.auth.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.example.demo.auth.dto.LoginRequest;
import com.example.demo.auth.dto.LoginResponse;
import com.example.demo.auth.entity.SysUser;
import com.example.demo.auth.service.SysUserService;
import com.example.demo.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
//组合ResponseBody和Controller 返回json
@RequestMapping("/api/auth")
//	实际访问路径是：/api/auth
@RequiredArgsConstructor //生成包含所有final字段的构造函数
/**
 * public AuthController(SysUserService sysUserService) {
 *     this.sysUserService = sysUserService;
 * }
 */
@Tag(name = "认证授权", description = "登录与用户个人信息查询接口")
public class AuthController {

    private final SysUserService sysUserService;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用账号密码登录并获取 Sa-Token")
    //RequestBody 从前端接受JSON请求体解析数据  Valid 数据检验 校验LoginRequest上的@NotBlank注解
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(sysUserService.login(request));
    }



    @GetMapping("/me")
    @SaCheckLogin //表示这个接口必须是「已登录状态」才能访问； 如果用户没登录（或 token 失效），Sa-Token 会自动拦截请求；
    @Operation(summary = "查询个人信息", description = "返回当前登录用户的基础信息与角色")
    public ApiResponse<Map<String, Object>> me() {
        SysUser user = sysUserService.findByUsername(StpUtil.getLoginIdAsString());//从当前 token 中获取登录用户的 ID（这里存的是用户名）
        return ApiResponse.ok(Map.of(
            "username", user == null ? StpUtil.getLoginIdAsString() : user.getUsername(),
            "role", user == null ? "UNKNOWN" : user.getRole()
        ));
    }
}
