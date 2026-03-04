# SaaS 平台数据权限落地实施设计（DSL + MyBatis）

## 1. 目标与边界

### 1.1 设计目标
- 支持 **行级权限（Data Scope）**、**列级权限（Field Scope）**、**脱敏规则（Masking）**。
- 支持 **一用户多角色**，角色权限可组合，且支持 `ALLOW / DENY`。
- 支持低代码场景：查询来源于 **DSL + 可视化构造**，运行时由 MyBatis 执行。
- 支持子查询、JOIN、CTE、UNION 等复杂 SQL 的权限注入。
- 支持控制面发布、数据面缓存、灰度发布、规则回滚。

### 1.2 非目标（MVP 阶段）
- 不支持最终用户直接编写原始 SQL（避免 SQL 重写复杂度失控）。
- 不在应用层做行级内存过滤（仅允许列脱敏在结果集阶段执行）。

### 1.3 核心原则
1. **行级权限必须在数据库层过滤**（SQL 注入条件），禁止内存过滤。
2. **权限规则必须结构化 DSL 存储**，禁止存 SQL 字符串。
3. **运行时使用参数绑定**，禁止权限值直接拼接进 SQL。
4. **统一注入入口**：所有查询必须经过统一 QueryBuilder/MyBatis 拦截器。

---

## 2. 总体架构（先不拆分控制面/运行面）

```text
                 单体权限服务（Monolith）
┌────────────────────────────────────────────────────┐
│ 权限可视化配置 + DSL 管理 + 发布 + 执行引擎            │
│                                                    │
│  ├─ PolicyDesigner（规则配置）                       │
│  ├─ PolicyStore（DSL 存储/版本）                     │
│  ├─ RuleCache（规则缓存）                            │
│  ├─ PolicyCompiler（DSL -> AST）                    │
│  ├─ RoleMerger（多角色合并）                         │
│  ├─ SqlInjector（MyBatis Interceptor）              │
│  └─ MaskingEngine（列脱敏）                          │
└───────────────────────┬────────────────────────────┘
                        ▼
                   MyBatis / DB
```

## 2.1 当前阶段落地建议（单体优先）
- 当前阶段将“规则配置、规则发布、规则执行”放在同一服务内，先保证能力闭环。
- 规则发布可先用本地版本号 + 数据库表实现，不强依赖 MQ/配置中心。
- 查询执行统一走同一个 MyBatis 拦截器，避免多入口绕过。
- 当租户规模、组织复杂度增长后，再平滑演进为控制面/运行面分离架构。

---

## 3. 权限模型（ER）

## 3.1 表结构

### `user`
- `id`
- `tenant_id`
- `username`
- `status`

### `role`
- `id`
- `tenant_id`
- `name`
- `scope_type`（GLOBAL / APP）

### `user_role`
- `user_id`
- `role_id`

### `policy`
- `id`
- `tenant_id`
- `app_id`
- `name`
- `effect`（ALLOW / DENY）
- `resource_code`（如 `orders`、`employee`）
- `policy_type`（ROW / COLUMN / MASK）
- `dsl_json`
- `version`
- `status`

### `role_policy`
- `role_id`
- `policy_id`

### `policy_publish_log`
- `id`
- `app_id`
- `version`
- `operator`
- `published_at`
- `rollback_from_version`

## 3.2 关系
- User ↔ Role：多对多。
- Role ↔ Policy：多对多。
- Policy 按 `app_id + version` 做发布快照。

---

## 4. DSL 规范（结构化、可递归）

## 4.1 统一节点模型
- `group`：逻辑组（AND / OR / NOT）
- `compare`：字段比较（EQ/NE/GT/GE/LT/LE/LIKE/BETWEEN）
- `in`：集合匹配（常量集合 / 子查询）
- `exists`：存在性子查询
- `func`：受控函数（如 `dept_descendants(current_user.dept_id)`）

## 4.2 示例：行级策略 DSL

```json
{
  "type": "group",
  "operator": "AND",
  "children": [
    {
      "type": "compare",
      "field": "status",
      "operator": "EQ",
      "value": "published"
    },
    {
      "type": "group",
      "operator": "OR",
      "children": [
        {
          "type": "compare",
          "field": "owner_id",
          "operator": "EQ",
          "value": "${current_user.id}"
        },
        {
          "type": "in",
          "field": "dept_id",
          "subquery": {
            "select": ["id"],
            "from": "dept",
            "where": {
              "type": "compare",
              "field": "manager_id",
              "operator": "EQ",
              "value": "${current_user.id}"
            }
          }
        }
      ]
    }
  ]
}
```

## 4.3 变量规则
- 支持：`current_user.*`、`tenant.*`、`env.*`、`root.*`。
- 变量解析发生在编译阶段，结果以参数绑定形式进入 SQL。
- 禁止 DSL 内嵌原始 SQL 字符串。

---

## 5. 多角色合并策略

## 5.1 默认规则（行级）
- 用户拥有多个角色时，**同一资源的 ALLOW 规则按 OR 合并**。
- 若引入 DENY，最终表达式为：

