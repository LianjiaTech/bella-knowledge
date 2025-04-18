#!/bin/bash

# 帮助函数
show_help() {
    echo "用法: $0 [选项]"
    echo "选项:"
    echo "  -h, --help       显示帮助信息"
    echo "  -b, --build      重新构建服务"
    echo "  -r, --rebuild    强制重新构建服务（不使用缓存）"
    echo "  --base-openapi   与OpenAPI在同一服务器上启动，不需要再启动MySQL"
    echo "  --openapi_url URL 指定OpenAPI的URL（当不在同一服务器启动时必须提供）"
    echo "  -v, --version VERSION 指定镜像版本"
    echo "  --push           构建后推送镜像到仓库"
    echo "  --registry username   指定推送的docker仓库 (username)"
    echo "  --restart SERVICE     重启指定服务，不重新编译 (例如: api)"
    echo "  --update-image   从远程仓库更新镜像，即使本地已存在"
    echo "  --storage-type TYPE 指定存储类型 (s3, oss, local), 默认为local"
    echo "  --storage-config CONFIG 指定存储配置，格式为 JSON"
    echo "  --proxy-host HOST                         配置代理服务器主机名或IP地址"
    echo "  --proxy-port PORT                         配置代理服务器端口"
    echo "  --proxy-type TYPE                         配置代理类型 (socks 或 http)"
    echo ""
    echo "示例:"
    echo "  ./start.sh           启动服务（如果已存在编译文件则不重新构建）"
    echo "  ./start.sh --build   启动服务并重新构建"
    echo "  ./start.sh --rebuild 启动服务并强制重新构建"
    echo "  ./start.sh --base-openapi   与OpenAPI在同一服务器上启动"
    echo "  ./start.sh --openapi_url https://example.com:8080/   指定OpenAPI的URL"
    echo "  ./start.sh --storage-type s3   指定存储类型为S3"
    echo "  ./start.sh --storage-config '{\"ak\":\"YOUR_ACCESS_KEY_ID\",\"sk\":\"YOUR_SECRET_ACCESS_KEY\",\"endpoint\":\"https://s3.amazonaws.com\",\"region\":\"US_EAST_1\"}'   指定存储配置"
    echo "  ./start.sh --proxy-host 0.0.0.0 --proxy-port 8118 --proxy-type http   配置HTTP代理"
    echo "  ./start.sh --build --push --registry username --version v1.0.0   构建并推送镜像到指定仓库"
    echo "  ./start.sh --restart api    仅重启 API 服务，不重新编译"
    echo "  ./start.sh --update-image   从远程仓库更新镜像"
    echo ""
    echo "版本参数:"
    echo "  --version VERSION    指定镜像版本，例如 --version v1.0.0"
    echo "  -v VERSION    指定镜像版本，例如 --v v1.0.0"
}

# 默认值
BUILD=""
FORCE_RECREATE=""
BASE_OPENAPI=false
OPENAPI_URL=""
STORAGE_TYPE="local"
STORAGE_CONFIG=""
PUSH=false
REGISTRY=""
NO_CACHE=""
RESTART_SERVICE=""
UPDATE_IMAGE=false
# 代理配置
PROXY_HOST=""
PROXY_PORT=""
PROXY_TYPE=""

# 添加重试函数
retry_command() {
    local max_attempts=3
    local timeout=5
    local attempt=1
    local exit_code=0

    while [[ $attempt -le $max_attempts ]]
    do
        echo "尝试执行命令: $@（第 $attempt 次，共 $max_attempts 次）"
        "$@"
        exit_code=$?

        if [[ $exit_code -eq 0 ]]; then
            echo "命令执行成功！"
            break
        fi

        echo "命令执行失败，退出码: $exit_code"
        
        if [[ $attempt -lt $max_attempts ]]; then
            echo "等待 $timeout 秒后重试..."
            sleep $timeout
            # 每次重试增加等待时间
            timeout=$((timeout * 2))
        fi
        
        attempt=$((attempt + 1))
    done

    return $exit_code
}

# 检查镜像是否存在
image_exists() {
    local image_name=$1
    docker image inspect $image_name &>/dev/null
    return $?
}

