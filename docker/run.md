
#  Docker Compose 安装中间件 PostgreSql、Redis、Minio、Xxl-Job

## 安装

```bash
docker-compose -f ./docker-compose.yml -p godlikeagents up -d
```

- p godlikeagents 指定命名空间，避免与其他容器冲突，这里方便管理，统一管理和卸载

## 卸载
```bash
docker-compose -f ./docker-compose.yml -p godlikeagents down
```