```text
FINAL = (ALLOW_1 OR ALLOW_2 OR ...)
        AND NOT (DENY_1 OR DENY_2 OR ...)
```

## 5.2 字段级（列权限）
- 可见列集合：多个角色按并集。
- 禁止列集合：若存在 DENY 列，优先剔除。

## 5.3 冲突优先级
1. SuperAdmin（平台保留）
2. DENY
3. ALLOW
4. 默认拒绝（未命中任何 ALLOW）

---

## 6. 运行时链路（MyBatis）

## 6.1 处理流程
1. 读取用户上下文（tenant、userId、roleIds、appId）。
2. 从缓存取该用户角色组合对应的已编译规则。
3. MyBatis `StatementHandler#prepare` 拦截原 SQL。
4. 对 SQL 做 AST 解析（JSQLParser / Druid Parser）。
5. 将策略 AST 注入 SQL AST（递归处理主查询、子查询、CTE、UNION）。
6. 生成新 SQL 与参数列表，回写 `BoundSql`。
7. 执行 SQL，返回结果。
8. 如有列脱敏规则，在结果映射阶段处理。

## 6.2 注入规则
- 仅对目标 `resource_code` 对应表/视图注入。
- 维持原 SQL 业务条件不变，以 `AND (policy_expr)` 合并。
- 子查询若命中同一资源，必须递归注入，防止绕过。
- UNION 每个 SELECT 分支都要注入。

## 6.3 伪代码

```java
void injectPolicy(SelectNode node, PolicyExpr expr, TargetTable table) {
    if (containsTarget(node, table)) {
        node.where = (node.where == null)
            ? expr
            : and(node.where, expr);
    }
    for (SelectNode sub : node.subQueries()) {
        injectPolicy(sub, expr, table);
    }
    for (SelectNode unionPart : node.unionParts()) {
        injectPolicy(unionPart, expr, table);
    }
    for (CteNode cte : node.ctes()) {
        injectPolicy(cte.query(), expr, table);
    }
}
```

## 6.4 修改/删除（UPDATE/DELETE）权限控制放在哪一层

> 结论：**修改/删除的权限控制必须在数据面执行层（MyBatis 拦截 + SQL AST 注入）强制做**，
> 同时在控制面定义策略，在接口层做前置授权校验。三层协同，但“最终兜底”在 SQL 执行层。

### 分层职责（推荐）
1. **控制面（策略定义层）**
   - 定义 `UPDATE_SCOPE` / `DELETE_SCOPE` 规则（DSL）。
   - 与 `ROW_READ_SCOPE` 分离，避免“可读即可删/改”的隐式放权。

2. **接口层（应用服务层）**
   - 做动作级权限校验（是否允许执行 update/delete 动作）。
   - 用于快速失败与友好提示，但不能作为唯一安全边界。

3. **数据层（强制执行层，必须）**
   - 在 MyBatis 拦截器中识别 `UPDATE` / `DELETE` 语句。
   - 将策略条件注入到 `WHERE`，确保只影响有权限的数据行。
   - 若原 SQL 无 `WHERE`，注入后必须变为 `WHERE <policy_expr>`，禁止全表写操作。

### UPDATE/DELETE 注入示例

原 SQL：

```sql
DELETE FROM orders WHERE status = 'draft'
```

策略：`owner_id = ${current_user.id}`

注入后：

```sql
DELETE FROM orders
WHERE status = ?
  AND owner_id = ?
```

### 工程红线
- 禁止仅在前端按钮可见性控制“删除/修改”。
- 禁止仅在 Service 层 if/else 判断后直连 Mapper 执行原 SQL。
- 导入/批处理/定时任务同样必须走统一拦截器链路。

### 建议扩展
- 增加 `AFFECTED_ROWS_GUARD`：若预期单条更新却影响多行，自动回滚并告警。
- 审计记录 `before/after` 摘要与策略命中信息，满足合规追溯。

---

## 7. 关键实现模块

## 7.1 单体服务内部模块划分
- `PolicyDesigner`：可视化策略编辑器（条件树 UI）。
- `PolicyValidator`：字段白名单、操作符合法性、子查询深度校验。
- `PolicyVersionService`：版本生成、灰度发布、回滚。
- `RuleRepository`：读取规则快照。

## 7.2 数据结构建议（Java）


```java
interface Condition {
    SqlFragment toSql(CompileContext ctx);
}

class SqlFragment {
    String sql;
    List<Object> params;
}

class CompileContext {
    Long tenantId;
    Long userId;
    List<Long> roleIds;
    Map<String, Object> vars;
}
```

---

## 8. 安全设计

## 8.1 防绕过
- 所有 Repository/Mapper 查询必须经过统一数据访问层。
- 禁止“导出接口直连 Mapper”绕过拦截器。
- 对关键接口增加“权限注入已执行”审计标记。

## 8.2 防注入
- 规则值全参数化，禁止拼字面量。
- 字段名、表名来自元数据白名单，不接受前端自由输入。
- 受控函数采用注册表机制，不允许任意函数调用。

