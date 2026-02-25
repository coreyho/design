import com.acme.permission.dsl.CompareCondition;
import com.acme.permission.dsl.GroupCondition;
import com.acme.permission.engine.RolePolicyMerger;
import com.acme.permission.engine.SqlInjector;
import com.acme.permission.model.CompileContext;
import com.acme.permission.model.PolicyEffect;
import com.acme.permission.model.PolicyRule;
import com.acme.permission.model.SqlFragment;

import java.util.List;
import java.util.Map;

public class PermissionDemo {
    public static void main(String[] args) {
        CompileContext ctx = new CompileContext(10L, 2001L, List.of(1L, 2L), Map.of("env.region", "cn"));

        PolicyRule allowOwner = new PolicyRule(
                "p-allow-owner",
                "orders",
                PolicyEffect.ALLOW,
                new CompareCondition("owner_id", "EQ", "${current_user.id}")
        );

        PolicyRule allowPublished = new PolicyRule(
                "p-allow-published",
                "orders",
                PolicyEffect.ALLOW,
                new CompareCondition("status", "EQ", "published")
        );

        PolicyRule denyClosed = new PolicyRule(
                "p-deny-closed",
                "orders",
                PolicyEffect.DENY,
                new GroupCondition("AND", List.of(
                        new CompareCondition("status", "EQ", "closed"),
                        new CompareCondition("tenant_id", "EQ", "${tenant.id}")
                ))
        );

        RolePolicyMerger merger = new RolePolicyMerger();
        SqlFragment mergedExpr = merger.mergeRowPolicies(List.of(allowOwner, allowPublished, denyClosed), "orders", ctx);

        SqlInjector injector = new SqlInjector();
        SqlInjector.InjectedSql injected = injector.injectWhere("SELECT * FROM orders", mergedExpr);

        System.out.println("SQL: " + injected.sql());
        System.out.println("Params: " + injected.params());
    }
}
