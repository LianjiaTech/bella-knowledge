package com.ke.bella.files.service.storage.ke;

import com.ke.bella.files.service.storage.config.S3StorageConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "bella.file-api.storage.ke")
public class KeStorageConfig extends S3StorageConfig {
    private String endpointReadPrivate;
    private String endpointReadPublic;
    private String endpointPreview;
    private String akPreview;
    private String skPreview;
}
