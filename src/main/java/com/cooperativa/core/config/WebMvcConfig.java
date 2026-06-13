package com.cooperativa.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private TenantInterceptor tenantInterceptor;

    @Autowired
    private com.cooperativa.core.security.JwtSecurityInterceptor jwtSecurityInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. TenantInterceptor se ejecuta primero para establecer el inquilino si viene la cabecera
        registry.addInterceptor(tenantInterceptor).addPathPatterns("/**");
        
        // 2. JwtSecurityInterceptor valida la sesion y alinea/completa el inquilino activo
        registry.addInterceptor(jwtSecurityInterceptor).addPathPatterns("/**");
    }

    @Override
    public void addResourceHandlers(org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
        String uploadPath = System.getProperty("user.dir") + "/uploads/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath);
    }
}
