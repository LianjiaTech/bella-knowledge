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
     *{@link com.amazonaws.regions.Regions}
     */
    private String region = "";
}
