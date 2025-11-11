package com.example.demo.order.util;

import org.springframework.util.StringUtils;

public final class TrackingCategoryUtil {

    private TrackingCategoryUtil() {
    }

    public static String resolve(String trackingNumber) {
        if (!StringUtils.hasText(trackingNumber)) {
            return "其他";
        }
        String upper = trackingNumber.trim().toUpperCase();
        if (upper.startsWith("SF")) {
            return "顺丰";
        }
        if (upper.startsWith("JDX") || upper.startsWith("JDA") || upper.startsWith("JD")) {
            return "京东";
        }
        if (upper.startsWith("YT")) {
            return "圆通";
        }
        if (upper.startsWith("7353")) {
            return "中通";
        }
        if (upper.startsWith("77203")) {
            return "申通";
        }
        if (upper.startsWith("43415") || upper.startsWith("31250")) {
            return "韵达";
        }
        if (upper.startsWith("JT")) {
            return "极兔";
        }
        if (upper.startsWith("DPK")) {
            return "德邦快递";
        }
        if (upper.startsWith("DPL")) {
            return "德邦物流";
        }
        if (upper.startsWith("KYE")) {
            return "跨越速运";
        }
        if (upper.startsWith("EMS") || (isNumeric(upper) && upper.length() == 13)) {
            return "EMS";
        }
        if ((upper.startsWith("6") || upper.startsWith("5") || upper.startsWith("00"))
            && isNumeric(upper) && upper.length() == 14) {
            return "天天快递";
        }
        return "其他";
    }

    private static boolean isNumeric(String input) {
        for (int i = 0; i < input.length(); i++) {
            if (!Character.isDigit(input.charAt(i))) {
                return false;
            }
        }
        return input.length() > 0;
    }
}
