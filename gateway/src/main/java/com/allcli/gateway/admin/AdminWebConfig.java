package com.allcli.gateway.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 注册管理面鉴权拦截器（/actuator 与 /mcp 不在范围）。 */
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;

    public AdminWebConfig(@Value("${allcli.admin.tokens:}") String adminTokensCsv) {
        this.adminAuthInterceptor = new AdminAuthInterceptor(adminTokensCsv);
    }

    @Bean
    public AdminAuthInterceptor adminAuthInterceptorBean() {
        return adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor).addPathPatterns("/api/v1/**");
    }
}
