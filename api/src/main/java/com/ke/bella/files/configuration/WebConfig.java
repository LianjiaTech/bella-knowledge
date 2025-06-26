package com.ke.bella.files.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.ke.bella.files.api.interceptor.RequestInterceptor;
import com.ke.bella.openapi.request.BellaRequestFilter;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private RequestInterceptor requestInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestInterceptor)
                .addPathPatterns("/**")
                .order(Ordered.HIGHEST_PRECEDENCE + 1001);
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
                .defaultContentType(MediaType.APPLICATION_JSON)
                .ignoreAcceptHeader(true);
    }

    @Bean
    public FilterRegistrationBean<BellaRequestFilter> bellaRequestFilter() {
        FilterRegistrationBean<BellaRequestFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        BellaRequestFilter bellaRequestFilter = new BellaRequestFilter(Configs.SERVICE_NAME);
        filterRegistrationBean.setFilter(bellaRequestFilter);
        filterRegistrationBean.setOrder(Integer.MIN_VALUE + 1);
        return filterRegistrationBean;
    }
}
