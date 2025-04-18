package com.ke.bella.files.service.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * oss可完全兼容s3
 */
@Component
@ConfigurationProperties(prefix = "bella.file-api.storage.oss")
public class OssStorageConfig extends S3StorageConfig {
}
