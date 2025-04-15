package com.ke.bella.files.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "apollo.enabled", havingValue = "true", matchIfMissing = false)
public class ApolloConfig {
}
