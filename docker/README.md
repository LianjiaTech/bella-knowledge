# bella-knowledge Docker 部署指南

本文档提供 bella-knowledge 项目的完整 Docker 部署方案，包含开发和生产环境的不同部署方式。

## 🔗 前置依赖

**重要**：bella-knowledge 依赖于 [bella-openapi](https://github.com/LianjiaTech/bella-openapi) 项目，您需要：

1. **首先部署 bella-openapi 项目**并确保其正常运行
2. **获取 bella-openapi 的访问地址**和相关配置信息
3. **在部署 bella-knowledge 前**，正确配置环境变量中的 bella-openapi 相关设置

⚠️ **注意**：如果没有正确配置 bella-openapi，bella-knowledge 将无法正常工作，特别是文件管理和OpenAI API兼容功能。

### OpenAPI 依赖说明

bella-knowledge 作为 Bella 体系内的知识管理中心，需要通过 bella-openapi 提供：
- 标准化的 OpenAI File API 兼容接口
- 统一的认证和授权服务
- 跨服务的数据流转能力

## 📁 目录结构

```
docker/
├── docker-compose.yml                  # 完整应用部署配置
├── docker-compose.middleware.yml       # 中间件部署配置
├── docker-compose.enterprise.yml       # 企业版完整部署配置（包含 Kafka、S3 等企业特性）
├── .env.example                        # 环境变量示例
├── README.md                          # 本部署文档
└── volumes/                           # 数据卷目录
    ├── mysql/data/                    # MySQL 数据存储
    ├── redis/data/                    # Redis 数据存储
    ├── nginx/                         # Nginx 配置和日志
    └── api/                           # API 应用数据
        ├── logs/                      # 应用日志
        └── cache/                     # 缓存文件
```

## 🚀 部署方案

### 方案一：快速体验（推荐生产环境）

一键启动所有服务，包括中间件、后端API和前端Web，快速体验完整功能：

```bash
# 1. 进入 docker 目录
cd docker/

# 2. 复制并配置环境变量
cp .env.example .env
vim .env

# 3. 启动所有服务
docker-compose up -d

# 4. 查看服务状态
docker-compose ps
```

**包含服务**：
- MySQL 8.0 (数据库)
- Redis 7 (缓存)
- bella-knowledge API (后端服务)
- bella-knowledge Web (前端服务)
- Nginx (反向代理)

### 方案二：企业版部署（推荐企业生产环境）

使用企业版配置，启动包含 Kafka 消息队列和 S3 对象存储的完整企业级服务：

```bash
# 1. 进入 docker 目录
cd docker/

# 2. 复制并配置环境变量
cp .env.example .env
cp .env.enterprise.example .env.enterprise
vim .env
vim .env.enterprise

# 3. 启动企业版服务
docker-compose -f docker-compose.enterprise.yml up -d

# 4. 查看服务状态
docker-compose -f docker-compose.enterprise.yml ps
```

**包含服务**：
- MySQL 8.0 (数据库)
- Redis 7 (缓存)
- MinIO (S3 兼容对象存储)
- Kafka (消息队列)
- bella-file-api (后端服务，企业版配置)
- bella-file-web (前端服务)
- Nginx (反向代理)

**企业版特性**：
- **S3 对象存储**：使用 MinIO 提供 S3 兼容的对象存储服务，支持大文件存储和分布式存储
- **Kafka 消息队列**：支持异步消息处理、事件驱动架构和微服务间通信
- **企业级配置**：包含完整的健康检查、资源限制和生产环境优化配置
- **高可用性支持**：服务间依赖管理，确保服务启动顺序和稳定性

### 方案三：本地源码部署（推荐开发环境）

仅启动基础中间件，API和前端服务使用源码手动启动，便于开发调试和代码修改：

```bash
# 1. 进入 docker 目录
cd docker/

# 2. 启动中间件服务
docker-compose -f docker-compose.middleware.yml up -d

# 3. 启动后端服务（新终端）
cd ../api
mvn spring-boot:run -Dserver.port=8080

# 4. 启动前端服务（新终端）
cd ../web
export NEXT_PUBLIC_BELLA_FILE_API_URL=http://localhost:8080
export NEXT_PUBLIC_BELLA_OPENAPI_URL=http://your-bella-openapi-host:port
npm run dev
```

**包含服务**：
- MySQL 8.0 (数据库)
- Redis 7 (缓存)  
- Nginx (反向代理)

## 💻 开发环境配置

### 系统要求

- **Java**: 8+ (推荐 OpenJDK 11)
- **Node.js**: 20+ (推荐 20.x LTS)
- **Maven**: 3.6+
- **Git**: 2.0+
- **Docker**: 20+ (用于中间件服务)

### 开发工具配置

#### IDE 推荐

**后端开发 (IntelliJ IDEA)**:
```bash
1. File -> Open -> 选择 api 目录
2. 配置 JDK: File -> Project Structure -> Project SDK
3. 安装插件: Lombok, Spring Boot
```

**前端开发 (VS Code)**:
```bash
# 推荐插件
- ES7+ React/Redux/React-Native snippets
- TypeScript Importer
- Prettier - Code formatter
- ESLint
- Tailwind CSS IntelliSense
```

### 开发调试

#### 后端调试

```bash
# 查看应用日志
tail -f volumes/api/logs/app.log

# 数据库连接测试
mysql -h localhost -P 3306 -u root -p

# 健康检查
curl http://localhost:8080/actuator/health
```

#### 前端调试

```bash
# 开发模式启动 (自带热重载)
npm run dev

# 代码检查
npm run lint
npm run type-check

# 构建测试
npm run build
```

#### 技术栈信息

| 组件 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 后端 | Java + Spring Boot | 8+ / 2.3+ | REST API 服务 |
| 前端 | React + Next.js | 19+ / 14+ | Web 应用界面 |
| 数据库 | MySQL | 5.7+ | 关系数据库 |
| 缓存 | Redis | 6.0+ | 缓存服务 |
| 构建 | Maven + npm | 3.6+ / 10+ | 构建工具 |

## ⚙️ 环境变量配置

### 快速体验环境变量 (.env)

使用 `docker-compose.yml` 完整部署时：

```bash
# 复制环境变量示例文件并根据需要修改
cp .env.example .env
vim .env

# 启动所有服务
docker-compose up -d
```

### 本地源码部署环境变量

使用 `docker-compose.middleware.yml` 仅启动中间件时：

```bash
# 启动中间件服务
docker-compose -f docker-compose.middleware.yml up -d

# 设置前后端环境变量并启动
export NEXT_PUBLIC_BELLA_FILE_API_URL=http://localhost:8080
export NEXT_PUBLIC_BELLA_OPENAPI_URL=http://your-bella-openapi-host:port
export server.port=8080
```

### 本地前端源码启动环境变量

前端源码本地启动时需要以下环境变量：

```bash
# API服务地址
export NEXT_PUBLIC_BELLA_FILE_API_URL=http://localhost:8080

# bella-openapi服务地址（重要：需要指向实际的bella-openapi部署地址）
export NEXT_PUBLIC_BELLA_OPENAPI_URL=http://your-bella-openapi-host:port

# 启动前端服务
npm run dev
```

### 本地后端源码启动环境变量

后端源码本地启动时需要指定端口：

```bash
# 使用默认端口8080启动
mvn spring-boot:run -Dserver.port=8080

# 或使用自定义端口
mvn spring-boot:run -Dserver.port=8081
```

## 🔗 服务访问地址

### 快速体验访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端Web | http://localhost:3000 | React应用界面 |
| 后端API | http://localhost:8080 | REST API服务 |
| API文档 | http://localhost:8080/swagger-ui.html | Swagger文档 |
| Nginx代理 | http://localhost:80 | 反向代理入口 |
| MySQL | localhost:3306 | 数据库连接 |
| Redis | localhost:6379 | 缓存连接 |

### 企业版访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端Web | http://localhost:3001 | React应用界面 |
| 后端API | http://localhost:8081 | REST API服务 |
| API文档 | http://localhost:8081/swagger-ui.html | Swagger文档 |
| Nginx代理 | http://localhost:80 | 反向代理入口 |
| MySQL | localhost:3306 | 数据库连接 |
| Redis | localhost:6379 | 缓存连接 |
| MinIO API | http://localhost:9000 | S3 兼容API |
| MinIO Console | http://localhost:9001 | MinIO 管理控制台 |
| Kafka | localhost:9092 | Kafka 连接地址 |
| Kafka 外部访问 | localhost:19092 | Kafka 外部连接地址 |

### 本地源码访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端Web | http://localhost:3000 | 本地开发服务 |
| 后端API | http://localhost:8080 | 本地开发服务 |
| MySQL | localhost:3306 | Docker中间件 |
| Redis | localhost:6379 | Docker中间件 |

## 🛠️ Maven镜像源配置

为了优化构建速度，项目支持多种Maven镜像源：

### 支持的镜像源

| 镜像源 | 配置值 | 适用场景 | 性能 |
|--------|--------|----------|------|
| 阿里云镜像 | `aliyun` | 中国大陆地区 | 快速 |
| Maven中央仓库 | `central` | 海外地区 | 标准 |

### 配置方法

在 `.env` 文件中设置：

```bash
# 中国大陆用户推荐
MAVEN_MIRROR=aliyun

# 海外用户推荐  
MAVEN_MIRROR=central
```

### 临时切换镜像源

```bash
# 使用阿里云镜像构建
MAVEN_MIRROR=aliyun docker-compose up --build

# 使用中央仓库构建
MAVEN_MIRROR=central docker-compose up --build
```

## 🔧 运维操作

### 服务管理

**标准版服务管理**：
```bash
# 启动服务
docker-compose up -d

# 停止服务  
docker-compose down

# 重启服务
docker-compose restart

# 查看服务状态
docker-compose ps

# 查看资源使用
docker-compose top
```

**企业版服务管理**：
```bash
# 启动企业版服务
docker-compose -f docker-compose.enterprise.yml up -d

# 停止企业版服务
docker-compose -f docker-compose.enterprise.yml down

# 重启企业版服务
docker-compose -f docker-compose.enterprise.yml restart

# 查看企业版服务状态
docker-compose -f docker-compose.enterprise.yml ps

# 查看企业版资源使用
docker-compose -f docker-compose.enterprise.yml top
```

### 日志管理

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f bella-knowledge-api

# 查看最近100行日志
docker-compose logs --tail 100 bella-knowledge-api
```

### 数据备份

```bash
# 备份MySQL数据
docker-compose exec mysql mysqldump -u root -p bella_knowledge > backup.sql

# 备份Redis数据  
docker-compose exec redis redis-cli --rdb backup.rdb

# 备份应用数据
tar -czf backup-$(date +%Y%m%d).tar.gz volumes/
```

### 健康检查

```bash
# 查看健康检查状态
docker-compose ps

# 手动健康检查
curl http://localhost:8080/actuator/health
```

## 🐛 故障排除

### 常见问题

1. **端口冲突**
   ```bash
   # 检查端口占用
   netstat -tulpn | grep :3306
   
   # 修改 .env 文件中的端口配置
   API_PORT=8081
   WEB_PORT=3001
   ```

2. **权限问题**
   ```bash
   # 设置正确的目录权限
   sudo chown -R 1000:1000 volumes/
   ```

3. **服务启动失败**
   ```bash
   # 查看详细错误信息
   docker-compose logs service-name
   ```

4. **前端无法访问后端API或OpenAPI服务**
   ```bash
   # 检查环境变量是否正确设置
   echo $NEXT_PUBLIC_BELLA_FILE_API_URL
   echo $NEXT_PUBLIC_BELLA_OPENAPI_URL
   
   # 确保后端服务正常运行
   curl http://localhost:8080/actuator/health
   
   # 确保bella-openapi服务正常运行
   curl http://your-bella-openapi-host:port/health
   ```

5. **bella-openapi连接问题**
   ```bash
   # 检查bella-openapi服务状态
   curl http://your-bella-openapi-host:port/health
   
   # 检查网络连通性
   ping your-bella-openapi-host
   
   # 验证配置是否正确
   echo $BELLA_OPENAPI_URL
   ```

6. **Maven依赖下载慢**
   ```bash
   # 切换到阿里云镜像源
   MAVEN_MIRROR=aliyun docker-compose up --build
   ```

### 性能优化

1. **调整JVM参数**
   ```bash
   # 在 .env 中调整堆内存
   API_MIN_HEAP=2048m
   API_MAX_HEAP=2048m  
   ```

2. **清理Docker缓存**
   ```bash
   # 清理未使用的镜像和容器
   docker system prune -f
   
   # 强制重新构建
   docker-compose up --build --no-cache
   ```

## 🔒 生产环境部署

### 安全配置

1. **修改默认密码**
   ```bash
   # 在 .env 文件中设置强密码
   MYSQL_ROOT_PASSWORD=your-strong-root-password
   MYSQL_PASSWORD=your-strong-user-password
   REDIS_PASSWORD=your-strong-redis-password
   ```

2. **SSL配置**
   ```bash
   # 将SSL证书放到 volumes/nginx/ssl/ 目录
   # 配置Nginx HTTPS
   ```

3. **防火墙设置**
   ```bash
   # 仅开放必要端口
   ufw allow 80
   ufw allow 443
   ```

### 数据持久化

所有重要数据都挂载到 `volumes/` 目录：

- MySQL数据: `volumes/mysql/data`
- Redis数据: `volumes/redis/data`  
- 应用日志: `volumes/api/logs`
- Nginx配置: `volumes/nginx/conf.d`

### 更新升级

```bash
# 拉取最新镜像
docker-compose pull

# 重新构建并启动
docker-compose up -d --build

# 清理旧镜像
docker image prune
```

## 🚀 部署顺序

为确保系统正常运行，请按照以下顺序进行部署：

1. **部署 bella-openapi** - 部署并启动 [bella-openapi](https://github.com/LianjiaTech/bella-openapi) 服务
2. **验证 bella-openapi** - 确保 bella-openapi 服务正常运行并可访问
3. **配置环境变量** - 修改 bella-knowledge 环境变量文件中的 bella-openapi 相关配置
4. **启动 bella-knowledge** - 启动 bella-knowledge 服务

⚠️ **重要提醒**：如果跳过了前两步或配置不正确，将导致 bella-knowledge 的核心功能无法正常使用。

## 📞 支持与反馈

如有问题，请查看：

1. [项目主文档](../README.md)
2. [bella-openapi 项目](https://github.com/LianjiaTech/bella-openapi) - 前置依赖服务
3. [GitHub Issues](https://github.com/LianjiaTech/bella-knowledge/issues)
4. 应用日志: `volumes/api/logs/`
5. 服务日志: `docker-compose logs [service-name]`