# =============================================================================
# GodlikeAgents 多语言沙箱镜像
# 基于 Alpine Linux，预装 Python / Node.js / Bash 等常用运行时
# 用于 Agent 代码执行、文件操作、命令运行的隔离环境
# =============================================================================

# ---- 阶段 1：安装依赖 ----
FROM python:3.12-alpine AS builder

# 安装编译工具（部分 pip 包需要编译）
RUN apk add --no-cache --virtual .build-deps \
    gcc g++ musl-dev libffi-dev openssl-dev

# 预装常用 Python 包
RUN pip install --no-cache-dir --prefix=/install \
    numpy==2.2.6 \
    pandas==2.2.3 \
    requests==2.32.3 \
    httpx==0.28.1 \
    pyyaml==6.0.2

# ---- 阶段 2：最终镜像 ----
FROM python:3.12-alpine

LABEL maintainer="GodlikeAgents"
LABEL description="GodlikeAgents 多语言沙箱镜像"
LABEL version="1.0.0"

# 时区
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo "$TZ" > /etc/timezone && \
    apk del tzdata

# 安装运行时依赖（保留在最终镜像中）
# - bash: Shell 脚本执行
# - nodejs/npm: JavaScript 运行时
# - curl/wget: HTTP 请求（离线环境可移除）
# - git: 代码克隆（离线环境可移除）
# - ca-certificates: HTTPS 证书
# - util-linux: strace, timeout 等调试工具
RUN apk add --no-cache \
    bash \
    nodejs \
    npm \
    curl \
    wget \
    git \
    ca-certificates \
    util-linux \
    procps \
    coreutils

# 从 builder 阶段复制 Python 包
COPY --from=builder /install /usr/local

# 安装常用 Node.js 全局包
RUN npm install -g \
    mathjs \
    axios \
    dayjs \
    docx \
    && npm cache clean --force

# Node.js 全局模块路径（Node v24 默认不包含 npm -g 安装路径）
ENV NODE_PATH=/usr/local/lib/node_modules

# 创建非 root 用户运行沙箱代码
RUN addgroup -S sandbox && \
    adduser -S -G sandbox -h /workspace -s /bin/bash sandbox && \
    chown sandbox:sandbox /workspace

# 工作目录
WORKDIR /workspace

# 切换到非 root 用户
USER sandbox

# 健康检查：验证各运行时可用
HEALTHCHECK --interval=60s --timeout=5s --retries=1 CMD \
    python3 -c "print('python ok')" && \
    node -e "console.log('node ok')" && \
    bash -c "echo 'bash ok'" || exit 1
