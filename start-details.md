# Bella File API 启动与部署详情

本文档详细介绍了 Bella File API 的启动和部署流程，包括前提条件、启动服务、环境变量配置和服务管理等内容。

## 目录

- [项目概述](#项目概述)
  - [项目结构](#项目结构)
  - [技术栈](#技术栈)
- [前提条件](#前提条件)
- [启动模式](#启动模式)
  - [与OpenAPI共享服务器](#与openapi共享服务器)
  - [独立部署模式](#独立部署模式)
- [启动服务](#启动服务)
  - [启动选项](#启动选项)
  - [启动示例](#启动示例)
- [docker-compose环境变量配置](#docker-compose环境变量配置)
  - [环境变量说明](#环境变量说明)
- [服务管理](#服务管理)
  - [停止服务](#停止服务)
  - [查看日志](#查看日志)
  - [重启服务](#重启服务)
- [Docker 镜像管理](#docker-镜像管理)
  - [推送 Docker 镜像](#推送-docker-镜像)
  - [更新镜像](#更新镜像)
- [Nginx配置](#nginx配置)
  - [独立部署模式下的Nginx](#独立部署模式下的nginx)
  - [共享服务器模式下的Nginx](#共享服务器模式下的nginx)
  - [容器名称使用](#容器名称使用)
- [存储配置](#存储配置)
  - [存储类型](#存储类型)
  - [存储配置参数](#存储配置参数)
- [代理服务器配置](#代理服务器配置)
  - [代理配置参数](#代理配置参数)
  - [代理配置示例](#代理配置示例)

## 项目概述

Bella File API是一个文件管理服务，为Bella OpenAPI平台提供文件存储和管理功能。

### 项目结构

- `api/`: 后端API服务，基于Spring Boot框架

### 技术栈

- 后端：Java、Spring Boot、MySQL
- 部署：Docker、Docker Compose
- 依赖：Bella OpenAPI服务

## 前提条件

- 安装 [Docker](https://www.docker.com/get-started)
- 安装 [Docker Compose](https://docs.docker.com/compose/install/)
- 执行目录必须在example-file-api项目的根目录下

## 启动模式

Bella File API 支持两种启动模式，可以根据实际需求选择合适的部署方式。

### 与OpenAPI共享服务器

在这种模式下，Bella File API 与 Bella OpenAPI 部署在同一服务器上，共享 Bella OpenAPI 的 MySQL 数据库服务。

**特点：**
- 使用 `docker-compose-api.yml` 配置文件
- 连接到 `bella-openapi-mysql` 容器作为数据库
- 通过 Docker 网络 `bella-openapi_default` 与 OpenAPI 服务通信
- 不需要单独启动 MySQL 服务

**适用场景：**
- 资源有限的环境
- 需要与 Bella OpenAPI 紧密集成的场景
- 开发和测试环境

### 独立部署模式

在这种模式下，Bella File API 独立部署，启动自己的 MySQL 容器。

**特点：**
- 使用 `docker-compose.yml` 配置文件
- 启动独立的 MySQL 容器
- 需要配置 OpenAPI 的 URL 以便通信

**适用场景：**
- 分布式部署环境
- 需要独立扩展的生产环境
- 高可用性要求的场景

## 启动服务

```bash
./start.sh [选项]
```

### 启动选项

- `-b, --build`: 重新构建服务
- `-r, --rebuild`: 强制重新构建服务（不使用缓存）
- `-h, --help`: 显示帮助信息
- `--base-openapi`: 与OpenAPI在同一服务器上启动，不需要再启动MySQL
- `--openapi_url URL`: 指定OpenAPI的URL（当不在同一服务器启动时必须提供）
- `--version VERSION`: 指定镜像版本
- `--push`: 构建后推送镜像到仓库
- `--registry username`: 指定推送的docker仓库
- `--update-image`: 从远程仓库更新应用镜像，即使本地已存在
- `--restart SERVICE`: 重启指定服务，不重新编译

### 启动示例

```bash
# 独立模式启动（需要指定OpenAPI URL）
./start.sh --openapi_url https://example.com:8080/

# 与OpenAPI共享服务器模式启动
./start.sh --base-openapi

# 启动服务并重新构建
./start.sh --build --openapi_url https://example.com:8080/

# 强制更新应用镜像
./start.sh --update-image --openapi_url https://example.com:8080/

# 构建并推送镜像到指定仓库
./start.sh --build --push --registry username --version v1.0.0

# 仅重启API服务
./start.sh --restart api
```

## docker-compose环境变量配置

### 环境变量说明

**共享模式 (docker-compose-api.yml):**

```yaml
environment:
  - JAVA_OPTS=-server -Xms2048m -Xmx2048m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=256m
  - SPRING_PROFILES_ACTIVE=docker
  - BELLA_OPEN_API_BASE=${OPENAPI_URL:-https://localhost:8080/}
  - MYSQL_HOST=bella-openapi-mysql
  - MYSQL_PORT=3306
  - MYSQL_USER=root
  - MYSQL_PASSWORD=123456
```

**独立模式 (docker-compose.yml):**

```yaml
environment:
  - JAVA_OPTS=-server -Xms2048m -Xmx2048m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=256m
  - SPRING_PROFILES_ACTIVE=docker
  - BELLA_OPEN_API_BASE=${OPENAPI_URL:-https://localhost:8080/}
  - MYSQL_HOST=mysql
  - MYSQL_PORT=3306
  - MYSQL_USER=bella_user
  - MYSQL_PASSWORD=123456
```

主要区别在于数据库连接配置：
- 共享模式连接到 `bella-openapi-mysql` 容器
- 独立模式连接到自己的 `mysql` 容器

## 存储配置

Bella File API 支持多种存储服务，包括 S3、OSS 和本地存储。可以通过启动脚本的参数来配置存储类型和相关参数。

### 存储类型

使用 `--storage-type` 参数指定存储类型，支持以下选项：
- `s3`: Amazon S3 或兼容 S3 协议的存储服务
- `oss`: 阿里云对象存储服务
- `local`: 本地文件系统存储（默认）

```bash
# 指定使用 S3 存储
./start.sh --storage-type s3

# 指定使用 OSS 存储
./start.sh --storage-type oss

# 指定使用本地存储
./start.sh --storage-type local
```

### 存储配置参数

使用 `--storage-config` 参数以 JSON 格式指定存储配置，不同存储类型需要不同的配置参数。

#### S3 存储配置

S3 存储需要提供以下参数：
- `ak`: 访问密钥 ID (Access Key ID)
- `sk`: 访问密钥 (Secret Access Key)
- `endpoint`: S3 服务端点 URL
- `region`: AWS 区域名称，对应 `com.amazonaws.regions.Regions` 枚举值

```bash
# 配置 S3 存储
./start.sh --storage-type s3 --storage-config '{"ak":"YOUR_ACCESS_KEY_ID","sk":"YOUR_SECRET_ACCESS_KEY","endpoint":"https://s3.amazonaws.com","region":"US_EAST_1"}'
```

#### OSS 存储配置

OSS 存储继承自 S3 存储配置，需要提供相同的参数：
- `ak`: 访问密钥 ID
- `sk`: 访问密钥
- `endpoint`: OSS 服务端点 URL

```bash
# 配置 OSS 存储
./start.sh --storage-type oss --storage-config '{"ak":"YOUR_ACCESS_KEY_ID","sk":"YOUR_SECRET_ACCESS_KEY","endpoint":"https://oss-cn-hangzhou.aliyuncs.com"}'
```

#### 本地存储配置

本地存储需要提供以下参数：
- `rootPath`: 本地存储根目录路径（默认为 `/tmp/bella-files`）

```bash
# 配置本地存储
./start.sh --storage-type local --storage-config '{"rootPath":"/tmp/storage"}'
```

## 代理服务器配置

在某些网络环境下，可能需要通过代理服务器访问外部资源。Bella File API 支持配置 HTTP 或 SOCKS 代理。

### 代理配置参数

使用以下参数配置代理服务器：
- `--proxy-host`: 代理服务器主机名或 IP 地址
- `--proxy-port`: 代理服务器端口
- `--proxy-type`: 代理类型，支持 `http` 或 `socks`

```bash
# 配置 HTTP 代理
./start.sh --proxy-host 0.0.0.0 --proxy-port 8080 --proxy-type http

# 配置 SOCKS 代理
./start.sh --proxy-host 0.0.0.0 --proxy-port 1080 --proxy-type socks
```

### 代理配置示例

以下是一些常见的代理配置场景：

```bash
# 启动服务并配置 HTTP 代理
./start.sh --openapi_url https://example.com:8080/ --proxy-host 0.0.0.0 --proxy-port 8080 --proxy-type http

# 启动服务并配置 SOCKS 代理
./start.sh --openapi_url https://example.com:8080/ --proxy-host 0.0.0.0 --proxy-port 1080 --proxy-type socks

# 启动服务并同时配置存储和代理
./start.sh --storage-type s3 --storage-config '{"ak":"YOUR_ACCESS_KEY_ID","sk":"YOUR_SECRET_ACCESS_KEY","endpoint":"https://s3.amazonaws.com"}' --proxy-host 0.0.0.0 --proxy-port 8080 --proxy-type http
```

## 服务管理

### 停止服务

```bash
# 停止所有服务
docker-compose down

# 停止特定服务
docker-compose stop api
```

### 查看日志

```bash
# 查看所有服务的日志
docker-compose logs

# 查看特定服务的日志
docker-compose logs api
docker-compose logs mysql

# 实时查看日志
docker-compose logs -f
```

### 重启服务

```bash
# 使用start.sh脚本重启API服务
./start.sh --restart api

# 直接使用Docker Compose命令
docker-compose restart api
```

## Docker 镜像管理

### 推送 Docker 镜像

构建并推送镜像到指定的Docker仓库：
```bash
./start.sh --build --push --registry username --version v1.0.0
```

这个命令会执行以下操作：
1. 构建应用的Docker镜像
2. 为镜像打上标签：`username/example-file-api:v1.0.0`
3. 将镜像推送到Docker仓库

### 更新镜像
强制从远程仓库更新应用镜像，通常用于使用latest版本的情况：
```bash
./start.sh --update-image --openapi_url https://example.com:8080/
```

或者在共享模式下：
```bash
./start.sh --update-image --base-openapi
```

这个命令会拉取最新的应用镜像，即使本地已经存在该镜像。

## Nginx配置

Bella File API 支持通过Nginx进行反向代理，根据不同的部署模式有不同的配置方式。

### 独立部署模式下的Nginx

在独立部署模式下，Bella File API 会启动自己的Nginx服务，监听80端口，将请求转发到API服务。

**配置文件位置**：
- `/nginx/conf.d/default.conf`：Nginx服务器配置

**默认配置**：
```nginx
server {
    listen       80;
    listen  [::]:80;
    server_name  _;

    # 访问日志
    access_log  /var/log/nginx/host.access.log  main;

    # 所有请求转发到 api 服务
    location / {
        proxy_pass http://example-file-api:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 共享服务器模式下的Nginx

在与OpenAPI共享服务器的模式下，Bella File API 不需要启动自己的Nginx服务，而是通过Bella OpenAPI的动态服务配置功能，将请求路由到Bella File API服务。

**配置示例**：
```bash
# 在Bella OpenAPI中添加File API服务
cd /path/to/bella-openapi
./start.sh --service example-file-api:file.example.com:8081
```

### 容器名称使用

为了避免在Docker网络中的命名冲突，Nginx配置中使用容器名称而不是服务名称来路由请求：

- **正确**：`proxy_pass http://example-file-api:8081;`
- **避免**：`proxy_pass http://api:8081;`

这样做的好处是，即使在多个服务共享同一网络的情况下，也能确保请求被正确路由到目标服务。

**注意事项**：
1. 确保容器名称在整个Docker网络中是唯一的
2. 如果修改了容器名称，需要同步更新Nginx配置
3. 在共享网络环境中，建议始终使用完整的容器名称，而不是服务名称
