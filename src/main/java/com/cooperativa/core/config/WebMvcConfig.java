package com.cooperativa.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

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
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/uploads/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addResourceHandlers(org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
        String uploadPath = System.getProperty("user.dir") + "/uploads/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath);
    }
}
