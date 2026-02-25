package com.acme.permission.model;

import java.util.List;
import java.util.Map;

public record CompileContext(
        Long tenantId,
        Long userId,
        List<Long> roleIds,
        Map<String, Object> vars
) {
    public Object resolveValue(Object raw) {
        if (!(raw instanceof String str)) {
            return raw;
        }
        if (!str.startsWith("${") || !str.endsWith("}")) {
            return raw;
        }
        String key = str.substring(2, str.length() - 1);
        if ("current_user.id".equals(key)) {
            return userId;
        }
        if ("tenant.id".equals(key)) {
            return tenantId;
        }
        return vars.get(key);
    }
}
