#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

MYSQL_CONTAINER_NAME="${MYSQL_CONTAINER_NAME:-bella-knowledge-mysql}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-bella_file_api}"
MYSQL_USER="${MYSQL_USER:-bella_user}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-123456}"
MYSQL_PORT="${MYSQL_PORT:-3306}"

REDIS_CONTAINER_NAME="${REDIS_CONTAINER_NAME:-bella-knowledge-redis}"
REDIS_PORT="${REDIS_PORT:-6380}"
REDIS_PASSWORD="${REDIS_PASSWORD:-bella123}"

MINIO_CONTAINER_NAME="${MINIO_CONTAINER_NAME:-bella-knowledge-minio}"
MINIO_PORT="${MINIO_PORT:-9000}"
MINIO_CONSOLE_PORT="${MINIO_CONSOLE_PORT:-9001}"
MINIO_ROOT_USER="${MINIO_ROOT_USER:-minioadmin}"
MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD:-minioadmin}"
MINIO_BUCKET="${MINIO_BUCKET:-bella-file-api}"

API_PORT="${API_PORT:-18081}"
WEB_PORT="${WEB_PORT:-3002}"
DEV_AUTH_TOKEN="${DEV_AUTH_TOKEN:-local-dev}"

PIDS=()
STOPPING=0

log() {
  printf '[local-dev] %s\n' "$*"
}

have() {
  command -v "$1" >/dev/null 2>&1
}

port_open() {
  nc -z 127.0.0.1 "$1" >/dev/null 2>&1
}

wait_port() {
  local port="$1"
  local name="$2"
  local i

  for i in $(seq 1 60); do
    if port_open "$port"; then
      log "$name is ready on port $port"
      return 0
    fi
    sleep 1
  done

  log "$name did not become ready on port $port"
  return 1
}

container_exists() {
  docker ps -a --format '{{.Names}}' | grep -Fxq "$1"
}

container_running() {
  docker ps --format '{{.Names}}' | grep -Fxq "$1"
}

ensure_mysql() {
  if container_running "$MYSQL_CONTAINER_NAME"; then
    log "MySQL container is already running: $MYSQL_CONTAINER_NAME"
  elif container_exists "$MYSQL_CONTAINER_NAME"; then
    log "Starting MySQL container: $MYSQL_CONTAINER_NAME"
    docker start "$MYSQL_CONTAINER_NAME" >/dev/null
  else
    log "Creating MySQL container: $MYSQL_CONTAINER_NAME"
    docker run -d \
      --name "$MYSQL_CONTAINER_NAME" \
      -p "$MYSQL_PORT:3306" \
      -e MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
      -e MYSQL_DATABASE="$MYSQL_DATABASE" \
      -e MYSQL_USER="$MYSQL_USER" \
      -e MYSQL_PASSWORD="$MYSQL_PASSWORD" \
      -v bella-knowledge-mysql-data:/var/lib/mysql \
      -v "$ROOT_DIR/api/sql:/docker-entrypoint-initdb.d:ro" \
      mysql:8.0 \
      --default-authentication-plugin=mysql_native_password \
      --character-set-server=utf8mb4 \
      --collation-server=utf8mb4_0900_ai_ci >/dev/null
  fi

  wait_port "$MYSQL_PORT" MySQL
}

ensure_redis() {
  if container_running "$REDIS_CONTAINER_NAME"; then
    log "Redis container is already running: $REDIS_CONTAINER_NAME"
  elif container_exists "$REDIS_CONTAINER_NAME"; then
    log "Starting Redis container: $REDIS_CONTAINER_NAME"
    docker start "$REDIS_CONTAINER_NAME" >/dev/null
  else
    log "Creating Redis container: $REDIS_CONTAINER_NAME"
    docker run -d \
      --name "$REDIS_CONTAINER_NAME" \
      -p "$REDIS_PORT:6379" \
      -v bella-knowledge-redis-data:/data \
      redis:7-alpine \
      redis-server --appendonly yes --requirepass "$REDIS_PASSWORD" >/dev/null
  fi

  wait_port "$REDIS_PORT" Redis
}

find_minio_container() {
  if container_running "$MINIO_CONTAINER_NAME"; then
    printf '%s' "$MINIO_CONTAINER_NAME"
    return 0
  fi
  if container_running api-minio-1; then
    printf '%s' api-minio-1
    return 0
  fi
  return 1
}

