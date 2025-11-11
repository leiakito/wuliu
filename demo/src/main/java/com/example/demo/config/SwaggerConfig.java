package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI logisticsOpenAPI() {
        Info info = new Info()
            .title("物流结账系统 API 文档")
            .version("v1.0")
            .description("基于 Spring Boot + MyBatis Plus 的物流结账管理系统接口文档")
            .termsOfService("https://example.com/terms")
            .license(new License().name("Apache 2.0").url("https://springdoc.org"));

        return new OpenAPI().info(info);
    }
}
