#!/bin/bash

set -e

JAVA_COMMENT="java"

# JVM 参数
JAVA_OPTS="$JAVA_OPTS -Dlogging.path=${MATRIX_APPLOGS_DIR}"
JAVA_OPTS="$JAVA_OPTS -Dsun.net.inetaddr.ttl=1 -Dsun.net.inetaddr.negative.ttl=1"
JAVA_OPTS="$JAVA_OPTS -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:${MATRIX_ACCESSLOGS_DIR}/gc-%t.log"

# GC 参数
GC_OPTS="$GC_OPTS -XX:+UseG1GC -XX:G1HeapRegionSize=4m -XX:MaxGCPauseMillis=500 -XX:InitiatingHeapOccupancyPercent=50 -XX:G1ReservePercent=10"
GC_OPTS="$GC_OPTS -XX:+UnlockExperimentalVMOptions -XX:+G1EagerReclaimHumongousObjects -XX:+G1TraceEagerReclaimHumongousObjects -XX:G1LogLevel=finest"
GC_OPTS="$GC_OPTS -XX:+ParallelRefProcEnabled -XX:ParallelGCThreads=8 -XX:ConcGCThreads=4 -XX:+ExplicitGCInvokesConcurrent -XX:+UseFastAccessorMethods"

# 测试、预览开启DEBUG功能，生产开启JMX
if [ "$ENVTYPE" != "" ]; then
    JAVA_OPTS="$JAVA_OPTS -Djavax.net.debug=ssl -Xdebug -Xnoagent -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=$DEBUGPORT"
    JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=$JMXPORT -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=$HOSTNAME"
else
    JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=$JMXPORT -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=$HOSTNAME"
fi

# 待启动jar
JARFILE=`find "$MATRIX_CODE_DIR/lib" -name *.jar`

# 启动应用
echo "$JAVA_COMMENT $JAVA_OPTS $GC_OPTS -jar $JARFILE ${USER_ARGS}"
exec $JAVA_COMMENT $JAVA_OPTS $GC_OPTS -jar $JARFILE ${USER_ARGS}