## 8.3 可审计
- 记录 `policy_hit_log`：用户、资源、命中规则、最终 SQL 哈希、耗时。
- 提供 `explain` 接口：返回命中/未命中条件，便于排障。

---

## 9. 性能与缓存

## 9.1 缓存层次
1. 用户角色缓存：`userId -> roleIds`
2. 规则快照缓存：`appId + version -> rules`
3. 编译结果缓存：`appId + roleSetHash + resource -> CompiledPolicy`

## 9.2 失效策略

- 在单体服务内发布新版本后，直接刷新本地缓存版本号。
- 如后续拆分多节点，再通过 MQ/配置中心广播失效 cache key。
- 采用双缓冲：新版本预热成功后原子切换。

## 9.3 性能指标（建议）
- 权限编译命中缓存率 > 95%
- 拦截器额外开销 P95 < 8ms
- 复杂查询注入后 SQL 长度增长 < 30%

---

## 10. MVP 分阶段实施计划

## Phase 1（2~4 周）
- 行级权限：`compare/group/in(单层子查询)`
- 多角色 OR 合并（仅 ALLOW）
- MyBatis 拦截注入（SELECT）
- 字段可见性控制（查询列裁剪）

## Phase 2（4~6 周）
- 引入 DENY 规则
- 支持 EXISTS、多层子查询
- 支持 UNION / CTE 注入
- 增加 explain 与命中日志

## Phase 3（持续演进）
- 列脱敏策略中心
- 权限灰度发布
- 权限仿真回放（上线前验证）
- 跨数据源统一策略编译

---

## 11. MyBatis 集成示例（简化）

```java
@Intercepts({
  @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class MybatisPermissionInterceptor implements Interceptor {

  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    StatementHandler handler = (StatementHandler) PluginUtils.realTarget(invocation.getTarget());
    BoundSql boundSql = handler.getBoundSql();

    UserContext ctx = UserContextHolder.get();
    CompiledPolicy policy = ruleCache.getCompiled(ctx.appId(), ctx.roleSetHash(), "orders");

    String rawSql = boundSql.getSql();
    SqlAst ast = sqlParser.parse(rawSql);
    SqlAst merged = sqlInjector.inject(ast, policy);

    BoundSqlUtils.rewrite(boundSql, merged.toSql(), merged.params());
    return invocation.proceed();
  }
}
```

---

## 12. 上线检查清单（Go-Live Checklist）
- [ ] 所有查询入口均接入统一拦截器。
- [ ] 导出、报表、分页、聚合接口已覆盖权限注入测试。
- [ ] 规则发布/回滚链路已演练。
- [ ] explain 接口可用于线上排障。
- [ ] 关键表已建立权限相关索引（如 `tenant_id`, `owner_id`, `dept_id`）。
- [ ] 安全测试覆盖 SQL 注入、权限绕过、越权读取。

---

## 13. 最终结论
- 对于 `DSL + MyBatis` 的低代码 SaaS 平台，最可落地且可规模化的方案是：
  - **可视化构造 + 结构化 DSL 存储 + MyBatis AST 注入执行**。
  - 行级权限全部下推数据库。
  - 多角色按 `ALLOW OR` 合并，并预留 `DENY` 机制。
  - 先以单体服务内的版本化与缓存机制落地，后续可扩展为多集群动态更新。

该方案可从 MVP 快速起步，并平滑演进到企业级权限引擎。


---

## 14. UPDATE/DELETE 落地测试矩阵（实施必做）

为保证“修改/删除权限在执行层强制生效”，上线前至少覆盖以下用例：

| 类别 | 用例 | 预期结果 |
|---|---|---|
| UPDATE | 用户更新本人创建数据 | `affected_rows > 0`，更新成功 |
| UPDATE | 用户更新他人数据 | `affected_rows = 0`，无越权更新 |
| UPDATE | 原 SQL 无 WHERE | 自动注入权限 WHERE，不允许全表更新 |
| DELETE | 用户删除本人草稿 | 删除成功且仅影响授权数据 |
| DELETE | 用户删除他人草稿 | `affected_rows = 0`，删除失败 |
| DELETE | 批量删除 + IN ids | 最终仅删除命中权限的数据子集 |
| 导出/任务 | 定时任务删除历史数据 | 仍走拦截器，命中任务身份策略 |
| 回归 | 关闭拦截器开关演练 | 平台阻断启动或降级到只读模式 |

### 14.1 建议自动化断言
- SQL 断言：拦截后 SQL 必含 `policy_expr`。
- 参数断言：权限变量全部走参数绑定，不出现字面拼接。
- 结果断言：无权限场景 `affected_rows = 0`。
- 安全断言：审计日志必须记录 policyId、userId、resource、sqlHash。

### 14.2 变更保护（推荐默认开启）
- `MAX_AFFECTED_ROWS`：对 UPDATE/DELETE 设置影响行数阈值（如 500/次）。
- `REQUIRE_BUSINESS_FILTER`：除权限过滤外，必须带业务过滤条件（如单据状态/时间窗）。
- `HIGH_RISK_DOUBLE_CONFIRM`：高危删除（如物理删除）要求二次确认令牌。
