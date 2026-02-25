package com.acme.permission.dsl;

import com.acme.permission.model.CompileContext;
import com.acme.permission.model.SqlFragment;

import java.util.List;

public record GroupCondition(String operator, List<Condition> children) implements Condition {
    @Override
    public SqlFragment toSql(CompileContext ctx) {
        if (children == null || children.isEmpty()) {
            return SqlFragment.of("1 = 1");
        }

        return switch (operator) {
            case "AND" -> children.stream()
                    .map(child -> child.toSql(ctx))
                    .reduce((left, right) -> left.and(right))
                    .orElse(SqlFragment.of("1 = 1"));
            case "OR" -> children.stream()
                    .map(child -> child.toSql(ctx))
                    .reduce((left, right) -> left.or(right))
                    .orElse(SqlFragment.of("1 = 0"));
            case "NOT" -> {
                if (children.size() != 1) {
                    throw new IllegalArgumentException("NOT group must have exactly one child");
                }
                yield SqlFragment.not(children.get(0).toSql(ctx));
            }
            default -> throw new IllegalArgumentException("Unsupported group operator: " + operator);
        };
    }
}
