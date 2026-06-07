package com.hypersense.boot.codegen.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Java类型枚举
 *
 * @author Ray
 * @since 2.10.0
 */
@Getter
public enum JavaTypeEnum {

    VARCHAR("varchar", "String", "string"),
    CHAR("char", "String", "string"),
    BLOB("blob", "byte[]", "Uint8Array"),
    TEXT("text", "String", "string"),
    JSON("json", "String", "any"),
    INTEGER("int", "Integer", "number"),
    TINYINT("tinyint", "Integer", "number"),
    SMALLINT("smallint", "Integer", "number"),
    MEDIUMINT("mediumint", "Integer", "number"),
    BIGINT("bigint", "Long", "number"),
    FLOAT("float", "Float", "number"),
    DOUBLE("double", "Double", "number"),
    DECIMAL("decimal", "BigDecimal", "number"),
    DATE("date", "LocalDate", "string"),
    DATETIME("datetime", "LocalDateTime", "string"),
    TIMESTAMP("timestamp", "LocalDateTime", "string"),
    BOOLEAN("boolean", "Boolean", "boolean"),
    BIT("bit", "Boolean", "boolean");

    private final String dbType;
    private final String javaType;
    private final String tsType;

    private static final Map<String, JavaTypeEnum> typeMap = new HashMap<>();

    static {
        for (JavaTypeEnum javaTypeEnum : JavaTypeEnum.values()) {
            typeMap.put(javaTypeEnum.getDbType(), javaTypeEnum);
        }
    }

    JavaTypeEnum(String dbType, String javaType, String tsType) {
        this.dbType = dbType;
        this.javaType = javaType;
        this.tsType = tsType;
    }

    public static String getJavaTypeByColumnType(String columnType) {
        String normalized = normalizeColumnType(columnType);
        JavaTypeEnum javaTypeEnum = typeMap.get(normalized);
        if (javaTypeEnum != null) {
            return javaTypeEnum.getJavaType();
        }
        return "String";
    }

    public static String getTsTypeByJavaType(String javaType) {
        if (javaType == null) {
            return "any";
        }
        for (JavaTypeEnum javaTypeEnum : JavaTypeEnum.values()) {
            if (javaTypeEnum.getJavaType().equals(javaType)) {
                return javaTypeEnum.getTsType();
            }
        }
        return "any";
    }

    private static String normalizeColumnType(String columnType) {
        if (columnType == null) {
            return "";
        }
        String normalized = columnType.trim().toLowerCase();
        int parenIndex = normalized.indexOf('(');
        if (parenIndex > -1) {
            normalized = normalized.substring(0, parenIndex);
        }
        normalized = normalized.replace("unsigned", "").replace("zerofill", "").trim();
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized;
    }
}
