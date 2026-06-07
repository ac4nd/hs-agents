#!/bin/bash
# 在远程服务器 47.107.160.31 上构建 godlikeagents/sandbox:1.0.0 镜像
# 使用方法：
#   1. 将 docker/sandbox/ 目录上传到远程服务器
#   2. 在远程服务器上执行此脚本
#
# 或者一行命令：
#   scp -r docker/sandbox root@47.107.160.31:/tmp/sandbox-build/
#   ssh root@47.107.160.31 "cd /tmp/sandbox-build && bash build-and-push.sh"

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "===== 构建 godlikeagents/sandbox:1.0.0 ====="
echo "Dockerfile 目录: $SCRIPT_DIR"
echo ""

docker build -t godlikeagents/sandbox:1.0.0 .

echo ""
echo "===== 验证镜像 ====="
docker images | grep godlikeagents/sandbox

echo ""
echo "===== 验证 docx npm 包 ====="
docker run --rm godlikeagents/sandbox:1.0.0 node -e "const d = require('docx'); console.log('docx OK: ' + Object.keys(d).length + ' exports')"

echo ""
echo "构建完成！镜像 godlikeagents/sandbox:1.0.0 已就绪。"
