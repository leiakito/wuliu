package com.example.demo.config;

import cn.dev33.satoken.filter.SaServletFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SaTokenConfig {

    @Bean
    public SaServletFilter saServletFilter() {
        return new SaServletFilter()
            .addInclude("/api/**")// /api 开头的接口都需要经过权限校验。
            .addExclude("/api/auth/login")//放开接口
            .setAuth(obj -> {
                // 统一权限交给注解处理
                //SaCheckLogin  @SaCheckRole("admin") @SaCheckPermission("user.add")
            });
    }
}
