FROM maven:3.8.4-openjdk-8 AS builder

# 设置工作目录
WORKDIR /build

# 复制 API 源代码和脚本
COPY api/ .

# 接收Maven镜像源参数，默认使用aliyun
ARG MAVEN_MIRROR=aliyun

# 复制Maven配置文件
RUN mkdir -p /root/.m2
COPY api/settings-${MAVEN_MIRROR}.xml /root/.m2/settings.xml

# 设置脚本权限
RUN chmod +x build.sh run.sh setenv.sh

# 接收构建参数
ARG MAVEN_ARGS="-Dmaven.test.skip=true -U -Dmaven.build.cache.enabled=false"

# 执行构建
RUN bash build.sh ${MAVEN_ARGS}

# 第二阶段：运行环境
FROM openjdk:8

# 设置环境变量
ENV IDC zeus
ENV MODULE bella-file-api
ENV ENVTYPE test
ENV DEBUGPORT 9008
ENV JMXPORT 9009
ENV MATRIX_CODE_DIR /opt/bella-file-api/htdocs
ENV MATRIX_APPLOGS_DIR /opt/bella-file-api/applogs
ENV MATRIX_ACCESSLOGS_DIR /opt/bella-file-api/logs
ENV MATRIX_LOGS_DIR /opt/bella-file-api/logs
ENV MATRIX_CACHE_DIR /opt/bella-file-api/cache
ENV MATRIX_PRIVDATA_DIR /opt/bella-file-api/privdata

# 创建必要的目录
RUN mkdir -p /opt/bella-file-api/htdocs/bin /opt/bella-file-api/htdocs/lib \
    /opt/bella-file-api/applogs /opt/bella-file-api/logs \
    /opt/bella-file-api/cache /opt/bella-file-api/privdata

# 从构建阶段复制构建结果
COPY --from=builder /build/release/bin/* /opt/bella-file-api/htdocs/bin/
COPY --from=builder /build/release/lib/* /opt/bella-file-api/htdocs/lib/

# 设置可执行权限
RUN chmod +x /opt/bella-file-api/htdocs/bin/*.sh

# 暴露端口
EXPOSE 8080 9008 9009

# 设置工作目录
WORKDIR /opt/bella-file-api/htdocs

# 声明卷
VOLUME ["/opt/bella-file-api/applogs", "/opt/bella-file-api/logs", "/opt/bella-file-api/cache", "/opt/bella-file-api/privdata"]

# 启动命令
CMD ["/bin/bash", "-x", "/opt/bella-file-api/htdocs/bin/run.sh"]
