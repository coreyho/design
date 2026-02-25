package com.acme.permission.dsl;

import com.acme.permission.model.CompileContext;
import com.acme.permission.model.SqlFragment;

public interface Condition {
    SqlFragment toSql(CompileContext ctx);
}
