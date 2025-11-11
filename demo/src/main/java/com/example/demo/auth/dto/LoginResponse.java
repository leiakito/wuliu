package com.example.demo.auth.dto;

import lombok.Data;

//用于返回数据给前端
@Data
public class LoginResponse {
    private String token;
    private String role;
    private String username;
}
