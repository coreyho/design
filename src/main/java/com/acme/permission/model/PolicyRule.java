package com.acme.permission.model;

import com.acme.permission.dsl.Condition;

public record PolicyRule(
        String policyId,
        String resourceCode,
        PolicyEffect effect,
        Condition condition
) {
}
