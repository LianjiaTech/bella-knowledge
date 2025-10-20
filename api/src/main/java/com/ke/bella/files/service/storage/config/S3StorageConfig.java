package com.ke.bella.files.service.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "bella.file-api.storage.s3")
public class S3StorageConfig {
    private String ak;
    private String sk;
    private String endpoint;
    /**
     * 取值为枚举的name值
     *{@link software.amazon.awssdk.regions.Region}
     */
    private String region = "";

    /**
     * HTTP连接池最大连接数，建议根据压测结果调整
     */
    private int maxConcurrency = 200;

    /**
     * 连接超时时间（秒）
     */
    private int connectionTimeoutSeconds = 60;

    /**
     * 连接最大空闲时间（秒）
     * 建议设置较长时间以复用连接，减少TLS握手开销
     */
    private int connectionMaxIdleSeconds = 300;

    /**
     * 是否启用 TCP KeepAlive
     * 保持长连接，避免频繁建立和销毁连接
     * 减少 TLS/SSL 握手开销，降低 CPU 占用
     */
    private boolean tcpKeepAlive = true;
}