# 拉取镜像（如果本地不存在）
pull_image_if_not_exists() {
    local image_name=$1
    local update_image=${2:-false}
    local message=${3:-"拉取镜像: $image_name"}
    
    if [ "$update_image" = true ] || ! image_exists $image_name; then
        echo "$message"
        retry_command docker pull $image_name || true
        return $?
    else
        echo "本地镜像 $image_name 已存在，跳过拉取"
        return 0
    fi
}

# 拉取应用镜像（如果本地不存在）
pull_app_image_if_not_exists() {
    local service=$1
    local version=${VERSION:-latest}
    
    # 镜像名称（带仓库前缀）
    local image_name="${REGISTRY:-saizhuolin}/example-file-api:$version"
    
    # 检查镜像是否存在
    if [ "$UPDATE_IMAGE" = true ] || ! image_exists $image_name; then
        if [ "$UPDATE_IMAGE" = true ]; then
            echo "强制更新镜像 $image_name ..."
        else
            echo "镜像 $image_name 不存在，尝试从远程仓库拉取..."
        fi
        pull_image_if_not_exists $image_name $UPDATE_IMAGE "拉取 $service 镜像: $image_name"
        return $?
    else
        echo "镜像 $image_name 已存在，跳过拉取"
        return 0
    fi
}

# 预先拉取所需的 Docker 镜像
pre_pull_images() {
    if [ "$UPDATE_IMAGE" = true ]; then
        echo "强制更新应用镜像..."
    else
        echo "预先拉取所需的 Docker 镜像..."
    fi
    
    # 创建数据目录并设置权限
    if [ "$BASE_OPENAPI" = false ] && [ "$PUSH" = false ]; then
        echo "创建数据目录并设置权限..."
        mkdir -p ./mysql/data
        chmod -R 777 ./mysql/data
    fi
    
    # 拉取基础镜像（如果本地不存在）
    pull_image_if_not_exists "openjdk:8" false "拉取 OpenJDK 镜像..."
    
    if [ "$BASE_OPENAPI" = false ] && [ "$PUSH" = false ]; then
        pull_image_if_not_exists "mysql:8.0" false "拉取 MySQL 镜像..."
    fi
    
    # 如果不需要编译（没有 --build 参数），则拉取应用镜像
    if [ -z "$BUILD" ]; then
        echo "检查是否需要拉取应用镜像..."
        
        # 拉取 API 镜像
        pull_app_image_if_not_exists "api"
    else
        echo "检测到 --build 参数，跳过拉取应用镜像，将使用本地构建"
    fi
    
    echo "所有镜像拉取完成"
}

