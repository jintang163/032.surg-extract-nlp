package com.surg.extract.common;

import cn.hutool.core.util.StrUtil;

public class UserContext {

    private static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();

    public static void setRole(String role) {
        CURRENT_ROLE.set(role);
    }

    public static String getRole() {
        String role = CURRENT_ROLE.get();
        return StrUtil.isNotBlank(role) ? role : "ADMIN";
    }

    public static boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(getRole());
    }

    public static void checkAdmin() {
        if (!isAdmin()) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
    }

    public static void clear() {
        CURRENT_ROLE.remove();
    }
}
