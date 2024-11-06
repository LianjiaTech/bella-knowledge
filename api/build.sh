#!/bin/sh
# 构建命令为 sh build.sh ARGS_DEV, 可传入ARGS_DEV作为用户变量 如 -Dmaven.test.skip=true



ARGS_DEV="$@"

set -e

rm -rf release/
mvn clean package ${ARGS_DEV}

mkdir -p release/{bin,lib}
chmod +x setenv.sh && cp setenv.sh release/bin/
chmod +x run.sh && cp run.sh release/bin/
cp target/*.jar release/lib/
tar czvf release.tar.gz release
