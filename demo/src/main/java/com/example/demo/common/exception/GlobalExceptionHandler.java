package com.example.demo.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.example.demo.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException ex) {
        log.warn("业务异常: {}", ex.getMessage());
        return ApiResponse.error(ex.getErrorCode().getCode(), ex.getMessage());
    }

    @ExceptionHandler({
        BindException.class,
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        IllegalArgumentException.class
    })
    public ApiResponse<Void> handleBadRequest(Exception ex) {
        log.warn("参数异常", ex);
        return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), ex.getMessage());
    }

    @ExceptionHandler({NotLoginException.class})
    public ApiResponse<Void> handleNotLogin(NotLoginException ex) {
        return ApiResponse.error(ErrorCode.UNAUTHORIZED.getCode(), ex.getMessage());
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ApiResponse<Void> handleForbid(Exception ex) {
        return ApiResponse.error(ErrorCode.FORBIDDEN.getCode(), ex.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResponse<Void> handleDuplicate(DuplicateKeyException ex) {
        return ApiResponse.error(ErrorCode.DUPLICATE.getCode(), "数据已存在");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOthers(Exception ex) {
        log.error("系统异常", ex);
        return ApiResponse.error(ErrorCode.SYSTEM_ERROR.getCode(), "系统异常，请稍后再试");
    }
}
