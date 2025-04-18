package com.ke.bella.files.service.storage;

import com.ke.bella.files.service.storage.config.LocalStorageConfig;
import com.ke.bella.files.service.storage.config.OssStorageConfig;
import com.ke.bella.files.service.storage.config.S3StorageConfig;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageServiceFactory {
    @Value("${bella.file-api.storage.type:local}")
    private String storageType;

    @Bean
    @ConditionalOnMissingBean
    public StorageService storageService(
            S3StorageConfig s3StorageConfig,
            OssStorageConfig ossStorageConfig,
            LocalStorageConfig localStorageConfig) {

        switch (storageType.toLowerCase()) {
        case "s3":
            return new S3StorageService(getConfig(s3StorageConfig, S3StorageConfig.class));
        case "oss":
            return new S3StorageService(getConfig(ossStorageConfig, OssStorageConfig.class));
        case "local":
        default:
            return new LocalStorageService(getConfig(localStorageConfig, LocalStorageConfig.class));
        }
    }

    private <T> T getConfig(T config, Class<T> tClass) {
        String configJson = System.getProperty("bella.storage.config");
        if(StringUtils.isNotBlank(configJson)) {
            return JacksonUtils.deserialize(configJson, tClass);
        }
        return config;
    }
}
