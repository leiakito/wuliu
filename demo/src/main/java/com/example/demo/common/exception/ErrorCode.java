package com.example.demo.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    BAD_REQUEST("BAD_REQUEST", "请求参数错误"),
    UNAUTHORIZED("UNAUTHORIZED", "未登录或登录已过期"),
    FORBIDDEN("FORBIDDEN", "没有访问权限"),
    NOT_FOUND("NOT_FOUND", "数据不存在"),
    DUPLICATE("DUPLICATE", "数据重复"),
    OPTIMISTIC_LOCK_CONFLICT("OPTIMISTIC_LOCK_CONFLICT", "数据已被其他用户修改，请刷新后重试"),
    SYSTEM_ERROR("SYSTEM_ERROR", "系统异常");

    private final String code;
    private final String defaultMessage;
}
