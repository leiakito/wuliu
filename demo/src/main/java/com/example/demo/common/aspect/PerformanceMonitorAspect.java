package com.example.demo.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * 性能监控切面
 * 自动记录慢查询和慢操作，便于性能优化分析
 */
@Slf4j
@Aspect
@Component
public class PerformanceMonitorAspect {

    /** 慢查询阈值（毫秒），超过此时间会记录警告日志 */
    private static final long SLOW_QUERY_THRESHOLD = 1000;

    /** 非常慢的查询阈值（毫秒），超过此时间会记录错误日志 */
    private static final long VERY_SLOW_QUERY_THRESHOLD = 3000;

    @Pointcut("execution(* com.example.demo..service..*(..))")
    public void serviceLayer() {}

    @Pointcut("execution(* com.example.demo..mapper..*(..))")
    public void mapperLayer() {}

    @Around("serviceLayer() || mapperLayer()")
    public Object monitorPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        String fullMethodName = className + "." + methodName;

        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;

            // 记录慢查询
            if (executionTime >= VERY_SLOW_QUERY_THRESHOLD) {
                log.error("⚠️ 非常慢的操作 - {} 耗时: {}ms, 参数: {}",
                    fullMethodName, executionTime, formatArgs(joinPoint.getArgs()));
            } else if (executionTime >= SLOW_QUERY_THRESHOLD) {
                log.warn("⏱️ 慢查询 - {} 耗时: {}ms, 参数: {}",
                    fullMethodName, executionTime, formatArgs(joinPoint.getArgs()));
            } else if (log.isDebugEnabled()) {
                // 在 debug 级别记录所有操作的执行时间
                log.debug("✅ {} 耗时: {}ms", fullMethodName, executionTime);
            }
        }
    }

    /**
     * 格式化参数，避免日志过长
     */
    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length && i < 3; i++) { // 最多显示前3个参数
            if (i > 0) {
                sb.append(", ");
            }
            Object arg = args[i];
            if (arg == null) {
                sb.append("null");
            } else {
                String argStr = arg.toString();
                // 截断过长的参数
                if (argStr.length() > 100) {
                    sb.append(argStr, 0, 100).append("...");
                } else {
                    sb.append(argStr);
                }
            }
        }
        if (args.length > 3) {
            sb.append(", ... (").append(args.length).append(" 个参数)");
        }
        sb.append("]");
        return sb.toString();
    }
}
