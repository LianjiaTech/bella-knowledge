package com.ke.bella.files.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Configs {
    public static Long MAX_SIZE_IN_MB;
    public static String OPEN_API_BASE;

    @Value("${bella.file-api.file.max-size-in-MB}")
    public void setMaxSizeInMB(Long maxSizeInMB) {
        MAX_SIZE_IN_MB = maxSizeInMB;
    }

    @Value("${bella.open-api-base}")
    public void setOpenApiBase(String openApiBase) {
        OPEN_API_BASE = openApiBase;
    }
}
