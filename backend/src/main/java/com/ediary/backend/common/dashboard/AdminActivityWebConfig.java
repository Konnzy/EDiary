package com.ediary.backend.common.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AdminActivityWebConfig implements WebMvcConfigurer {

    private final AdminActivityInterceptor adminActivityInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminActivityInterceptor).addPathPatterns("/api/admin/**");
    }
}
