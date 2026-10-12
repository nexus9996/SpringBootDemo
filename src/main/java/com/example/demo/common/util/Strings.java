package com.example.demo.common.util;

/**
 * 轻量字符串工具。
 *
 * <p>不引入第三方依赖，只保留项目真正使用的语义；脱敏方法用于对外输出（DTO）前的敏感字段处理。
 */
public final class Strings {

    private static final int MOBILE_LENGTH = 11;

    private Strings() {
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    /**
     * 手机号脱敏：{@code 13812345678 -> 138****5678}，带国家码时保留前缀（{@code +8613812345678 -> +86138****5678}）。
     * 非大陆手机号原样返回。
     */
    public static String maskPhone(String phone) {
        var national = nationalNumber(phone);
        if (national == null) {
            return phone;
        }
        var prefix = phone.substring(0, phone.length() - MOBILE_LENGTH);
        return prefix + national.substring(0, 3) + "****" + national.substring(7);
    }

    /**
     * 邮箱脱敏：{@code alice@example.com -> a***e@example.com}，本地部分不超过 2 位时只保留首字符。
     * 非法邮箱原样返回。
     */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        var at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return email;
        }
        var local = email.substring(0, at);
        var masked = local.length() <= 2
                ? local.charAt(0) + "***"
                : local.charAt(0) + "***" + local.charAt(local.length() - 1);
        return masked + email.substring(at);
    }

    /** 取出不带国家码的大陆手机号，非法号码返回 {@code null}。 */
    public static String nationalNumber(String phone) {
        if (phone == null) {
            return null;
        }
        var national = phone.startsWith("+86") ? phone.substring(3)
                : phone.startsWith("86") ? phone.substring(2)
                : phone;
        return isMainlandMobile(national) ? national : null;
    }

    /** 是否为大陆手机号（允许 {@code +86} / {@code 86} 前缀）。 */
    public static boolean isMobilePhone(String phone) {
        return nationalNumber(phone) != null;
    }

    private static boolean isMainlandMobile(String national) {
        if (national.length() != MOBILE_LENGTH || national.charAt(0) != '1') {
            return false;
        }
        var second = national.charAt(1);
        if (second < '3' || second > '9') {
            return false;
        }
        for (var i = 2; i < national.length(); i++) {
            if (!Character.isDigit(national.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
