package com.ke.bella.files;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;

import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;

@SpringBootApplication(exclude = { DataSourceTransactionManagerAutoConfiguration.class }, scanBasePackages = { "com.ke.bella.files" })
public class TestApplication {
}
