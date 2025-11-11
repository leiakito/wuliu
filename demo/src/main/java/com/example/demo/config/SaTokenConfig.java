package com.example.demo.config;

import cn.dev33.satoken.filter.SaServletFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SaTokenConfig {

    @Bean
    public SaServletFilter saServletFilter() {
        return new SaServletFilter()
            .addInclude("/api/**")
            .addExclude("/api/auth/login")
            .setAuth(obj -> {
                // 统一权限交给注解处理
            });
    }
}
