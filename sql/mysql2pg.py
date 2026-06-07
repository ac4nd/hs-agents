#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MySQL → PostgreSQL SQL 语法转换脚本

用法:
    python mysql2pg.py input.sql output.sql

功能:
    - 数据类型转换 (tinyint→smallint, datetime→timestamp, json→jsonb ...)
    - 移除 MySQL 特有语法 (backticks, ENGINE, CHARACTER SET, USING BTREE ...)
    - AUTO_INCREMENT → BIGSERIAL / SERIAL
    - 行内索引 → 独立 CREATE INDEX / CREATE UNIQUE INDEX
    - INSERT IGNORE → INSERT ... ON CONFLICT DO NOTHING
    - COMMENT → COMMENT ON TABLE / COMMENT ON COLUMN
    - 自动追加 SEQUENCE RESET (setval)
    - 移除 SET / CREATE DATABASE / USE 等语句
"""

import re
import sys
import os


# ============================================================
# 数据类型映射
# ============================================================
DATATYPE_MAP = {
    # tinyint / tinyint(N) → SMALLINT
    r'(?i)tinyint\s*\(\s*\d+\s*\)': 'SMALLINT',
    r'(?i)tinyint': 'SMALLINT',
    # int → INTEGER
    r'(?i)\bint\b': 'INTEGER',
    # smallint 保持
    r'(?i)\bsmallint\b': 'SMALLINT',
    # bigint 保持 (AUTO_INCREMENT 单独处理)
    r'(?i)\bbigint\b': 'BIGINT',
    # datetime → TIMESTAMP
    r'(?i)\bdatetime\b': 'TIMESTAMP',
    # json → JSONB
    r'(?i)\bjson\b': 'JSONB',
    # varchar / char / text 保持不变(仅去引号问题)
}

# 需要生成 setval 的表及其主键列
SERIAL_TABLES = []  # 动态收集


def convert_datatypes(line: str) -> str:
    """逐行转换数据类型 (优先匹配带括号的)"""
    for pattern, replacement in DATATYPE_MAP.items():
        line = re.sub(pattern, replacement, line)
    return line


def remove_mysql_noise(line: str) -> str:
    """移除 MySQL 特有语法"""
    # 移除 USING BTREE / USING HASH
    line = re.sub(r'\s*USING\s+(BTREE|HASH)', '', line, flags=re.IGNORECASE)
    # 移除 ASC / DESC 在索引列定义中
    line = re.sub(r'\bASC\b', '', line, flags=re.IGNORECASE)
    return line


def strip_backticks(text: str) -> str:
    """移除所有反引号"""
    return text.replace('`', '')


def fix_default_quotes(line: str) -> str:
    """DEFAULT '0' / DEFAULT '1' → DEFAULT 0 / DEFAULT 1 (仅数字)"""
    line = re.sub(r"DEFAULT\s+'(\d+)'", r'DEFAULT \1', line)
    return line


# ============================================================
# 主转换类
# ============================================================
class Mysql2PgConverter:
    def __init__(self):
        self.lines_out: list[str] = []
        self.current_table: str | None = None
        self.current_table_has_serial: bool = False
        self.serial_tables: list[tuple[str, str]] = []  # (table, pk_col)
        self.deferred_indexes: list[str] = []
        self.deferred_comments: list[str] = []
        self.in_create_table: bool = False
        self.paren_depth: int = 0
        self.raw_sql: str = ""

    def _collect_deferred(self) -> list[str]:
        """收集延迟的索引和注释为行列表（调用方负责追加到输出）"""
        result = []
        if self.deferred_indexes:
            result.append("")
            result.extend(self.deferred_indexes)
            self.deferred_indexes.clear()
        if self.deferred_comments:
            result.append("")
            result.extend(self.deferred_comments)
            self.deferred_comments.clear()
        return result

    def _convert_create_table(self, line: str) -> str:
        """处理 CREATE TABLE 行"""
        m = re.match(r'\s*CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(\w+)`?\s*\(', line, re.IGNORECASE)
        if m:
            self.current_table = m.group(1).lower()
            self.current_table_has_serial = False
            self.in_create_table = True
            self.paren_depth = line.count('(') - line.count(')')
            line = f"CREATE TABLE {self.current_table} (\n"
        return line

    def _convert_column(self, line: str) -> str:
        """处理列定义"""
        if not self.current_table:
            return line

        # 提取列名
        col_match = re.match(r'\s*`?(\w+)`?\s+', line)
        if not col_match:
            return line
        col_name = col_match.group(1).lower()

        # 跳过约束行 (PRIMARY KEY, UNIQUE, KEY, INDEX)
        if re.match(r'\s*(PRIMARY\s+KEY|UNIQUE\s*(INDEX|KEY)?|KEY|INDEX|CONSTRAINT)', line, re.IGNORECASE):
            return line

        # 处理 AUTO_INCREMENT → 记录为 serial 表
        if re.search(r'AUTO_INCREMENT', line, re.IGNORECASE):
            self.current_table_has_serial = True
            self.serial_tables.append((self.current_table, col_name))
            line = re.sub(r'\s*AUTO_INCREMENT', '', line, flags=re.IGNORECASE)
            # bigint NOT NULL → BIGSERIAL (主键)
            line = re.sub(r'\bBIGINT\s+NOT\s+NULL\b', 'BIGSERIAL', line, flags=re.IGNORECASE)
            line = re.sub(r'\bINTEGER\s+NOT\s+NULL\b', 'SERIAL', line, flags=re.IGNORECASE)

        # 数据类型转换
        line = convert_datatypes(line)

        # 提取列注释
        comment_match = re.search(r"COMMENT\s+'((?:[^'\\]|\\.)*)'", line, re.IGNORECASE)
        if comment_match:
            comment_text = comment_match.group(1)
            self.deferred_comments.append(
                f"COMMENT ON COLUMN {self.current_table}.{col_name} IS '{comment_text}';"
            )
            # 移除列定义中的 COMMENT
            line = re.sub(r"\s*COMMENT\s+'(?:[^'\\]|\\.)*'", '', line, flags=re.IGNORECASE)

        # 修复 DEFAULT 引号
        line = fix_default_quotes(line)

        return line

    def _extract_inline_indexes(self, line: str) -> str:
        """从 CREATE TABLE 中提取行内索引定义，转为独立的 CREATE INDEX"""
        if not self.current_table:
            return line

        stripped = line.strip()

        # PRIMARY KEY 复合主键 → 保留在表定义中
        if re.match(r'PRIMARY\s+KEY\s*\(', stripped, re.IGNORECASE):
            cleaned = remove_mysql_noise(stripped)
            return f"    {cleaned}\n"

        # UNIQUE INDEX / UNIQUE KEY
        um = re.match(
            r'(?:UNIQUE\s+(?:INDEX|KEY))\s*`?(\w+)`?\s*\(([^)]+)\)',
            stripped, re.IGNORECASE
        )
        if um:
            idx_name = um.group(1).lower()
            cols = um.group(2)
            # 清理 ASC/DESC 和多余空格
            cols = re.sub(r'\b(ASC|DESC)\b', '', cols, flags=re.IGNORECASE)
            cols = re.sub(r'\s+', ' ', cols).strip()
            self.deferred_indexes.append(
                f"CREATE UNIQUE INDEX {idx_name} ON {self.current_table} ({cols});"
            )
            return None  # 从 CREATE TABLE 中移除

        # KEY / INDEX (普通索引)
        km = re.match(
            r'(?:KEY|INDEX)\s*`?(\w+)`?\s*\(([^)]+)\)',
            stripped, re.IGNORECASE
        )
        if km:
            idx_name = km.group(1).lower()
            cols = km.group(2)
            cols = re.sub(r'\b(ASC|DESC)\b', '', cols, flags=re.IGNORECASE)
            cols = re.sub(r'\s+', ' ', cols).strip()
            self.deferred_indexes.append(
                f"CREATE INDEX {idx_name} ON {self.current_table} ({cols});"
            )
            return None

        return line

    def _handle_closing_paren(self, line: str) -> list[str]:
        """处理 CREATE TABLE 结尾的 ) ENGINE=...  返回 [);行, ...延迟索引/注释]"""
        results = []
        if self.in_create_table and self.current_table:
            # 修复前一行尾逗号（最后一列/约束后不应有逗号）
            if self.lines_out and self.lines_out[-1].rstrip().endswith(','):
                self.lines_out[-1] = self.lines_out[-1].rstrip().rstrip(',') + '\n'

            # 提取表注释
            table_comment_match = re.search(r"COMMENT\s*=?\s*'((?:[^'\\]|\\.)*)'", line, re.IGNORECASE)
            if table_comment_match:
                comment_text = table_comment_match.group(1)
                self.deferred_comments.insert(0,
                    f"COMMENT ON TABLE {self.current_table} IS '{comment_text}';"
                )

            # 移除 ENGINE=... 及后续所有 MySQL 表选项
            line = re.sub(
                r'\)\s*ENGINE\s*=.*',
                ');',
                line, flags=re.IGNORECASE
            )
            line = re.sub(
                r"\)\s*COMMENT\s*=\s*'(?:[^'\\]|\\.)*'",
                ');',
                line, flags=re.IGNORECASE
            )

            self.in_create_table = False
            results.append(line)
            # 延迟内容跟在 ); 之后
            results.extend(self._collect_deferred())
            self.current_table = None
        else:
            results.append(line)
        return results

    def convert_line(self, line: str) -> list[str]:
        """转换单行 SQL"""
        original = line
        results = []

        # 1. 移除反引号
        line = strip_backticks(line)

        # 2. 跳过/移除 MySQL 特有语句
        stripped = line.strip()

        # 空行和注释直接通过
        if not stripped or stripped.startswith('--') or stripped.startswith('#'):
            # 将 # 注释转为 -- 注释
            if stripped.startswith('#'):
                line = '-- ' + stripped[1:].lstrip()
            results.append(line)
            return results

        # 移除 SET 语句
        if re.match(r'SET\s+', stripped, re.IGNORECASE):
            return []

        # 移除 CREATE DATABASE / USE
        if re.match(r'CREATE\s+DATABASE', stripped, re.IGNORECASE):
            return []
        if re.match(r'USE\s+', stripped, re.IGNORECASE):
            return []

        # 3. DROP TABLE IF EXISTS
        if re.match(r'DROP\s+TABLE\s+IF\s+EXISTS', stripped, re.IGNORECASE):
            line = re.sub(r'`', '', stripped)
            results.append(line)
            return results

        # 4. CREATE TABLE 开始
        if re.match(r'\s*CREATE\s+TABLE', stripped, re.IGNORECASE):
            line = self._convert_create_table(line)
            results.append(line)
            return results

        # 5. 在 CREATE TABLE 内部
        if self.in_create_table:
            # 跟踪括号深度
            self.paren_depth += line.count('(') - line.count(')')

            # 列定义转换
            line = self._convert_column(line)
            if line is None:
                return []

            # 提取行内索引
            line = self._extract_inline_indexes(line)
            if line is None:
                return []

            # MySQL 噪音移除
            line = remove_mysql_noise(line)

            # 检查是否是 CREATE TABLE 结束行
            if re.match(r'\s*\)', line) or re.search(r'\)\s*ENGINE', line, re.IGNORECASE):
                return self._handle_closing_paren(line)

            results.append(line)
            return results

        # 6. INSERT IGNORE → INSERT ... ON CONFLICT DO NOTHING
        line = re.sub(
            r'INSERT\s+IGNORE\s+INTO',
            'INSERT INTO',
            line, flags=re.IGNORECASE
        )
        # 对简单 INSERT IGNORE 在行尾加 ON CONFLICT DO NOTHING
        if re.match(r'INSERT\s+INTO', line, re.IGNORECASE) and 'ON CONFLICT' not in line.upper():
            # 检查原始是否是 INSERT IGNORE
            if re.match(r'INSERT\s+IGNORE', original.strip(), re.IGNORECASE):
                line = line.rstrip().rstrip(';')
                line += '\nON CONFLICT DO NOTHING;'

        # 7. ALTER TABLE ADD COLUMN
        if re.match(r'ALTER\s+TABLE', stripped, re.IGNORECASE):
            line = convert_datatypes(line)
            line = fix_default_quotes(line)
            # 提取列注释
            alt_col_match = re.search(
                r"ADD\s+COLUMN\s+(\w+)\s+.*?COMMENT\s+'((?:[^'\\]|\\.)*)'",
                line, re.IGNORECASE
            )
            if alt_col_match:
                col_name = alt_col_match.group(1).lower()
                comment_text = alt_col_match.group(2)
                table_match = re.search(r'ALTER\s+TABLE\s+(\w+)', line, re.IGNORECASE)
                if table_match:
                    tbl = table_match.group(1).lower()
                    # 用 results 收集，保证输出顺序
                    results.append(f"COMMENT ON COLUMN {tbl}.{col_name} IS '{comment_text}';")
                # 移除 COMMENT
                line = re.sub(r"\s*COMMENT\s+'(?:[^'\\]|\\.)*'", '', line, flags=re.IGNORECASE)

        # 8. UPDATE 语句 — 补全 SET 子句检测
        if re.match(r'UPDATE\s+\w+\s*\n\s*WHERE', stripped + '\nWHERE', re.IGNORECASE):
            pass  # 已在下面统一处理

        # 8. UPDATE 语句
        if re.match(r'UPDATE\s+', stripped, re.IGNORECASE):
            line = strip_backticks(line)

        results.append(line)
        return results

    def convert(self, input_path: str, output_path: str):
        """执行完整转换"""
        with open(input_path, 'r', encoding='utf-8') as f:
            self.raw_sql = f.read()

        lines = self.raw_sql.split('\n')

        # 头部
        self.lines_out.append("-- godlikeagents_admin_tenant 数据库(PostgreSQL 16+) - 多租户版本")
        self.lines_out.append("-- Copyright (c) 2021-present, youlai.tech")
        self.lines_out.append("-- Copyright (c) 2026-present, HyperSense")
        self.lines_out.append("--")
        self.lines_out.append("-- 说明：此脚本为多租户版本的完整数据库初始化脚本 (PostgreSQL 版本)")
        self.lines_out.append("-- 由 mysql2pg.py 自动转换生成")
        self.lines_out.append("")

        for line in lines:
            converted = self.convert_line(line)
            self.lines_out.extend(converted)

        # 追加 SEQUENCE RESET
        if self.serial_tables:
            self.lines_out.append("")
            self.lines_out.append("-- ----------------------------")
            self.lines_out.append("-- 重置序列 (使自增ID从已有最大值继续)")
            self.lines_out.append("-- ----------------------------")
            for tbl, pk in self.serial_tables:
                self.lines_out.append(
                    f"SELECT setval(pg_get_serial_sequence('{tbl}', '{pk}'), "
                    f"COALESCE((SELECT MAX({pk}) FROM {tbl}), 1));"
                )

        with open(output_path, 'w', encoding='utf-8') as f:
            f.write('\n'.join(self.lines_out))

        print(f"[OK] 转换完成: {input_path} → {output_path}")
        print(f"     共 {len(self.serial_tables)} 个自增表已添加 setval 重置")


# ============================================================
# 入口
# ============================================================
def main():
    if len(sys.argv) < 2:
        print("用法: python mysql2pg.py <input.sql> [output.sql]")
        print("")
        print("示例:")
        print("  python mysql2pg.py mysql_dump.sql pg_dump.sql")
        print("  python mysql2pg.py ../godlikeagents_admin_tenant.sql ./postgresql/init.sql")
        sys.exit(1)

    input_path = sys.argv[1]
    if len(sys.argv) >= 3:
        output_path = sys.argv[2]
    else:
        base, ext = os.path.splitext(input_path)
        output_path = f"{base}_pg{ext}"

    if not os.path.exists(input_path):
        print(f"[ERROR] 文件不存在: {input_path}")
        sys.exit(1)

    converter = Mysql2PgConverter()
    converter.convert(input_path, output_path)


if __name__ == '__main__':
    main()
