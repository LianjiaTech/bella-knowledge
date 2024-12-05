package com.ke.bella.files.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@Data
public class BucketConfig {
    @Value("${bella.file-api.bucket-name.vision}")
    private String visionBucket;
    @Value("${bella.file-api.bucket-name.general}")
    private String generalBucket;
}
