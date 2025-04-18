#!/bin/bash

# 设置日志路径
export LOG_PATH="/data0/www/applogs/"

# 启用apollo配置中心
export APOLLO_ENABLED='true'
export KAFKA_ENABLED='true'

if [ "x$ENVTYPE" = "xpreview" ]; then
  export SPRING_PROFILES_ACTIVE='preview'
elif [ "x$ENVTYPE" = "xdocker" ]; then
  export SPRING_PROFILES_ACTIVE="${ENVTYPE}"
elif [ "x$ENVTYPE" = "xtest" ]; then
  export SPRING_PROFILES_ACTIVE="${ENVTYPE}"
elif [ "x$ENVTYPE" = "xprod" ]; then
  export SPRING_PROFILES_ACTIVE="${ENVTYPE}"
  export HAWK_SERVER_HOST="example.com"
  export HAWK_SERVER_SOCKET_PORT="8101"
elif [ "x$ENVTYPE" = "xdev" ]; then
  export SPRING_PROFILES_ACTIVE="${ENVTYPE}"
elif [ -n "${ENVTYPE}" ]; then
  export SPRING_PROFILES_ACTIVE="${ENVTYPE}"
else
  export SPRING_PROFILES_ACTIVE='prod'
fi

USER_OPTS=""
USER_OPTS="$USER_OPTS -XX:+PrintPromotionFailure -XX:+PrintGCApplicationStoppedTime -XX:+PrintGCCause"
USER_OPTS="$USER_OPTS -XX:-UseBiasedLocking -XX:AutoBoxCacheMax=20000 -Djava.security.egd=file:/dev/./urandom"
USER_OPTS="$USER_OPTS -XX:+PrintCommandLineFlags -XX:-OmitStackTraceInFastThrow"
USER_OPTS="$USER_OPTS -Djava.net.preferIPv4Stack=true -Djava.awt.headless=true -Dfile.encoding=UTF-8"
USER_OPTS="$USER_OPTS -Droot.path=${MATRIX_CODE_DIR}"
USER_OPTS="$USER_OPTS -Dlogging.path=${MATRIX_APPLOGS_DIR}"
USER_OPTS="$USER_OPTS -Djava.io.tmpdir=${MATRIX_CACHE_DIR}"
USER_OPTS="$USER_OPTS -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}"
USER_OPTS="$USER_OPTS -Dspring.config.apollo.server=http://${APOLLO_SERVER:-${SH_APOLLO_SERVER}}"
USER_OPTS="$USER_OPTS -Dspring.config.apollo.environment=${APOLLO_ENV}"
USER_OPTS="$USER_OPTS -Dspring.config.apollo.cache-path=${MATRIX_PRIVDATA_DIR}"
USER_OPTS="$USER_OPTS -Dspring.config.apollo.cluster=${APOLLO_CLUSTER}"
USER_OPTS="$USER_OPTS -Dspring.discovery.client.server-name=${EUREKA_SERVER_NAME:-${SH_EUREKA_SERVER_NAME}}"
USER_OPTS="$USER_OPTS -Dspring.discovery.client.server-port=${EUREKA_SERVER_PORT}"
USER_OPTS="$USER_OPTS -Dspring.discovery.client.module-name=${MODULE}"
USER_OPTS="$USER_OPTS -Dspring.discovery.client.zone=${EUREKA_ZONE}"
USER_OPTS="$USER_OPTS -Dspring.discovery.client.role=${EUREKA_ROLE}"
USER_OPTS="$USER_OPTS -Dorg.gradle.daemon=false"
# jar包外读取配置文件，需要额外增加如下内容，同时修改打包脚本，将配置文件都cp到conf下
# USER_ARGS="$USER_ARGS --spring.config.location=$MATRIX_CODE_DIR/conf/"
if [ -n "${HAWK_SERVER_HOST}" ]; then
  USER_OPTS="$USER_OPTS -Dhawk.server.host=${HAWK_SERVER_HOST}"
  USER_OPTS="$USER_OPTS -Dhawk.server.socket-port=${HAWK_SERVER_SOCKET_PORT}"
fi
