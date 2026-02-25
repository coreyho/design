package com.acme.permission.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SqlFragment {
    private final String sql;
    private final List<Object> params;

    public SqlFragment(String sql, List<Object> params) {
        this.sql = sql;
        this.params = new ArrayList<>(params);
    }

    public static SqlFragment of(String sql, Object... params) {
        List<Object> values = new ArrayList<>();
        Collections.addAll(values, params);
        return new SqlFragment(sql, values);
    }

    public String sql() {
        return sql;
    }

    public List<Object> params() {
        return Collections.unmodifiableList(params);
    }

    public SqlFragment and(SqlFragment right) {
        List<Object> merged = new ArrayList<>(params);
        merged.addAll(right.params);
        return new SqlFragment("(" + sql + ") AND (" + right.sql + ")", merged);
    }

    public SqlFragment or(SqlFragment right) {
        List<Object> merged = new ArrayList<>(params);
        merged.addAll(right.params);
        return new SqlFragment("(" + sql + ") OR (" + right.sql + ")", merged);
    }

    public static SqlFragment not(SqlFragment fragment) {
        return new SqlFragment("NOT (" + fragment.sql + ")", fragment.params);
    }
}
