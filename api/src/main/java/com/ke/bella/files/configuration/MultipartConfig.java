package com.ke.bella.files.configuration;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

/**
 * Multipart文件上传优化配置
 *
 * 优化策略：
 * 增大Tomcat的输入缓冲区，减少系统调用次数
 */
@Configuration
public class MultipartConfig {


    /**
     * 自定义Tomcat配置，优化文件上传性能
     *
     * 优化Tomcat从网络读取数据到JVM内存的过程
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> {
            factory.addConnectorCustomizers(connector -> {
                connector.setProperty("socketBuffer", String.valueOf(2 * 1024 * 1024));
            });
        };
    }

    /**
     * 显式配置StandardServletMultipartResolver
     *
     * StandardServletMultipartResolver基于Servlet 3.0+规范，性能优于CommonsMultipartResolver：
     * 1. 使用Tomcat原生实现，减少一层抽象
     * 2. 支持异步处理
     * 3. 更好的内存管理
     */
    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }
}
