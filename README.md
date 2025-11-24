# logistics-list

## Docker 一键部署

1. 安装 Docker/Docker Compose，并在项目根目录根据实际数据库信息创建 `.env`（例如）
   ```env
   APP_DB_URL=jdbc:mysql://your-db-host:3306/logistics?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true&useSSL=false&allowPublicKeyRetrieval=true&cachePrepStmts=true&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048
   APP_DB_USERNAME=root
   APP_DB_PASSWORD=your-secret
   ```
   如果已在宿主机运行 MySQL，未设置 `.env` 时会默认连接 `host.docker.internal`。
2. 在根目录执行 `docker compose build` 构建前后端镜像。
3. 运行 `docker compose up -d` 一键启动。Nginx 会通过 `backend` 服务反向代理 `/api` 请求。

### 访问方式

- 前端页面：`http://<服务器 IP>:8081`
- 后端 API：同一端口，通过前端反向代理访问 `http://<服务器 IP>:8081/api/...`
