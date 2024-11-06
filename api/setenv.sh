#!/bin/bash

SH_APOLLO_SERVER="example.com"
SH_EUREKA_SERVER_NAME="example.com"

if [ "x$ENVTYPE" = "xpreview" ]; then
  export SPRING_PROFILES_ACTIVE='preview'
  export APOLLO_SERVER="prev.${APOLLO_SERVER:-${SH_APOLLO_SERVER}}"
  export APOLLO_ENV=${APOLLO_ENV:-"PREV"}
  export APOLLO_CLUSTER=${APOLLO_CLUSTER:-"default"}
  export EUREKA_SERVER_NAME="prev.${EUREKA_SERVER_NAME:-${SH_EUREKA_SERVER_NAME}}"
  export EUREKA_SERVER_PORT="10135"
  export EUREKA_ZONE="${IDC}"
elif [ "x$ENVTYPE" = "xdocker" ]; then
  export SPRING_PROFILES_ACTIVE="${ENVTYPE}"
  export EUREKA_SERVER_NAME="test.${EUREKA_SERVER_NAME:-${SH_EUREKA_SERVER_NAME}}"
  export EUREKA_SERVER_PORT="10140"
  export EUREKA_ZONE="${IDC}"
  export APOLLO_SERVER="test.${APOLLO_SERVER:-${SH_APOLLO_SERVER}}"
  export APOLLO_ENV=${APOLLO_ENV:-"TEST"}
  export APOLLO_CLUSTER="${APOLLO_CLUSTER:-default}"
  export EUREKA_ROLE="${ROLE:-${ENVNAME}}"
elif [ "x$ENVTYPE" = "xtest" ]; then
  export SPRING_PROFILES_ACTIVE="${ENVTYPE}"
  export APOLLO_SERVER="test.${APOLLO_SERVER:-${SH_APOLLO_SERVER}}"
  export APOLLO_ENV=${APOLLO_ENV:-"TEST"}
  export APOLLO_CLUSTER=${APOLLO_CLUSTER:-"default"}
  export EUREKA_SERVER_NAME="test.${EUREKA_SERVER_NAME:-${SH_EUREKA_SERVER_NAME}}"
  export EUREKA_SERVER_PORT="10140"
  export EUREKA_ZONE="${IDC}"
  export EUREKA_ROLE="${ENVTYPE}"
elif [ "x$ENVTYPE" = "xprod" ]; then
  export SPRING_PROFILES_ACTIVE="${ENVTYPE}"
  export APOLLO_SERVER="prod.${APOLLO_SERVER:-${SH_APOLLO_SERVER}}"
  export APOLLO_ENV=${APOLLO_ENV:-"PROD"}
  export APOLLO_CLUSTER=${APOLLO_CLUSTER:-"default"}
  export EUREKA_SERVER_NAME="prod.${EUREKA_SERVER_NAME:-${SH_EUREKA_SERVER_NAME}}"
  export EUREKA_SERVER_PORT="10122"
  export EUREKA_ZONE="${IDC}"
  export HAWK_SERVER_HOST="example.com"
  export HAWK_SERVER_SOCKET_PORT="8101"
elif [ "x$ENVTYPE" = "xdev" ]; then
  export SPRING_PROFILES_ACTIVE="${ENVTYPE}"
  export APOLLO_SERVER="dev.${APOLLO_SERVER:-${SH_APOLLO_SERVER}}"
  export APOLLO_ENV=${APOLLO_ENV:-"DEV"}
  export APOLLO_CLUSTER=${APOLLO_CLUSTER:-"default"}
  export EUREKA_SERVER_NAME="dev.${EUREKA_SERVER_NAME:-${SH_EUREKA_SERVER_NAME}}"
  export EUREKA_SERVER_PORT="10153"
  export EUREKA_ZONE="${IDC}"
elif [ -n "${ENVTYPE}" ]; then
  export SPRING_PROFILES_ACTIVE="${ENVTYPE}"
  export APOLLO_SERVER="test.${APOLLO_SERVER:-${SH_APOLLO_SERVER}}"
  export APOLLO_ENV=${APOLLO_ENV:-"TEST"}
  export APOLLO_CLUSTER=${APOLLO_CLUSTER:-"default"}
  export EUREKA_SERVER_NAME="test.${EUREKA_SERVER_NAME:-${SH_EUREKA_SERVER_NAME}}"
  export EUREKA_SERVER_PORT="10140"
  export EUREKA_ZONE="${IDC}"
  export EUREKA_ROLE="${ENVTYPE}"
else
  export SPRING_PROFILES_ACTIVE='prod'
  export APOLLO_SERVER="prod.${APOLLO_SERVER:-${SH_APOLLO_SERVER}}"
  export APOLLO_ENV=${APOLLO_ENV:-"PROD"}
  export APOLLO_CLUSTER=${APOLLO_CLUSTER:-"default"}
  export EUREKA_SERVER_NAME="prod.${EUREKA_SERVER_NAME:-${SH_EUREKA_SERVER_NAME}}"
  export EUREKA_SERVER_PORT="10122"
  export EUREKA_ZONE="${IDC}"
  export HAWK_SERVER_HOST="example.com"
  export HAWK_SERVER_SOCKET_PORT="8101"
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
