package com.ke.bella.files.configuration;

import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "apollo.enabled", havingValue = "true", matchIfMissing = false)
@EnableApolloConfig
public class ApolloConfig {
}
