package com.lukou.decision.util;

import com.lukou.decision.common.BusinessException;

/**
 * 当前登录用户上下文，由拦截器写入。
 */
public final class UserContext {

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId) {
        HOLDER.set(userId);
    }

    public static Long get() {
        return HOLDER.get();
    }

    public static Long require() {
        Long id = HOLDER.get();
        if (id == null) {
            throw new BusinessException(401, "请先登录");
        }
        return id;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
