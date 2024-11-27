package com.ke.bella.files.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Configs {
    public static Long MAX_SIZE_IN_MB;
    public static String OPEN_API_BASE;

    @Value("${file-api.file.max_size_in_MB}")
    public void setMaxSizeInMB(Long maxSizeInMB) {
        MAX_SIZE_IN_MB = maxSizeInMB;
    }

    @Value("${bella.open_api_base}")
    public void setOpenApiBase(String openApiBase) {
        OPEN_API_BASE = openApiBase;
    }
}
