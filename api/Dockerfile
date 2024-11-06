FROM openjdk:8

ENV IDC zeus
ENV MODULE example-file-api
ENV ENVTYPE test
ENV DEBUGPORT 9008
ENV JMXPORT 9009
ENV MATRIX_CODE_DIR /opt/example-file-api/htdocs
ENV MATRIX_APPLOGS_DIR /opt/example-file-api/applogs
ENV MATRIX_ACCESSLOGS_DIR /opt/example-file-api/logs
ENV MATRIX_LOGS_DIR /opt/example-file-api/logs
ENV MATRIX_CACHE_DIR /opt/example-file-api/cache
ENV MATRIX_PRIVDATA_DIR /opt/example-file-api/privdata

COPY release/ /opt/example-file-api/htdocs/
RUN chmod +x /opt/example-file-api/htdocs/bin/*.sh

EXPOSE 8080 9008 9009
WORKDIR /opt/example-file-api/htdocs
VOLUME ["/opt/example-file-api/applogs", "/opt/example-file-api/logs", "/opt/example-file-api/cache", "/opt/example-file-api/privdata"]
CMD ["/bin/bash", "-x", "/opt/example-file-api/htdocs/bin/run.sh"]