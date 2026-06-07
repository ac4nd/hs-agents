package com.hypersense.boot.framework.mybatis.interceptor;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.hypersense.boot.common.annotation.DataPermission;
import com.hypersense.boot.common.enums.DataScopeEnum;
import com.hypersense.boot.framework.security.model.RoleDataScope;
import com.hypersense.boot.framework.security.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 数据权限控制器
 */
@Slf4j
public class MyDataPermissionHandler implements DataPermissionHandler {

    private static final String DEPT_TABLE = "sys_dept";
    private static final String DEPT_ID_COLUMN = "id";
    private static final String DEPT_TREE_PATH_COLUMN = "tree_path";

    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        try {
            if (SecurityUtils.getUserId() == null || SecurityUtils.isRoot()) {
                return where;
            }

            List<RoleDataScope> dataScopes = SecurityUtils.getDataScopes();

            if (hasAllDataScope(dataScopes)) {
                return where;
            }

            if (CollectionUtil.isEmpty(dataScopes)) {
                return where;
            }

            Class<?> clazz = Class.forName(mappedStatementId.substring(0, mappedStatementId.lastIndexOf(StringPool.DOT)));
            String methodName = mappedStatementId.substring(mappedStatementId.lastIndexOf(StringPool.DOT) + 1);
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if (method.getName().equals(methodName)) {
                    DataPermission annotation = method.getAnnotation(DataPermission.class);
                    if (annotation == null) {
                        return where;
                    }
                    return dataScopeFilterWithUnion(mappedStatementId, annotation, dataScopes, where);
                }
            }
            return where;
        } catch (Exception e) {
            log.error("DataPermission resolve error. mappedStatementId={}", mappedStatementId, e);
            return where;
        }
    }

    private boolean hasAllDataScope(List<RoleDataScope> dataScopes) {
        if (CollectionUtil.isEmpty(dataScopes)) {
            return false;
        }
        return dataScopes.stream()
                .anyMatch(scope -> DataScopeEnum.ALL.getValue().equals(scope.getDataScope()));
    }

    private Expression dataScopeFilterWithUnion(String mappedStatementId, DataPermission annotation, List<RoleDataScope> dataScopes, Expression where) {
        String deptAlias = annotation.deptAlias();
        String deptIdColumnName = annotation.deptIdColumnName();
        String userAlias = annotation.userAlias();
        String userIdColumnName = annotation.userIdColumnName();

        Expression unionExpression = null;
        for (RoleDataScope dataScope : dataScopes) {
            Expression roleExpression = buildRoleDataScopeExpression(
                    deptAlias, deptIdColumnName, userAlias, userIdColumnName, dataScope);
            if (roleExpression != null) {
                if (unionExpression == null) {
                    unionExpression = roleExpression;
                } else {
                    unionExpression = new OrExpression(unionExpression, roleExpression);
                }
            }
        }

        if (unionExpression == null) {
            return where;
        }

        Expression finalExpression = parseCondExpressionSafely("(" + unionExpression + ")");

        if (where == null) {
            log.debug("DataPermission applied. mappedStatementId={}, segment={}", mappedStatementId, finalExpression);
            return finalExpression;
        }

        Expression combined = new AndExpression(where, finalExpression);
        log.debug("DataPermission applied. mappedStatementId={}, originWhere={}, segment={}, combined={}",
                mappedStatementId, where, finalExpression, combined);
        return combined;
    }

    private Expression parseCondExpressionSafely(String sql) {
        try {
            return CCJSqlParserUtil.parseCondExpression(sql);
        } catch (JSQLParserException e) {
            throw new RuntimeException(e);
        }
    }

    private Expression buildRoleDataScopeExpression(String deptAlias, String deptIdColumnName,
                                                     String userAlias, String userIdColumnName,
                                                     RoleDataScope roleDataScope) {
        Column deptColumn = buildColumn(deptAlias, deptIdColumnName);
        Column userColumn = buildColumn(userAlias, userIdColumnName);

        Long deptId = SecurityUtils.getDeptId();
        Long userId = SecurityUtils.getUserId();

        DataScopeEnum dataScopeEnum = DataScopeEnum.getByValue(roleDataScope.getDataScope());
        if (dataScopeEnum == null) {
            return null;
        }

        return switch (dataScopeEnum) {
            case ALL -> null;
            case DEPT_AND_SUB -> buildDeptAndSubExpression(deptColumn, deptId);
            case DEPT -> buildEqualsExpression(deptColumn, deptId);
            case SELF -> buildEqualsExpression(userColumn, userId);
            case CUSTOM -> buildCustomDeptExpression(deptColumn, roleDataScope.getCustomDeptIds());
        };
    }

    private Column buildColumn(String alias, String columnName) {
        if (StrUtil.isNotBlank(alias)) {
            return new Column(alias + StringPool.DOT + columnName);
        }
        return new Column(columnName);
    }

    private Expression buildEqualsExpression(Column column, Long value) {
        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(column);
        equalsTo.setRightExpression(new LongValue(value));
        return equalsTo;
    }

    private Expression buildDeptAndSubExpression(Column deptColumn, Long deptId) {
        String columnName = deptColumn.toString();
        String sql = columnName + " IN (SELECT " + DEPT_ID_COLUMN + " FROM " + DEPT_TABLE +
                " WHERE " + DEPT_ID_COLUMN + " = " + deptId +
                " OR FIND_IN_SET(" + deptId + ", " + DEPT_TREE_PATH_COLUMN + "))";
        return parseCondExpressionSafely(sql);
    }

    private Expression buildCustomDeptExpression(Column deptColumn, List<Long> customDeptIds) {
        if (CollectionUtil.isEmpty(customDeptIds)) {
            EqualsTo falseCondition = new EqualsTo();
            falseCondition.setLeftExpression(new LongValue(1));
            falseCondition.setRightExpression(new LongValue(0));
            return falseCondition;
        }

        String columnName = deptColumn.toString();
        String ids = customDeptIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        String sql = columnName + " IN (" + ids + ")";
        return parseCondExpressionSafely(sql);
    }

}
