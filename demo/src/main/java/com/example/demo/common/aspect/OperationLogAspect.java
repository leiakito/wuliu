package com.example.demo.common.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.example.demo.common.annotation.LogOperation;
import com.example.demo.log.entity.SysLog;
import com.example.demo.log.service.SysLogService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j //Lombok 提供的日志对象 log，可以直接用 log.info() 打印日志。
@Aspect //表示这是一个 切面类（Aspect），用于 AOP。
@Component //@Component让 Spring 管理这个类（自动装配）。
@RequiredArgsConstructor //自动生成构造函数，注入 final 字段 SysLogService

public class OperationLogAspect {

    //日志保存数据库的服务类
    private final SysLogService sysLogService;
    //目标方法执行 成功执行之后,执行 只拦截@logOperation注解的方法 , returning = "result" 目标的返回值
    //当你调用任意一个带 @LogOperation("xxx") 的方法，
    //这个 afterOperation() 方法会在方法执行结束后自动执行。

    @AfterReturning(value = "@annotation(logOperation)", returning = "result")
    public void afterOperation(JoinPoint joinPoint, LogOperation logOperation, Object result) {
        SysLog logEntity = new SysLog();
        logEntity.setAction(logOperation.value()); //获取注解描述
        logEntity.setCreatedAt(LocalDateTime.now());//获取时间
        logEntity.setDetail(buildDetail(joinPoint, result));//获取方法名和 信息
        logEntity.setUsername(currentUser());//当前用户
        logEntity.setIp(clientIp());//请求Ip
        sysLogService.save(logEntity);//保存到数据库
    }
    //	如果当前用户已登录 → 返回用户名；否则 → 返回 "anonymous"（匿名）。
    private String currentUser() {
        return StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : "anonymous";
    }

    private String clientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "N/A";
        }
        HttpServletRequest request = attrs.getRequest();
        String realIp = request.getHeader("X-Forwarded-For");
        return realIp == null ? request.getRemoteAddr() : realIp.split(",")[0];
    }

    private String buildDetail(JoinPoint joinPoint, Object result) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "#" + signature.getMethod().getName();
    }
}

/*
┌────────────────────────────────────────────┐
│     调用一个业务方法：deleteUser()        │
│   ↓                                        │
│   方法上有注解：@LogOperation("删除用户")   │
│   ↓                                        │
│  执行完方法（成功返回）                    │
│   ↓                                        │
│  触发 AOP 切面 OperationLogAspect.afterOperation() │
│   ↓                                        │
│  收集信息：操作名、方法名、时间、用户、IP │
│   ↓                                        │
│  调用 sysLogService.save(logEntity) 写数据库 │
└────────────────────────────────────────────┘
 */