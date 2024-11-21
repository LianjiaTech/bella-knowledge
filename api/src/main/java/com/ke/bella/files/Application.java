package com.ke.bella.files;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;

/**
 * 服务启动类
 *
 * @author keboot
 */
@EnableApolloConfig
@SpringBootApplication(scanBasePackages = { "com.ke.bella.files" })
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
