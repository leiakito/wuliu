package com.example.demo.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.auth.dto.LoginRequest;
import com.example.demo.auth.dto.LoginResponse;
import com.example.demo.auth.dto.UserRequest;
import com.example.demo.auth.entity.SysUser;
import com.example.demo.auth.mapper.SysUserMapper;
import com.example.demo.auth.service.SysUserService;
import com.example.demo.common.exception.BusinessException;
import com.example.demo.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor ////生成包含所有final字段的构造函数
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional //@Transactional：说明这个方法是一个事务方法 如果抛出异常 数据库自动回滚
    public LoginResponse login(LoginRequest request) {
        SysUser user = findByUsername(request.getUsername());
        if (user == null || !"ENABLED".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或已禁用");
        }
        if (!matchesPassword(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!user.getPassword().startsWith("$2")) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            sysUserMapper.updateById(user);
        }
        StpUtil.login(user.getUsername()); // sa——token 为用户生成唯一token token用于后续接口认证 存在浏览器Header里
        user.setLastLogin(LocalDateTime.now()); //获取最后登录时间
        sysUserMapper.updateById(user); //新随后登录时间 保存用户登录行为

        //返回对象 填入生成的token 角色和用户名 返回前端
        LoginResponse response = new LoginResponse();
        response.setToken(StpUtil.getTokenValue());
        response.setRole(user.getRole());
        response.setUsername(user.getUsername());
        return response;
    }

    @Override
    @Transactional //@Transactional：说明这个方法是一个事务方法 如果抛出异常 数据库自动回滚
    public SysUser create(UserRequest request) {
        if (exists(request.getUsername())) {
            throw new BusinessException(ErrorCode.DUPLICATE, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setFullName(request.getFullName());
        user.setRole(request.getRole() == null ? "USER" : request.getRole());
        user.setStatus(request.getStatus() == null ? "ENABLED" : request.getStatus());
        String rawPassword = StringUtils.hasText(request.getPassword()) ? request.getPassword() : "ChangeMe123!";
        user.setPassword(passwordEncoder.encode(rawPassword));
        sysUserMapper.insert(user);
        return user;
    }

    @Override
    @Transactional //@Transactional：说明这个方法是一个事务方法 如果抛出异常 数据库自动回滚
    public void update(Long id, UserRequest request) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        user.setFullName(request.getFullName());
        if (StringUtils.hasText(request.getRole())) {
            user.setRole(request.getRole());
        }
        if (StringUtils.hasText(request.getStatus())) {
            user.setStatus(request.getStatus());
        }
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        sysUserMapper.updateById(user);
    }

    @Override
    @Transactional //@Transactional：说明这个方法是一个事务方法 如果抛出异常 数据库自动回滚
    public void resetPassword(Long id, String newPassword) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(user);
    }

    @Override
    public List<SysUser> list() {
        return sysUserMapper.selectList(null);
    }

    @Override
    public SysUser findByUsername(String username) {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, username));
    }

    @Override
    @Transactional //@Transactional：说明这个方法是一个事务方法 如果抛出异常 数据库自动回滚
    public void delete(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw  new BusinessException(ErrorCode.NOT_FOUND,"用户不存在");
        }
        user.setDeleted(1);//标记为已删除
        sysUserMapper.updateById(user);

    }

    private boolean exists(String username) {
        return sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)) > 0;
    }

    private boolean matchesPassword(String raw, String encoded) {
        return passwordEncoder.matches(raw, encoded) || raw.equals(encoded);
    }
}
