package com.acme.permission.dsl;

import com.acme.permission.model.CompileContext;
import com.acme.permission.model.SqlFragment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public record InCondition(String field, Collection<?> values) implements Condition {
    @Override
    public SqlFragment toSql(CompileContext ctx) {
        if (values == null || values.isEmpty()) {
            return SqlFragment.of("1 = 0");
        }

        List<Object> params = new ArrayList<>();
        for (Object value : values) {
            params.add(ctx.resolveValue(value));
        }

        String placeholders = params.stream().map(v -> "?").collect(Collectors.joining(", "));
        return new SqlFragment(field + " IN (" + placeholders + ")", params);
    }
}
