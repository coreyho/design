package com.acme.permission.engine;

import com.acme.permission.model.SqlFragment;

import java.util.ArrayList;
import java.util.List;

public class SqlInjector {
    public InjectedSql injectWhere(String rawSql, SqlFragment policyExpr) {
        String normalized = rawSql.trim();
        String lower = normalized.toLowerCase();

        String rewritten;
        if (lower.contains(" where ")) {
            rewritten = normalized + " AND (" + policyExpr.sql() + ")";
        } else {
            rewritten = normalized + " WHERE (" + policyExpr.sql() + ")";
        }
        return new InjectedSql(rewritten, new ArrayList<>(policyExpr.params()));
    }

    public record InjectedSql(String sql, List<Object> params) {
    }
}
