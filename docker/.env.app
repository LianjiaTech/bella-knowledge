# Bella File API Enterprise App Configuration
# 应用层配置，连接到bella-workflow的中间件基础设施
SPRING_PROFILES_ACTIVE=docker,enterprise

## OpenAPI业务配置
BELLA_OPEN_API_BASE=https://openapi-ait.ke.com
BELLA_OPENAPI_URL=https://bella-openapi.ke.com
BELLA_SESSION_COOKIE_DOMAIN=ke.com
BELLA_SESSION_COOKIE_NAME=bella_openapi_sessionId
BELLA_LOGIN_TYPE=client

# 时区配置
TZ=Asia/Shanghai

# MySQL 配置 (连接到bella-workflow的中间件)
MYSQL_HOST=bella-mysql
MYSQL_PORT=3306
MYSQL_DATABASE=bella_file_api
MYSQL_USER=bella_user
MYSQL_PASSWORD=123456
MYSQL_ROOT_PASSWORD=root

# Redis 配置 (连接到bella-workflow的中间件)
REDIS_HOST=bella-redis
REDIS_PORT=6379
REDIS_PASSWORD=bella123

# Kafka 配置 (连接到bella-workflow的中间件)
KAFKA_ENABLED=true
KAFKA_HOST=bella-kafka
KAFKA_PORT=9092
KAFKA_EXTERNAL_HOST=bella-kafka
KAFKA_EXTERNAL_PORT=19092
FILE_API_TOPIC=bella_file_api

# MinIO 配置 (连接到bella-workflow的中间件)
MINIO_ROOT_USER=bella_admin
MINIO_ROOT_PASSWORD=bella123456
S3_HOST=bella-minio
S3_API_PORT=9000
S3_CONSOLE_PORT=9001
S3_ACCESS_KEY=bella_admin
S3_SECRET_KEY=bella123456
S3_BUCKET=bella-file-api
S3_ROOT_PATH=files
S3_REGION=us-east-1

# Elasticsearch 配置 (连接到bella-workflow的中间件)
ELASTICSEARCH_HOST=bella-elasticsearch
ELASTICSEARCH_HTTP_PORT=9200
ELASTICSEARCH_HOSTS=bella-elasticsearch:9200
ELASTICSEARCH_USERNAME=
ELASTICSEARCH_PASSWORD=

# =================================================================
# API 应用配置
# =================================================================
API_HOST=bella-file-api
API_PORT=8081
API_MIN_HEAP=1024m
API_MAX_HEAP=2048m
API_METASPACE=256m
API_MAX_METASPACE=512m
LOG_PATH=/opt/bella-file-api/applogs

# Spring Configuration
SPRING_PROFILES_ACTIVE=docker

# Bella 应用配置
BELLA_STORAGE_TYPE=s3
BELLA_TMP_FILE_DIR=/opt/bella-file-api/cache
BELLA_STORAGE_S3_BUCKET=bella-file-api

# =================================================================
# Web 前端配置
# =================================================================
WEB_PORT=3001
ALLOWED_DEV_ORIGINS=
SERVER_DOMAIN=bella-file-api
SERVER_PROTOCOL=http

# =================================================================
# Docker 配置
# =================================================================
REGISTRY=bellatop
VERSION=latest
MAVEN_MIRROR=aliyun