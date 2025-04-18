package com.ke.bella.files.service.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "bella.file-api.storage.local")
public class LocalStorageConfig {
    /**
     * 本地存储根目录路径
     */
    private String rootPath = "/tmp/bella-files";
}
