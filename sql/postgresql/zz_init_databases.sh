#!/bin/bash
# 创建额外的数据库（XXL-Job 等）
# PostgreSQL docker-entrypoint-initdb.d 会在首次初始化时自动执行此脚本
set -e

for dbname in xxl_job; do
    echo "Creating database '$dbname' if not exists..."
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
        SELECT 'CREATE DATABASE $dbname' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$dbname')\gexec
EOSQL
    echo "Database '$dbname' ready."
done
