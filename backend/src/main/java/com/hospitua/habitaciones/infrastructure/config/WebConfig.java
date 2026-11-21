package com.hospitua.habitaciones.infrastructure.config;

import com.hospitua.habitaciones.infrastructure.adapters.in.web.IntegrationKeyInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra la verificación de llaves de integración sobre la API (plan de integración, Fase 1). */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final IntegrationKeyInterceptor integrationKeyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(integrationKeyInterceptor).addPathPatterns("/api/**");
    }
}
