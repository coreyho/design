package com.acme.permission.engine;

import com.acme.permission.model.CompileContext;
import com.acme.permission.model.PolicyEffect;
import com.acme.permission.model.PolicyRule;
import com.acme.permission.model.SqlFragment;

import java.util.List;
import java.util.stream.Collectors;

public class RolePolicyMerger {

    public SqlFragment mergeRowPolicies(List<PolicyRule> policies, String resourceCode, CompileContext ctx) {
        List<SqlFragment> allows = policies.stream()
                .filter(policy -> policy.resourceCode().equals(resourceCode))
                .filter(policy -> policy.effect() == PolicyEffect.ALLOW)
                .map(policy -> policy.condition().toSql(ctx))
                .collect(Collectors.toList());

        List<SqlFragment> denies = policies.stream()
                .filter(policy -> policy.resourceCode().equals(resourceCode))
                .filter(policy -> policy.effect() == PolicyEffect.DENY)
                .map(policy -> policy.condition().toSql(ctx))
                .collect(Collectors.toList());

        if (allows.isEmpty()) {
            return SqlFragment.of("1 = 0");
        }

        SqlFragment allowExpr = allows.stream().reduce(SqlFragment::or).orElseThrow();
        if (denies.isEmpty()) {
            return allowExpr;
        }

        SqlFragment denyExpr = denies.stream().reduce(SqlFragment::or).orElseThrow();
        return allowExpr.and(SqlFragment.not(denyExpr));
    }
}