ensure_minio() {
  local minio_container

  if minio_container="$(find_minio_container)"; then
    log "MinIO container is already running: $minio_container"
  elif container_exists "$MINIO_CONTAINER_NAME"; then
    log "Starting MinIO container: $MINIO_CONTAINER_NAME"
    docker start "$MINIO_CONTAINER_NAME" >/dev/null
    minio_container="$MINIO_CONTAINER_NAME"
  elif port_open "$MINIO_PORT"; then
    log "MinIO port $MINIO_PORT is already in use; reusing external MinIO"
    return 0
  else
    log "Creating MinIO container: $MINIO_CONTAINER_NAME"
    docker run -d \
      --name "$MINIO_CONTAINER_NAME" \
      -p "$MINIO_PORT:9000" \
      -p "$MINIO_CONSOLE_PORT:9001" \
      -e MINIO_ROOT_USER="$MINIO_ROOT_USER" \
      -e MINIO_ROOT_PASSWORD="$MINIO_ROOT_PASSWORD" \
      -v bella-knowledge-minio-data:/data \
      minio/minio server /data --console-address ":9001" >/dev/null
    minio_container="$MINIO_CONTAINER_NAME"
  fi

  wait_port "$MINIO_PORT" MinIO
  ensure_minio_bucket "$minio_container"
}

ensure_minio_bucket() {
  local minio_container="$1"

  log "Ensuring MinIO bucket exists: $MINIO_BUCKET"
  docker run --rm \
    --entrypoint /bin/sh \
    --network "container:$minio_container" \
    minio/mc \
    -c "mc alias set local http://127.0.0.1:9000 '$MINIO_ROOT_USER' '$MINIO_ROOT_PASSWORD' >/dev/null && mc mb -p 'local/$MINIO_BUCKET' >/dev/null 2>&1 || true"
}

cleanup() {
  STOPPING=1
  log "Stopping local dev processes"
  if [ "${#PIDS[@]}" -eq 0 ]; then
    return 0
  fi
  for pid in "${PIDS[@]}"; do
    if kill -0 "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
    fi
  done
}

start_api() {
  if port_open "$API_PORT"; then
    log "API port $API_PORT is already in use; reusing existing backend"
    return 0
  fi

  log "Starting API on http://localhost:$API_PORT"
  (
    cd "$ROOT_DIR/api"
    SPRING_PROFILES_ACTIVE=local \
    SERVER_PORT="$API_PORT" \
    MYSQL_PORT="$MYSQL_PORT" \
    MYSQL_DATABASE="$MYSQL_DATABASE" \
    MYSQL_USER="$MYSQL_USER" \
    MYSQL_PASSWORD="$MYSQL_PASSWORD" \
    REDIS_PORT="$REDIS_PORT" \
    REDIS_PASSWORD="$REDIS_PASSWORD" \
    BELLA_STORAGE_S3_BUCKET="$MINIO_BUCKET" \
    S3_ENDPOINT="http://127.0.0.1:$MINIO_PORT" \
    S3_ACCESS_KEY="$MINIO_ROOT_USER" \
    S3_SECRET_KEY="$MINIO_ROOT_PASSWORD" \
    mvn spring-boot:run
  ) &
  PIDS+=("$!")
}

start_web() {
  if port_open "$WEB_PORT"; then
    log "Web port $WEB_PORT is already in use; reusing existing frontend"
    return 0
  fi

  log "Starting Web on http://localhost:$WEB_PORT"
  (
    cd "$ROOT_DIR/web"
    while [ "$STOPPING" -eq 0 ]; do
      BELLA_DEV_AUTH=true \
      BELLA_DEV_AUTH_TOKEN="$DEV_AUTH_TOKEN" \
      BELLA_DEV_USER_ID="${BELLA_DEV_USER_ID:-1}" \
      BELLA_DEV_USER_NAME="${BELLA_DEV_USER_NAME:-Local Dev}" \
      BELLA_DEV_SPACE_CODE="${BELLA_DEV_SPACE_CODE:-local-dev}" \
      BELLA_DEV_SPACE_NAME="${BELLA_DEV_SPACE_NAME:-Local Dev}" \
      NEXT_PUBLIC_BELLA_FILE_API_URL="http://localhost:$API_PORT" \
      NEXT_PUBLIC_BELLA_OPENAPI_URL="${NEXT_PUBLIC_BELLA_OPENAPI_URL:-https://api.bella.top}" \
      WATCHPACK_POLLING="${WATCHPACK_POLLING:-true}" \
      CHOKIDAR_USEPOLLING="${CHOKIDAR_USEPOLLING:-true}" \
      PORT="$WEB_PORT" \
      pnpm dev

      if [ "$STOPPING" -eq 0 ]; then
        log "Web dev server exited; restarting in 2s"
        sleep 2
      fi
    done
  ) &
  PIDS+=("$!")
}

main() {
  trap cleanup INT TERM EXIT

  have docker || { log "docker is required"; exit 1; }
  have mvn || { log "mvn is required"; exit 1; }
  have pnpm || { log "pnpm is required"; exit 1; }
  have nc || { log "nc is required"; exit 1; }

  ensure_mysql
  ensure_redis
  ensure_minio
  start_api
  start_web

  log "Local dev is starting"
  log "API: http://localhost:$API_PORT"
  log "Web: http://localhost:$WEB_PORT/admin"
  log "Press Ctrl-C to stop API/Web processes started by this script"

  wait
}

main "$@"
