# Dify 官方服务接入

本项目不再把自写 YAML 导出当成完整 Dify 画布能力。官方 Dify 的 Workflow 是可视化 canvas，DSL 需要导入 Dify 应用后才会显示为可拖动节点。

## 启动官方 Dify

官方推荐使用 Dify 仓库 `docker` 目录中的 Docker Compose：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-dify-official.ps1
```

默认会把 Dify Web 暴露到：

```text
http://localhost:3001
```

脚本会写入官方 Docker Compose 使用的 `EXPOSE_NGINX_PORT=3001`，因此入口是 Dify 官方 Nginx 服务，不是本项目的 `vs-dify-bridge` 页面。

如果 GitHub 网络不可用，脚本会在 `git clone https://github.com/langgenius/dify.git` 处失败。网络恢复后重新执行即可。

如果 Docker Hub 拉取 `langgenius/dify-api` 长时间卡住，可以从已克隆的官方仓库本地构建：

```powershell
cd .\runtime\dify\dify
docker build -f api\Dockerfile -t langgenius/dify-api:1.14.2 .
cd .\docker
docker compose up -d --no-build
```

## 接入当前系统

当前系统有两层接入：

- 官方 Dify Web：用于导入 DSL、查看和拖动画布。
- `vs-dify-bridge`：轻量转发 Dify Workflow API，供本项目前端调用 `/run`。

要让 Bridge 调用官方 Dify Workflow，重启容器时设置：

```powershell
docker rm -f vs-dify-bridge
docker run -d --name vs-dify-bridge --network vs-net -p 8090:8090 `
  -e DIFY_BASE_URL=http://host.docker.internal:3001 `
  -e DIFY_API_KEY=app-你的DifyWorkflowKey `
  -e DIFY_DEFAULT_WORKFLOW_ID=你的默认workflowId `
  vs-dify-bridge:latest
```

前端 Dify 页面默认访问：

```text
http://localhost:8090
```

## 当前限制

`/workflow/{id}/dify-dsl` 当前仍是最小 DSL 导出，不能保证在所有 Dify 版本中完整还原画布布局。官方 Dify 部署完成后，下一步应以官方导出的 DSL 为样本，补齐 `graph.nodes[].position`、节点 `data`、edge metadata 等字段。
