# SaaS 数据权限核心代码示例

本目录基于 `saas-data-permission-design.md` 实现了一个可运行的 MVP 核心：

- 结构化条件 DSL（`compare / in / group`）
- 多角色规则合并（`ALLOW OR` + `AND NOT DENY`）
- SQL WHERE 注入（参数化）
- 配置样例与策略 DSL 样例

## 目录

- `src/main/java/com/acme/permission/dsl`：DSL 条件节点
- `src/main/java/com/acme/permission/engine`：合并与注入引擎
- `src/main/java/com/acme/permission/model`：上下文与 SQL 片段模型
- `examples/PermissionDemo.java`：可直接运行的示例
- `config/permission-engine.yaml`：实例配置
- `config/policies/*.json`：策略样例

## 快速运行

```bash
javac $(find src/main/java -name '*.java') examples/PermissionDemo.java
java -cp src/main/java:examples PermissionDemo
```
