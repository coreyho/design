package com.acme.permission.dsl;

import com.acme.permission.model.CompileContext;
import com.acme.permission.model.SqlFragment;

import java.util.Set;

public record CompareCondition(String field, String operator, Object value) implements Condition {
    private static final Set<String> ALLOWED_OPERATORS = Set.of("EQ", "NE", "GT", "GE", "LT", "LE", "LIKE");

    @Override
    public SqlFragment toSql(CompileContext ctx) {
        if (!ALLOWED_OPERATORS.contains(operator)) {
            throw new IllegalArgumentException("Unsupported operator: " + operator);
        }

        Object compiledValue = ctx.resolveValue(value);
        String sqlOperator = switch (operator) {
            case "EQ" -> "=";
            case "NE" -> "!=";
            case "GT" -> ">";
            case "GE" -> ">=";
            case "LT" -> "<";
            case "LE" -> "<=";
            case "LIKE" -> "LIKE";
            default -> throw new IllegalStateException("Unreachable");
        };

        return SqlFragment.of(field + " " + sqlOperator + " ?", compiledValue);
    }
}