# 检查MySQL数据库是否存在
check_mysql_database() {
    echo "检查MySQL数据库是否存在..."
    
    # 检查MySQL是否正在运行
    if ! docker exec -i example-file-api-mysql mysqladmin ping -h localhost -u root --password=123456 &>/dev/null; then
        echo "MySQL未运行，跳过数据库检查"
        return 1
    fi
    
    # 检查数据库是否存在
    if ! docker exec -i example-file-api-mysql mysql -u root --password=123456 -e "SHOW DATABASES LIKE 'bella_file_api';" | grep -q "bella_file_api"; then
        echo "数据库 bella_file_api 不存在，将创建数据库并初始化"
        docker exec -i example-file-api-mysql mysql -u root --password=123456 -e "CREATE DATABASE IF NOT EXISTS bella_file_api CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
        
        # 执行SQL初始化脚本
        for sql_file in $(ls -v ./api/sql/*.sql); do
            echo "执行SQL脚本: $sql_file"
            docker exec -i example-file-api-mysql mysql -u root --password=123456 bella_file_api < "$sql_file"
        done
        
        return 0
    else
        echo "数据库 bella_file_api 已存在，跳过创建"
        return 0
    fi
}

# 检查本地MySQL数据库是否存在（当与OpenAPI在同一服务器上启动时）
check_local_mysql_database() {
    echo "检查本地MySQL数据库是否存在..."
    
    # 使用Docker容器中的MySQL客户端
    # 首先检查bella-openapi-mysql容器是否存在并运行
    if ! docker ps | grep -q "bella-openapi-mysql"; then
        echo "未找到运行中的bella-openapi-mysql容器，请确保OpenAPI已正确启动"
        return 1
    fi
    
    # 检查数据库是否存在
    if ! docker exec -i bella-openapi-mysql mysql -h localhost -u root --password=123456 -e "SHOW DATABASES LIKE 'bella_file_api';" | grep -q "bella_file_api"; then
        echo "数据库 bella_file_api 不存在，将创建数据库并初始化"
        docker exec -i bella-openapi-mysql mysql -h localhost -u root --password=123456 -e "CREATE DATABASE IF NOT EXISTS bella_file_api CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
        
        # 执行SQL初始化脚本
        for sql_file in $(ls -v ./api/sql/*.sql); do
            echo "执行SQL脚本: $sql_file"
            docker exec -i bella-openapi-mysql mysql -h localhost -u root --password=123456 bella_file_api < "$sql_file"
        done
        
        return 0
    else
        echo "数据库 bella_file_api 已存在，跳过创建"
        return 0
    fi
}

# 构建服务
build_services() {
    # 设置缓存选项
    CACHE_OPT=""
    if [ -n "$NO_CACHE" ]; then
        CACHE_OPT="--no-cache"
    fi
    
    # 执行 API 服务的 Maven 编译
    echo "执行 API 服务的 Maven 编译..."
    chmod +x api/build.sh
    ./api/build.sh
    if [ $? -ne 0 ]; then
        echo "错误: API 服务编译失败，退出执行"
        exit 1
    fi
    
    # 根据是否需要推送镜像选择构建方式
    if [ "$PUSH" = true ] && [ -n "$REGISTRY" ]; then
        # 多架构构建并推送
        if docker buildx version >/dev/null 2>&1; then
            echo "使用 buildx 进行多架构构建并推送..."
            
            # 清理 builder 缓存，避免磁盘空间不足
            echo "清理 buildx 缓存..."
            docker buildx prune -f
            
            # 删除并重新创建 builder 实例，确保干净的构建环境
            echo "重新创建 buildx builder 实例..."
            docker buildx rm multibuilder 2>/dev/null || true
            docker buildx create --name multibuilder --driver docker-container --bootstrap --use
            
            # 确认 builder 状态
            echo "检查 builder 状态..."
            docker buildx inspect --bootstrap
            
            # 推送时使用多架构
            PLATFORMS="linux/amd64,linux/arm64"
            echo "推送多架构镜像，支持平台: $PLATFORMS"
            
            # 构建并推送 API 多架构镜像
            echo "构建并推送 API 多架构镜像..."
            docker buildx build $CACHE_OPT \
                --platform $PLATFORMS \
                --build-arg VERSION=${VERSION:-v1.0.0} \
                --build-arg REGISTRY=${REGISTRY:-saizhuolin} \
                -t ${REGISTRY:-saizhuolin}/example-file-api:${VERSION:-v1.0.0} \
                -t ${REGISTRY:-saizhuolin}/example-file-api:latest \
                --push ./api
                
            echo "验证多架构镜像..."
            docker buildx imagetools inspect ${REGISTRY:-saizhuolin}/example-file-api:${VERSION:-v1.0.0}
                
            echo "✅ 多架构镜像已成功推送到 ${REGISTRY:-saizhuolin}"
            echo "   这些镜像可以在任何支持的平台上运行，包括:"
            echo "   - x86_64/amd64 系统 (大多数 Linux 服务器、Intel Mac、Windows)"
            echo "   - ARM64 系统 (Apple Silicon Mac、AWS Graviton、树莓派 4 64位)"
            
            # 推送后不自动启动服务，直接退出
            echo ""
            echo "镜像已成功推送，可以在服务器上使用 start.sh 脚本启动服务"
            exit 0
        else
            echo "错误: buildx 不可用，无法构建多架构镜像"
            exit 1
        fi
    else
        # 本地构建，使用 docker-compose
        echo "本地构建，使用 docker-compose..."
        if [ -n "$NO_CACHE" ]; then
            echo "强制重新构建（不使用缓存）..."
            docker-compose build --no-cache --build-arg VERSION=${VERSION:-v1.0.0} --build-arg REGISTRY=${REGISTRY:-saizhuolin}
        else
            echo "重新构建..."
            docker-compose build --build-arg VERSION=${VERSION:-v1.0.0} --build-arg REGISTRY=${REGISTRY:-saizhuolin}
        fi
    fi
}

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -b|--build)
            BUILD="--build"
            shift
            ;;
        -r|--rebuild)
            BUILD="--build"
            FORCE_RECREATE="--force-recreate"
            NO_CACHE="--no-cache"
            shift
            ;;
        --base-openapi)
            BASE_OPENAPI=true
            shift
            ;;
        --openapi_url)
            OPENAPI_URL="$2"
            shift 2
            ;;
        -v|--version)
            VERSION="$2"
            shift 2
            ;;
        --push)
            PUSH=true
            shift
            ;;
        --registry)
            REGISTRY="$2"
            shift 2
            ;;
        --restart)
            RESTART_SERVICE="$2"
            shift 2
            ;;
        --update-image)
            UPDATE_IMAGE=true
            shift
            ;;
        --storage-type)
            STORAGE_TYPE="$2"
            shift 2
            ;;
        --storage-config)
            STORAGE_CONFIG="$2"
            shift 2
            ;;
        --proxy-host)
            PROXY_HOST="$2"
            shift 2
            ;;
        --proxy-port)
            PROXY_PORT="$2"
            shift 2
            ;;
        --proxy-type)
            PROXY_TYPE="$2"
            shift 2
            ;;
        *)
            echo "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
done

# 设置环境变量
export VERSION=${VERSION:-latest}
export REGISTRY=${REGISTRY:-saizhuolin}

# 设置代理环境变量
if [ -n "$PROXY_HOST" ] && [ -n "$PROXY_PORT" ] && [ -n "$PROXY_TYPE" ]; then
    echo "配置代理服务器: $PROXY_TYPE://$PROXY_HOST:$PROXY_PORT"
    export PROXY_HOST=$PROXY_HOST
    export PROXY_PORT=$PROXY_PORT
    export PROXY_TYPE=$PROXY_TYPE
fi

#设置存储环境变量
if [ -n "$STORAGE_TYPE" ]; then
    echo "配置存储类型: $STORAGE_TYPE"
    export STORAGE_TYPE=$STORAGE_TYPE
fi

if [ -n "$STORAGE_CONFIG" ]; then
    echo "配置存储配置: $STORAGE_CONFIG"
    export STORAGE_CONFIG=$STORAGE_CONFIG
fi

# 如果指定了重启服务
if [ -n "$RESTART_SERVICE" ]; then
    echo "重启服务: $RESTART_SERVICE"
    docker-compose restart $RESTART_SERVICE
    exit 0
fi

# 检查参数
if [ "$BASE_OPENAPI" = false ] && [ -z "$OPENAPI_URL" ] && [ "$PUSH" = false ]; then
    echo "错误: 当不在同一服务器启动时，必须提供 --openapi_url 参数"
    show_help
    exit 1
fi

# 设置OpenAPI URL环境变量
if [ "$BASE_OPENAPI" = true ]; then
    export OPENAPI_URL="http://localhost:8080/"
else
    export OPENAPI_URL="$OPENAPI_URL"
fi

# 预先拉取镜像
if [ "$UPDATE_IMAGE" = true ]; then
    echo "强制更新应用镜像..."
    pre_pull_images
elif [ -z "$BUILD" ]; then
    echo "检查是否需要更新镜像..."
    pre_pull_images
fi

# 执行构建（如果需要）
if [ -n "$BUILD" ] || [ -n "$FORCE_RECREATE" ]; then
    echo "构建服务..."
    build_services
fi

# 如果与OpenAPI在同一服务器上启动
if [ "$BASE_OPENAPI" = true ]; then
    echo "与OpenAPI在同一服务器上启动，检查本地MySQL数据库..."
    check_local_mysql_database
    docker-compose -f docker-compose-api.yml up -d api
else
    # 启动所有服务
    echo "启动所有服务..."
    docker-compose up -d ${FORCE_RECREATE}
    
    # 检查MySQL数据库
    echo "等待MySQL启动..."
    sleep 10
    check_mysql_database
fi

# 检查服务是否启动成功
echo "检查服务状态..."
sleep 5  # 等待服务启动

# 获取服务状态
SERVICES_STATUS=$(docker-compose ps --services --filter "status=running")

# 检查 API 服务
if ! echo "$SERVICES_STATUS" | grep -q "api"; then
    echo "错误: API 服务启动失败"
    echo "查看日志: docker-compose logs api"
    exit 1
fi

echo "✅ 服务启动成功！"
echo ""
echo "查看日志: docker-compose logs -f"
