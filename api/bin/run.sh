#!/bin/bash

set -e

if [ "$JAVA_HOME" = "" ]; then
    echo "Error: JAVA_HOME is not set."
    exit 1
fi

JAVA_COMMENT="$JAVA_HOME/bin/java"

if [ "$Xmn" = "" ]; then
    export Xmn="1024m"
fi

if [ "$Xms" = "" ]; then
    export Xms="4096m"
fi

if [ "$Xmx" = "" ]; then
    export Xmx="4096m"
fi

if [ "$MetaspaceSize" = "" ]; then
    export MetaspaceSize="512m"
fi

if [ "$MaxMetaspaceSize" = "" ]; then
    export MaxMetaspaceSize="512m"
fi

if [ "$CMSINIT" = "" ]; then
    export CMSINIT="70"
fi
#jvm 参数
JAVA_OPTS="$JAVA_OPTS -server -Xms${Xms} -Xmx${Xmx}"
JAVA_OPTS="$JAVA_OPTS -XX:MetaspaceSize=${MetaspaceSize} -XX:MaxMetaspaceSize=${MaxMetaspaceSize}"
JAVA_OPTS="$JAVA_OPTS -Dlogging.path=${MATRIX_APPLOGS_DIR}"
JAVA_OPTS="$JAVA_OPTS -Dsun.net.inetaddr.ttl=1 -Dsun.net.inetaddr.negative.ttl=1"
JAVA_OPTS="$JAVA_OPTS -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:${MATRIX_ACCESSLOGS_DIR}/gc-%t.log"
Xmn_OPTS="-Xmn${Xmn}"
GC_OPTS="$GC_OPTS -XX:+UseConcMarkSweepGC -XX:+UseParNewGC -XX:+CMSParallelRemarkEnabled -XX:+UseFastAccessorMethods"
GC_OPTS="$GC_OPTS -XX:CMSInitiatingOccupancyFraction=${CMSINIT} -XX:+CMSScavengeBeforeRemark -XX:+ExplicitGCInvokesConcurrent"

# 测试、预览开启DEBUG功能，生产开启JMX
if [ "$ENVTYPE" != "" ]; then
    JAVA_OPTS="$JAVA_OPTS -Djavax.net.debug=ssl -Xdebug -Xnoagent -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=$DEBUGPORT"
    JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=$JMXPORT -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=$HOSTNAME"
else
    JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=$JMXPORT -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=$HOSTNAME"
fi

#待启动jar
JARFILE=`find "$MATRIX_CODE_DIR/lib" -name *.jar`

#加载个性化配置
if [ -f "${MATRIX_CODE_DIR}/bin/setenv.sh" ];then
    echo ${MATRIX_CODE_DIR}/bin/setenv.sh
    source "${MATRIX_CODE_DIR}/bin/setenv.sh"
fi

if [[ "${GC_OPTS}" =~ UseG1GC ]];then
    if [[ "${USER_OPTS}" != "" ]]; then
        if [[ "${USER_ARGS}" != "" ]];then
            echo "$JAVA_COMMENT $JAVA_OPTS $GC_OPTS ${USER_OPTS} -jar $JARFILE ${USER_ARGS}"
            exec $JAVA_COMMENT $JAVA_OPTS $GC_OPTS ${USER_OPTS} -jar $JARFILE ${USER_ARGS}
        else
            echo "$JAVA_COMMENT $JAVA_OPTS $GC_OPTS ${USER_OPTS} -jar $JARFILE"
            exec $JAVA_COMMENT $JAVA_OPTS $GC_OPTS ${USER_OPTS} -jar $JARFILE
        fi
    else
        echo "$JAVA_COMMENT $JAVA_OPTS $GC_OPTS -jar $JARFILE ${USER_ARGS}"
        exec $JAVA_COMMENT $JAVA_OPTS $GC_OPTS -jar $JARFILE ${USER_ARGS}
    fi
else
    if [[ "${USER_OPTS}" != "" ]]; then
        if [[ "${USER_ARGS}" != "" ]];then
            echo "$JAVA_COMMENT $JAVA_OPTS $Xmn_OPTS $GC_OPTS ${USER_OPTS} -jar $JARFILE ${USER_ARGS}"
            exec $JAVA_COMMENT $JAVA_OPTS $Xmn_OPTS $GC_OPTS ${USER_OPTS} -jar $JARFILE ${USER_ARGS}
        else
            echo "$JAVA_COMMENT $JAVA_OPTS $Xmn_OPTS $GC_OPTS ${USER_OPTS} -jar $JARFILE"
            exec $JAVA_COMMENT $JAVA_OPTS $Xmn_OPTS $GC_OPTS ${USER_OPTS} -jar $JARFILE
        fi
    else
        echo "$JAVA_COMMENT $JAVA_OPTS $Xmn_OPTS $GC_OPTS -jar $JARFILE ${USER_ARGS}"
        exec $JAVA_COMMENT $JAVA_OPTS $Xmn_OPTS $GC_OPTS -jar $JARFILE ${USER_ARGS}
    fi
fi
