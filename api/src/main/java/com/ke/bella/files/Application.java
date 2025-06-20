package com.ke.bella.files;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import com.ke.bella.files.configuration.ApolloConfig;
import com.ke.bella.openapi.login.config.EnableBellaLogin;

@Import(ApolloConfig.class)
@EnableBellaLogin
@SpringBootApplication(scanBasePackages = { "com.ke.bella.files" })
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
