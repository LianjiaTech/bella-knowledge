package com.ke.bella.files.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@Data
public class S3Config {
    @Value("${bella.file-api.bucket-name.vision}")
    private String visionBucket;
    @Value("${bella.file-api.bucket-name.general}")
    private String generalBucket;

    /**
     * 多媒体预览服务
     *
     * @return
     */
    @Bean
    public MediaServiceConfig previewServiceConfig(@Value("${s3.endpoint_preview}") String enpoint,
            @Value("${s3.ak_preview}") String ak,
            @Value("${s3.sk_preview}") String sk) {
        MediaServiceConfig config = new MediaServiceConfig();
        config.setEndpoint(enpoint);
        config.setAk(ak);
        config.setSk(sk);
        return config;
    }

    /**
     * 多媒体服务配置项
     */
    @Data
    public static class MediaServiceConfig {
        private String endpoint;
        private String ak;
        private String sk;
    }
}
