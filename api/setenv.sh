#!/bin/bash

# 设置日志路径
export LOG_PATH="${MATRIX_APPLOGS_DIR:-/opt/bella-file-api/applogs}"

if [ "x$ENVTYPE" = "xpreview" ]; then
  export SPRING_PROFILES_ACTIVE='preview'
elif [ "x$ENVTYPE" = "xdocker" ]; then
  export SPRING_PROFILES_ACTIVE="${ENVTYPE}"
elif [ "x$ENVTYPE" = "xtest" ]; then
  export SPRING_PROFILES_ACTIVE="${ENVTYPE}"
elif [ "x$ENVTYPE" = "xprod" ]; then
  export SPRING_PROFILES_ACTIVE="${ENVTYPE}"
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
USER_OPTS="$USER_OPTS -Dorg.gradle.daemon=false"
