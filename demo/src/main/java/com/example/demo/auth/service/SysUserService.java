package com.example.demo.auth.service;

import com.example.demo.auth.dto.LoginRequest;
import com.example.demo.auth.dto.LoginResponse;
import com.example.demo.auth.dto.UserRequest;
import com.example.demo.auth.entity.SysUser;
import java.util.List;

public interface SysUserService {

    LoginResponse login(LoginRequest request);

    SysUser create(UserRequest request);

    void update(Long id, UserRequest request);

    void resetPassword(Long id, String newPassword);

    List<SysUser> list();

    SysUser findByUsername(String username);

    void delete(Long id);
}
