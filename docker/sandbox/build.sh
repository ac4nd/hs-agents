#!/bin/bash
# =============================================================================
# 构建与推送沙箱镜像
# =============================================================================

IMAGE_NAME="godlikeagents/sandbox"
IMAGE_TAG="1.0.0"
REGISTRY="${REGISTRY:-}"

# 构建镜像
docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .

if [ -n "$REGISTRY" ]; then
    docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}
    docker push ${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}
    echo "已推送: ${REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
else
    echo "构建完成: ${IMAGE_NAME}:${IMAGE_TAG}"
    echo "如需推送，设置 REGISTRY 环境变量后重新运行"
fi

# 显示镜像信息
docker images ${IMAGE_NAME}:${IMAGE_TAG}
