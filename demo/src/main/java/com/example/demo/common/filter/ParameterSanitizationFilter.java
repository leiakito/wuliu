package com.example.demo.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 参数清理过滤器 - 移除查询参数中的非法字符
 * 主要用于清理从前端传递过来的可能包含特殊字符的数字参数
 */
@Component
public class ParameterSanitizationFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            SanitizedRequestWrapper wrappedRequest = new SanitizedRequestWrapper(httpRequest);
            chain.doFilter(wrappedRequest, response);
        } else {
            chain.doFilter(request, response);
        }
    }

    /**
     * 包装 HttpServletRequest 以清理参数值
     */
    private static class SanitizedRequestWrapper extends HttpServletRequestWrapper {

        private final Map<String, String[]> sanitizedParams;

        public SanitizedRequestWrapper(HttpServletRequest request) {
            super(request);
            this.sanitizedParams = sanitizeParameters(request.getParameterMap());
        }

        @Override
        public String getParameter(String name) {
            String[] values = sanitizedParams.get(name);
            return (values != null && values.length > 0) ? values[0] : null;
        }

        @Override
        public String[] getParameterValues(String name) {
            return sanitizedParams.get(name);
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return sanitizedParams;
        }

        /**
         * 清理参数值 - 对于数字类型的参数（page, size），移除所有非数字字符
         */
        private Map<String, String[]> sanitizeParameters(Map<String, String[]> originalParams) {
            Map<String, String[]> cleaned = new HashMap<>();

            for (Map.Entry<String, String[]> entry : originalParams.entrySet()) {
                String key = entry.getKey();
                String[] values = entry.getValue();

                if (values == null || values.length == 0) {
                    cleaned.put(key, values);
                    continue;
                }

                // 对于已知的数字参数，清理非数字字符
                if (isNumericParameter(key)) {
                    String[] cleanedValues = new String[values.length];
                    for (int i = 0; i < values.length; i++) {
                        cleanedValues[i] = sanitizeNumericValue(values[i]);
                    }
                    cleaned.put(key, cleanedValues);
                } else {
                    // 其他参数保持原样
                    cleaned.put(key, values);
                }
            }

            return cleaned;
        }

        /**
         * 判断是否是数字类型的参数
         */
        private boolean isNumericParameter(String paramName) {
            return "page".equals(paramName) ||
                   "size".equals(paramName) ||
                   "limit".equals(paramName) ||
                   "offset".equals(paramName);
        }

        /**
         * 清理数字值 - 移除所有非数字字符
         */
        private String sanitizeNumericValue(String value) {
            if (value == null) {
                return null;
            }
            // 移除所有非数字字符
            String cleaned = value.replaceAll("[^\\d]", "");
            // 如果清理后为空，返回原值（让后续验证处理）
            return cleaned.isEmpty() ? value : cleaned;
        }
    }
}
