package com.ke.bella.files.configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@Data
public class BucketConfig {
    @Value("${bella.file-api.bucket-name.public}")
    private String publicBucket;
    @Value("${bella.file-api.bucket-name.private}")
    private String privateBucket;
    /**
     * Comma-separated buckets that import-from-path may read from directly,
     * so business teams can migrate without copying objects first.
     */
    @Value("${bella.file-api.bucket-name.import-sources:}")
    private String importSources;

    public Set<String> getImportSourceBuckets() {
        if(StringUtils.isBlank(importSources)) {
            return Collections.emptySet();
        }
        return Arrays.stream(importSources.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toSet());
    }
}
