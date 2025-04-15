package com.ke.bella.files;

import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;
import com.ke.bella.files.configuration.ApolloConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import(ApolloConfig.class)
@SpringBootApplication(scanBasePackages = { "com.ke.bella.files" })
@EnableApolloConfig
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
